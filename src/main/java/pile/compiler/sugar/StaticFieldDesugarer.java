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

import pile.compiler.CompilerState;
import pile.compiler.Scopes.ScopeLookupResult;
import pile.compiler.form.VarScope;
import pile.core.Namespace;
import pile.core.Symbol;
import pile.core.binding.Binding;

public class StaticFieldDesugarer implements SymbolDesugarer {

    private static final Optional<Object> EMPTY = Optional.empty();
    private static final Object DOT_SYM = new Symbol("pile.core", ".");

    @Override
    public Optional<Object> desugarSymbol(Symbol sym, CompilerState cs, Namespace ns) {
        var symNs = sym.getNamespace();
        if (symNs == null) {
            return EMPTY;
        }
        ScopeLookupResult slr = cs.getScope().lookupNamespaceAndLiteral(new Symbol(symNs));
        if (slr == null) {
            return EMPTY;
        }
        Class cls = null;
        if (slr.scope() == VarScope.NAMESPACE) {
            Binding b = (Binding) slr.val();
            if (b.getValue() instanceof Class c) {
                cls = c;
            }
        }
        else if (slr.scope() == VarScope.LITERAL) {
            if (slr.val() instanceof Class c) {
                cls = c;
            }
        }
        if (cls != null) {
            var desugared = list(DOT_SYM, sym(cls.getName()), sym(sym.getName()));
            return Optional.of(desugared);
        }
        return EMPTY;
    }

}
