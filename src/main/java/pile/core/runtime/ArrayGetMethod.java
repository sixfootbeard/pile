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
package pile.core.runtime;

import static java.lang.invoke.MethodHandles.*;
import static pile.compiler.Helpers.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;

import pile.core.PileMethod;
import pile.core.exception.PileExecutionException;
import pile.core.indy.CallSiteType;
import pile.core.indy.CompilerFlags;
import pile.core.indy.guard.GuardBuilder;
import pile.core.indy.guard.JavaGuardBuilder;
import pile.core.method.AbstractRelinkingCallSite;
import pile.core.method.LinkableMethod;
import pile.nativebase.method.PileInvocationException;

public class ArrayGetMethod implements PileMethod {

    public ArrayGetMethod() {
    }

    @Override
    public boolean acceptsArity(int arity) {
        return arity == 2;
    }
    
    @Override
    public Optional<Class<?>> getReturnType(CallSiteType csType, MethodType staticTypes, long anyMask) {
        switch (csType) {
            case PLAIN:
                List<Class<?>> blendedMask = blendAnyMask(staticTypes, anyMask);
                var first = blendedMask.get(0);
                if (first.isArray()) {
                    return Optional.of(first.componentType());
                }
            default:
                return Optional.empty();
        }
    }
    
    @Override
    public Optional<CallSite> staticLink(CallSiteType csType, MethodType staticTypes, long anyMask) {
        if (csType == CallSiteType.PLAIN) {
            List<Class<?>> blendedMask = blendAnyMask(staticTypes, anyMask);
            var first = blendedMask.get(0);
            if (first.isArray()) {
                MethodHandle getter = arrayElementGetter(first);
                var cs = getter.asType(staticTypes);
                return Optional.of(new ConstantCallSite(cs));
            }   
        }
        return Optional.empty();
    }
    
    @Override
    public CallSite dynamicLink(CallSiteType csType, MethodType statictypes, long anyMask, CompilerFlags flags) {
        if (csType == CallSiteType.PLAIN) {
            return new AbstractRelinkingCallSite(statictypes) {
                
                @Override
                protected MethodHandle findHandle(Object[] args) throws Throwable {
                    if (args[0] == null) {
                        MethodHandle throwEx = getExceptionHandle(statictypes, NullPointerException.class, NullPointerException::new, "Array may not be null");
                        GuardBuilder builder = new JavaGuardBuilder(throwEx, getTarget(), statictypes);
                        builder.guardNull(0);
                        return builder.getHandle();
                    } else {
                        Class<? extends Object> base = args[0].getClass();
                        MethodHandle getter = arrayElementGetter(base);
                        GuardBuilder builder = new JavaGuardBuilder(getter, getTarget(), statictypes);
                        builder.guardNotNull(0);
                        builder.guardExact(0, base);
                        return builder.getHandle();
                    }
                }
            };
        } else {
            return PileMethod.super.dynamicLink(csType, statictypes, anyMask, flags);
        }
    }

    @Override
    public Object invoke(Object... args) throws Throwable {
        var first = args[0];
        Class<? extends Object> clazz = first.getClass();
        if (clazz.isArray()) {
            MethodHandle getter = arrayElementGetter(clazz);
            MethodHandle lenGetter = arrayLength(clazz);
            Number second = (Number) args[1];
            int idx = second.intValue();
            int len = (int) lenGetter.invoke(args[0]);
            if (idx >= len) {
                if (args.length == 3) {
                    return args[2];
                } else {
                    return null;
                }
            } else {
                return getter.invoke(first, idx);
            }
        } else {
            throw new PileInvocationException("aget: Expect array base class, found: " + clazz);
        }
    }

}
