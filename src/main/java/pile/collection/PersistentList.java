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

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import pile.core.Conjable;
import pile.core.Cons;
import pile.core.ISeq;
import pile.core.PObj;
import pile.core.Seqable;
import pile.core.indy.PersistentLiteralLinker;
import pile.nativebase.NativeCore;

public class PersistentList<T> implements PObj<PersistentList<T>>, Conjable<T>, Counted, Seqable<T>, Iterable<T> {

	public static final PersistentList EMPTY = new PersistentList();
	private final ISeq head;
	private final int size;
	private final PersistentMap meta;

	private PersistentList(PersistentMap meta, PersistentList other) {
		Objects.requireNonNull(meta, "Metadata may not be null");
		this.meta = meta;
		this.head = other.head;
		this.size = other.size;
	}

	private PersistentList(PersistentMap meta, ISeq newHead, int size) {
		Objects.requireNonNull(meta, "Metadata may not be null");
		this.meta = meta;
		this.head = newHead;
		this.size = size;
	}

	public PersistentList() {
		this(PersistentMap.EMPTY, (Cons) null, 0);
	}

	@Override
	public ISeq<T> seq() {
		if (count() == 0) {
			return null;
		}
		return head;
	}

	@Override
	public int count() {
		return size;
	}

	@Override
	public PersistentList<T> conj(T t) {
		return new PersistentList<>(meta(), new Cons(t, head), size + 1);
	}

	public PersistentList<T> pop() {
		return new PersistentList<T>(meta(), head.next(), size - 1);
	}
	
	public T head() {
	    return (T) head.first();
	}
	
	@Override
	public PersistentMap meta() {
		return meta;
	}

	@Override
	public PersistentList<T> withMeta(PersistentMap newMeta) {
		return new PersistentList<>(newMeta, head, size);
	}

	@Override
	public PersistentList<T> updateMeta(Function<PersistentMap, PersistentMap> update) {
		PersistentMap oldMeta = this.meta();
		if (oldMeta == null) {
			oldMeta = PersistentMap.EMPTY;
		}
		return withMeta(update.apply(oldMeta));
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		Iterator it = iterator();

		sb.append("(");

		while (it.hasNext()) {
			sb.append(it.next());
			if (it.hasNext()) {
				sb.append(" ");
			}
		}

		sb.append(")");

		return sb.toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof PersistentList opl) {
			return ISeq.equals(this.seq(), opl.seq());
		}
		if (obj instanceof ISeq opl) {
			return ISeq.equals(this.seq(), opl);
		}
		return false;
	}

	@Override
	public Iterator<T> iterator() {
		if (count() == 0) {
			return Collections.emptyIterator();
		}
		return head.iterator();
	}

	public Object[] toArray() {
    	Object[] out = new Object[count()];
    	int index = 0;
    	for (Object o : ISeq.iter(this.seq())) {
    		out[index] = o;
    		++index;
    	}
    	return out;
    }
    
    public Stream<T> stream() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator(), 0), false);
    }

    public static PersistentList<Object> fromList(List<Object> list) {
	    ISeq<Object> seq = NativeCore.seq(list);
	    return new PersistentList<Object>(PersistentMap.EMPTY, seq, list.size());
	    // slow way
        // Cons cons = null;
        // for (int i = list.size() - 1; i >= 0; --i) {
        // cons = new Cons(list.get(i), cons);
        // }
        // return new PersistentList<>(PersistentMap.EMPTY, cons, list.size());
	}

	public static PersistentList reversed(Object... args) {
		return fromList(Arrays.asList(args));
	}
	
	public static PersistentList of(Object... args) {
        return fromList(Arrays.asList(args));
    }

	public static PersistentList createArr(Object... args) {
		PersistentList p = EMPTY;
		for (Object o : args) {
			p = p.conj(o);
		}
		return p;
	}

	public static PersistentList unspliceReverse(long unspliceMask, Object... args) {
		List<Object> out = new ArrayList<>();
		for (int i = 0; i < args.length; ++i) {
			var item = args[i];
			if ((unspliceMask & (1 << i)) != 0) {
				for (Object inner : ISeq.iter(NativeCore.seq(item))) {
					out.add(inner);
				}
			} else {
				out.add(item);
			}
		}
		return fromList(out);
	}

	public static <T> PersistentList<T> fromSeq(ISeq<T> head) {
        int count = 0;
        ISeq<T> seq = head;
        while (seq != ISeq.EMPTY)  {
            count++;
            seq = seq.next();
        }
        return new PersistentList<T>(PersistentMap.empty(), head, count);
    }

}
