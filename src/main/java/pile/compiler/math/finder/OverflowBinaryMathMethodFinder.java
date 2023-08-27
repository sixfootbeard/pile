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
package pile.compiler.math.finder;

import static java.lang.invoke.MethodType.*;
import static pile.compiler.Helpers.*;

import java.lang.invoke.MethodType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

import pile.compiler.math.NumberHelpers;
import pile.util.ComparableUtils;

public class OverflowBinaryMathMethodFinder implements BinaryMethodFinder {

    private final Comparator<Class<?>> cmp;
    private final HashSet<Class<?>> all = new HashSet<>();
    private final Class<?> min;

    public OverflowBinaryMathMethodFinder(Class<?> min) {
        Map<Class<?>, Integer> orderMap = new HashMap<>();
        int i = 0;
        for (Class<?> c : NumberHelpers.ALL_ARBITRARY) {
            orderMap.put(c, i);
            all.add(c);
            ++i;
        }
        this.cmp = Comparator.comparingInt(orderMap::get);
        this.min = min;
    }

    @Override
    public Optional<MethodType> findTarget(Class<?> lhs, Class<?> rhs) {

        // lower to higher
        // Integral: byte, short, int, long, BigInteger
        // Float: float, double, BigDecimal
        // Big: BigInteger, BigDecimal

        // Some widening primitive conversions (5.1.2) won't ever incur a loss of
        // precision
        // integral to higher integral
        // byte/short/char to floating point
        // int to double
        // float to double

        // Floating point to Integral is always disallowed

        // Some minimum promotions eg. no add for (short, short);

        if (all.contains(lhs) && all.contains(rhs)) {
            final Class<?> target;
            if (NumberHelpers.isArbitraryPrecisionIntegralType(lhs)) {
                if (NumberHelpers.isArbitraryPrecisionIntegralType(rhs)) {
                    target = findIntegral(lhs, rhs);
                } else {
                    target = findIntegralDecimal(lhs, rhs);
                }
            } else {
                if (NumberHelpers.isArbitraryPrecisionIntegralType(rhs)) {
                    target = findIntegralDecimal(rhs, lhs);
                } else {
                    target = findDecimal(lhs, rhs);
                }
            }
            Class<?> targetType = ComparableUtils.max(target, min, cmp);
    
            MethodType methodType = methodType(getReturnType(targetType), targetType, targetType);
            return Optional.of(methodType);
        }
        return Optional.empty();
    }

    private Class<?> getReturnType(Class<?> targetType) {
        return Number.class;
    }

    private Class<?> findIntegralDecimal(Class integralType, Class floatType) {
        // Some widening primitive conversions (5.1.2) won't ever incur a loss of
        // precision
        // integral to higher integral
        // byte/short/char to floating point
        // int to double
        // float to double

        // byte, use floatType
        // short, use floatType
        // char?, use floatType
        // int, float? double else: floatType
        // long, use BigDecimal
        if (integralType.equals(Byte.TYPE) || integralType.equals(Byte.class) || integralType.equals(Short.TYPE)
                || integralType.equals(Short.class) || integralType.equals(Character.TYPE)
                || integralType.equals(Character.class)) {
            return floatType;
        } else if (integralType.equals(Integer.TYPE) || integralType.equals(Integer.class)) {
            if (floatType.equals(Float.TYPE) || floatType.equals(Float.class)) {
                return double.class;
            }
            // both double/BigDecimal return themselves
            return floatType;
        } else if (integralType.equals(Long.TYPE) || integralType.equals(Long.class) ||
                integralType.equals(BigInteger.class)) {
            return BigDecimal.class;
        } else {
            throw new IllegalArgumentException("Unexpected type pair:" + integralType + ", " + floatType);
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

}
