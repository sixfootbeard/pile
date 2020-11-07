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

import pile.compiler.CompilerState;
import pile.core.Namespace;
import pile.core.Symbol;

public interface SymbolDesugarer {

    /**
     * Attempts to desugar the provided symbol.
     * 
     * @param in The symbol to desugar
     * @param cs
     * @param ns
     * @return The desugared syntax, or empty if this operation was not applicable
     *         for the provided input.
     */
    Optional<Object> desugarSymbol(Symbol in, CompilerState cs, Namespace ns);

}
