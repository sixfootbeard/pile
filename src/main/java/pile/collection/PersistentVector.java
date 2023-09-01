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
import static org.objectweb.asm.Type.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.StringConcatFactory;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.stream.Collector;

import org.objectweb.asm.ConstantDynamic;

import pile.compiler.Constants;
import pile.compiler.form.CollectionLiteralForm;
import pile.core.Conjable;
import pile.core.ISeq;
import pile.core.PCall;
import pile.core.exception.ShouldntHappenException;
import pile.core.indy.PersistentLiteralLinker;
import pile.nativebase.NativeCore;

public abstract class PersistentVector<E> extends AbstractList<E>
        implements PersistentCollection<E>, Associative<Integer, E>, Conjable<E>, RandomAccess, FMap<PersistentVector> {

    public static final PersistentVector EMPTY = new PersistentArrayVector<>();

    @Override
    public E get(int index) {
        if (index >= size()) {
            throw new NoSuchElementException();
        }
        return get(index, null);
    }

    @Override
    public int size() {
        return count();
    }

    @Override
    public ISeq<E> seq() {
        return NativeCore.seq(this);
    }
    
    @Override
    public boolean acceptsArity(int arity) {
        return arity == 1;
    }

    @Override
    public abstract PersistentVector<E> conj(E t);

    public PersistentVector<E> pushAll(Iterable<? extends E> it) {
        PersistentVector<E> local = this;
        for (E e : it) {
            local = local.conj(e);
        }
        return local;
    }
    
    @Override
    public PersistentVector fmap(PCall tx) throws Throwable {
        Object[] newVals = new Object[count()];
        int i = 0;
        for (var val : this) {
            Object v = tx.invoke(val);
            newVals[i] = v;
            ++i;
        }
        return PersistentVector.create(newVals);
    }

    @Override
    public Optional<ConstantDynamic> toConst() {
        List<Object> parts = new ArrayList<>();
        for (E e : this) {
            Optional<?> key = Constants.toConstAndNull(e);
            if (key.isEmpty()) {
                return Optional.empty();
            }

            parts.add(key.get());
        }
        ConstantDynamic cform = new ConstantDynamic("vec", getDescriptor(PersistentVector.class),
                CollectionLiteralForm.CONDY_BOOTSTRAP_HANDLE, parts.toArray());
        return Optional.of(cform);
    }

    public static <T> PersistentVector<T> fromList(List<T> parts) {
        PersistentArrayVector<T> pv = new PersistentArrayVector<>();
        for (var o : parts) {
            pv = pv.conj(o);
        }
        return pv;
    }
    
    

    /**
     * Create a method from a recipe while partially adding all the provided
     * constants
     * 
     * @param recipe    A recipe string. At each index is either
     *                  {@link PersistentLiteralLinker#CONSTANT} or
     *                  {@link PersistentLiteralLinker#ORDINARY}
     * @param constants The collection of constants.
     * @see StringConcatFactory#makeConcatWithConstants(Lookup, String,
     *      java.lang.invoke.MethodType, String, Object...)
     * @return A method handle which accepts arguments in the slots associated with
     *         all the ordinary recipe arguments.
     */
    public static MethodHandle fromRecipe(String recipe, Object... constants) {
        if (constants.length == 0) {
            return MethodHandles.constant(PersistentVector.class, PersistentVector.EMPTY);
        }
        PersistentArrayVector v = new PersistentArrayVector();
        List<Integer> ordinaryIndexes = new ArrayList<>();
        int constantIndex = 0;
        for (int i = 0; i < recipe.length(); ++i) {
            char part = recipe.charAt(i);
            if (part == PersistentLiteralLinker.CONSTANT) {
                v = v.push(constants[constantIndex]);
                ++constantIndex;
            } else if (part == PersistentLiteralLinker.ORDINARY) {
                v = v.push(null); // slot
                ordinaryIndexes.add(i);
            }
        }
        // We have N ordinaryIndexes corresponding to the current stack elements
        MethodHandle assoc;
        try {
            assoc = lookup().findVirtual(PersistentArrayVector.class, "assoc",
                    methodType(PersistentArrayVector.class, Integer.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ShouldntHappenException(e);
        }

        Collections.reverse(ordinaryIndexes);
        Iterator<Integer> it = ordinaryIndexes.iterator();
        MethodHandle first = assoc;
        first = insertArguments(first, 1, it.next());
        while (it.hasNext()) {
            MethodHandle boundAssoc = insertArguments(assoc, 1, it.next());
            first = collectArguments(first, 0, boundAssoc);
        }

        first = first.bindTo(v);

        return first;

    }

    public static PersistentVector of(Iterable coll) {
        PersistentArrayVector vec = PersistentArrayVector.empty();
        for (Object o : coll) {
            vec = vec.push(o);
        }
        return vec;
    }

    public static PersistentVector create(Object[] args) {
        if (args.length == 0) {
            // saving a single list alloc
            return EMPTY;
        }
        return of(Arrays.asList(args));
    }

    public static PersistentVector createArr(Object... args) {
        return create(args);
    }

    public static PersistentVector unsplice(long unspliceMask, Object... args) {
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

    public static Collector<Object, List<Object>, PersistentVector<Object>> getCollector() {
        return Collector.<Object, List<Object>, PersistentVector<Object>>of(ArrayList::new, List::add, (l, r) -> {
            l.addAll(r);
            return l;
        }, PersistentVector::fromList);
    }

}
