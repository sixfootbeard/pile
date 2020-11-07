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

import static pile.nativebase.NativeCore.*;

import java.util.function.Function;

import pile.core.ISeq;
import pile.core.PCall;

@SuppressWarnings("rawtypes")
public class PersistentHashSet<E> extends PersistentSet<E> {

	protected final PersistentMap meta;

	public PersistentHashSet(PersistentMap<E, Boolean> inner, PersistentMap meta) {
		super(inner);
		this.meta = meta;
	}

	public PersistentHashSet(PersistentHashSet<E> other, PersistentMap meta) {
		super(other.inner);
		this.meta = meta;
	}

	public PersistentHashSet() {
		super(new EmptyMap<>());
		this.meta = PersistentMap.EMPTY;
	}

	@Override
	public PersistentMap meta() {
		return meta;
	}

	@Override
	public PersistentSet<E> withMeta(PersistentMap newMeta) {
		return new PersistentHashSet<>(this, newMeta);
	}

	@Override
	public PersistentSet<E> updateMeta(Function<PersistentMap, PersistentMap> update) {
		PersistentMap out = update.apply(meta);
		if (meta == out) {
			return this;
		}
		return withMeta(out);
	}

	@Override
	public PersistentHashSet<E> conj(E t) {
		var assoc = inner.assoc(t, true);
		if (assoc == inner) {
			return this;
		}
		return new PersistentHashSet<>(assoc, meta);
	}
	
	@Override
	public PersistentSet fmap(PCall tx) throws Throwable {
	    PCall mod = (args) -> {
	        // entry<key, value>
	        var key = first(args[0]);
	        // rewrap kv for map to (newkey, true)
	        return ISeq.of(tx.invoke(key), true);
	    };
	    
	    return new PersistentHashSet<>(inner.fmap(mod), meta);
	}
	
	

}
