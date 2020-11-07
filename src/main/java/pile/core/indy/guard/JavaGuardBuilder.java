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
package pile.core.indy.guard;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;

import pile.core.ProtocolMethod;

/**
 * Specialized guard builder which can elide certain guards when we know we're
 * calling a java method. Compare with {@link ProtocolGuardBuilder}.
 * 
 */
public class JavaGuardBuilder extends GuardBuilder {

    /**
     * 
     * @param base        The target method to call
     * @param relink      The relinking method to call, on failure.
     * @param staticTypes The static types of the callsite.
     */

    public JavaGuardBuilder(MethodHandle base, MethodHandle relink, MethodType staticTypes) {
        super(base, relink, staticTypes);
    }

    @Override
    public void guardExact(int argIndex, Class<?> type) {
        if (type.equals(staticType(argIndex)) && isFinalType(type)) {
            // If the static type is the same as the guard type and the type is final then
            // this guard is moot.
            return;
        }
        super.guardExact(argIndex, type);
    }

    @Override
    public void guardSubtype(int argIndex, Class<?> type) {
        if (staticType(argIndex).isAssignableFrom(type)) {
            // If the static type is already a subtype of the guard type then any actual
            // type is also going to be a subtype, thus this guard is moot.
            return;
        }
        super.guardSubtype(argIndex, type);
    }

    private boolean isFinalType(Class<?> type) {
        return (type.getModifiers() & Modifier.FINAL) > 0;
    }


}
