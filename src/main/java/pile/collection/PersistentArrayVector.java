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

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import pile.core.AbstractSeq;
import pile.core.ISeq;
import pile.core.PObj;
import pile.util.Pair;

public class PersistentArrayVector<V> extends PersistentVector<V> implements PObj {

    public static <V> PersistentArrayVector<V> empty() {
        return new PersistentArrayVector<>();
    }

    private static final int SHIFT = 4;
    private static final int NODE_SIZE = 16;// = (int) Math.pow(2, SHIFT);
    private static final int MASK = NODE_SIZE - 1;

    private final int levels;
    private final int count;
    private final int min, max;
    private final Node root;
    private final PersistentMap meta;

    private PersistentArrayVector(PersistentMap meta, Node root, int count, int levels, int min, int max) {
        this.meta = meta;
        this.root = root;
        this.count = count;
        this.levels = levels;
        this.min = min;
        this.max = max;
    }

    private PersistentArrayVector(Object[] root, int count, int levels, int min, int max) {
        this(PersistentMap.EMPTY, new Node(root), count, levels, min, max);
    }

    public PersistentArrayVector() {
        this(PersistentMap.EMPTY, new Node(new Object[NODE_SIZE]), 0, 0, 0, NODE_SIZE);
    }

    @Override
    public int count() {
        return count;
    }

    @Override
    public boolean containsKey(Integer key) {
        return key < count;
    }

    @Override
    public Pair<Integer, V> entryAt(Integer key) {
        return doFind(key, (arr, findex) -> new Pair<>(key, (V) arr[findex]));
    }

    @Override
    public PersistentArrayVector<V> assoc(Integer key, V val) {
        // TODO check count
        Node newRoot = edit(root, levels, key, val, (arr, newind) -> arr[newind] = val);
        return new PersistentArrayVector<>(meta, newRoot, count, levels, min, max);
    }

    @Override
    public PersistentArrayVector<V> conj(V t) {
        return push(t);
    }

    public PersistentArrayVector<V> push(V v) {
        if (count() == max) {
            Node newRoot = new Node();
            newRoot.data[0] = root;
            newRoot = edit(newRoot, levels + 1, count, v, (arr, key) -> arr[key] = v);
            return new PersistentArrayVector<V>(meta, newRoot, count + 1, levels + 1, max, max << SHIFT);
        } else {
            Node newRoot = edit(root, levels, count(), v, (arr, key) -> arr[key] = v);
            return new PersistentArrayVector<>(meta, newRoot, count + 1, levels, min, max);
        }
    }

    public PersistentArrayVector<V> pop() {
        if (count() == 0) {
            throw new NoSuchElementException();
        } else if (count() == 1) {
            return new PersistentArrayVector<>();
        } else if (count() == min) {
            Object[] newRootData = ((Node) root.data[0]).data;
            return new PersistentArrayVector<>(newRootData, count - 1, levels - 1, min << SHIFT, min);
        } else {
            Node newRoot = edit(root, levels, count(), null, (arr, newind) -> arr[newind] = null);
            return new PersistentArrayVector<>(meta, newRoot, count - 1, levels, min, max);
        }
    }

    @Override
    public PersistentMap meta() {
        return meta;
    }

    @Override
    public PersistentArrayVector<V> withMeta(PersistentMap newMeta) {
        return new PersistentArrayVector<>(newMeta, root, count, levels, min, max);
    }

    private Object[] arrayFor(int key) {
        Node local = root;
        int nextEntry = key;
        for (int levelAt = levels; levelAt > 0; --levelAt) {
            nextEntry = (key >>> (SHIFT * levelAt)) & MASK;
            local = (Node) local.data[nextEntry];
        }
        return local.data;
    }

    private <T> T doFind(int key, BiFunction<Object[], Integer, T> fn) {
        int indexInArr = key % NODE_SIZE;
        return fn.apply(arrayFor(key), indexInArr);
    }

    /**
     * Generates a new tree computing the provided function on the leaf node/slot
     * associated with the provided key.
     * 
     * @param root  The node we're inspecting
     * @param level The level the root node is at
     * @param key   The int key slot the values should go in
     * @param v     The value
     * @param fn    The function which takes in the subvector and slot in that
     *              subvector the key are associated with. The vector will be a
     *              mutable copy and will be the leaf node in the new rooted tree.
     * @return A new tree in which the edit has been made.
     */
    private Node edit(Node root, int level, int key, V v, BiConsumer<Object[], Integer> fn) {
        Node local;
        if (root == null) {
            local = new Node();
        } else {
            local = root.copy();
        }
        int nextEntry = (key >>> (SHIFT * level)) & MASK;
        if (level == 0) {
            fn.accept(local.data, nextEntry);
            return local;
        } else {
            Node last = (Node) local.data[nextEntry];
            local.data[nextEntry] = edit(last, level - 1, key, v, fn);
        }

        return local;
    }

    @Override
    public ISeq<V> seq() {
        if (count() == 0) {
            return ISeq.EMPTY;
        } else {
            return new PVSeq(0, arrayFor(0));
        }
    }

    @Override
    public boolean contains(Object o) {
        for (Object ours : this) {
            if (Objects.equals(ours, o)) {
                return true;
            }
        }
        return false;
    }

//	@Override
//	public V get(int index) {
//		return entryAt(index).right();
//	}

    @Override
    public Associative<Integer, V> dissoc(Integer key, V val) {
        if (key == count && doFind(key, (arr, n) -> arr[n].equals(val))) {
            return pop();
        }
        throw new IllegalArgumentException("Cannot dissoc from internal nodes");
    }

    @Override
    public Associative<Integer, V> dissoc(Integer key) {
        if (key == count) {
            return pop();
        }
        throw new IllegalArgumentException("Cannot dissoc from internal nodes");
    }

    @Override
    public Iterator<V> iterator() {
        if (count() == 0) {
            return Collections.emptyIterator();
        }
        return super.iterator();
    }

    private class PVSeq extends AbstractSeq<V> {

        private final int idx;
        private final Object[] current;

        public PVSeq(int idx, Object[] current) {
            super();
            this.idx = idx;
            this.current = current;
        }

        @Override
        public V first() {
            return (V) current[idx % NODE_SIZE];
        }

        @Override
        public ISeq<V> next() {
            int nextIdx = idx + 1;
            if (nextIdx == count) {
                return ISeq.EMPTY;
            }
            Object[] arr = current;
            if (nextIdx % NODE_SIZE == 0) {
                arr = arrayFor(nextIdx);
            }
            return new PVSeq(nextIdx, arr);

        }

    }

    private static class Node {

        private final Object[] data;

        public Node() {
            this(new Object[NODE_SIZE]);
        }

        public Node(Object[] data) {
            super();
            this.data = data;
        }

        public Node copy() {
            Object[] copy = new Object[data.length];
            System.arraycopy(this.data, 0, copy, 0, data.length);
            return new Node(copy);
        }
    }

}
