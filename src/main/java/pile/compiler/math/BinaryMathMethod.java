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
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import pile.compiler.Helpers;
import pile.compiler.math.finder.BinaryMethodFinder;
import pile.compiler.math.finder.JavaBinaryMathMethodFinder;
import pile.core.PileMethod;
import pile.core.exception.PileException;
import pile.core.exception.PileExecutionException;
import pile.core.indy.CallSiteType;
import pile.core.indy.CompilerFlags;
import pile.core.indy.guard.GuardBuilder;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.method.AbstractRelinkingCallSite;
import pile.core.method.LinkableMethod;
import pile.core.runtime.generated_classes.LookupHolder;
import pile.util.ComparableUtils;
import pile.util.Pair;

public class BinaryMathMethod implements PileMethod {

    private static final Logger LOG = LoggerSupplier.getLogger(BinaryMathMethod.class);


    private final Class<?> methodClass;
    private final String methodName;
    private final BinaryMethodFinder finder;

    
    public BinaryMathMethod(String methodName) {
        this(NumberMethods.class, methodName, new JavaBinaryMathMethodFinder(NumberHelpers.ALL_FIXED, Integer.TYPE));
    }

    public BinaryMathMethod(Class<?> methodClass, String methodName, BinaryMethodFinder finder) {
        super();
        this.methodClass = methodClass;
        this.methodName = methodName;
        this.finder = finder;
    }
    
    @Override
    public boolean acceptsArity(int arity) {
        return arity == 2;
    }
    
    @Override
    public Optional<CallSite> staticLink(CallSiteType csType, MethodType staticTypes, long anyMask) {
        return switch (csType) {
            case PLAIN -> {
                Class<?> lhs = staticTypes.parameterType(0);
                Class<?> rhs = staticTypes.parameterType(1);
                yield getHandle(lhs, rhs)
                    .map(h -> h.asType(h.type().changeReturnType(staticTypes.returnType())))
                    .map(ConstantCallSite::new);
            } 
            case PILE_VARARGS -> Optional.empty();
        };        
    }
    
    @Override
    public Optional<Class<?>> getReturnType(CallSiteType csType, MethodType staticTypes, long anyMask) {
        switch (csType) {
            case PLAIN:
                List<Class<?>> blendedMask = blendAnyMask(staticTypes, anyMask);
                Optional<MethodType> maybeTarget = finder.findTarget(blendedMask.get(0), blendedMask.get(1));
                return maybeTarget.map(MethodType::returnType);
            default:
                return Optional.empty();
        }
    }
    
    /**
     * @implSpec
     */
    @Override
    public CallSite dynamicLink(CallSiteType csType, MethodType statictypes, long anyMask, CompilerFlags flags) {

        return switch (csType) {
            case PLAIN -> {
                /**
                 * @implSpec Threading Concerns: None, currently. This just relinks every time,
                 *           which is bad but we have no threading concerns.
                 */
                yield new AbstractRelinkingCallSite(statictypes) {

                    @Override
                    protected MethodHandle findHandle(Object[] args) throws Throwable {
                        final var lhs = args[0].getClass();
                        final var rhs = args[1].getClass();
                        MethodHandle target = getHandle(args[0].getClass(), args[1].getClass()).orElseThrow(() -> {
                            String msg = "Cannot link method '" + methodName + "(" + lhs + ", " + rhs + ")'";
                            return new PileException(msg);
                        });

                        GuardBuilder guard = new GuardBuilder(target, relink, statictypes);
                        var lhsStaticType = statictypes.parameterType(0);
                        if (!lhsStaticType.isPrimitive()) {
                            guard.guardExact(0, lhs);
                        }
                        var rhsStaticType = statictypes.parameterType(1);
                        if (!rhsStaticType.isPrimitive()) {
                            guard.guardExact(1, rhs);
                        }

                        LOG.trace("(Re)linked to math method %s%s", methodName, target.type());
                        return guard.getHandle();
                    }
                };
            }
            case PILE_VARARGS -> {
                var mh = LinkableMethod.invokeLink(csType, this).asType(statictypes);
                yield new ConstantCallSite(mh);
            }
        };

    }

    @Override
    public Object invoke(Object... args) throws Throwable {
        ensure(args.length == 2, "Unary math method called with too many arguments");
        Optional<MethodHandle> maybe = getHandle(args[0].getClass(), args[1].getClass());
        ensureEx(maybe.isPresent(), IllegalArgumentException::new, 
                () -> String.format("Cannot find method types matching call %s(%s, %s)", methodName, args[0].getClass(), args[1].getClass()));

        MethodHandle handle = maybe.get();
        return handle.invokeWithArguments(args);
    }

    private Optional<MethodHandle> getHandle(Class<?> lhs, Class<?> rhs) {
        return finder.findTarget(lhs, rhs)
                .flatMap(methodType -> {
                    try {
                        MethodHandle foundHandle = LookupHolder.PRIVATE_LOOKUP.findStatic(methodClass, methodName, methodType);
                        MethodType synthetic = methodType(foundHandle.type().returnType(), lhs, rhs);
                        NumericPromoter promoter = new NumericPromoter();
                        MethodHandle promoted = promoter.promote(foundHandle, synthetic);
                        return Optional.of(promoted);
                    } catch (NoSuchMethodException | IllegalAccessException e) {
                        LOG.warnEx("Could not lookup static method in %s.%s(%s)", e, methodClass, methodName, methodType);
                        return Optional.empty();                
                    }
                });        
    }

}
