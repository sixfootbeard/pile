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

public class StrCatFormTest extends AbstractTest {
	
	@Test
	public void testStrCompiledLocals() {
		pre("""
			(def docat (fn* [] (str "a" "b" "c")))
			""");
		assertEquals("abc", eval("(eval (docat))"));
	}
	
	@Test
	public void testStrCompiledLocalsNested() {
		pre("""
			(def docat (fn* [] (str "a" (str "b" (str "c" "d") "e") "f")))
			""");
		assertEquals("abcdef", eval("(eval (docat))"));
	}
	
	@Test
	public void testStrCompiledLocalsNestedLocal() {
		pre("""
			(def docat (fn* [d] (str "a" (str "b" (str "c" d) "e") "f")))
			""");
		assertEquals("abcdef", eval("(eval (docat \"d\"))"));
	}
	
	@Test
	public void testStrCompiledLocalsAllTypes() {
		pre("""
			(def docat (fn* [] (str "a" 1 true \\space false)))
			""");
		assertEquals("a1true false", eval("(eval (docat))"));
	}
	
	@Test
	public void testStrEvalLocalsAllTypes() {
		assertEquals("a1true false", eval("(eval (str \"a\" 1 true \\space false))"));
	}
	
	@Test
	public void testStrCompiledMixed() {
		pre("""
			(def docat (fn* [b] (str "a" b "c")))
			""");
		assertEquals("abc", eval("(eval (docat \"b\"))"));
	}
	
	@Test
	public void testStrCompiledMixedNull() {
		pre("""
			(def docat (fn* [b] (str "a" b nil)))
			""");
		assertEquals("abnull", eval("(eval (docat \"b\"))"));
	}
	
	@Test
	public void testStrEvalNull() {
		assertEquals("abnull", eval("(eval (str \"a\" \"b\" nil))"));
	}
	
	@Test
	public void testStrCompiledNonConst() {
		pre("""
			(def docat (fn* [a b c] (str a b c)))
			""");
		assertEquals("abc", eval("(eval (docat \"a\" \"b\" \"c\"))"));
	}
	
	@Test
	public void testStrCompiledFunctionArg() {
		pre("""
			(def docat (fn* [s a b c] (s a b c)))
			""");
		assertEquals("abc", eval("(eval (docat str \"a\" \"b\" \"c\"))"));
	}


}
