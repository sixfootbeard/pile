package pile.collection;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

import pile.collection.adapter.JavaImmutableMap;
import pile.core.ISeq;
import pile.core.Metadata;
import pile.core.PObj;
import pile.core.Seqable;
import pile.core.adapter.ArraySeq;
import pile.core.hierarchy.PersistentObject;
import pile.util.Pair;

public class PersistentHashMap<K, V> extends JavaImmutableMap<K, V>
        implements PObj, Associative<K, V>, Counted, Seqable<Entry<K, V>> {

    private interface Node<K, V> extends Seqable<Entry<K, V>> {
        Entry<K, V> find(int hash, K key);

        Node<K, V> add(int hash, K k, V v);

        Node<K, V> remove(int hash, K k);

        Node<K, V> remove(int hash, K k, V v);
    }

    private static class Empty<K, V> implements Node<K, V> {

        @Override
        public Entry<K, V> find(int hash, K key) {
            return null;
        }

        @Override
        public Node<K, V> add(int hash, K k, V v) {
            return new SingleEntry(0, hash, k, v);
        }

        @Override
        public Node<K, V> remove(int hash, K k) {
            return this;
        }

        @Override
        public Node<K, V> remove(int hash, K k, V v) {
            return this;
        }

        @Override
        public ISeq<Entry<K, V>> seq() {
            return null;
        }

    }

    private static class SingleEntry<K, V> implements Node<K, V> {

        private final int shift;
        private final int hash;
        private final K k;
        private final V v;

        public SingleEntry(int shift, int hash, K k, V v) {
            super();
            this.shift = shift;
            this.hash = hash;
            this.k = k;
            this.v = v;
        }

        @Override
        public Entry<K, V> find(int hash, K key) {
            if (this.hash == hash && KEY_EQ.test(this.k, key)) {
                return new AbstractMap.SimpleEntry<K, V>(k, v);
            }
            return null;
        }

        @Override
        public Node<K, V> add(int hash, K k, V v) {
            Node<K, V> multi = new BitmapMultiEntry(shift);
            multi = multi.add(this.hash, this.k, this.v);
            multi = multi.add(hash, k, v);
            return multi;
        }

        @Override
        public Node<K, V> remove(int hash, K k) {
            if (KEY_EQ.test(this.k, k)) {
                return null;
            } else {
                return this;
            }
        }

        @Override
        public Node<K, V> remove(int hash, K k, V v) {
            if (KEY_EQ.test(this.k, k) && VAL_EQ.test(this.v, v)) {
                return null;
            } else {
                return this;
            }
        }

        @Override
        public ISeq<Entry<K, V>> seq() {
            return ISeq.single(entry(k, v));
        }

    }

    private static class BitmapMultiEntry<K, V> implements Node<K, V> {

        private final int shift;
        private final int bitmap;
        private final Node<K, V>[] slots;

        public BitmapMultiEntry(int shift) {
            this(shift, 0, new Node[0]);
        }

        public BitmapMultiEntry(int shift, int bitmap, Node<K, V>[] slots) {
            super();
            this.shift = shift;
            this.slots = slots;
            this.bitmap = bitmap;
        }

        private static final int MASK = 31 << 27;

        private int logicalSlot(int hash) {
            return ((hash << shift) & MASK) >>> 27;
        }

        /**
         * Returns the actual slot the entry would exist in within the compressed array.
         * 
         * @param hash
         * @return
         */
        private Pair<Integer, Boolean> getActualSlot(int hash) {
            int logicalNum = logicalSlot(hash);
            boolean isSet = true;
            if (((Integer.MIN_VALUE >>> logicalNum) & bitmap) == 0) {
                isSet = false;
            }

            final int countAbove;
            if (logicalNum == 0) {
                countAbove = 0;
            } else {
                int mapMask = (Integer.MIN_VALUE >> (logicalNum - 1));
                countAbove = (Integer.bitCount(mapMask & bitmap));
            }
            return new Pair<>(countAbove, isSet);
        }

        private Node<K, V> editAdd(int hash, K k, V v, UnaryOperator<Node<K, V>> fn) {
            Pair<Integer, Boolean> slotIsSet = getActualSlot(hash);
            int actualSlot = slotIsSet.left();
            boolean isSet = slotIsSet.right();
            if (!isSet) {
                // Empty
                int logicalSlot = logicalSlot(hash);
                Node<K, V>[] copy = copy(slots, actualSlot);
                copy[actualSlot] = new SingleEntry(nextShift(), hash, k, v);
                int newBitmap = (Integer.MIN_VALUE >>> logicalSlot) | bitmap;
                return new BitmapMultiEntry(shift, newBitmap, copy);
            } else {
                Node<K, V> newentry = fn.apply(slots[actualSlot]);
                Node<K, V>[] copy = copy(slots);
                copy[actualSlot] = newentry;// new SingleEntry(nextShift(), hash, k, v);
                return new BitmapMultiEntry(shift, bitmap, copy);
            }
        }

        private int nextShift() {
            return shift + 5;
        }

        private Node<K, V> editRemove(int hash, UnaryOperator<Node<K, V>> fn) {
            Pair<Integer, Boolean> p = getActualSlot(hash);
            if (!p.right()) {
                // Empty
                return this;
            } else {
                Integer slot = p.left();
                Node<K, V> newentry = fn.apply(slots[slot]);
                Node<K, V>[] copy = copy(slots);
                copy[slot] = newentry;
                return new BitmapMultiEntry(shift, bitmap, copy);
            }
        }

        private Node<K, V>[] copy(Node<K, V>[] slots) {
            return Arrays.copyOf(slots, slots.length);
        }

        private Node<K, V>[] copy(Node<K, V>[] slots, int newPosition) {
            int newLen = slots.length + 1;
            Node<K, V>[] local = new Node[newLen];
            // 0, 1, 2, 3 ~ 1
            // 0, _, 1, 2, 3

            int preCopyLen = newPosition;
            if (preCopyLen > 0) {
                System.arraycopy(slots, 0, local, 0, preCopyLen);
            }

            int postCopyLen = slots.length - newPosition;
            if (postCopyLen > 0) {
                System.arraycopy(slots, newPosition, local, newPosition + 1, postCopyLen);
            }
            return local;
        }

        @Override
        public Entry<K, V> find(int hash, K key) {
            Pair<Integer, Boolean> p = getActualSlot(hash);
            if (!p.right()) {
                return null;
            } else {
                return slots[p.left()].find(hash, key);
            }
        }

        @Override
        public Node<K, V> add(int hash, K k, V v) {
            return editAdd(hash, k, v, node -> node.add(hash, k, v));
        }

        @Override
        public Node<K, V> remove(int hash, K k) {
            return editRemove(hash, node -> node.remove(hash, k));
        }

        @Override
        public Node<K, V> remove(int hash, K k, V v) {
            return editRemove(hash, node -> node.remove(hash, k, v));
        }

        @Override
        public ISeq<Entry<K, V>> seq() {
            ArraySeq<Node<K, V>> arr = new ArraySeq<>(slots, 0);
            return arr.flatMap(Node::seq);
        }

    }

    // TODO Fix this
    private static final ToIntFunction<Object> HASHER = Object::hashCode;
    private static final BiPredicate KEY_EQ = Objects::equals;
    private static final BiPredicate VAL_EQ = Objects::equals;

    //

    private final Node<K, V> root;
    private final int count;
    private final PersistentHashMap meta;

    public PersistentHashMap() {
        this(new Empty(), 0, null);
    }

    public PersistentHashMap(Node<K, V> newRoot, int count) {
        this(newRoot, count, null);
    }

    public PersistentHashMap(Node<K, V> root, int count, PersistentHashMap meta) {
        this.root = root;
        this.count = count;
        this.meta = meta;
    }

    @Override
    public PersistentHashMap meta() {
        return meta;
    }

    @Override
    public PersistentHashMap<K, V> withMeta(PersistentHashMap newMeta) {
        return new PersistentHashMap(root, count, newMeta);
    }

    @Override
    public ISeq<Entry<K, V>> seq() {
        return root.seq();
    }

    @Override
    public int count() {
        return count;
    }

    @Override
    public Pair<K, V> entryAt(K key) {
        Entry<K, V> entry = root.find(HASHER.applyAsInt(key), key);
        return new Pair<>(entry.getKey(), entry.getValue());
    }

    @Override
    public PersistentHashMap<K, V> assoc(K key, V val) {
        Node<K, V> newRoot = root.add(HASHER.applyAsInt(key), key, val);
        if (root == newRoot) {
            return this;
        } else {
            return new PersistentHashMap<K, V>(newRoot, count() + 1);
        }
    }

    @Override
    public Associative<K, V> dissoc(K key) {
        Node<K, V> newRoot = root.remove(HASHER.applyAsInt(key), key);
        if (root == newRoot) {
            return this;
        } else {
            return new PersistentHashMap<K, V>(newRoot, count() - 1);
        }
    }

    @Override
    public Associative<K, V> dissoc(K key, V val) {
        Node<K, V> newRoot = root.remove(HASHER.applyAsInt(key), key, val);
        if (root == newRoot) {
            return this;
        } else {
            return new PersistentHashMap<K, V>(newRoot, count() - 1);
        }
    }

    public int hashCode() {
        // TODO Memoize
        int base = 7;
        for (Entry<K, V> entry : seq()) {
            int hash = HASHER.applyAsInt(entry.getKey());
            base = 31 * base + hash;
        }
        return base;
    }

    private static <K, V> Entry<K, V> entry(K k, V v) {
        return new AbstractMap.SimpleEntry<>(k, v);
    }

}
