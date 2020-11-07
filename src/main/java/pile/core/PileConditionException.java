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

import pile.collection.PersistentMap;

public class PileConditionException extends RuntimeException {

    private PersistentMap map = null;
    private Symbol sym;

    public PileConditionException() {
    }

    public PileConditionException(String message) {
        super(message);
    }

    public PileConditionException(Symbol sym, PersistentMap map) {
        super("condition error");
        this.sym = sym;
        this.map = map;
    }

    public PileConditionException(Throwable cause) {
        super(cause);
    }

    public PileConditionException(String message, Throwable cause) {
        super(message, cause);
    }

    public PileConditionException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
    
    public Symbol getSym() {
        return sym;
    }
    
    public PersistentMap getMap() {
        return map;
    }
}
