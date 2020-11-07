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

import static pile.compiler.sugar.InteropMethodCallSugar.*;
import static pile.nativebase.NativeCore.*;

import java.util.Optional;

import pile.collection.PersistentList;
import pile.compiler.CompilerState;
import pile.core.Namespace;
import pile.core.Symbol;

/**
 * Converts:
 * 
 * <pre>
 * (Class/staticMethod ...)
 * to
 * (. Class staticMethod ...)
 * </pre>
 * 
 * @author john
 *
 */
public class StaticMethodCallSugar implements SExprDesugarer<PersistentList<Object>> {

    public StaticMethodCallSugar() {
    }

    @Override
    public Optional<PersistentList<Object>> desugar(PersistentList<Object> result, CompilerState cs, Namespace ns) {

        Object first = first(result);

        if (first instanceof Symbol firstSym) {
            String firstName = firstSym.getName();
            String firstSymNs = firstSym.getNamespace();
            if (firstSymNs != null) {
                PersistentList<Object> newList = result.pop();
                Optional<Class<?>> maybeStaticClass = new Symbol(firstSymNs).tryGetAsClass(ns);
                if (maybeStaticClass.isPresent()) {
                    newList = newList.conj(new Symbol(firstName)).conj(new Symbol(firstSymNs)).conj(DOT_SYM);
                    return Optional.of(newList);
                }

            }

        }
        return Optional.empty();
    }

}
