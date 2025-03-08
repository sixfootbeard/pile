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

import static pile.compiler.Helpers.*;
import static pile.core.indy.InteropInstanceMethodCallSite.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import pile.compiler.typed.DynamicTypeLookup;
import pile.compiler.typed.TypedHelpers;
import pile.core.exception.UnlinkableMethodException;
import pile.core.indy.InteropInstanceMethodCallSite.CrackResult;
import pile.core.indy.InteropInstanceMethodCallSite.CrackError;
import pile.core.indy.InteropInstanceMethodCallSite.CrackResult;
import pile.core.indy.guard.GuardBuilder;
import pile.core.indy.guard.JavaGuardBuilder;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.method.AbstractRelinkingCallSite;

/**
 * 
 * @author john
 * @implNote Threading Concerns: None, currently. This callsite infinite chains
 *           guard tests, which is obviously dumb but has no threading concerns.
 */
public class InteropStaticMethodCallSite extends AbstractRelinkingCallSite {

    private static Logger LOG = LoggerSupplier.getLogger(InteropStaticMethodCallSite.class);

    private final String methodName;
    private final Class<?> clazz;
    private final long anyMask;

    private final Lookup caller;

    public InteropStaticMethodCallSite(Lookup caller, MethodType type, Class<?> clazz, String methodName, long anyMask) {
        super(type); // argument type +1?
        this.caller = caller;
        this.methodName = methodName;
        this.clazz = clazz;
        this.anyMask = anyMask;
    }

    @Override
    protected MethodHandle findHandle(Object[] args) throws Throwable {

        MethodType type = type();
        List<Class<?>> staticTypes = blendAnyMask(type(), anyMask);

        List<Class<?>> runtimeTypes = getArgClasses(args);

        boolean[] contentionIndexes = new boolean[runtimeTypes.size()];
        DynamicTypeLookup<Method> dyn = new DynamicTypeLookup<Method>(TypedHelpers::ofMethod);
        Optional<Method> matchedMethod = dyn.findMatchingTarget(staticTypes, runtimeTypes,
                i -> contentionIndexes[i] = true, TypedHelpers.findStaticMethods(clazz, methodName));

        Method method = matchedMethod.orElseThrow(() -> new UnlinkableMethodException(
                "Could not find method " + clazz + "." + methodName + runtimeTypes));
        CrackResult<MethodHandle> result = crackReflectedMethod(caller, clazz, methodName, method);
        MethodHandle handle = switch (result) {
            case ResultValue(MethodHandle t) -> t;
            case CrackError(String msg) -> throw new UnlinkableMethodException(msg);
        };

        // TODO Infinite chain relinks
        GuardBuilder builder = new JavaGuardBuilder(handle, getTarget(), type);
        for (int i = 0; i < runtimeTypes.size(); ++i) {
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
