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
import static java.util.Objects.*;
import static pile.core.indy.guard.GuardBuilder.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Objects;

import pile.core.ProtocolMethod;

/**
 * Builds type guards.
 *
 */
public class GuardBuilder {

    private static final MethodHandle OBJ_EQ, CLASS_EQ, GET_CLASS, IS_ASSIGNABLE_FROM, IS_NULL;

    static {
        try {
            OBJ_EQ = lookup().findStatic(Objects.class, "equals", methodType(boolean.class, Object.class, Object.class));
            CLASS_EQ = lookup().findVirtual(Class.class, "equals", methodType(boolean.class, Object.class))
                    .asType(methodType(boolean.class, Class.class, Class.class));
            IS_ASSIGNABLE_FROM = lookup().findVirtual(Class.class, "isAssignableFrom",
                    methodType(boolean.class, Class.class));
            GET_CLASS = lookup().findVirtual(Object.class, "getClass", methodType(Class.class));
            IS_NULL = lookup().findStatic(Objects.class, "isNull", methodType(boolean.class, Object.class));

        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    protected final MethodHandle relink;
    protected final MethodType staticTypes;

    //
    protected MethodHandle base;

    /**
     * 
     * @param base        The target method to call
     * @param relink      The relinking method to call, on failure.
     * @param staticTypes The static types of the callsite.
     */
    public GuardBuilder(MethodHandle base, MethodHandle relink, MethodType staticTypes) {
        super();
        this.base = base.asType(staticTypes);
        this.relink = relink.asType(staticTypes);
        this.staticTypes = staticTypes;
    }

    /**
     * Guard index such that the argument must always match the provided type
     * exactly.
     * 
     * @param argIndex The index to match.
     * @param type     The type to match against.
     */
    public void guardExact(int argIndex, Class<?> type) {
        guardHandle(argIndex, type, CLASS_EQ);
    }

    /**
     * Guard index such that the argument must always be a subtype of the the
     * provided type .
     * 
     * @param argIndex The index to match.
     * @param type     The type to match against.
     */
    public void guardSubtype(int argIndex, Class<?> type) {
        guardHandle(argIndex, type, IS_ASSIGNABLE_FROM);
    }
    
    /**
     * Guard index such that the argument must always be null of any type.
     * 
     * @param argIndex Index to check.
     */
    public void guardNull(int argIndex) {
        MethodHandle typed = IS_NULL.asType(methodType(boolean.class, staticType(argIndex)));
        MethodHandle dropped = dropArgumentsToMatch(typed, 0, staticTypes.parameterList(), argIndex);
        base = guardWithTest(dropped, base, relink);
    }

    public void guardNotNull(int argIndex) {
        MethodHandle typed = IS_NULL.asType(methodType(boolean.class, staticType(argIndex)));
        MethodHandle dropped = dropArgumentsToMatch(typed, 0, staticTypes.parameterList(), argIndex);
        base = guardWithTest(dropped, relink, base);
    }


    /**
     * Guard index such that the provided type bipredicate test returns true.
     * 
     * @param argIndex Index to guard
     * @param type     The expected type
     * @param test     The handle which accepts the expected type and actual type
     */
    public void guardHandle(int argIndex, Class<?> type, MethodHandle test) {
        MethodHandle boundTest = test.bindTo(type);
        MethodHandle typed = filterArguments(boundTest, 0, GET_CLASS)
                .asType(methodType(boolean.class, staticTypes.parameterType(argIndex)));
        // (...., arg)
        MethodHandle dropped = dropArgumentsToMatch(typed, 0, staticTypes.parameterList(), argIndex);
        base = guardWithTest(dropped, base, relink);
    }
    

    public void guardEquals(int argIndex, Object key) {
        MethodHandle bound = OBJ_EQ.bindTo(key);
        MethodHandle typed = bound.asType(methodType(boolean.class, staticTypes.parameterType(argIndex)));
        // (...., arg)
        MethodHandle dropped = dropArgumentsToMatch(typed, 0, staticTypes.parameterList(), argIndex);
        base = guardWithTest(dropped, base, relink);        
    }

    public MethodHandle getHandle() {
        return base;
    }

    protected Class<?> staticType(int argIndex) {
        return staticTypes.parameterType(argIndex);
    }

}
