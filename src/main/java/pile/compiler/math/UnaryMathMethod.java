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

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;

import pile.core.PileMethod;
import pile.core.exception.PileExecutionException;
import pile.core.indy.CallSiteType;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;

public class UnaryMathMethod implements PileMethod {

    private static final Logger LOG = LoggerSupplier.getLogger(UnaryMathMethod.class);

    private final NavigableSet<Class<?>> order;
    private final Class<?> methodClass;
    private final String methodName;

    public UnaryMathMethod(Class<?> methodClass, String methodName, NavigableSet<Class<?>> order) {
        super();
        this.order = order;
        this.methodClass = methodClass;
        this.methodName = methodName;
    }
    
    @Override
    public boolean acceptsArity(int arity) {
        return arity == 1;
    }

    @Override
    public Optional<CallSite> staticLink(CallSiteType csType, MethodType staticTypes, long anyMask) {
        Class<?> firstParameterType = staticTypes.parameterType(0);
        return getHandle(firstParameterType)
            .map(handle -> handle.asType(staticTypes))
            .map(ConstantCallSite::new);
    }
    
    private Optional<MethodHandle> getHandle(Class<?> firstParameterType) {
        if (NumberHelpers.isNumberType(firstParameterType)) {
            Class<?> targetMethodType = order.ceiling(toPrimitive(firstParameterType));
            MethodType type = methodType(targetMethodType, targetMethodType);
            MethodHandle handle;
            try {
                handle = lookup().findStatic(methodClass, methodName, type);
                return Optional.of(handle);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                LOG.warnEx("Could not lookup static method in %s.%s(%s)", e, methodClass, methodName, type);
                // fall through
            }
        }
        return Optional.empty();
    }

    @Override
    public Object invoke(Object... args) throws Throwable {
        ensure(args.length == 1, "Unary math method called with too many arguments");
        Optional<MethodHandle> maybe = getHandle(args[0].getClass());
        ensureEx(maybe.isPresent(), IllegalArgumentException::new, "Cannot find method types matching call");

        MethodHandle handle = maybe.get();
        return handle.invoke(args[0]);
    }

}
