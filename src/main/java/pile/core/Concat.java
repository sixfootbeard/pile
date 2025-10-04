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

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 
 * @author john
 *
 * @param <T>
 */
public class Concat<T> extends AbstractSeq<T> {

    private final ISeq<T> pre;
    private final Supplier<ISeq<T>> postGen;

    public Concat(ISeq<T> pre, Supplier<ISeq<T>> post) {        
        this.pre = Objects.requireNonNull(pre, "pre shouldn't be null");;
        this.postGen = Objects.requireNonNull(post, "post may not be null");
    }

    @Override
    public T first() {
        return pre.first();
    }

    @Override
    public ISeq<T> next() {
        ISeq<T> maybeNext = pre.next();
        if (maybeNext == null) {
            return postGen.get();
        } else {
            return new Concat<>(maybeNext, postGen);
        }
    }

}