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

public class NewInstanceSugar implements SExprDesugarer<PersistentList<Object>> {

    private static final Symbol NEW_SYM = new Symbol("pile.core", "new");

    public NewInstanceSugar() {
    }

    @Override
    public Optional<PersistentList<Object>> desugar(PersistentList<Object> in, CompilerState cs, Namespace ns) {

        if (in.head() instanceof Symbol sym) {
            String name = sym.getName();
            if (name.length() > 1 && name.endsWith(".")) {
                // File. a
                // new File a
                var typeName = name.substring(0, name.length() - 1);
                PersistentList<Object> outList = in.pop().conj(sym(typeName)).conj(NEW_SYM);
                return Optional.of(outList);
            }
        }
        return Optional.empty();
    }

}
