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

import static pile.compiler.Helpers.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pile.compiler.typed.Any;
import pile.compiler.typed.DynamicTypeLookup;
import pile.compiler.typed.NativeMethodTypeLookup;
import pile.compiler.typed.StaticTypeLookup;
import pile.compiler.typed.SubtypeMatcher;
import pile.compiler.typed.TypedHelpers;
import pile.core.PileMethod;
import pile.core.StaticTypeMismatchException;
import pile.core.exception.PileException;
import pile.core.exception.PileExecutionException;
import pile.core.indy.CallSiteType;
import pile.core.indy.CompilerFlags;
import pile.core.indy.guard.GuardBuilder;
import pile.nativebase.NativeCore;

/**
 * Represents a function in the {@link NativeCore native standard library}.<br>
 * Linking:
 * <ul>
 * <li>Static - All unambiguous arity targets are always directly linked (which
 * could cause {@link ClassCastException} if improperly called). If arity+types
 * match it will be statically linked, otherwise deferred.</li>
 * <li>Dynamic - Uses {@link NativeMethodInlineCacheCallSite} which is
 * configured via {@link CompilerFlags} at the {@link CallSite}.
 * </ul>
 *
 */
public class HiddenNativeMethod implements PileMethod {

    private final Map<Integer, List<MethodHandle>> arityHandles;
    private final MethodHandle varArgsMethod;
    private final int varArgsAirity;
    private final Optional<Class<?>> returnType;
    private final boolean isPure;

    public HiddenNativeMethod(Map<Integer, List<MethodHandle>> arityHandles, int varArgsAirity,
            MethodHandle varArgsMethod, Class<?> returnType, boolean isPure) {

        this.arityHandles = arityHandles;
        this.varArgsAirity = varArgsAirity;
        this.varArgsMethod = varArgsMethod;
        this.returnType = Optional.<Class<?>>of(returnType).filter(c -> c.equals(Any.class));
        this.isPure = isPure;
    }

    @Override
    public boolean acceptsArity(int arity) {
        return (arityHandles != null && arityHandles.containsKey(arity))
                || (varArgsAirity != -1 && varArgsAirity <= arity);
    }

    @Override
    public boolean isPure() {
        return isPure;
    }

    @Override
    public Optional<Class<?>> getReturnType() {
        return returnType;
    }

    @Override
    public Optional<Class<?>> getReturnType(CallSiteType csType, MethodType staticTypes, long anyMask) {
        return returnType;
    }

    @Override
    public Optional<CallSite> staticLink(CallSiteType csType, MethodType staticTypes, long anyMask) {
        if (csType == CallSiteType.PLAIN) {
            MethodType staticTypeAny = blendAnyMaskType(staticTypes, anyMask);
            return findHandlesMatchingSize(staticTypes.parameterCount())
                .flatMap(handles -> {
                    StaticTypeLookup<MethodHandle> tl = new StaticTypeLookup<>(TypedHelpers::of);
                    Optional<MethodHandle> match = tl.findSingularMatchingTarget(staticTypeAny.parameterList(), handles.stream());
                    match = match.filter(maybe -> maybe.type().equals(maybe.type().generic()));
                    return match;
                }).map(ConstantCallSite::new)
            ;            
        }
        return Optional.empty();
    }

    @Override
    public CallSite dynamicLink(CallSiteType csType, MethodType statictypes, long anyMask, CompilerFlags flags) {
        if (csType == CallSiteType.PLAIN) {
            return new AbstractRelinkingCallSite(statictypes) {
                
                @Override
                protected MethodHandle findHandle(Object[] args) throws Throwable {
                    List<Class<?>> actualTypes = getArgClasses(args);
                    NativeMethodTypeLookup<MethodHandle> typeLookup = new NativeMethodTypeLookup<>(TypedHelpers::of);
                    final List<MethodHandle> handles;
                    List<MethodHandle> fixedHandles = arityHandles.get(args.length);
                    if (fixedHandles != null) {
                        handles = fixedHandles;
                    } else {
                        if (varArgsAirity != -1 && varArgsAirity <= args.length) {
                            // TODO OBO?
                            handles = List.of(varArgsMethod);
                        } else {
                            return getExceptionHandle(statictypes, RuntimeException.class, RuntimeException::new, "Invalid arity: " + args.length);
                        }
                    }
                    boolean[] contention = new boolean[args.length];
                    Optional<MethodHandle> maybeMatch = typeLookup.findMatchingTarget(actualTypes, actualTypes, i -> contention[i] = true, handles.stream());
                    if (maybeMatch.isPresent()) {
                        MethodHandle target = maybeMatch.get();
                        GuardBuilder guard = new GuardBuilder(target, relink, statictypes);
                        for (int i = 0; i < args.length; ++i) {
                            if (contention[i]) {
                                guard.guardSubtype(i, target.type().parameterType(i));
                                // FIXME Bad for seq(null), will always relink...
                                guard.guardNotNull(i);
                            }
                        }
                        return guard.getHandle();
                    } else {
                        return getExceptionHandle(statictypes, PileExecutionException.class, PileExecutionException::new, "Unable to find method to call");
                    }
                    
                }
            };
        } else {
            return PileMethod.super.dynamicLink(csType, statictypes, anyMask, flags);
        }
    }    

    @Override
    public Object invoke(Object... args) throws Throwable {
        List<Class<?>> argClasses = getArgClasses(args);
        DynamicTypeLookup<MethodHandle> lookup = new DynamicTypeLookup<>(TypedHelpers::of);
        Optional<List<MethodHandle>> maybe = findHandlesMatchingSize(args.length);
        Optional<MethodHandle> tgt = Optional.empty();
        if (maybe.isPresent()) {
            List<MethodHandle> handles = maybe.get();
            if (handles.size() == 1) {
                tgt = Optional.of(handles.get(0));
            } else {
                for (var candidate : handles) {
                    if (lookup.isCandidate(argClasses, candidate)) {
                        tgt = Optional.of(candidate);
                        break;
                    }
                }
            }
        }
        MethodHandle handle = tgt
                .orElseThrow(() -> new PileExecutionException("No matching arity/type method to call"));
            
        return handle.invokeWithArguments(args);
    }


    Optional<List<MethodHandle>> findHandlesMatchingSize(int size) {
        if (arityHandles.containsKey(size)) {
            return Optional.of(arityHandles.get(size));
        } else if (varArgsAirity != -1 && varArgsAirity <= size) {
            MethodType vtype = varArgsMethod.type();
            MethodHandle vararg = varArgsMethod.asCollector(vtype.lastParameterType(),
                    size - vtype.parameterCount() + 1);
            return Optional.of(List.of(vararg));
        }
        return Optional.empty();
    }

    Optional<MethodHandle> tryLink(MethodType type) {
        return findHandlesMatchingSize(type.parameterCount())
                .flatMap(handles -> {
                    if (handles.size() == 1) {
                        return Optional.of(handles.get(0));
                    } else {
                        DynamicTypeLookup<MethodHandle> lookup = new DynamicTypeLookup<>(TypedHelpers::of);
                        for (var candidate : handles) {
                            if (lookup.isCandidate(type.parameterList(), type.parameterList(), candidate)) {
                                return Optional.of(candidate);
                            }
                        }
                        return Optional.empty();
                    }
                });
    }
}
