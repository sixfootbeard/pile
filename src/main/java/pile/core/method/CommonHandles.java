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

import static java.lang.invoke.MethodType.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import pile.compiler.math.NumberMethods;
import pile.core.ISeq;
import pile.core.exception.PileInternalException;
import pile.nativebase.NativeCore;

public class CommonHandles {

    static final MethodHandle SEQ_FROM_ARRAY, TO_SEQ, UNROLL_EXACT, COUNT, LT;

    static {
        try {
            Lookup lookup = MethodHandles.lookup();

            SEQ_FROM_ARRAY = lookup.findStatic(ISeq.class, "of", methodType(ISeq.class, Object[].class))
                    .asVarargsCollector(Object[].class);
                    
            UNROLL_EXACT = lookup.findStatic(ISeq.class, "unrollExact", methodType(Object[].class, int.class, ISeq.class));
            
            COUNT = lookup.findStatic(NativeCore.class, "count", methodType(int.class, Object.class));
            
            TO_SEQ = lookup.findStatic(NativeCore.class, "seq", methodType(ISeq.class, Object.class));
            
            LT = lookup.findStatic(NumberMethods.class, "lessThan", methodType(boolean.class, int.class, int.class));

        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new PileInternalException(e);
        }
    }
    

    private CommonHandles() {
    }

}
