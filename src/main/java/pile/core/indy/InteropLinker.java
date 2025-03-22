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
import static pile.compiler.Helpers.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import pile.compiler.Helpers;
import pile.compiler.form.InteropType;
import pile.compiler.typed.DynamicTypeLookup;
import pile.compiler.typed.StaticTypeLookup;
import pile.compiler.typed.TypedHelpers;
import pile.core.exception.PileCompileException;
import pile.core.exception.UnlinkableMethodException;
import pile.core.indy.guard.FullTypeGuard;
import pile.core.indy.guard.GuardBuilder;
import pile.core.indy.guard.JavaGuardBuilder;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.method.AbstractRelinkingCallSite;
import pile.core.runtime.generated_classes.LookupHolder;
import pile.nativebase.method.PileInvocationException;
import pile.util.InvokeDynamicBootstrap;

public class InteropLinker {
	
	private static final Logger LOG = LoggerSupplier.getLogger(InteropLinker.class);

	@InvokeDynamicBootstrap
	public static CallSite bootstrap(Lookup raw, String name, MethodType type, InteropType interopMethod,
			long anyMask, Object... args) throws Exception {
			
		var caller = raw.dropLookupMode(Lookup.MODULE);
	    
		switch (interopMethod) {
			case STATIC_METHOD_CALL: {
				// indy args: class,
				// stack: arg0, arg1...
				return new InteropStaticMethodCallSite(caller, type, loadClass((String) args[0]), name, anyMask);
			}
			case INSTANCE_CALL: {
				// indy args: <none>
				// stack: recv, arg0, arg1...
				return new InteropInstanceMethodCallSite(caller, type, name, anyMask);
			} 
			case INSTANCE_FIELD_GET: {
			    Class<?> base = type.parameterType(0);
			    var nullReceiverHandle = getExceptionHandle(type, NullPointerException.class, NullPointerException::new,
			            "Cannot get field " + name + " with a null receiver.");
			            
				if (Object.class.equals(base)) {
					return new AbstractRelinkingCallSite(type) {	
					
					    /**
					     * @implSpec Threading Concerns: None, relinks on failure.
					     */
						@Override
						protected MethodHandle findHandle(Object[] args) throws Throwable {
						    if (args[0] == null) {
						        GuardBuilder builder = new JavaGuardBuilder(nullReceiverHandle, relink, type);
						        builder.guardNull(0);
                                return builder.getHandle();
						    } else {						    
    							Class<?> argBaseClass = args[0].getClass();
    							MethodHandle handle = findField(caller, argBaseClass, name)
    													.orElseThrow(() -> new PileCompileException("Cannot find field: " + argBaseClass + "." + name))
    													.asType(type);
    							
    							GuardBuilder builder = new JavaGuardBuilder(handle, relink, type);
    							builder.guardExact(0, argBaseClass);
    							builder.guardNotNull(0);
    							return builder.getHandle();
						    }							
						}
					};
				} else {
					MethodHandle handle = findField(caller, base, name)
											.orElseThrow(() -> error("Cannot find field: " + base + "." + name))
											.asType(type);
                            
                    GuardBuilder builder = new JavaGuardBuilder(handle, nullReceiverHandle, type);
                    builder.guardNotNull(0);
					
					return new ConstantCallSite(builder.getHandle());
				}
			}

			default:
				throw new PileCompileException("Unexpected interop type: " + interopMethod);
		}
	}
	
	/**
	 * @param caller
	 * @param method
	 * @param type
	 * @param className
	 * @return
	 * @throws ClassNotFoundException
	 * @implSpec Threading Concerns: None, guard relinks on failure. 
	 */
	@InvokeDynamicBootstrap
	public static CallSite constructor(Lookup caller, String method, MethodType type, Long anyMask, String className)
			throws ClassNotFoundException {
		Class<?> clazz = loadClass(className);
		LOG.debug("Linking to constructor for '%s'", clazz);
		
		List<Class<?>> blendedTypes = blendAnyMask(type, anyMask);
		
        StaticTypeLookup<Constructor> staticLookup = new StaticTypeLookup<>(TypedHelpers::ofConstructor);
        Optional<Constructor> singleMatch = staticLookup.findSingularMatchingTarget(blendedTypes,
                TypedHelpers.findConstructors(clazz));
        if (singleMatch.isPresent()) {
            Constructor cons = singleMatch.get();
            LOG.debug("Directly linking to constructor: %s with static types: %s", cons, type);
            MethodHandle handle = TypedHelpers.quietUnreflectConstructor(caller, cons);
            return new ConstantCallSite(handle.asType(type));
        } else {
            LOG.debug("Creating relinking callsite for class '%s' with types: %s", clazz, type);
            return new AbstractRelinkingCallSite(type) {
                @Override
                protected MethodHandle findHandle(Object[] args) throws Throwable {
                    DynamicTypeLookup<Constructor> dyn = new DynamicTypeLookup<>(TypedHelpers::ofConstructor);
                    List<Class<?>> argClasses = getArgClasses(args);

                    boolean[] contentionIndexes = new boolean[argClasses.size()];
                    Optional<Constructor> matchedMethod = dyn.findMatchingTarget(blendedTypes, argClasses,
                            i -> contentionIndexes[i] = true, TypedHelpers.findConstructors(clazz));

                    MethodHandle handle = matchedMethod
                            .map(cons -> TypedHelpers.quietUnreflectConstructor(caller, cons))
                            .orElseThrow(() -> new UnlinkableMethodException(
                                    "Could not find constructor: " + clazz + argClasses));

                    LOG.debug("Linking to constructor for '%s': %s", clazz, handle.type());

                    MethodHandle typedHandle = handle.asType(type);

                    GuardBuilder builder = new JavaGuardBuilder(typedHandle, getTarget(), type);
                    for (int i = 0; i < handle.type().parameterCount(); ++i) {
                        if (contentionIndexes[i]) {
                            builder.guardExact(i, toWrapper(handle.type().parameterType(i)));
                        }
                    }

                    return builder.getHandle();
                }
            };
        }
	}
	
    public static MethodHandle findConstructor(Class<?> clazz, List<Class<?>> args) throws IllegalAccessException {
        Lookup caller;
        if (clazz.getName().startsWith(LookupHolder.PACKAGE_NAME)) {
            caller = LookupHolder.PRIVATE_LOOKUP;
        } else {
            caller = LookupHolder.PUBLIC_LOOKUP;
        }
        DynamicTypeLookup<Constructor> dyn = new DynamicTypeLookup<>(TypedHelpers::ofConstructor);
        return dyn.findMatchingTarget(args, TypedHelpers.findConstructors(clazz))
                .map(c -> TypedHelpers.quietUnreflectConstructor(caller, c)).orElseThrow(() -> {
                    MethodType methodType = methodType(clazz, args);
                    String msg = "Cannot find constructor to call: %s %s";
                    return new PileInvocationException(String.format(msg, clazz, methodType));
                });
    }

    private static Optional<MethodHandle> findField(Lookup caller, Class<?> clazz, String name) {    
    	return Arrays.stream(clazz.getFields())
    			.filter(f -> name.equals(f.getName()))
    			.map(f -> TypedHelpers.quietUnreflectField(caller, f))
    			.findFirst();
    }

}
