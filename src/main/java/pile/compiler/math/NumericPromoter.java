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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;

/**
 * Creates a method handle matching a specified callsite type which promotes
 * arguments eventually calling a target handle. eg.
 * 
 * <pre>
 * target: (long, long)
 * callsiteType: (long, short)
 * result: A method handle of type (long, short) which promotes the short-to-long and then calls the target method.
 * </pre>
 * 
 * This class is similar to {@link MethodHandle#asType(MethodType)} and in fact
 * uses it in certain cases but additionally can handle BigInteger, BigDecimal
 * along with all primitive/wrapper number types (byte, short, int, long, float,
 * double).<br>
 * <br>
 * Groups (lowest to highest)
 * <ol>
 * <li>Java Types: byte, short, int, long, float, double
 * <li>Integral Types: byte, short, int, long
 * <li>Float Types: float, double
 * </ol>
 * <br>
 * Promotions
 * <ol>
 * <li>Java Type -to- (higher) Java Type
 * <li>Integral Types -to- BigInteger
 * <li>Float Types -to- BigDecimal
 * <li>BigInteger -to- BigDecimal
 * <li>Object -to- Any (directly casts as type)
 * </ol>
 * 
 *
 */
public class NumericPromoter {

    private static final MethodHandle LONG_TO_BIG_INTEGER, LONG_TO_BIGDECIMAL, DOUBLE_TO_BIGDECIMAL, BIGINTEGER_TO_BIGDECIMAL;

    static {
        try {
            LONG_TO_BIG_INTEGER = lookup().findStatic(BigInteger.class, "valueOf",
                    methodType(BigInteger.class, Long.TYPE));
            LONG_TO_BIGDECIMAL = lookup().findStatic(BigDecimal.class, "valueOf",
                    methodType(BigDecimal.class, Long.TYPE));
            DOUBLE_TO_BIGDECIMAL = lookup().findStatic(BigDecimal.class, "valueOf",
                    methodType(BigDecimal.class, Double.TYPE));
            BIGINTEGER_TO_BIGDECIMAL = lookup().findConstructor(BigDecimal.class, methodType(void.class, BigInteger.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException("Shouldn't happen");
        }
    }

    /**
     * 
     * @param target The target method to call.
     * @param csType The callsite type which the resulting method handle will match.
     * @return A method handle of the specified type which contains the appropriate
     *         promotions to call the target method.
     */
    public MethodHandle promote(MethodHandle target, MethodType csType) {
        // target (+ long long)
        // csType (+ long short)
        MethodType targetType = target.type();
        var out = target;
        for (int i = 0; i < targetType.parameterCount(); ++i) {
            out = promoteSingle(out, csType, i);
        }
        return out;
    }

    public MethodHandle promoteSingle(MethodHandle out, MethodType csType, int i) {
        MethodType targetType = out.type();
        var targetParamClass = targetType.parameterType(i);
        Class<?> callSiteParamClass = csType.parameterType(i);

        if (targetParamClass.equals(callSiteParamClass)) {
            return out;
        } else if ((Object.class.equals(callSiteParamClass) || NumberHelpers.isFixedWidthNumberType(callSiteParamClass))&& 
                    NumberHelpers.isFixedWidthNumberType(targetParamClass)) {
            var loopParamType = out.type().changeParameterType(i, callSiteParamClass);
            return out.asType(loopParamType);
        }
        
        if (targetParamClass.equals(BigInteger.class)) {
            if (NumberHelpers.isFixedWidthIntegralType(callSiteParamClass)) {
                // Integral -> BigInteger
                out = filterArguments(out, i, LONG_TO_BIG_INTEGER);
                // (..., long, ...)

                var loopParamType = out.type().changeParameterType(i, callSiteParamClass);
                out = out.asType(loopParamType);
            } else {
                throw new IllegalArgumentException("Cannot promote a " + callSiteParamClass + " to a BigInteger");
            }
        } else if (targetParamClass.equals(BigDecimal.class)) {
            if (NumberHelpers.isFixedWidthIntegralType(callSiteParamClass)) {
                out = filterArguments(out, i, LONG_TO_BIGDECIMAL);
                // (..., long, ...)

                var loopParamType = out.type().changeParameterType(i, callSiteParamClass);
                out = out.asType(loopParamType);
            } else if (NumberHelpers.isFixedWidthFloatType(callSiteParamClass)) {
                out = filterArguments(out, i, DOUBLE_TO_BIGDECIMAL);
                // (..., double, ...)

                var loopParamType = out.type().changeParameterType(i, callSiteParamClass);
                out = out.asType(loopParamType);
            } else if (BigInteger.class.equals(callSiteParamClass)) {
                out = filterArguments(out, i, BIGINTEGER_TO_BIGDECIMAL);
            } else {
                throw new IllegalArgumentException("Cannot promote a " + callSiteParamClass + " to a BigDecimal");
            }
        } else {
            throw new IllegalArgumentException(
                    "Unknown promotion: " + callSiteParamClass + " -> " + targetParamClass);
        }
        return out;
    }

    public static boolean isNumber(Class<?> clazz) {
        return NumberHelpers.isNumberType(clazz) || BigInteger.class.equals(clazz) || BigDecimal.class.equals(clazz);
    }

}
