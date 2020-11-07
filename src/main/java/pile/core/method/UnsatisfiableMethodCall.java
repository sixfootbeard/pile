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

import java.lang.invoke.MethodType;

public class UnsatisfiableMethodCall extends RuntimeException {

    private MethodType type;

    public UnsatisfiableMethodCall() {}
    
    public UnsatisfiableMethodCall(MethodType type) {
        super("Unable to find method to call: " + type);
        this.type = type;
    }

    public UnsatisfiableMethodCall(String message) {
        super(message);
    }

    public UnsatisfiableMethodCall(Throwable cause) {
        super(cause);
    }

    public UnsatisfiableMethodCall(String message, Throwable cause) {
        super(message, cause);
    }

    public UnsatisfiableMethodCall(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
