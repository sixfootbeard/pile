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
module pile.lang {
	exports pile.collection;
	
	exports pile.core;
	exports pile.core.parse;
	exports pile.core.hierarchy;
	exports pile.compiler;
	exports pile.core.binding;
	exports pile.core.runtime.generated_classes;
	exports pile.core.indy;
	exports pile.repl;
	
	exports pile.nativebase;
	exports pile.util;

	requires java.base;
	requires org.objectweb.asm;
	requires org.objectweb.asm.util;
	requires org.objectweb.asm.commons;
	
	// Testing only...
	requires jdk.unsupported;
    requires jdk.incubator.concurrent;
}