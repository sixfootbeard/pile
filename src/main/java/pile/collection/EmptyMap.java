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

import pile.core.ISeq;
import pile.util.Pair;

public class EmptyMap<K, V> extends PersistentMap<K, V> {

	private final PersistentMap<K, V> meta;
	
	public EmptyMap() {
		this(null);
	}
	
	public EmptyMap(PersistentMap<K, V> meta) {
		super();
		this.meta = meta;
	}

	@Override
	public Pair<K, V> entryAt(K key) {
		return null;
	}

	@Override
	public PersistentMap<K, V> dissoc(K key, V val) {
		return this;
	}

	@Override
	public PersistentMap<K, V> dissoc(K key) {
		return this;
	}

	@Override
	public int count() {
		return 0;
	}

	@Override
	public ISeq<Entry<K, V>> seq() {
		return ISeq.EMPTY;
	}

	@Override
	public PersistentMap meta() {
		return meta == null ? this : meta;
	}

	@Override
	public PersistentMap<K, V> withMeta(PersistentMap newMeta) {
		return new EmptyMap(newMeta);
	}

	@Override
	public PersistentMap<K, V> assoc(K key, V val) {
		return new SingleMap<K, V>(key, val, meta);
	}

	@Override
	protected SingleMap assocGeneric(Object key, Object val) {
		return new SingleMap<>(key, val);
	}
	
	@Override
	public PersistentMap<K, V> merge(PersistentMap<K, V> other) {
	    return other;
	}

}
