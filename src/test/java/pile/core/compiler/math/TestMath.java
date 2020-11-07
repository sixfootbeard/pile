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
package pile.core.compiler.math;

import static org.junit.Assert.*;
import static pile.core.TestHelpers.*;

import org.junit.Test;

import pile.core.AbstractTest;

public class TestMath extends AbstractTest {
	
	@Test
	public void testEquals() {
		assertEquals(true, eval("(eval (= (to-int 1) (to-long 1)))"));
	}
	
	
	@Test
	public void testPlus() {
		assertEquals(3, eval("(eval (+ 1 2))"));
	}
	
	@Test
	public void testPlusMixed() {
		assertEquals(3.2, eval("(eval (+ 1 2.2))"));
	}
	
	
	@Test
	public void testPlusCompiled() {
		pre("""
			(def pluscmp (fn* [^Integer a ^Integer b] (+ a b)))
			""");
		assertEquals(3, eval("(eval (pluscmp 1 2))"));
	}
	
	@Test
	public void testPlusCompiledHalf() {
		pre("""
			(def pluscmp (fn* [a] (+ a 1)))
			""");
		assertEquals(4, eval("(eval (pluscmp 3))"));
	}
	@Test
	public void testPlusCompiledHalfOther() {
		pre("""
			(def pluscmp (fn* [a] (+ 1 a)))
			""");
		assertEquals(4, eval("(eval (pluscmp 3))"));
	}
	
	@Test
	public void testPlusCompiledHalfHint() {
		pre("""
			(def pluscmp (fn* [^Integer a] (+ a 1)))
			""");
		assertEquals(4, eval("(eval (pluscmp 3))"));
	}
	
	@Test
	public void testPlusCompiledHalfOtherHint() {
		pre("""
			(def pluscmp (fn* [^Integer a] (+ 1 a)))
			""");
		assertEquals(4, eval("(eval (pluscmp 3))"));
	}
	
	@Test
	public void testPlusCompiledNoHintTypeChange() {
		pre("""
				(def foo (fn* [a b] (+ a b)))		
			""");
		assertEquals(8.4, ((Double)eval("(eval (foo 4 4.4))")).doubleValue(), 0.0);
		assertEquals(8.7, eval("(eval (foo 4.3 4.4))"));
	}

	
//	
}
