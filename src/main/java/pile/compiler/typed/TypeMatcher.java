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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;

import pile.core.exception.PileCompileException;
import pile.core.exception.PileInternalException;

/**
 * 
 * Matches candidate types against a target method type. Also, given multiple
 * candidates determines the best candidate to use. <br>
 * <br>
 * 
 * Single use lifecycle
 * <ol>
 * <li>{@link #isCandidate(MethodType)} - Any number of times
 * <li>{@link #reduce(MethodHandle, MethodHandle)} - Optional
 * <li>{@link #didReduce()} - Optional
 * </ol>
 *
 */
public class TypeMatcher {

	private final MethodType target;
	private boolean didReduce = false;

	/**
	 * 
	 * @param target The callsite
	 */
	public TypeMatcher(MethodType target) {
		super();
		this.target = target;
	}

	/**
	 * 
	 * @param candidate
	 * @return True if the candidate method type is callable with the target arg
	 *         types.
	 **/
	public boolean isCandidate(MethodType candidate) {
		BiPredicate<Class<?>, Class<?>> matchType = (l, r) -> matchClass(r, l);
		return matchSizeAndArgs(candidate,  matchType)
				&& matchReturnType(candidate, matchType);
	}
	
    public boolean isCandidate(MethodType method, boolean isVariadic) {
        if (isVariadic) {
            // TODO more bounds checking for cases
            int argParamCount = target.parameterCount();
            if (argParamCount > 0) {
                // Pre-variadic part
                for (int i = 0; i < method.parameterCount() - 1; ++i) {
                    if (! matchClass(method.parameterType(i), target.parameterType(i))) {
                        return false;
                    }
                }
            }
            Class<?> arrayClazz = method.lastParameterType();
            Class<?> arrayComponentType = arrayClazz.componentType();
            if (argParamCount > 1) {
                for (int i = argParamCount - 1; i < target.parameterCount(); ++i) {
                    if (!matchClass(arrayComponentType, target.parameterType(i))) {
                        return false;
                    }
                }
            }
            return true;            
        } else {
            BiPredicate<Class<?>, Class<?>> matchType = (l, r) -> matchClass(r, l);
            return matchSizeAndArgs(method, matchType) && matchReturnType(method, matchType);
        }
    }
	
	public boolean isCallableWithArgs(MethodType candidate) {
		if (target.parameterCount() != candidate.parameterCount()) {
			return false;
		}
		
//		if (! matchClass(target.returnType(), candidate.returnType())) {
//		    return false;
//		}

		int size = target.parameterCount();
		for (int i = 0; i < size; ++i) {

			Class<?> candidateType = candidate.parameterType(i);
			Class<?> targetType = target.parameterType(i);
			
			if (! (Void.class.equals(candidateType)) && 
					! matchClass(targetType, candidateType)) {
				return false;
			}
		}		
		
		return true;
	}
	
	public boolean matchSizeAndArgs(MethodType candidate, BiPredicate<Class<?>, Class<?>> matchType) {
		if (target.parameterCount() != candidate.parameterCount()) {
			return false;
		}

		int size = target.parameterCount();
		for (int i = 0; i < size; ++i) {

			Class<?> candidateType = candidate.parameterType(i);
			Class<?> targetType = target.parameterType(i);
			
			
			if (targetType == Void.class) {
				continue;
			}

			if (! matchType.test(targetType, candidateType)) {
				return false;
			}
		}		
		
		return true;
	}

	
	
	// TODO Implement both sub/supertype variants instead of just isCandidate.

	public boolean didReduce() {
		return didReduce;
	}

	/**
	 * Compares two method handles and determines which handle is the most specific
	 * for use. If there is no relationship between the types in the provided
	 * handles one will be chosen by an undefined method. All methods provided to
	 * this reduce must be candidates, although that method does not need invoked if
	 * this can be determined by other means.
	 * 
	 * @param lhs
	 * @param rhs
	 * @return
	 */
	public MethodHandle reduce(MethodHandle lhs, MethodHandle rhs) {
		return reduce(lhs, rhs, MethodHandle::type);
	}
	
	public <T> T reduce(T glhs, T grhs, Function<T, MethodType> fn) {
		didReduce = true;
		int i = 0;
		int size = target.parameterCount();
		while (i < size) {

			Class<?> lhsType = fn.apply(glhs).parameterType(i);
			Class<?> rhsType = fn.apply(grhs).parameterType(i);

			if (lhsType.equals(rhsType)) {
				++i;
				continue;
			}
			
			return TypedHelpers.chooseNarrowLeft(lhsType, rhsType) ? glhs : grhs;
		}

		throw new PileInternalException("Comparing two candidate methods with the same type.");
	}
	
	private boolean matchReturnType(MethodType candidate, BiPredicate<Class<?>, Class<?>> matchType) {
    	return matchType.test(candidate.returnType(), target.returnType());
    }

    // TODO Implement both sub/supertype variants instead of just isCandidate.
    
    private static boolean matchClass(Class<?> superType, Class<?> subType) {
    	if (! superType.isAssignableFrom(subType)) {
    		superType = toWrapper(superType);
    		subType = toWrapper(subType);
    		
    		if (! superType.isAssignableFrom(subType)) {
    			return false;
    		}
    	}
    	return true;
    }

}
