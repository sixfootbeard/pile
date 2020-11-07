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
package pile.compiler.form;

import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.MacroEvaluated;
import pile.core.Keyword;

public interface Form {

    /**
     * Compile this form.
     * 
     * @param compilerState The current state of the compiler
     * @return Any statically useful information.
     */
    DeferredCompilation compileForm(CompilerState compilerState);

    Object evaluateForm(CompilerState cs) throws Throwable;

    default DeferredCompilation macroCompileForm(CompilerState compilerState, Keyword context) {
        return compileForm(compilerState);
    }

    default MacroEvaluated macroEvaluateForm(CompilerState cs, Keyword context) throws Throwable {
        return new MacroEvaluated(evaluateForm(cs), false);
    }

}