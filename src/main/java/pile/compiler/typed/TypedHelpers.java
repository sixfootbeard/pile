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
package pile.compiler.typed;

import static java.lang.invoke.MethodType.*;
import static pile.compiler.Helpers.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class TypedHelpers {

    private TypedHelpers() {}
    
    public static Stream<Method> findMethods(Class<?> receiver, String methodName, boolean isStatic) {
        return isStatic ? findStaticMethods(receiver, methodName) : findInstanceMethods(receiver, methodName);
    }

    public static Stream<Method> findStaticMethods(Class<?> receiver, String methodName) {
        return Arrays.stream(receiver.getMethods())
                .filter(m -> Modifier.isStatic(m.getModifiers()))
                .filter(m -> m.getName().equals(methodName));                
    }

    public static Stream<Method> findInstanceMethods(Class<?> receiver, String methodName) {
        return Arrays.stream(receiver.getMethods())
                .filter(m -> ! Modifier.isStatic(m.getModifiers()))
                .filter(m -> m.getName().equals(methodName));                
    }
    
    public static Stream<Constructor> findConstructors(Class<?> clazz) {
        return Arrays.stream(clazz.getConstructors());
    }

    public static TypeVarArg of(MethodHandle handle) {
        return new TypeVarArg(handle.type(), handle.isVarargsCollector());
    }

    public static TypeVarArg ofMethod(Method method) {
        var parameterTypes = Arrays.asList(method.getParameterTypes());
        return new TypeVarArg(methodType(method.getReturnType(), parameterTypes), method.isVarArgs());
    }
    
    public static TypeVarArg ofConstructor(Constructor<?> cons) {
        return new TypeVarArg(methodType(cons.getDeclaringClass(), cons.getParameterTypes()), cons.isVarArgs());
    }

    public static boolean chooseNarrowLeft(Class<?> lhsType, Class<?> rhsType) {
        if (lhsType.isAssignableFrom(rhsType)) {
            return false;
        } else if (rhsType.isAssignableFrom(lhsType)) {
            return true;
        } else {
            lhsType = toWrapper(lhsType);
            rhsType = toWrapper(rhsType);
            if (lhsType.isAssignableFrom(rhsType)) {
                return false;
            } else if (rhsType.isAssignableFrom(lhsType)) {
                return true;
            }
        }
        // Two unrelated terms both of which are candidates, arbitrarily pick one.
        return true;
    }

    public static Class<?> chooseNarrow(Class<?> lhsType, Class<?> rhsType) {
        return chooseNarrowLeft(lhsType, rhsType) ? lhsType : rhsType;
    }

    public static MethodHandle quietUnreflectConstructor(Lookup lookup, Constructor cons) {
    	try {
    		return lookup.unreflectConstructor(cons);
    	} catch (IllegalAccessException e) {
    		throw error("Unable to unreflect constructor:" + cons, e);
    	}
    }
    
    public static MethodHandle quietUnreflectField(Lookup lookup, Field f) {
        try {
            return lookup.unreflectGetter(f);
        } catch (IllegalAccessException e) {
            throw error("Unable to unreflect field:" + f);
        }
    }


}
