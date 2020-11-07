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
package pile.core.indy.guard;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pile.core.exception.PileInternalException;

/**
 * Create a {@link MethodHandle} appropriate to guard the types provided
 * exactly.
 * 
 */
public class FullTypeGuard implements Guard {

    private static final MethodHandle CLASS_EQ, GET_CLASS, IS_ASSIGNABLE_FROM;

    static {
        try {
            CLASS_EQ = lookup().findVirtual(Class.class, "equals", methodType(boolean.class, Object.class));
            IS_ASSIGNABLE_FROM = lookup().findVirtual(Class.class, "isAssignableFrom",
                    methodType(boolean.class, Class.class));
            GET_CLASS = lookup().findVirtual(Object.class, "getClass", methodType(Class.class));
        } catch (ReflectiveOperationException e) {
            throw new PileInternalException(e);
        }
    }

    private final List<Class<?>> actualTypeArray = new ArrayList<>();
    private final MethodType staticTypes;
    private final MethodHandle testHandle;

    public FullTypeGuard(MethodHandle classEqTest, List<Class<?>> args, MethodType staticTypes) {
        this.actualTypeArray.addAll(args);
        this.staticTypes = staticTypes;
        this.testHandle = classEqTest;
    }

    @Override
    public MethodHandle guard(MethodHandle target, MethodHandle fallback) {
        MethodHandle wrappedTarget = target.asType(staticTypes);
        for (int i = 0; i < actualTypeArray.size(); ++i) {
            Class<?> dynamicClazz = actualTypeArray.get(i);
            Class<?> targetType = target.type().parameterType(i);
            // TODO Primitives?
            if (Object.class.equals(targetType)) {
                continue;
            }
            Class<?> staticType = staticTypes.parameterType(i);
            if (targetType.isAssignableFrom(staticType)) {
                continue;
            }
            MethodHandle partial = testHandle.asType(methodType(boolean.class, Class.class, Class.class))
                    .bindTo(dynamicClazz);
            MethodHandle getClass = filterArguments(partial, 0, GET_CLASS);
            MethodHandle typed = getClass.asType(methodType(boolean.class, staticType));
            // (...., arg)
            MethodHandle dropped = dropArgumentsToMatch(typed, 0, staticTypes.parameterList(), i);
            wrappedTarget = guardWithTest(dropped, wrappedTarget, fallback);

        }
        return wrappedTarget;
    }
    
    public static FullTypeGuard getEqualsGuard(Class<?>[] actualTypes, MethodType staticTypes) {
        return new FullTypeGuard(CLASS_EQ, Arrays.asList(actualTypes), staticTypes);
    }
    
    public static FullTypeGuard getEqualsGuard(Object[] args, MethodType staticTypes) {
        List<Class<?>> actualTypeArray = new ArrayList<>();
        Arrays.stream(args).map(o -> o == null ? Object.class : o.getClass()).forEach(actualTypeArray::add);
        return new FullTypeGuard(CLASS_EQ, actualTypeArray, staticTypes);
    }
    
    public static FullTypeGuard getSubtypeGuard(Class<?>[] actualTypes, MethodType staticTypes) {
        return new FullTypeGuard(IS_ASSIGNABLE_FROM, Arrays.asList(actualTypes), staticTypes);
    }
    
    public static FullTypeGuard getSubtypeGuard(Object[] args, MethodType staticTypes) {
        List<Class<?>> actualTypeArray = new ArrayList<>();
        Arrays.stream(args).map(o -> o == null ? Object.class : o.getClass()).forEach(actualTypeArray::add);
        return new FullTypeGuard(IS_ASSIGNABLE_FROM, actualTypeArray, staticTypes);
    }
}
