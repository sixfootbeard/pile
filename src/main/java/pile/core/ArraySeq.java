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

import static pile.compiler.Helpers.*;

public class ArraySeq<T> extends AbstractSeq<T> implements ReversibleSeq<T> {

    private final T[] arr;
    private final int index;
    private final int start;
    private final int stop;
    private final boolean reverse;

    public ArraySeq(T[] arr, int index) {
        this(arr, index, 0, arr.length, false);
    }
    
    public ArraySeq(T[] arr, int index, int start, int stop, boolean reverse) {
        super();
        ensure(start <= index && index < arr.length, "Bad seq index");
        ensure(start < stop, "Bad range");
        ensure(stop <= arr.length, "Bad stop");
        
        this.arr = arr;
        this.index = index;
        this.start = start;
        this.stop = stop;
        this.reverse = reverse;
    }

    @Override
    public T first() {
        return arr[index];
    }

    @Override
    public ISeq<T> next() {
        var next = index + (reverse ? -1 : 1);
        if (next == (start - 1) || next == stop) {
            return null;
        }
        return new ArraySeq<T>(arr, next, start, stop, reverse);
    }
    
    @Override
    public ISeq reverse() {
        return new ArraySeq<>(arr, stop - 1, index, stop, ! reverse);
    }
    
}
