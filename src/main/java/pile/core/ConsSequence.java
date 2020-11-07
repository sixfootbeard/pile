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

public class ConsSequence extends AbstractSeq<Object> {

    private final Object head;
    
    // protected by synchronized (this)
    private Seqable rest;
    // protected by synchronized (this)
    private volatile ISeq realized = null;

    public ConsSequence(Object head) {
        this(head, null);
    }

    public ConsSequence(Object head, Seqable rest) {
        this.head = head;
        this.rest = rest;
    }

    @Override
    public Object first() {
        return head;
    }

    @Override
    public ISeq next() {
        // TODO end of sequence always dips into sync, maybe have a boolean as well?
        if (realized == null) {
            synchronized (this) {
                if (realized == null) {
                    if (rest != null) {
                        realized = rest.seq();
                        rest = null;
                    }
                }
            }
        }
        return realized;
    }

}
