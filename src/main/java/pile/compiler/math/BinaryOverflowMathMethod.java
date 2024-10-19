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
import static pile.util.CollectionUtils.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.function.Predicate;

import pile.compiler.Helpers;
import pile.compiler.typed.Any;
import pile.core.PileMethod;
import pile.core.exception.PileException;
import pile.core.indy.CallSiteType;
import pile.core.indy.CompilerFlags;
import pile.core.indy.guard.GuardBuilder;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.method.AbstractRelinkingCallSite;
import pile.core.runtime.generated_classes.LookupHolder;
import pile.util.ComparableUtils;

public class BinaryOverflowMathMethod implements PileMethod {

    private static final Logger LOG = LoggerSupplier.getLogger(BinaryMathMethod.class);

    private final Class<?> methodClass;
    private final String methodName;
    private final NavigableSet<Class<?>> order;
    private final Predicate<Class<?>> argTest;
    

    public BinaryOverflowMathMethod(Class<?> methodClass, String methodName, NavigableSet<Class<?>> order, Predicate<Class<?>> argTest) {
        super();
        this.order = order;
        this.methodClass = methodClass;
        this.methodName = methodName;
        this.argTest = argTest;
    }
    
    @Override
    public boolean acceptsArity(int arity) {
        return arity == 2;
    }
    
    @Override
    public Optional<CallSite> staticLink(CallSiteType csType, MethodType raw, long anyMask) {
        MethodType staticTypes = blendAnyMaskType(raw, anyMask);
        Class<?> lhs = staticTypes.parameterType(0);
        Class<?> rhs = staticTypes.parameterType(1);
        if (Any.class.equals(lhs) || Any.class.equals(rhs)) {
            return Optional.empty();
        }
        
        return getHandle(lhs, rhs)
            .map(ConstantCallSite::new);
    }
    
    private Optional<MethodHandle> getHandle(Class lhs, Class rhs) {

        // lower to higher
        // Integral: byte, short, int, long, BigInteger
        // Float: float, double, BigDecimal
        // Big: BigInteger, BigDecimal 
        
        // Some widening primitive conversions (5.1.2) won't ever incur a loss of precision
        // integral to higher integral
        // byte/short/char to floating point
        // int to double
        // float to double
        
        // Floating point to Integral is always disallowed
        
        // Some minimum promotions eg. no add for (short, short);
        
        final Class<?> target;
        if (NumberHelpers.isArbitraryPrecisionIntegralType(lhs)) {
            if (NumberHelpers.isArbitraryPrecisionIntegralType(rhs)) {
                target = findIntegral(lhs, rhs);
            }
            else {
                target = findIntegralDecimal(lhs, rhs);
            }
        } else {
            if (NumberHelpers.isArbitraryPrecisionIntegralType(rhs)) {
                target = findIntegralDecimal(rhs, lhs);
            }
            else {
                target = findDecimal(lhs, rhs);
            }
        }
        Class<?> targetType = order.ceiling(target);

        MethodType methodType = methodType(getReturnType(targetType), targetType, targetType);
        try {
            MethodHandle foundHandle = LookupHolder.PRIVATE_LOOKUP.findStatic(methodClass, methodName, methodType);
            MethodType synthetic = methodType(foundHandle.type().returnType(), lhs, rhs);
            NumericPromoter promoter = new NumericPromoter();
            MethodHandle promoted = promoter.promote(foundHandle, synthetic);
            return Optional.of(promoted);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            LOG.warnEx("Could not lookup static method in %s.%s(%s)", e, methodClass, methodName, methodType);
            // fall through
        }
        return Optional.empty();
       
    }
    
    private Class<?> findIntegralDecimal(Class integralType, Class floatType) {
        // Some widening primitive conversions (5.1.2) won't ever incur a loss of precision
        // integral to higher integral
        // byte/short/char to floating point
        // int to double
        // float to double
        
        // byte, use floatType
        // short, use floatType
        // char?, use floatType
        // int, float? double else: floatType
        // long, use BigDecimal
        if (integralType.equals(Byte.TYPE) || integralType.equals(Byte.class) ||
                integralType.equals(Short.TYPE) || integralType.equals(Short.class) ||
                integralType.equals(Character.TYPE) || integralType.equals(Character.class)) {
            return floatType;
        } else if (integralType.equals(Integer.TYPE) || integralType.equals(Integer.class)) {
            if (floatType.equals(Float.TYPE) || floatType.equals(Float.class)) {
                return double.class;
            }
            // both double/BigDecimal return themselves
            return floatType;
        } else if (integralType.equals(Long.TYPE)|| integralType.equals(Long.class)) {
            return BigDecimal.class;
        } else {
            throw new IllegalArgumentException("Unexpected type");
        }        
    }

    private Class<?> findDecimal(Class lhs, Class rhs) {
        Class<?> target = ComparableUtils.max(toPrimitive(lhs), toPrimitive(rhs),
                NumberHelpers.getArbitraryPrecisionFloatComparator());
        return target;
    }

    private Class<?> findIntegral(Class lhs, Class rhs) {
        Class<?> target = ComparableUtils.max(toPrimitive(lhs), toPrimitive(rhs),
                NumberHelpers.getArbitraryPrecisionIntegralComparator());
        return target;
    }

    /**
     * @implSpec
     */
    @Override
    public CallSite dynamicLink(CallSiteType csType, MethodType statictypes, long anyMask, CompilerFlags flags) {

        /**
         * @implSpec Threading Concerns: None, currently. This just relinks every time,
         *           which is bad but we have no threading concerns.
         */
        return new AbstractRelinkingCallSite(statictypes) {

            @Override
            protected MethodHandle findHandle(Object[] args) throws Throwable {
                final var lhs = args[0].getClass();
                final var rhs = args[1].getClass();
                MethodHandle target = getHandle(args[0].getClass(), args[1].getClass())
                                        .orElseThrow(() -> {
                                            String msg  = "Cannot link method '" + methodName + "(" + lhs + ", " + rhs + ")'";
                                            return new PileException(msg);
                                        });

//                NumericPromoter promoter = new NumericPromoter();
//                MethodHandle promoted = promoter.promote(target, statictypes);
                GuardBuilder guard = new GuardBuilder(target, relink, statictypes);
                var lhsStaticType = statictypes.parameterType(0);
                if (! lhsStaticType.isPrimitive()) {
                    guard.guardExact(0, lhs);
                }
                var rhsStaticType = statictypes.parameterType(1);
                if (! rhsStaticType.isPrimitive()) {
                    guard.guardExact(1, rhs);
                }
                
                LOG.trace("(Re)linked to math method %s%s", methodName, target.type());
                return guard.getHandle();
            }
        };
    }

    protected Class<?> getReturnType(Class<?> targetType) {
        return Number.class;
    }

    @Override
    public Object invoke(Object... args) throws Throwable {
        ensure(args.length == 2, "Unary math method called with too many arguments");
        Optional<MethodHandle> maybe = getHandle(args[0].getClass(), args[1].getClass());
        ensureEx(maybe.isPresent(), IllegalArgumentException::new, "Cannot find method types matching call");

        MethodHandle handle = maybe.get();

        return handle.invokeWithArguments(args);
    }

}
