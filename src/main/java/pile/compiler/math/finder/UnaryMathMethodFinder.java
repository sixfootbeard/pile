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

import static pile.compiler.Helpers.*;

import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.function.Predicate;

public interface UnaryMathMethodFinder {

    public Optional<MethodType> findTarget(Class<?> arg);
    
    public static final UnaryMathMethodFinder EXACT = (c) -> Optional.of(MethodType.methodType(c, c));
    public static final UnaryMathMethodFinder NUMBER = (c) -> Optional.of(MethodType.methodType(Number.class, c));
    
    
    
    public static UnaryMathMethodFinder only(UnaryMathMethodFinder delegate, Predicate<Class<?>> p) {
        return c -> {
            if (p.test(c)) {
                return delegate.findTarget(c);
            } else {
                return Optional.empty();
            }
        };
    }
    
    public static UnaryMathMethodFinder of(Class<Number> n, MethodType t) {
        return c -> {
            if (n.equals(c)) return Optional.of(t);
            else return Optional.empty();
        };
    }
    
    public static UnaryMathMethodFinder primitive(UnaryMathMethodFinder f) {
        return c -> f.findTarget(toPrimitive(c));
    }
    
    public static UnaryMathMethodFinder compose(UnaryMathMethodFinder... mf) {
        return c -> {
            for (UnaryMathMethodFinder sub : mf) {
                var m = sub.findTarget(c);
                if (m.isPresent()) {
                    return m;
                }
            }
            return Optional.empty();
        };
    }

}
