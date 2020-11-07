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
package pile.compiler.typed;

import static pile.compiler.Helpers.*;

import java.lang.invoke.MethodType;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import pile.compiler.math.NumberHelpers;

class LookupHelpers {

    /**
     * Creates a predicate which tests each candidate argument type against the
     * closed over type stack. 
     * 
     * @param test
     * @param methodStack
     * @return
     */
    static BiPredicate<MethodType, Boolean> match(BiPredicate<Class<?>, Class<?>> test, List<Class<?>> methodStack) {
        final int stackSize = methodStack.size();
        
        return (candidate, varArgs) -> {
            int candidateSize = candidate.parameterCount();
            Class<?>[] candidateParameterTypes = candidate.parameterArray();
            if (varArgs) {
                int varDiff = stackSize - candidateSize;
                if (varDiff >= -1) {
                    int i = 0;
                    while (i < candidateSize - 1) {
                        Class<?> candidateClass = candidateParameterTypes[i];
                        Class<?> stackClass = methodStack.get(i);
                        if (!test.test(stackClass, candidateClass)) {
                            return false;
                        }
                        ++i;
                    }
                    Class<?> varArgArrayType = candidateParameterTypes[candidateParameterTypes.length - 1];
                    Class<?> arrayType = varArgArrayType.componentType();
                    ensure(arrayType != null, "Candidate method target did not have a trailing array element");
                    while (i < stackSize) {
                        var stackClass = methodStack.get(i);
                        if (!test.test(stackClass, arrayType)) {
                            return false;
                        }
                        ++i;
                    }
                    return true;
                }
                return false;
            } else {
                if (candidateSize != stackSize) {
                    return false;
                }
                for (int i = 0; i < stackSize; ++i) {
                    var staticType = methodStack.get(i);
                    var candidateType = candidateParameterTypes[i];
                    if (!test.test(staticType, candidateType)) {
                        return false;
                    }
                }
                return true;
            }
        };
    }
    
    static boolean matchSubtype(Class<?> dynamicType, Class<?> methodArgumentType) {
        if (methodArgumentType.isAssignableFrom(dynamicType)) {
            return true;
        }
        var wrapperDynamic = toWrapper(dynamicType);
        var wrapperMethodArg = toWrapper(methodArgumentType);
        if (wrapperMethodArg.isAssignableFrom(wrapperDynamic)) {
            return true;
        }
        
        return false;
    }
    static boolean matchNumeric(Class<?> dynamicType, Class<?> methodArgumentType) {
        if (NumberHelpers.isFixedWidthNumberType(dynamicType) && NumberHelpers.isFixedWidthNumberType(methodArgumentType)) {
            if (NumberHelpers.getJavaNumericComparator().compare(dynamicType, methodArgumentType) <= 0) {
                return true;
            }
        }
        return false;
    }

    static boolean matchDynamic(Class<?> dynamicType, Class<?> methodArgumentType) {
        if (Void.class.equals(dynamicType) && ! isPrimitive(methodArgumentType)) {
            return true;
        }
        return matchSubtype(dynamicType, methodArgumentType) || 
                matchNumeric(dynamicType, methodArgumentType);
    }

    static boolean matchStatic(Class<?> staticType, Class<?> methodArgumentType) {
        if (staticType.equals(Any.class)) {
            return true;
        }
        return matchSubtype(staticType, methodArgumentType);
        // TODO Numeric too?
    }

}
