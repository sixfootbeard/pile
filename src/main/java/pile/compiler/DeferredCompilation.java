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
package pile.compiler;

import java.util.Optional;
import java.util.function.Consumer;

import pile.core.parse.TypeTag;

/**
 * A piece of compilation that is deferred.
 *
 * @param formType The tag of the form to be compiled.
 * @param ref      A piece of data associated with the to be compiled form. Its
 *                 representation depends on what the tag is.
 * @param ldcForm  A value approriate for ASM's LDC (including condy forms), or
 *                 empty.
 * @param compile  A function which compiles the associated form.
 */
public record DeferredCompilation(TypeTag formType, Object ref, Optional<Object> ldcForm,
        Consumer<CompilerState> compile) {

    /**
     * A piece of compilation that is deferred.
     *
     * @param formType The tag of the form to be compiled.
     * 
     * @param ref      A piece of data associated with the to be compiled form. Its
     *                 representation depends on what the tag is.
     * @param compile  A function which compiles the associated form.
     */
    public DeferredCompilation(TypeTag formType, Object ref, Consumer<CompilerState> compile) {
        this(formType, ref, Optional.empty(), compile);
    }

    public DeferredCompilation withRef(Object newRef) {
        return new DeferredCompilation(formType, newRef, ldcForm, compile);
    }

}
