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

import static pile.compiler.typed.LookupHelpers.*;

import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;

import pile.util.Pair;

public class SubtypeMatcher<T> {

    private final Function<? super T, TypeVarArg> tx;

    public SubtypeMatcher(Function<? super T, TypeVarArg> tx) {
        this.tx = tx;
    }

    public Optional<T> findFirstMatch(List<Class<?>> staticTypes, Stream<T> candidateStream) {
        return find(staticTypes, candidateStream).findFirst();
    }

    public Stream<T> find(List<Class<?>> staticTypes, Stream<T> candidateStream) {

        BiPredicate<MethodType, Boolean> pred = match(LookupHelpers::matchDynamic, staticTypes);

        return candidateStream.map(e -> new Pair<>(tx.apply(e), e))
                .filter(pair -> pred.test(pair.left().type(), pair.left().varArgs()))
                .map(Pair::right);
    }
}
