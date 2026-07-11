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

import pile.nativebase.NativeCore;

public interface Conjable<T> {

    Conjable<T> conj(T t);

    /**
     * Create a new collection by repeatedly conj'ing elements from the source
     * (converted into a seq) into this collection. Implementing here allows for
     * specialization.
     * 
     * @param source The element to turn into a seq and conj into this
     * @return A new collection with all the elements from the source.
     */
    default Conjable<T> into(Object source) {
        ISeq seq = NativeCore.seq(source);
        Conjable base = this;

        while (seq != ISeq.EMPTY) {
            base = base.conj(seq.first());
            seq = seq.next();
        }

        return base;
    }

}
