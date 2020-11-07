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

import org.junit.Before;
import org.junit.Test;

import pile.collection.PersistentList;
import pile.core.AbstractTest;
import pile.core.Symbol;

public class MacroTest extends AbstractTest {

	private static final Symbol PLUS_SYM = new Symbol("+");
	
	private Symbol RESOLVED_PLUS_SYM = new Symbol("pile.core", "+");

	private Object testMacro(String s) {
		return eval("(macroexpand '(" + s + "))");
	}
	
	@Before
	public void setup() {
//	    RESOLVED_PLUS_SYM = new Symbol(nsStr, "+");
	}
	
	@Test
	public void testSimple() {
		pre("""
             (defmacro madd [a]
				  '(+ 1 ~a))
		     (def doadd (fn* [] (madd 2)))   
                """);
        assertEquals(3, eval("(eval (doadd))"));
	}
	
	@Test
	public void testSimpleSyntax() {
		pre("""
             (defmacro madd []
				  `(+ 1 2))
                """);
		assertEquals(PersistentList.reversed(RESOLVED_PLUS_SYM, 1L, 2L), 
				testMacro("madd"));
	}
	
	@Test
	public void testSimpleNestedSyntax() {
		pre("""
             (defmacro nested []
				  ````(+ 1 2))
                """);
		assertEquals(PersistentList.reversed(RESOLVED_PLUS_SYM, 1L, 2L), 
				testMacro("nested"));
	}
	
	@Test
	public void testSimpleSyntaxNested() {
		pre("""
             (defmacro madd []
				  `(+ 1 2 (+ 4 5)))
                """);
		PersistentList inner = PersistentList.reversed(RESOLVED_PLUS_SYM, 4L, 5L);
		assertEquals(PersistentList.reversed(RESOLVED_PLUS_SYM, 1L, 2L, inner), 
				testMacro("madd"));
	}
	
	@Test
	public void testSimpleSyntaxNestedTwo() {
		pre("""
             (defmacro madd []
				  `(+ 1 2 `(+ 4 5)))
                """);
		PersistentList inner = PersistentList.reversed(RESOLVED_PLUS_SYM, 4L, 5L);
		assertEquals(PersistentList.reversed(RESOLVED_PLUS_SYM, 1L, 2L, inner), 
				testMacro("madd"));
	}
	
	@Test
	public void testSimpleQuote() {
		pre("""
             (defmacro madd []
				  '(+ 1 2))
                """);
		
		assertEquals(PersistentList.reversed(PLUS_SYM, 1L, 2L), 
				testMacro("madd"));
//		assertEquals(PersistentList.reversed(PLUS_SYM, 1L, 2L), 
//				eval("(macroexpand '(madd))"));
	}
	
	@Test
	public void testSimpleQuoteNested() {
		pre("""
             (defmacro madd []
				  '(+ 1 2 (+ 4 5)))
                """);
		PersistentList inner = PersistentList.reversed(PLUS_SYM, 4L, 5L);
		assertEquals(PersistentList.reversed(PLUS_SYM, 1L, 2L, inner), 
				testMacro("madd"));
	}
	
	@Test
	public void testSimpleSyntaxUnquote() {
		pre("""
             (defmacro madd [a]
				  `(+ 1 ~a))
		     (def doadd (fn* [] (madd 2)))   
                """);
        assertEquals(3, eval("(eval (doadd))"));
	}
	
	@Test
	public void testSimpleSyntaxNestedCompiledTwo() {
		pre("""
             (defmacro madd [a] `(+ ~@a))
             (defmacro tadd [a] `(madd [~a ~a]))
		     (def doadd (fn* [a] (tadd a)))   
                """);
		assertEquals(2, eval("(eval (doadd 1))"));
		assertEquals(4, eval("(eval (doadd 2))"));
	}
	
	@Test
	public void testSimpleSyntaxUnquoteSplice() {
		pre("""
             (defmacro madd [a] `(+ ~@a))
		     (def doadd (fn* [] (madd [2 3])))   
                """);
        assertEquals(5, eval("(eval (doadd))"));
	}
	
//	@Ignore
	@Test
	public void testMacroLong() {
		var expected = PersistentList.reversed(new Symbol("+"), 1L, 1L);
		assertEquals(expected, eval("'(+ 1 1)"));
	}
}
