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
package pile.core.method;

import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.NavigableMap;

import pile.compiler.MethodCollector.MethodArity;

public class ClosureCompiledMethod extends AbstractCompiledMethod {

    private final Object base;
    private final MethodArity methods;

    public ClosureCompiledMethod(Class<?> clazz, Object base, MethodArity methods) {
        super(clazz);
        this.base = base;
        this.methods = methods;
    }

    @Override
    protected Integer getVarArgsArity() {
        return methods.varArgsSize();
    }

    @Override
    protected MethodHandle getVarArgsMethod() {
        return methods.varArgsMethod();
    }

    @Override
    protected NavigableMap<Integer, MethodHandle> getArityHandles() {
        return methods.arityHandles();
    }

    @Override
    protected MethodHandle preCall(MethodHandle h) {
        return h.bindTo(base);
    }

}
