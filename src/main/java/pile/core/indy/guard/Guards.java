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

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicInteger;

import pile.core.exception.PileInternalException;

/**
 * , Guards!!
 *
 */
public class Guards {

    private static MethodHandle OVER_MAX;

    private Guards() {}

    static {
        try {
            OVER_MAX = lookup().findStatic(Guards.class, "overMax",
                    methodType(boolean.class, AtomicInteger.class, int.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new PileInternalException("Shouldn't happen");
        }
    }

    /**
     * Guards calling the target only at most n times, after which fallback will be
     * called instead. If n==0 then fallback is unconditionally called. Thread-safe.
     * 
     * @param n        The maximum number of times to call the target.
     * @param target   The target to call up to n times.
     * @param fallback The fallback that will be called afterwards.
     * @return A {@link MethodHandle} which implements the above behavior.
     */
    public static MethodHandle maxCall(int n, MethodHandle target, MethodHandle fallback) {
        if (n < 0) {
            throw new IllegalArgumentException("Guard count must be positive");
        }
        if (n == 0) {
            return fallback;
        }
        AtomicInteger count = new AtomicInteger(0);
        MethodHandle pred = insertArguments(OVER_MAX, 0, count, n);
        var typedPred = dropArguments(pred, 0, target.type().parameterList());
        return guardWithTest(typedPred, target, fallback);
    }

    @SuppressWarnings("unused")
    private static boolean overMax(AtomicInteger count, int max) {
        return count.incrementAndGet() < max;
    }

}
