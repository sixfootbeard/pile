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

    // protected by synchronized(this)
    private Supplier<ISeq<T>> postGen;
    // protected by synchronized(this)
    private volatile ISeq<T> nextSeq;

    public Concat(ISeq<T> pre, ISeq<T> post) {
        this(pre, () -> post);
    }

    public Concat(ISeq<T> pre, Supplier<ISeq<T>> post) {
        Objects.requireNonNull(pre, "pre shouldn't be null");
        this.pre = pre;
        this.postGen = post;
    }

    @Override
    public T first() {
        return pre.first();
    }

    @Override
    public ISeq<T> next() {
        if (nextSeq == null) {
            synchronized (this) {
                // This might seem like overkill since we could just check 'pre' for next() but
                // that may lead to multiple instances of seqs which have the postgen supplier.
                // We only want one instance so we can synchronize its realization. 
                if (nextSeq == null) {
                    // Still might have elements in first sequence
                    ISeq<T> maybeNext = pre.next();
                    if (maybeNext != ISeq.EMPTY) {
                        // Create next() concat and store it so that there's only ever one instance
                        // holding postgen
                        nextSeq = new Concat<>(maybeNext, postGen);
                    } else {
                        // unrealized postgen, if postgen is not null
                        if (postGen != null) {
                            // While multiple instance of concat may reference postGen only one will ever
                            // attempt to call it.
                            nextSeq = postGen.get();
                            postGen = null;
                        }
                    }
                }
            }
        }
        return nextSeq;
    }

}