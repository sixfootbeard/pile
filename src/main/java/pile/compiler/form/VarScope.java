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
package pile.compiler.form;

public enum VarScope {

	/**
	 * (fn [a b] (let [a 1] .. ) )
	 */
	METHOD_LET,
	/**
	 * (fn [a b] ...)
	 * 
	 */
	METHOD,
	
	
	FIELD,
	
	/**
	 * (fn [a b] (fn [c] (+ a b c)))
	 */
	CLOSURE,

	/**
	 * (let [a 1] (prn a))
	 */
	NAMESPACE_LET,

	/**
	 * (def v 1) (prn v)
	 */
	NAMESPACE,
	
	/**
	 * Integer/TYPE
	 */
	JAVA_CLASS,

	/**
	 * java.lang.String (Java classes)
	 * pile.core (Pile namespaces)
	 */
	LITERAL;

}