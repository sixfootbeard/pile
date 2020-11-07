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

public enum InteropType {
	
	// (. Classname-symbol (method-symbol args*)) 
	// (. Classname-symbol method-symbol args*)
	STATIC_METHOD_CALL, 
	
	// (. Classname-symbol member-symbol)
	STATIC_FIELD_GET, 
	
	// (set! (. Classname-symbol staticFieldName-symbol) expr)
	STATIC_FIELD_SET, 
	
	// (. instance-expr (method-symbol args*))
	// (. instance-expr method-symbol args*)
	INSTANCE_CALL, 
	
	// (. instance-expr -field-symbol)
	INSTANCE_FIELD_GET, 

	// (set! (. instance-expr instanceFieldName-symbol) expr)
	INSTANCE_FIELD_SET;
}