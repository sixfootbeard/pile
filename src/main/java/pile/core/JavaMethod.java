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

import static java.lang.invoke.MethodHandles.*;
import static pile.compiler.Helpers.*;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

import pile.compiler.typed.DynamicTypeLookup;
import pile.compiler.typed.TypedHelpers;
import pile.core.exception.PileExecutionException;
import pile.core.runtime.generated_classes.LookupHolder;

public class JavaMethod implements PileMethod {

    private final Map<Integer, List<MethodHandle>> methods;
    private final NavigableMap<Integer, List<MethodHandle>> varArgsMethods;

    public JavaMethod(Map<Integer, List<MethodHandle>> methods,
            NavigableMap<Integer, List<MethodHandle>> varArgsMethods) {
        super();
        this.methods = methods;
        this.varArgsMethods = varArgsMethods;
    }

    @Override
    public boolean acceptsArity(int arity) {
        return methods.containsKey(arity) || 
                ! varArgsMethods.headMap(arity, true).isEmpty();
    }

    @Override
    public Object invoke(Object... args) throws Throwable {
        
        DynamicTypeLookup<MethodHandle> lookup = new DynamicTypeLookup<>(TypedHelpers::of);
        List<Class<?>> argClasses = getArgClasses(args);
        var maybe = forSize(args.length).map(List::stream);
        
        MethodHandle method = maybe.flatMap(candidates -> lookup.findMatchingTarget(argClasses, candidates)) 
             .orElseThrow(() -> new PileExecutionException("Could not call java method")); 
             
        return method.invokeWithArguments(args);        
    }

    private Optional<List<MethodHandle>> forSize(int size) {
        if (methods.containsKey(size)) {
            return Optional.of(methods.get(size));
        } else {
            NavigableMap<Integer, List<MethodHandle>> varMethods = varArgsMethods.headMap(size + 1, true);
            var out = varMethods.values().stream()
                                  .flatMap(List::stream)
                                  .map(m -> m.asCollector(m.type().lastParameterType(),
                                          size - m.type().parameterCount() + 1))
                                  .toList();
            
            return Optional.of(out);
        }
    }

    public static JavaMethod of(Class<?> base, String name) throws Exception {
        if ("new".equals(name)) {
            return collectConstructors(base);
        } else {
            return collectMethods(base, name);
        }
    }

    private static JavaMethod collectConstructors(Class<?> base) throws Exception {
        final Map<Integer, List<MethodHandle>> methods = new HashMap<>();
        final NavigableMap<Integer, List<MethodHandle>> varArgsMethods = new TreeMap<>();
        
        for (var cons : base.getConstructors()) {
            boolean varArgs = cons.isVarArgs();
            MethodHandle handle = LookupHolder.PUBLIC_LOOKUP.unreflectConstructor(cons);
            int size = handle.type().parameterCount();
            final Map<Integer, List<MethodHandle>> map;
            if (varArgs) {
                map = varArgsMethods;
            } else {
                map = methods;
            }
            map.computeIfAbsent(size, k -> new ArrayList<>()).add(handle);
        }
        
        return new JavaMethod(methods, varArgsMethods);
    }

    private static JavaMethod collectMethods(Class<?> base, String name) throws IllegalAccessException {
        var namedMethods = Arrays.stream(base.getMethods())
                .filter(m -> m.getName().equals(name))
                .toList();
                
        if (namedMethods.isEmpty()) {
            throw new IllegalArgumentException("No methods for "+ base.getName() + "."+ name);
        }
        
        List<Method> statics = new ArrayList<>();
        List<Method> instances = new ArrayList<>();
        
        namedMethods.forEach(m -> {
            boolean isStatic = Modifier.isStatic(m.getModifiers());
            if (isStatic) {
                statics.add(m);
            } else {
                instances.add(m);
            }
        });
        
        if (! statics.isEmpty() && ! instances.isEmpty()) {
            throw new IllegalArgumentException("Named method must only have static or instance methods, not both.");
        }
        
        List<Method> all = new ArrayList<>();
        all.addAll(statics);
        all.addAll(instances);
        
        final Map<Integer, List<MethodHandle>> methods = new HashMap<>();
        final NavigableMap<Integer, List<MethodHandle>> varArgsMethods = new TreeMap<>();
        
        for (var m : all) {
            boolean varArgs = m.isVarArgs();
            MethodHandle handle = LookupHolder.PUBLIC_LOOKUP.unreflect(m);
            int size = handle.type().parameterCount();
            final Map<Integer, List<MethodHandle>> map;
            if (varArgs) {
                map = varArgsMethods;
            } else {
                map = methods;
            }
            map.computeIfAbsent(size, k -> new ArrayList<>()).add(handle);
        }
        
        return new JavaMethod(methods, varArgsMethods);
    }

}
