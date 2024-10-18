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
import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import pile.compiler.typed.Any;
import pile.compiler.typed.DynamicTypeLookup;
import pile.compiler.typed.TypedHelpers;
import pile.core.exception.UnlinkableMethodException;
import pile.core.indy.guard.GuardBuilder;
import pile.core.indy.guard.JavaGuardBuilder;
import pile.core.indy.guard.ReceiverTypeGuard;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.method.AbstractRelinkingCallSite;
import pile.util.Pair;

/**
 * 
 * @author john
 * @implNote Threading Concerns: None, currently. This callsite infinite chains
 *           guard tests, which is obviously dumb but has no threading concerns.
 */
public class InteropInstanceMethodCallSite extends AbstractRelinkingCallSite {
	
	private static Logger LOG = LoggerSupplier.getLogger(InteropInstanceMethodCallSite.class);
	
	private final String methodName;

    private final long anyMask;

    private final Lookup caller;

	public InteropInstanceMethodCallSite(Lookup caller, MethodType type, String methodName, long anyMask) {
		super(type);
		this.methodName = methodName;
		this.anyMask = anyMask;
		this.caller = caller;
	}

    public static Optional<MethodHandle> crackReflectedMethod(Lookup lookup, Class<?> clazz, String methodName,
            Method actualMethod) {
        try {
            var method = lookup.unreflect(actualMethod);
            return Optional.of(method);
        } catch (IllegalAccessException iae) {
            // also slow way
            // TODO There's gotta be a faster way to do this.
            // So we can't touch the actual class but we may be able to touch interfaces
            // that the class implements. We do have a reference to a Method we can't 
            // crack but we can steal the actual argument types. 
            Class<?>[] parameterTypes = actualMethod.getParameterTypes();
            Class<?> returnType = actualMethod.getReturnType();
            MethodType actualMethodType = methodType(returnType, parameterTypes);
            
            // Might not be any interfaces either, but might be in a supertype.
            var currentType = actualMethod.getDeclaringClass();
            while ( currentType != null) {
                try {
                    MethodHandle maybeVirtual = lookup.findVirtual(currentType, methodName, actualMethodType);
                    return Optional.of(maybeVirtual);
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    // pass
                }
                for (Class<?> iface : currentType.getInterfaces()) {                    
                    try {
                        var ifaceMethod = lookup.findVirtual(iface, methodName, actualMethodType);
                        return Optional.of(ifaceMethod);
                    } catch (NoSuchMethodException | IllegalAccessException e1) {
                        // pass
                    }
                }
                currentType = currentType.getSuperclass();
            }
            
            return Optional.empty();
        }
    }
	
	@Override
	protected MethodHandle findHandle(Object[] args) throws Throwable {
		
		MethodType type = type();
		if (args[0] == null) {
		    throw new NullPointerException("Receiver type is null, cannot determine method target: " + methodName);
		}
		
		List<Class<?>> staticTypes = blendAnyMask(type(), anyMask);
		
		List<Class<?>> runtimeTypes = getArgClasses(args);
		Class<?> receiverType = runtimeTypes.get(0);
		
		boolean[] contentionIndexes = new boolean[runtimeTypes.size()];
        DynamicTypeLookup<Method> dyn = new DynamicTypeLookup<Method>(TypedHelpers::ofMethod);
        List<Class<?>> staticMethodTypes = withoutHead(staticTypes);
        List<Class<?>> runtimeMethodTypes = withoutHead(runtimeTypes);
        Optional<Method> matchedMethod = dyn.findMatchingTarget(staticMethodTypes, runtimeMethodTypes,
                i -> contentionIndexes[i+1] = true, TypedHelpers.findInstanceMethods(receiverType, methodName));
        MethodHandle handle = matchedMethod
                .flatMap(method -> crackReflectedMethod(caller, receiverType, methodName, method))
                .orElseThrow(() -> new UnlinkableMethodException("Could not find method " + receiverType + "." + methodName
                        + runtimeTypes));
                        
        GuardBuilder builder = new JavaGuardBuilder(handle, relink, type);
        if (! staticTypes.get(0).equals(receiverType)) {
            // If the receiver isn't statically known, have to guard it because our
            // candidates are all based on this type.
            builder.guardExact(0, receiverType);
        }
        for (int i = 1; i < runtimeTypes.size(); ++i) {
            if (!contentionIndexes[i]) {
                // If there's no contention at a particular index then don't create a guard for
                // it. Consider two targets:
                // foo(String, int)
                // foo(String, String)
                // The first index has no contention because both methods must have the same
                // type. Even though we may not actually know that our type matches the target
                // types we still don't need to guard here. If it's wrong the asType method cast
                // will blow up.
                continue;
            }
            Class<?> paramType = runtimeTypes.get(i);
            if (Void.class.equals(paramType)) {
                builder.guardNull(i);
            } else {
                builder.guardExact(i, paramType);
            }
        }
        
        LOG.debug("Dynamically linking to %s", matchedMethod.get());
        
        return builder.getHandle();		
	}
}
