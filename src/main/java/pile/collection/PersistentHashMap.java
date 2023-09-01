/**
 * Copyright 2023 John Hinchberger
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pile.collection;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import pile.core.ArraySeq;
import pile.core.Conjable;
import pile.core.ISeq;
import pile.core.PObj;
import pile.core.Seqable;
import pile.nativebase.NativeCore;
import pile.util.Pair;

public class PersistentHashMap<K, V> extends PersistentMap<K, V> implements PObj, Conjable {

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
            if (KEY_EQ.test(this.k, k)) {
                return new SingleEntry(this.shift, this.hash, this.k, v);
            } else {
                if (this.hash == hash) {
                    Node<K, V> coll = new CollisionNode<>(shift, hash);
                    coll = coll.add(hash, this.k, this.v);
                    coll = coll.add(hash, k, v);
                    return coll;
                } else {
                    Node<K, V> multi = new BitmapMultiEntry<>(shift);
                    multi = multi.add(this.hash, this.k, this.v);
                    multi = multi.add(hash, k, v);
                    return multi;
                }

            }
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
            this.bitmap = bitmap;
            this.slots = slots;
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

        private int nextShift() {
            return shift + 5;
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

        private Node<K, V> editRemove(int hash, UnaryOperator<Node<K, V>> fn) {
            Pair<Integer, Boolean> p = getActualSlot(hash);
            if (!p.right()) {
                // Empty
                return this;
            } else {
                Integer actualSlot = p.left();
                Node<K, V> newentry = fn.apply(slots[actualSlot]);
                if (newentry == null) {
                    if (slots.length == 1) {
                        return null;
                    } else {
                        Node<K, V>[] without = copyWithout(slots, actualSlot);
                        int newBitmap = (~(Integer.MIN_VALUE >>> logicalSlot(hash))) & bitmap;
                        return new BitmapMultiEntry(shift, newBitmap, without);
                    }
                } else {
                    Node<K, V>[] copy = copy(slots);
                    copy[actualSlot] = newentry;
                    return new BitmapMultiEntry(shift, bitmap, copy);
                }
            }
        }

        private Node<K, V>[] copyWithout(Node<K, V>[] arr, Integer actualSlot) {
            Node<K, V>[] out = new Node[arr.length - 1];
            if (actualSlot != 0) {
                System.arraycopy(arr, 0, out, 0, actualSlot);
            }
            if (actualSlot != (arr.length - 1)) {
                System.arraycopy(arr, actualSlot + 1, out, actualSlot, arr.length - actualSlot - 1);
            }
            return out;
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

    private static class CollisionNode<K, V> implements Node<K, V> {

        private final int shift;
        private final int hash;
        // FIXME suboptimal
        private final List<Entry<K, V>> entries;

        public CollisionNode(int shift, int hash) {
            this(shift, hash, Collections.emptyList());
        }

        public CollisionNode(int shift, int hash, List<Entry<K, V>> entries) {
            super();
            this.shift = shift;
            this.hash = hash;
            this.entries = entries;
        }

        @Override
        public ISeq<Entry<K, V>> seq() {
            return NativeCore.seq(entries);
        }

        @Override
        public Entry<K, V> find(int hash, K key) {
            int slot = slot(key);
            if (slot == -1) {
                return null;
            } else {
                return entries.get(slot);
            }
        }

        private int slot(K key) {
            int slot = 0;
            for (Entry<K, V> e : entries) {
                if (KEY_EQ.test(e.getKey(), key)) {
                    return slot;
                }
                ++slot;
            }
            return -1;
        }

        @Override
        public Node<K, V> add(int hash, K k, V v) {
            if (hash == this.hash) {
                // our hash
                int slot = slot(k);
                List<Entry<K, V>> copied = new ArrayList<>(entries);
                Entry<K, V> entry = entry(k, v);
                if (slot == -1) {
                    // New key
                    copied.add(entry);
                } else {
                    // extant key
                    copied.set(slot, entry);
                }
                return new CollisionNode<>(shift, hash, copied);
            } else {
                Node<K, V> bitmap = new BitmapMultiEntry<>(shift);
                bitmap = bitmap.add(HASHER.applyAsInt(k), k, v);
                for (Entry<K, V> entry : entries) {
                    int ehash = HASHER.applyAsInt(entry.getKey());
                    bitmap = bitmap.add(ehash, k, v);
                }
                return bitmap;
            }
        }

        @Override
        public Node<K, V> remove(int hash, K k) {
            int slot = slot(k);
            if (slot == -1) {
                return this;
            } else {
                List<Entry<K, V>> copied = new ArrayList<>(entries);
                copied.remove(slot);
                return newFromCopied(copied);
            }
        }

        private Node<K, V> newFromCopied(List<Entry<K, V>> copied) {
            if (copied.size() == 1) {
                Entry<K, V> entry = copied.get(0);
                return new SingleEntry<>(shift, hash, entry.getKey(), entry.getValue());
            } else {
                return new CollisionNode<>(shift, hash, copied);
            }
        }

        @Override
        public Node<K, V> remove(int hash, K k, V v) {
            int slot = slot(k);
            if (slot == -1) {
                return this;
            } else {
                List<Entry<K, V>> copied = new ArrayList<>(entries);
                Entry<K, V> entry = copied.get(slot);
                if (VAL_EQ.test(v, entry.getValue())) {
                    return newFromCopied(copied);
                } else {
                    return this;
                }
            }
        }

    }

    private final Node<K, V> root;
    private final int count;
    private final Optional<V> nullValue;
    private final PersistentMap meta;

    public PersistentHashMap() {
        this(new Empty<>(), 0, Optional.empty(), EMPTY);
    }

    private PersistentHashMap(PersistentHashMap<K, V> base, Node<K, V> newRoot, int count) {
        // root == null could propagate from a complete remove of the map
        this(newRoot == null ? new Empty() : newRoot, count, base.nullValue, base.meta);
    }

    private PersistentHashMap(Node<K, V> root, int count, Optional<V> nullValue, PersistentMap meta) {
        this.root = root;
        this.count = count;
        this.nullValue = nullValue;
        this.meta = meta;
    }

    @Override
    public PersistentMap meta() {
        return meta;
    }

    @Override
    public PersistentHashMap<K, V> withMeta(PersistentMap newMeta) {
        return new PersistentHashMap<>(root, count, nullValue, newMeta);
    }

    @Override
    public ISeq<Entry<K, V>> seq() {
        if (nullValue.isEmpty()) {
            return root.seq();
        } else {
            return ISeq.of(entry((K) null, nullValue.get())).concat(root.seq());
        }
    }

    @Override
    public int count() {
        return count;
    }

    @Override
    public Pair<K, V> entryAt(K key) {
        if (count == 0) {
            return null;
        }
        if (key == null) {
            return (Pair<K, V>) nullValue.map(v -> new Pair<>(null, v)).orElse(null);
        }
        Entry<K, V> entry = root.find(HASHER.applyAsInt(key), key);
        if (entry == null) {
            return null;
        }
        return new Pair<>(entry.getKey(), entry.getValue());
    }

    @Override
    public PersistentHashMap<K, V> assoc(K key, V val) {
        return assocGeneric(key, val);
    }

    protected PersistentHashMap assocGeneric(Object key, Object val) {
        if (key == null) {
            return new PersistentHashMap<>(root, nullValue.isPresent() ? count : count + 1, Optional.of((V) val), meta);
        }
        Node<?, ?> newRoot = ((Node) root).add(HASHER.applyAsInt(key), key, val);
        if (root == newRoot) {
            return this;
        } else {
            return new PersistentHashMap(newRoot, count() + 1, nullValue, meta);
        }
    }

    @Override
    public PersistentHashMap<K, V> dissoc(K key) {
        if (key == null) {
            if (nullValue.isPresent()) {
                return new PersistentHashMap<>(root, count - 1, Optional.empty(), meta);
            } else {
                return this;
            }
        }
        Node<K, V> newRoot = root.remove(HASHER.applyAsInt(key), key);
        if (root == newRoot) {
            return this;
        } else {
            return new PersistentHashMap<K, V>(this, newRoot, count() - 1);
        }
    }

    @Override
    public PersistentMap<K, V> dissoc(K key, V val) {
        Node<K, V> newRoot = root.remove(HASHER.applyAsInt(key), key, val);
        if (root == newRoot) {
            return this;
        } else {
            return new PersistentHashMap<K, V>(this, newRoot, count() - 1);
        }
    }

    private static <K, V> Entry<K, V> entry(K k, V v) {
        return new AbstractMap.SimpleEntry<>(k, v);
    }

    public static <K, V> PersistentHashMap<K, V> empty() {
        return EMPTY;
    }

    public static final PersistentHashMap EMPTY = new PersistentHashMap<>();
}
