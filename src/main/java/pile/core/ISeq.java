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
package pile.core;

import static java.util.Objects.*;

import java.util.Collections;
import java.util.Iterator;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import pile.compiler.Helpers;
import pile.core.exception.PileInternalException;
import pile.nativebase.NativeCore;

public interface ISeq<T> extends Iterable<T>, Conjable<T> {

    T first();

    ISeq<T> next();

    @Override
    default Iterator<T> iterator() {
        return new IteratorSeq<>(this);
    }

    @Override
    default Conjable<T> conj(T t) {
        return new Cons(t, this);
    }
    
    default ISeq reverse() {
        if (this instanceof ReversibleSeq rev) {
            return rev.reverse();
        }
        Cons last = null;
        var cur = this;
        while (cur != null) {
            last = new Cons(cur.first(), last);
            cur = cur.next();
        }
        return last;
    }

    default <O> ISeq<O> map(Function<T, O> fn) {

        final ISeq<T> ref = this;

        return new AbstractSeq<O>() {

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
        return new Concat<>(apply, StableValue.supplier(() -> {
            ISeq<T> next = next();
            if (next == null) {
                return null;
            } else {
                return next.flatMap(fn);
            }
        }));
    }

    default ISeq<T> concat(Seqable<T> after) {
        return new Concat<>(this, StableValue.supplier(after::seq));
    }
    
    default ISeq<T> concat(ISeq<T> after) {
        return new Concat<>(this, () -> after);
    }

    default ISeq<T> cons(T o) {
        return new Cons(o, this);
    }

    public static ISeq EMPTY = null;

    @SuppressWarnings("rawtypes")
    static <T> ISeq seqSized(T t, int current, int size, BiFunction<T, Integer, Object> create) {
        if (current == size) {
            return null;
        }
        return new AbstractSeq() {

            @Override
            public Object first() {
                return create.apply(t, current);
            }

            @Override
            public ISeq next() {
                return seqSized(t, current + 1, size, create);
            }

        };
    }

    static <T> ISeq<T> single(T entry) {
        return new Cons(entry);
    }

    static <T> ISeq<T> of(T... entry) {
        return new ArraySeq<>(entry, 0);
    }

    static <T> Iterable<T> iter(ISeq<T> seq) {
        if (seq == null) {
            return Collections.emptyList();
        } else {
            return seq;
        }
    }

    static boolean equals(ISeq lhs, ISeq rhs) {
        for (;;) {
            if (lhs == null && rhs == null) {
                return true;
            }
            if (lhs == null ^ rhs == null) {
                return false;
            }
            if (NativeCore.equals(lhs.first(), rhs.first())) {
                lhs = lhs.next();
                rhs = rhs.next();
            } else {
                return false;
            }
        }
    }

    static <T> Stream<T> stream(ISeq<T> args) {
        Iterable<T> iter = iter(args);
        return StreamSupport.stream(iter.spliterator(), false);
    }

    static <T> String toString(StringBuilder sb, ISeq<T> seq) {
        sb.append("(");
        while (seq != null) {
            sb.append(seq.first());

            if (seq.next() != null) {
                sb.append(" ");
            }
            seq = seq.next();

        }
        sb.append(")");
        return sb.toString();

    }

    /**
     * Unroll an {@link ISeq} into an array of the provided size. The last argument
     * will be the remaining elements of the seq.
     * 
     * @param finalSize
     * @param arg
     * @return
     */
    static Object[] unroll(int finalSize, ISeq arg) {
        Object[] out = new Object[finalSize];
        for (int i = 0; i < finalSize - 1; ++i) {
            requireNonNull(arg, "Unrolled seq must have a known size");
            Object first = NativeCore.first(arg);
            out[i] = first;
            arg = NativeCore.next(arg);
        }
        out[finalSize - 1] = arg;
        return out;
    }

    /**
     * Unroll an {@link ISeq} of a known size to an array
     * 
     * @param finalSize The number of known elements in the seq
     * @param seq       The seq
     * @return The elements of the Iseq in an array
     * @throws IllegalArgumentException If the number of elements in the iseq was
     *                                  not correct.
     */
    static Object[] unrollExact(int finalSize, ISeq seq) {
        Object[] out = new Object[finalSize];
        for (int i = 0; i < finalSize; ++i) {
            out[i] = NativeCore.first(seq);
            seq = NativeCore.next(seq);
        }
        if (seq != null) {
            throw new PileInternalException("iseq unroll: bad sizing");
        }
        return out;
    }

    /**
     * Given an array of arguments with an implied calling convention of 'apply' eg.
     * (the last argument is an Iseq) roll all of the rest of the arguments into the
     * Iseq and return it.
     * 
     * @param args
     * @return
     */
    static ISeq roll(Object[] args) {
        ISeq out = (ISeq) args[args.length - 1];
        for (int i = args.length - 2; i >= 0; ++i) {
            out = out.cons(args[i]);
        }
        return out;
    }

}