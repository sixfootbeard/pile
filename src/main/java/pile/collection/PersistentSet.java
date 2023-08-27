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

import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Function;

import org.objectweb.asm.ConstantDynamic;

import pile.compiler.Constants;
import pile.compiler.form.CollectionLiteralForm;
import pile.core.Conjable;
import pile.core.ISeq;
import pile.core.Metadata;
import pile.core.PCall;
import pile.core.Seqable;
import pile.core.exception.PileException;
import pile.core.indy.CallableLink;

public abstract class PersistentSet<E> extends AbstractSet<E>
		implements PersistentCollection<E>, Conjable<E>, FMap<PersistentSet> {

	protected final PersistentMap<E, Boolean> inner;

	public PersistentSet(PersistentMap<E, Boolean> inner) {
		super();
		this.inner = inner;
	}

	@Override
	public abstract PersistentSet<E> withMeta(PersistentMap newMeta);

	@Override
	public abstract PersistentSet<E> updateMeta(Function<PersistentMap, PersistentMap> update);

	@Override
	public int count() {
		return inner.count();
	}

	@Override
	public ISeq<E> seq() {
		if (size() == 0) {
			return ISeq.EMPTY;
		}
		return inner.seq().map(Entry::getKey);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof PersistentSet set) {
			return ISeq.equals(seq(), set.seq());
		}
		return false;
	}

	@Override
	public boolean contains(Object o) {
		return inner.containsKey(o);
	}

	@Override
	public Iterator<E> iterator() {
		return inner.keySet().iterator();
	}

	@Override
	public int size() {
		return inner.size();
	}

	@Override
    public Object invoke(Object... args) throws Throwable {
        ensureEx(args.length == 1, PileException::new, () -> "Wrong number of args. Expected=1, Saw=" + args.length);
    	if (contains(args[0])) {
    		return args[0];
    	}
    	return null;
    }
    
    @Override
    public boolean acceptsArity(int arity) {
        return arity == 1;
    }
    
    @Override
    public abstract PersistentSet<E> conj(E t);
    
	@Override
    public Optional<ConstantDynamic> toConst() {
        List<Object> parts = new ArrayList<>();
        for (E e : this) {
            Optional<?> opt = Constants.toConstAndNull(e);
            
            if (opt.isEmpty()) {
                return Optional.empty();
            }
    
            parts.add(opt.get());
        }
        ConstantDynamic cform = new ConstantDynamic("set", getDescriptor(PersistentSet.class),
                CollectionLiteralForm.CONDY_BOOTSTRAP_HANDLE, parts.toArray());
        return Optional.of(cform);
    }

    public static PersistentSet fromIterable(Iterable it) {
        PersistentSet set = PersistentHashSet.empty();
		for (Object o : it) {
			set = set.conj(o);
		}
		return set;
	}

	public static PersistentSet createArr(Object... args) {
		return fromIterable(Arrays.asList(args));
	}

	public static <E> PersistentSet<E> empty() {
		return new PersistentHashSet<>();
	}

	public static PersistentSet<?> fromList(List<Object> parts) {
		return fromIterable(parts);
	}

}
