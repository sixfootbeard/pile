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

import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;

import pile.util.Pair;

public class StaticTypeLookup<T> {

    private final Function<? super T, TypeVarArg> tx;

    public StaticTypeLookup(Function<? super T, TypeVarArg> tx) {
        this.tx = tx;
    }

    /**
     * Attempts to find a singular method handle that matches the static types of a
     * callsite.
     * 
     * @param staticTypes     The static types of the callsite (should have
     *                        {@link Any} blended in already).
     * @param candidateStream The candidate set to match against
     * @return A singular matching target. If there are 0 matches or >1 then this
     *         returns empty.
     */
    public Optional<T> findSingularMatchingTarget(List<Class<?>> staticTypes, Stream<T> candidateStream) {

        BiPredicate<MethodType, Boolean> pred = match(LookupHelpers::matchStatic, staticTypes);

        var it = candidateStream.map(e -> new Pair<>(tx.apply(e), e))
                .filter(pair -> pred.test(pair.left().type(), pair.left().varArgs())).iterator();

        return single(it).map(Pair::right);
    }
    
    public boolean isCandidate(List<Class<?>> staticTypes, T candidateMethod) {
        TypeVarArg typeVarArg = tx.apply(candidateMethod);
        BiPredicate<MethodType, Boolean> pred = match(LookupHelpers::matchStatic, staticTypes);
        return pred.test(typeVarArg.type(), typeVarArg.varArgs());        
    }   

}
