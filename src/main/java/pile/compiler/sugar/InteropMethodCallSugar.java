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
package pile.compiler.sugar;

import static pile.nativebase.NativeCore.*;

import java.util.Optional;

import pile.collection.PersistentList;
import pile.compiler.CompilerState;
import pile.core.Namespace;
import pile.core.Symbol;
import pile.core.binding.NativeDynamicBinding;

public class InteropMethodCallSugar implements SExprDesugarer<PersistentList<Object>> {

    static final Symbol DOT_SYM = new Symbol("pile.core", ".");
    private static final Symbol IDENTITY_METHOD = new Symbol("pile.core", "identity");

    public InteropMethodCallSugar() {
    }

    @Override
    public Optional<PersistentList<Object>> desugar(PersistentList<Object> result, CompilerState cs, Namespace ns) {

        Object first = first(result);
        
        if (first instanceof Symbol firstSym) {
            String dotName = firstSym.getName();
            if (dotName.length() > 1) {
                if (dotName.startsWith(".")) {
                    // (.add foo bar baz)
                    // (. foo (add bar baz))
                    String methodName = dotName.substring(1);
                    PersistentList methodArgs = result.pop().pop();
                    Object base = second(result);
                    if (base instanceof Symbol baseSym) {
                        // (.valueOf Integer "12)
                        // (. (identity Integer) valueOf "12")
                        Optional<Class<?>> maybeClass = baseSym.tryGetAsClass(ns);
                        if (maybeClass.isPresent()) {
                            base = PersistentList.reversed(IDENTITY_METHOD, baseSym);
                        }
                    }
                    PersistentList<Object> methodAndArgs = methodArgs.conj(new Symbol(methodName)).conj(base).conj(DOT_SYM);
                    return Optional.of(methodAndArgs);
                }

            }
        }
        return Optional.empty();
    }

}
