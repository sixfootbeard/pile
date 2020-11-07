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

import static org.junit.Assert.*;
import static pile.core.TestHelpers.*;

import org.junit.Test;

import pile.core.AbstractTest;

public class TypeHintTest extends AbstractTest {
	
	@Test
	public void testHinting() {
		pre("""
			(def ie (fn* [^String s] (. s isEmpty)))
			""");
		assertEquals(true, eval("(eval (ie \"\"))"));
		assertEquals(false, eval("(eval (ie \"foo\"))"));
	}
	
	@Test
	public void testHintingConstructor() {
		pre("""
			(def scons (fn* [^String s] (new String s)))
			""");
		assertEquals("", eval("(eval (scons \"\"))"));
		assertEquals("foo", eval("(eval (scons \"foo\"))"));
	}
}
