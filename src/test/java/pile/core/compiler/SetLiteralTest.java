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
package pile.core.compiler;

import org.junit.Test;

import static org.junit.Assert.*;
import static pile.core.TestHelpers.*;

import pile.collection.PersistentSet;
import pile.core.AbstractTest;

public class SetLiteralTest extends AbstractTest {

	
	@Test
	public void testCompiledEmpty() {
		pre("(def lit (fn* [] #{}))");
		assertEquals(PersistentSet.empty(), eval("(lit)"));
	}
	
	@Test
	public void testCompiledConstant() {
		pre("(def lit (fn* [] #{12}))");
		assertEquals(PersistentSet.createArr(12L), eval("(lit)"));
	}
	
	@Test
	public void testCompiledArg() {
		pre("(def lit (fn* [a] #{a}))");
		assertEquals(PersistentSet.createArr(12L), eval("(lit 12)"));
	}
	
	@Test
	public void testCompiledTwoArg() {
		pre("(def lit (fn* [a] #{a}))");
		assertEquals(PersistentSet.createArr(12L), eval("(lit 12)"));
	}
}
