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

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

public class IteratorSeq<T> implements Iterator<T> {

    private ISeq<T> seq;

    public IteratorSeq(ISeq<T> seq) {
        this.seq = seq;
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        T t = seq.first();
        seq = seq.next();
        return t;

    }

    @Override
    public boolean hasNext() {
        return seq != null;
    }

}
