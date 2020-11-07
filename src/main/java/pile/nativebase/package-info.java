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
/**
 * A native base is a class with static methods which will be loaded into a
 * particular namespace as if it had been bootstrapped and written in pure lisp
 * code. This means there is no linkage boilerplate necessary to call into these
 * java methods. EG. pile.nativebase.NativeCore is loaded into the "pile.core"
 * namespace and all static methods are available from lisp code. The methods
 * provided can be overloaded and the most appropriate method will be called at
 * runtime.
 */
package pile.nativebase;