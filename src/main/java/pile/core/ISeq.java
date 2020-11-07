package pile.core;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;

import pile.core.adapter.ArraySeq;
import pile.core.adapter.IteratorSeq;

public interface ISeq<T> extends Iterable<T> {

    T first();

    ISeq<T> next();
    
    default ISeq<T> more() {
        ISeq<T> n = next();
        if (n == null) {
            return empty();
        }
        return n;
    }

    @Override
    default Iterator<T> iterator() {
        if (first() == null) {
            return Collections.emptyIterator();
        }
        return new IteratorSeq<>(this);
    }

    default <O> ISeq<O> map(Function<T, O> fn) {

        final ISeq<T> ref = this;

        return new ISeq<O>() {

            @Override
            public O first() {
                return fn.apply(ref.first());
            }

            @Override
            public ISeq<O> next() {
                return ref.next() == null ? null : ref.next().map(fn);
            }

        };
    }

    /**
     * Call the provided function on all the nodes and flatmap the resulting seqs
     * into a new seq.
     * 
     * @param <O>
     * @param fn
     * @return
     */
    default <O> ISeq<O> flatMap(Function<T, ISeq<O>> fn) {
        ISeq<O> apply = fn.apply(first());
        return apply.concatLazy(() -> next() == null ? null : next().flatMap(fn));
    }

    default ISeq<T> concatLazy(Supplier<ISeq<T>> after) {
        return new Concat<>(this, after);
    }

    default ISeq<T> concat(ISeq<T> after) {
        return new Concat<>(this, after);
    }

    default ISeq<T> cons(Object o) {
        return new Cons(o, this);
    }

    public static <E> ISeq<E> empty() {
        return (ISeq<E>) EMPTY;
    }

    public static ISeq EMPTY = new ISeq() {

        @Override
        public Object first() {
            return null;
        }

        @Override
        public ISeq next() {
            return EMPTY;
        }
    };

    static <T> ISeq<T> single(T entry) {
        return new ISeq<T>() {

            @Override
            public T first() {
                return entry;
            }

            @Override
            public ISeq<T> next() {
                return null; // TODO empty() ?
            }
        };
    }
    
    static <T> ISeq<T> of(T... entry) {
        return new ArraySeq<>(entry, 0);
    }

}