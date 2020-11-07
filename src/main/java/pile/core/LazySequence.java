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

import pile.nativebase.NativeCore;
import pile.nativebase.method.PileInvocationException;

/**
 * 
 * 
 * @param <T>
 */
public class LazySequence<T> implements Seqable<T>/*, ISeq<T>*/ {

    private PCall afn;
    private ISeq<T> seq = null;
    private boolean reified = false;

    public LazySequence(PCall afn) {
        this.afn = afn;
    }

    @Override
    public synchronized ISeq<T> seq() {
        if (! reified) {
            try {
                seq = NativeCore.seq(afn.invoke());
            } catch (Throwable e) {
                throw new PileInvocationException("Error while calling function for lazy sequence", e);
            }
            afn = null;
            reified = true;
        }
        return seq;
    }
    
    @Override
    public String toString() {
        return ISeq.toString(new StringBuilder(), this.seq());
    }

}
