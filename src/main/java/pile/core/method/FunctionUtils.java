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
package pile.core.method;

import static pile.compiler.Helpers.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.function.Function;

import pile.core.PileMethod;
import pile.core.indy.CallSiteType;

public class FunctionUtils {

    private FunctionUtils() {}

    public static <I, O> PileMethod of(Class<I> input, Class<O> output, Function<I, O> fn) {
        return new PileMethod() {

            @Override
            public Optional<Class<?>> getReturnType() {
                return Optional.of(output);
            }

            @Override
            public Object invoke(Object... args) {
                ensure(args.length == 1, "Only need 1 arg");
                return fn.apply((I) args[0]);
            }

            @Override
            public boolean acceptsArity(int arity) {
                return arity == 1;
            }
        };
    }
    
    public static PileMethod ofJavaMethodHandle(MethodHandle h) {
    
        return new PileMethod() {
        
            @Override
            public Optional<Class<?>> getReturnType() {
                return Optional.of(h.type().returnType());
            }
            
            @Override
            public Optional<Class<?>> getReturnType(CallSiteType csType, MethodType staticTypes, long anyMask) {
                return getReturnType();
            }
            
            @Override
            public Optional<CallSite> staticLink(CallSiteType csType, MethodType staticTypes, long anyMask) {
                if (csType == CallSiteType.PLAIN) {
                    return Optional.of(h.asType(staticTypes)).map(ConstantCallSite::new);
                }
                return Optional.empty();
            }
            
            @Override
            public Object invoke(Object... args) throws Throwable {
                return h.invokeWithArguments(args);
            }
            
            @Override
            public boolean acceptsArity(int arity) {
                int count = h.type().parameterCount();
                if (h.isVarargsCollector()) {
                    return arity >= count;
                } else {
                    return arity == count;
                }
            }
        };
    }

}
