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
package pile.core;

import static pile.nativebase.NativeCore.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public interface PCall {

    Object invoke(Object... args) throws Throwable;
    
    /**
     * Invoke this function with the provided arguments, with an implied
     * sequence-ish as the final argument (or null).
     * 
     * @param args The args with the final argument being a sequence-ish
     * @return The value of the called function.
     * @implNote The default implementation will realize all arguments in the
     *           sequence and call {@link #invoke(Object...)}. Care should be taken
     *           to not call this with a lazy infinite sequence.
     * @throws Throwable
     */
    default Object applyInvoke(Object... args) throws Throwable {
        List list = Arrays.asList(args);
        ISeq last = seq(list.get(list.size() - 1)); 
        list = new ArrayList<>(list.subList(0, list.size() - 1));
        ISeq.iter(last).forEach(list::add);
        return invoke(list.toArray());
    }
    
}
