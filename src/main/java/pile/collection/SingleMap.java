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

import pile.core.ISeq;
import pile.nativebase.NativeCore;
import pile.util.Pair;

public class SingleMap<K, V> extends PersistentMap<K, V> {
	
	private final K k;
	private final V v;
	private final PersistentMap<K, V> meta;
	
	public SingleMap(K k, V v) {
		this(k, v, PersistentMap.EMPTY);
	}

	public SingleMap(K k, V v, PersistentMap<K, V> meta) {
		super();
		this.k = k;
		this.v = v;
		this.meta = meta;
	}

	@Override
	public Pair<K, V> entryAt(K key) {
		if (KEY_EQ.test(this.k, key)) {
			return asEntry();
		}
		return null;
	}

	private Pair<K, V> asEntry() {
		return new Pair<>(k, v);
	}

	@Override
	public PersistentMap<K, V> dissoc(K key, V val) {
		if (KEY_EQ.test(this.k, key) && VAL_EQ.test(this.v, val)) {
			return new EmptyMap<>(meta);
		}
		return this;
	}

	@Override
	public PersistentMap<K, V> dissoc(K key) {
		if (KEY_EQ.test(this.k, key)) {
			return new EmptyMap<>(meta);
		}
		return this;
	}

	@Override
	public int count() {
		return 1;
	}

	@Override
	public ISeq<Entry<K, V>> seq() {
		return ISeq.of(entry(k, v));
	}

	@Override
	public PersistentMap meta() {
		return meta;
	}

	@Override
	public PersistentMap<K, V> withMeta(PersistentMap newMeta) {
		return new SingleMap(k, v, newMeta);
	}

	@Override
	public PersistentMap<K, V> assoc(K key, V val) {
	    if (NativeCore.equals(k, key)) {
	        return new SingleMap<>(key, val, meta);
	    } else {
	        Object[] amap = new Object[4];
	        amap[0] = k;
	        amap[1] = v;
	        amap[2] = key;
	        amap[3] = val;
	        return new PersistentArrayMap<>(amap, meta);
	    }		
	}

	@Override
	protected PersistentHashMap assocGeneric(Object key, Object val) {
		PersistentHashMap hm = new PersistentHashMap<>();
		hm = hm.assoc(this.k, this.v);
		hm = hm.assoc(key, val);
		return hm;
	}

}
