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
package pile.test.classes;

public class MultiTargetConstructor {

    public final String s;

    public MultiTargetConstructor(String s) {
        this.s = s;
    }
    
    public MultiTargetConstructor(Integer i) {
        this.s = Integer.toString(i);
    }
    
    public String getS() {
        return s;
    }

}
