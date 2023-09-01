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

import static pile.util.CollectionUtils.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import pile.compiler.annotation.GeneratedMethod;
import pile.compiler.annotation.PileVarArgs;
import pile.nativebase.RenamedMethod;

/**
 * Reflectively inspects a class and collects all methods that have been
 * {@link GeneratedMethod generated} by this runtime.
 * 
 *
 */
public class MethodCollector {

    private final Class<?> clazz;
    private final Lookup lookup;

    public MethodCollector(Class<?> clazz, Lookup lookup) {
        super();
        this.clazz = clazz;
        this.lookup = lookup;
    }

    public Map<String, MethodArity> collectPileMethods() throws IllegalAccessException, InstantiationException {
        Map<String, MethodArity> out = new HashMap<>();

        Function<String, MethodArity> get = k -> out.computeIfAbsent(k,
                ig -> new MethodArity(new HashMap<>(), null, -1, null));

        for (Method m : clazz.getDeclaredMethods()) {
            if (m.isAnnotationPresent(GeneratedMethod.class)) {
                MethodHandle handle = lookup.unreflect(m);

                final String name;
                var renamedMethodAnnotation = m.getAnnotation(RenamedMethod.class);
                if (renamedMethodAnnotation == null) {
                    name = m.getName();
                } else {
                    name = renamedMethodAnnotation.value();
                }

                MethodArity arity = get.apply(name);
                if (m.isAnnotationPresent(PileVarArgs.class)) {
                    MethodHandle varArgsMethod = handle;

                    // base ... iseq (2)
                    int varArgsSize = handle.type().parameterCount() - 2;
                    out.put(name, arity.withVarArgs(varArgsMethod, varArgsSize));
                } else {
//                    int mCount = m.getParameterCount();
                    arity.arityHandles.put(handle.type().parameterCount(), handle);
                }
            }
        }

        // TODO kwargs unrolled
        return out;
    }

    public record MethodArity(Map<Integer, MethodHandle> arityHandles, MethodHandle varArgsMethod, int varArgsSize,
            MethodHandle kwArgsUnrolledMethod) {
            
        /**
         * Bind all method handles to the supplied base. Does not update any argument
         * count information.
         * 
         * @param base
         * @return
         */
        public MethodArity bind(Object base) {
            Map<Integer, MethodHandle> newArityHandles = mapV(arityHandles(), m -> m.bindTo(base));
            MethodHandle newVarArgsMethod = varArgsMethod != null ? varArgsMethod.bindTo(base) : null;
            MethodHandle newKwArgsUnrolledMethod = kwArgsUnrolledMethod != null ? kwArgsUnrolledMethod.bindTo(base) : null;
            return new MethodArity(newArityHandles, newVarArgsMethod, varArgsSize, newKwArgsUnrolledMethod);
        }   

        public MethodArity withVarArgs(MethodHandle newVarArgsMethod, int newVarArgsSize) {
            return new MethodArity(arityHandles(), newVarArgsMethod, newVarArgsSize, kwArgsUnrolledMethod());
        }
        
        public MethodArity mergeSupplement(MethodArity defaultMethods) {
            Map<Integer, MethodHandle> arityCopy = new HashMap<>(arityHandles);
            for (var other : defaultMethods.arityHandles().entrySet()) {
                arityCopy.merge(other.getKey(), other.getValue(), (l, r) -> l);
            }
            int newVarArgSize = varArgsSize == -1 ? defaultMethods.varArgsSize() : varArgsSize;
            MethodHandle newVarArgs = varArgsMethod == null ? defaultMethods.varArgsMethod() : varArgsMethod;
            // TODO KW UNROLLED
            return new MethodArity(arityCopy, newVarArgs, newVarArgSize, null);
        }
    }

}
