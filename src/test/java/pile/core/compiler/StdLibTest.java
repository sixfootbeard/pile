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
import pile.core.Keyword;
import pile.core.StaticTypeMismatchException;
import pile.core.exception.PileExecutionException;

public class StdLibTest extends AbstractTest {

	static Keyword KEY_A = Keyword.of(null, "a");
	static Keyword KEY_B = Keyword.of(null, "b");
	static Keyword KEY_C = Keyword.of(null, "c");
	
	@Test
	public void testMap() {
		assertEquals(KEY_A, eval("(eval (get {:b :a} :b))"));
	}
	
	@Test
	public void testMapGetNil() {
		assertEquals(null, eval("(eval (get {:b :a} :z))"));
	}
	
	@Test
	public void testMapGetDefault() {
		assertEquals(KEY_C, eval("(eval (get {:b :a} :z :c))"));
	}
	
	@Test
	public void testList() {
		assertEquals(KEY_A, eval("(eval (get [:b :a] 1))"));
	}
	
	@Test
	public void testListGetNil() {
		assertEquals(null, eval("(eval (get [:b :a] :z))"));
	}
	
	@Test
	public void testListGetDefault() {
		assertEquals(KEY_C, eval("(eval (get [:b :a] :z :c))"));
	}
	
	@Test
	public void testKeyword() {
		assertEquals(KEY_C, eval("(eval (keyword \"c\"))"));
	}
	
	@Test
	public void testPartial() {
		pre("""
				(def p (partial +))
				""");
		assertEquals(5, eval("(p 2 3)"));
	}
	
	@Test
	public void testInc() {
		pre("""
				(def iinc (partial + 1))
				""");
		assertEquals(4, eval("(iinc 3)"));
	}
	
	@Test(expected = ClassCastException.class)
	public void testKeywordBad() {
		eval("(eval (keyword 12))");
	}
	
	@Test(expected = PileExecutionException.class)
    public void testKeywordBadCompile() {
	    pre("""
	            (defn kw [a] (keyword a))
	            """);
        eval("(eval (kw 12))");
    }

	

	

}
