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

import java.util.Optional;

import pile.collection.PersistentList;
import pile.compiler.CompilerState;
import pile.core.Namespace;
import pile.core.Symbol;

/**
 * Turns syntax representing java method calls into their expanded forms:
 * <pre>
 * // Member functions
 * (Number::longValue 44) 
 * ((pile.core/java-function Number "longValue") 44)
 * 
 * // Static Functions
 * (Date::parse "Sat, 12 Aug 1995 13:30:00 GMT")
 * ((pile.core/java-function Date "parse")  "Sat, 12 Aug 1995 13:30:00 GMT") 
 * 
 * // Constructors
 * (Date::new 1234L)
 * ((pile.core/java-function Date "new") 1234L)
 * </pre>
 * 
 *
 */
public class JavaMethodSugar implements SymbolDesugarer {

    private static final Symbol JAVA_NAME_SYM = new Symbol("pile.core", "java-method");

    @Override
    public Optional<Object> desugarSymbol(Symbol s, CompilerState cs, Namespace ns) {
        String base = s.getName();
        int midpoint = base.indexOf("::");
        if (midpoint > 0) {
            // covers both not found and ::kw
            String classname = base.substring(0, midpoint);
            String fname = base.substring(midpoint + 2);
            var newForm = PersistentList.reversed(JAVA_NAME_SYM, new Symbol(classname), fname);
            return Optional.of(newForm); 
        }
        return Optional.empty();
    }

}
