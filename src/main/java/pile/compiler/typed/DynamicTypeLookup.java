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
package pile.compiler.typed;

import static pile.compiler.Helpers.*;
import static pile.compiler.typed.LookupHelpers.*;
import static pile.util.CollectionUtils.*;

import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

import pile.compiler.Helpers;
import pile.util.Pair;

public class DynamicTypeLookup<T> {

    private final Function<T, TypeVarArg> tx;

    public DynamicTypeLookup(Function<T, TypeVarArg> tx) {
        this.tx = tx;
    }
    
    public boolean isCandidate(List<Class<?>> runtimeTypes, T candidate) {
        BiPredicate<MethodType, Boolean> pred = match(LookupHelpers::matchDynamic, runtimeTypes);
        
        var pair = tx.apply(candidate);
        
        return pred.test(pair.type(), pair.varArgs());
    
    }
    
    public boolean isCandidate(List<Class<?>> staticTypes, List<Class<?>> runtimeTypes, T candidate) {
    
        List<Class<?>> finalTypes = collect(staticTypes, runtimeTypes);

        BiPredicate<MethodType, Boolean> pred = match(LookupHelpers::matchDynamic, finalTypes);
        
        var pair = tx.apply(candidate);
        
        return pred.test(pair.type(), pair.varArgs());
    
    }
    
    public Optional<T> findMatchingTarget(List<Class<?>> staticTypes, List<Class<?>> runtimeTypes, IntConsumer contentionIndex, Stream<T> s) {
        
        // We need to pare down the candidate list twice
        // First, we need to generate all the candidates that could match the static
        // types provided. This allows us to populate the contention index to identify
        // which targets *could* be called in the future with different runtime types.
        // Second, we need to select from that the list the most specific method
        // that would satisfy the dynamic types.        
        
        // fn(A a)
        // fn(B b)
        // fn(C c)
        
        // A > B > C
        
        // #1
        // static types [Any]
        // dynamic types [B]
        // --
        // Contention Candidates [fn(A), fn(B), fn(C)]
        // Actual Candidates [fn(A), fn(B)]
        // Target fn(B) with an exact typeguard.
        
        // #2
        // static types [B]
        // dynamic types [C]
        // --
        // Contention Candidates [fn(B), fn(C)]
        // Actual Candidates [fn(B), fn(C)]
        // Target fn(C) with an exact typeguard.

        BiPredicate<MethodType, Boolean> staticPred = match(LookupHelpers::matchStatic, staticTypes);
        BiPredicate<MethodType, Boolean> dynamicPred = match(LookupHelpers::matchDynamic, runtimeTypes);
        
        var it = s.map(e -> new Pair<>(tx.apply(e), e)).iterator();

        int size = staticTypes.size();

        Pair<TypeVarArg, T> local = null;
        Pair<TypeVarArg, T> lastStatic = null;
        while (it.hasNext()) {
            Pair<TypeVarArg, T> pair = it.next();
            var tva = pair.left();
            if (staticPred.test(tva.type(), tva.varArgs())) {
                if (lastStatic != null) {
                    findContention(lastStatic.left().expandToSize(size), pair.left().expandToSize(size), contentionIndex);
                }
                lastStatic = pair;
            }
            if (dynamicPred.test(tva.type(), tva.varArgs())) {
                if (local == null) {
                    local = pair;
                } else {
                    local = merge(local, pair, staticTypes);
                }
            }
        }
        return Optional.ofNullable(local).map(Pair::right);
    }
    
    public Optional<T> findMatchingTarget(List<Class<?>> types, Stream<T> s) {
        return findMatchingTarget(types, types, i -> {}, s);
    }

    public Optional<T> findMatchingTarget(List<Class<?>> staticTypes, List<Class<?>> runtimeTypes, Stream<T> s) {
        return findMatchingTarget(staticTypes, runtimeTypes, i -> {}, s);
    }

    private List<Class<?>> collect(List<Class<?>> staticTypes, List<Class<?>> runtimeTypes) {
        List<Class<?>> out = new ArrayList<Class<?>>();
        for (int i = 0; i < staticTypes.size(); ++i) {
            Class<?> staticType = staticTypes.get(i);
            Class<?> dynamicType = runtimeTypes.get(i);
            if (staticType.equals(Any.class)) {
                out.add(dynamicType);
            } else {
                out.add(staticType);
            }
        }
        return out;
    }
    
    private void findContention(MethodType leftTypeList, MethodType rightTypeList,
           IntConsumer contentionIndex) {

        int size = leftTypeList.parameterCount();

        for (int i = 0; i < size; ++i) {
            var ltype = leftTypeList.parameterType(i);
            var rtype = rightTypeList.parameterType(i);

            if (ltype.equals(rtype)) {
                continue;
            }
            contentionIndex.accept(i);
        }
    }

    protected Pair<TypeVarArg, T> merge(Pair<TypeVarArg, T> leftPair, Pair<TypeVarArg, T> rightPair,
            List<Class<?>> statics) {

        var size = statics.size();

        var left = leftPair.left();
        var right = rightPair.left();

        var leftTypeList = left.expandToSize(size);
        var rightTypeList = right.expandToSize(size);

        for (int i = 0; i < size; ++i) {
            var stype = statics.get(i);
            var ltype = leftTypeList.parameterType(i);
            var rtype = rightTypeList.parameterType(i);

            if (ltype.equals(rtype)) {
                continue;
            }
            
            if (stype.equals(ltype)) {
                return leftPair;
            }
            
            if (stype.equals(rtype)) {
                return rightPair;
            }

            switch (choose(ltype, rtype)) {
                case LEFT:
                    return leftPair;
                case RIGHT:
                    return rightPair;
                case NEITHER:
                    continue;
            }
        }

        // Seems like this should only happen if there are somehow two candidates that are completely unrelated:
        // Class A implements B, C ...
        // void fn(B b)
        // void fn(C c)
        // B & C are unrelated
        return leftPair;
    }

    enum Choice {
        LEFT, RIGHT, NEITHER;
    }

    static Choice choose(Class<?> lhsType, Class<?> rhsType) {
        if (lhsType.isAssignableFrom(rhsType)) {
            return Choice.RIGHT;
        } else if (rhsType.isAssignableFrom(lhsType)) {
            return Choice.LEFT;
        } else {
            lhsType = toWrapper(lhsType);
            rhsType = toWrapper(rhsType);
            if (lhsType.isAssignableFrom(rhsType)) {
                return Choice.RIGHT;
            } else if (rhsType.isAssignableFrom(lhsType)) {
                return Choice.LEFT;
            }
        }
        return Choice.NEITHER;
    }

}
