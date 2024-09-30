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
package pile.core;

public interface Var<T> extends SettableRef<T>, Metadata, PileMethod {

    public Namespace getNamespace();

    public String getName();

    /**
     * Create an invoker capable of {@link BindingInvocation#call(PCall) invoking} a
     * function in the context of 1 or more bound var values.
     * 
     * @param prev The invoker to attach our state to.
     * @param val
     * @return A new {@link BindingInvocation} with our bound var state.
     */
    public BindingInvocation bindWith(BindingInvocation prev, Object val);

    default public Symbol asSymbol() {
        return new Symbol(getNamespace().getName(), getName());
    }

}
