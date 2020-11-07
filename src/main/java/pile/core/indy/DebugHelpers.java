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
package pile.core.indy;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import pile.collection.PersistentMap;
import pile.core.exception.PileInternalException;
import pile.nativebase.NativeData;
import pile.nativebase.method.PileInvocationException;

public class DebugHelpers {

    private static final MethodHandle RETHROW_HANDLE, FORMAT_HANDLE;
    
    // alters the exceptions that get produced so don't want it on always.
    private static final boolean DEBUG = false; 
//    Boolean.valueOf(System.getenv("pile.debug"));
    
    static {
        try {
            RETHROW_HANDLE = lookup().findStatic(DebugHelpers.class, "rethrow",
                    methodType(void.class, String.class, Throwable.class));
            FORMAT_HANDLE = lookup().findStatic(DebugHelpers.class, "formatted",
                    methodType(void.class, String.class, Throwable.class, Object[].class));                    
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new PileInternalException("Shouldn't happen.", e);
        }
    }

    private DebugHelpers() {}
    
    public static boolean isDebugEnabled() {
        return DEBUG;
    }
    
    static final void rethrow(String message, Throwable t) {
        throw new PileInvocationException(message, t);
    } 
    
    static final void formatted(String raw, Throwable t, Object... args) {
        String message = String.format(raw, args);
        throw new PileInvocationException(message, t);
    }
    
    /**
     * Creates a {@link MethodHandle} which calls the provided function and if
     * there's an exception will wrap the provided exception in a
     * {@link PileInvocationException} with the provided error message.
     * 
     * @param targetHandle
     * @param errorMessage
     * @return
     */
    public static final MethodHandle catchWithMessage(MethodHandle targetHandle, String errorMessage) {
        MethodHandle handler = insertArguments(RETHROW_HANDLE, 0, errorMessage);
        MethodHandle typed = handler.asType(handler.type().changeReturnType(targetHandle.type().returnType()));
        return catchException(targetHandle, Throwable.class, typed);
    }
    
    /**
     * Creates a method handle which will call the provided handle and if there's an
     * exception will create an error message by formatting the provided string with
     * the arguments that were used to call the function, and then wrapping the
     * thrown exception with a {@link PileInvocationException} with the filled in
     * template.
     * 
     * @param targetHandle The method to call
     * @param formatString The {@link String#format(String, Object...) format
     *                     string} which will be formatted with the args used to
     *                     call the target method.
     * @return A new handle with the described behavior.
     */
    public static final MethodHandle catchFormatted(MethodHandle targetHandle, String formatString) {
        MethodHandle handler = insertArguments(FORMAT_HANDLE, 0, formatString);
        MethodHandle rtype = handler.asType(handler.type().changeReturnType(targetHandle.type().returnType()));
        MethodHandle collected = rtype.asCollector(1, Object[].class, targetHandle.type().parameterCount());
        MethodType handleWithT = targetHandle.type().insertParameterTypes(0, Throwable.class);
        MethodHandle bound = collected.asType(handleWithT);
        return catchException(targetHandle, Throwable.class, bound);
    }

}
