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

import static java.lang.invoke.MethodType.*;
import static pile.compiler.Helpers.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Optional;

import pile.compiler.math.finder.UnaryMathMethodFinder;
import pile.core.PileMethod;
import pile.core.indy.CallSiteType;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.runtime.generated_classes.LookupHolder;

public class UnaryMathMethod implements PileMethod {

    private static final Logger LOG = LoggerSupplier.getLogger(UnaryMathMethod.class);

    private final UnaryMathMethodFinder finder;
    private final Class<?> methodClass;
    private final String methodName;

    public UnaryMathMethod(Class<?> methodClass, String methodName, UnaryMathMethodFinder order) {
        super();
        this.finder = order;
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
    
    @Override
    public Optional<Class<?>> getReturnType(CallSiteType csType, MethodType staticTypes, long anyMask) {
        if (staticTypes.parameterCount() != 1) {
            return Optional.empty();
        }
        return switch (csType) {
            case CallSiteType.PLAIN -> getHandle(staticTypes.parameterType(0)).map(mh -> mh.type().returnType());
            default -> Optional.empty();
        };
    }
    
    private Optional<MethodHandle> getHandle(Class<?> firstParameterType) {
        if (NumberHelpers.isNumberType(firstParameterType)) {
            return finder.findTarget(firstParameterType)
                .flatMap(type -> {
                    try {
                        MethodHandle handle = LookupHolder.PRIVATE_LOOKUP.findStatic(methodClass, methodName, type);
                        NumericPromoter promoter = new NumericPromoter();
                        MethodHandle promoted = promoter.promote(handle, type);
                        return Optional.of(promoted);
                    } catch (NoSuchMethodException | IllegalAccessException e) {
                        LOG.warnEx("Could not lookup static method in %s.%s(%s)", e, methodClass, methodName, type);
                        return Optional.empty();
                    }
                    
                });                
            
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
