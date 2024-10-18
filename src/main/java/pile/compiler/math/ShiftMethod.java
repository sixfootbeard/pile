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
package pile.compiler.math;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;
import static pile.compiler.Helpers.*;
import static pile.compiler.math.NumberHelpers.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;

import pile.core.PileMethod;
import pile.core.exception.PileExecutionException;
import pile.core.indy.CallSiteType;
import pile.core.indy.CompilerFlags;
import pile.core.indy.guard.FullTypeGuard;
import pile.core.method.AbstractRelinkingCallSite;
import pile.core.runtime.generated_classes.LookupHolder;

public class ShiftMethod implements PileMethod {

    private final String methodName;

    public ShiftMethod(String methodName) {
        super();
        this.methodName = methodName;
    }

    @Override
    public boolean acceptsArity(int arity) {
        return arity == 2;
    }
    
    @Override
    public Optional<CallSite> staticLink(CallSiteType csType, MethodType staticTypes, long anyMask) {
        List<Class<?>> types = blendAnyMask(staticTypes, anyMask);
        Class<?> baseClass = types.get(0);
        Class<?> distanceClass = types.get(1);

        if (isNumberType(baseClass) && isNumberType(distanceClass)) {
            return promote(toPrimitive(baseClass), getLI())
                    .map(this::findMethod)
                    .map(h -> h.asType(staticTypes))
                    .map(ConstantCallSite::new);
        }
        return Optional.empty();
    }

    @Override
    public CallSite dynamicLink(CallSiteType csType, MethodType statictypes, long anyMask, CompilerFlags flags) {

        return new AbstractRelinkingCallSite(statictypes) {

            @Override
            protected MethodHandle findHandle(Object[] args) throws Throwable {
                MethodType argTypes = getMethodTypeFromArgs(args);
                MethodHandle handle = staticLink(CallSiteType.PLAIN, argTypes, 0L)
                                        .orElseThrow(() -> makeError(argTypes))
                                        .dynamicInvoker();
                
                FullTypeGuard guard = FullTypeGuard.getEqualsGuard(args, statictypes);
                MethodHandle fullGuard = guard.guard(handle, relink);
                return fullGuard;
            }
        };
    }
    
    @Override
    public Object invoke(Object... args) throws Throwable {
        MethodType argTypes = getMethodTypeFromArgs(args);
   
        return staticLink(CallSiteType.PLAIN, argTypes, 0L)
                .orElseThrow(() -> makeError(argTypes))
                .dynamicInvoker().invokeWithArguments(args);

    }

    private MethodHandle findMethod(Class<?> clazz) {
        try {
            return LookupHolder.PRIVATE_LOOKUP.findStatic(NumberMethods.class, methodName,
                    methodType(clazz, clazz, Long.TYPE));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw error("Shouldn't happen", e);
        }
    }

    private PileExecutionException makeError(MethodType type) {
        return new PileExecutionException("Shift method: Bad types: " + type);
    }

}
