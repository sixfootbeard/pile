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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import pile.core.ISeq;
import pile.core.indy.HandleType;

public record HandleLookup(HandleType htype, int args, MethodHandle handle) {

    private MethodHandle adaptNormalMethod(int expectedSize) {
        if (htype() == HandleType.NORMAL) {
            return handle;
        } else if (htype() == HandleType.VARARGS) {
            MethodType handleType = handle.type();

            int handleSize = handleType.parameterCount();
            // (def f (fn [a & b] ... ))

            if (expectedSize + 1 == handleSize) {
                // (f a)

                // Handle type includes the trailing Iseq but our caller didn't use it, so we
                // have to insert an empty to match.
                return MethodHandles.insertArguments(handle, expectedSize, ISeq.EMPTY);
            } else {
                // (f a b ...)

                // (Object, ISeq)
                MethodHandle adaptedSeqFromArray = MethodHandles.filterArguments(handle, handleSize - 1,
                        CommonHandles.SEQ_FROM_ARRAY);
                // (Object, Object[])
                return adaptedSeqFromArray.asCollector(handleSize - 1, Object[].class, expectedSize - handleSize + 1);
                // (Object, Object...)
            }

        }
        throw error("Unsupported handle type:" + htype());
    }
    
}