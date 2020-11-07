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

public class PileRestartException extends RuntimeException {

    private static final long serialVersionUID = 5368169880536094617L;

    private final Object restartName;
    private final Object[] restartFunctionArgs;

    public PileRestartException(Object restartName, Object... restartFunctionArgs) {
        super("In flight restart");
        this.restartName = restartName;
        this.restartFunctionArgs = restartFunctionArgs;
    }
    
    public Object getRestartName() {
        return restartName;
    }
    
    public Object[] getRestartFunctionArgs() {
        return restartFunctionArgs;
    }

}
