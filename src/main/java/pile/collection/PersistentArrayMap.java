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

import static pile.util.CollectionUtils.*;

import java.util.Arrays;

import pile.core.AbstractSeq;
import pile.core.ISeq;
import pile.nativebase.NativeCore;
import pile.util.Pair;

public class PersistentArrayMap<K, V> extends PersistentMap<K, V> {

    // alternating kv entries
    private final Object[] elements;
    private final int size;
    private final PersistentMap meta;

    PersistentArrayMap(Object[] parts, PersistentMap meta) {
        this.elements = parts;
        this.size = parts.length / 2;
        this.meta = meta;
    }

    private PersistentArrayMap(PersistentArrayMap other, PersistentMap meta) {
        this.elements = other.elements;
        this.size = other.size;
        this.meta = meta;
    }

    @Override
    public int count() {
        return size;
    }

    @Override
    public ISeq<Entry<K, V>> seq() {
        if (size == 0) {
            return ISeq.EMPTY;
        } else {
            return new InnerSeq(0);
        }
    }

    @Override
    public PersistentMap meta() {
        return meta;
    }

    @Override
    public Pair<K, V> entryAt(K key) {
        int index = findSlot(key);
        if (index == -1) {
            return null;
        } else {
            return new Pair(elements[index], elements[index + 1]);
        }
    }

    @Override
    public PersistentMap<K, V> withMeta(PersistentMap newMeta) {
        return new PersistentArrayMap<>(this, newMeta);
    }

    @Override
    public PersistentMap<K, V> assoc(K key, V val) {
        return assocGeneric(key, val);
    }

    @Override
    protected PersistentMap<K, V> assocGeneric(Object key, Object val) {
        int index = findSlot((K) key);

        int length = elements.length;
        if (index == -1) {
            // expand
            if (elements.length == maxArrayMapSize) {
                var hm = createHashMap(Arrays.stream(elements).iterator());
                var withKey = hm.assoc(key, val);
                return withKey.withMeta(meta);
            } else {
                Object[] copied = Arrays.copyOf(elements, length + 2);
                copied[length] = key;
                copied[length + 1] = val;
                return new PersistentArrayMap<>(copied, meta);
            }
        } else {
            // in-place
            Object[] copied = Arrays.copyOf(elements, length);
            copied[index] = key;
            copied[index + 1] = val;
            return new PersistentArrayMap<>(copied, meta);
        }
    }

    @Override
    public PersistentMap<K, V> dissoc(K key) {
        int index = findSlot(key);

        int length = elements.length;
        if (index == -1) {
            // expand
            return this;
        } else {
            // inplace
            Object[] out = new Object[length - 2];
            System.arraycopy(elements, 0, out, 0, index);
            System.arraycopy(elements, index + 2, out, index, length - index - 2);
            return new PersistentArrayMap<>(out, meta);
        }
    }

    @Override
    public PersistentMap<K, V> dissoc(K key, V val) {
        int index = findSlot(key);

        int length = elements.length;
        if (index == -1) {
            return this;
        } else {
            if (NativeCore.equals(val, elements[index + 1])) {
                Object[] out = new Object[length - 2];
                System.arraycopy(elements, 0, out, 0, index);
                System.arraycopy(elements, index + 2, out, index, length - index - 2);
                return new PersistentArrayMap<>(out, meta);
            } else {
                return this;
            }

        }

    }

    private int findSlot(K k) {
        for (int i = 0; i < elements.length; i += 2) {
            if (NativeCore.equals(k, elements[i])) {
                return i;
            }
        }
        return -1;
    }

    private class InnerSeq extends AbstractSeq<Entry<K, V>> {

        private int index;

        public InnerSeq(int index) {
            super();
            this.index = index;
        }

        @Override
        public Entry<K, V> first() {
            return (Entry<K, V>) entry(elements[index], elements[index + 1]);
        }

        @Override
        public ISeq<Entry<K, V>> next() {
            int next = index + 2;
            if (next == elements.length) {
                return ISeq.EMPTY;
            } else {
                return new InnerSeq(next);
            }
        }

    }

}
