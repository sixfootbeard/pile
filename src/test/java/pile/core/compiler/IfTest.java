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

public class IfTest extends AbstractTest {

	@Test
	public void testIfTrue() {
	    pre("""
	        (if true
	    		(def foo "one")
	    		(def foo "two"))
	        """);
	    
	    // Compiled
	    assertEquals("one", eval("(eval foo)"));
	}

	@Test
	public void testIfFalse() {
	    pre("""
	        (if false
	    		(def foo "one")
	    		(def foo "two"))
	        """);
	    
	    // Compiled
	    assertEquals("two", eval("(eval foo)"));
	}

	@Test
	public void testIfTrueCompile() {
	    pre("""
	    	(def iftest (fn* [] 
				            (if true
				        		"one"
				        		"two")))
	        """);
	    
	    // Compiled
	    assertEquals("one", eval("(eval (iftest))"));
	}

	@Test
	public void testIfTrueCompileDifferentTypesBothLet() {
	    pre("""
	    	(def iftest (fn* [] 
				            (if true
				        		(let* [a "one"] a)
				        		(let* [b 12] b))))
	        """);
	    
	    // Compiled
	    assertEquals("one", eval("(eval (iftest))"));
	}

	@Test
	public void testIfTrueCompileDifferentTypesLet() {
	    pre("""
	    	(def iftest (fn* [] 
				            (if true
				        		(let* [a "one"] a)
				        		12)))
	        """);
	    
	    // Compiled
	    assertEquals("one", eval("(eval (iftest))"));
	}

	@Test
	public void testIfTrueCompileDifferentTypesThen() {
	    pre("""
	    	(def iftest (fn* [] 
				            (if true
				        		"one"
				        		12)))
	        """);
	    
	    // Compiled
	    assertEquals("one", eval("(eval (iftest))"));
	}

	@Test
	public void testIfTrueCompileDifferentTypesElse() {
	    pre("""
	    	(def iftest (fn* [] 
				            (if false
				        		"one"
				        		12)))
	        """);
	    
	    // Compiled
	    assertEquals(12, eval("(eval (iftest))"));
	}

	@Test
	public void testIfFalseCompile() {
	    pre("""
	    	(def iftest (fn* [] 
				            (if false
				        		"one"
				        		"two")))
	
	        """);
	    
	    // Compiled
	    assertEquals("two", eval("(eval (iftest))"));
	}
	
	@Test
	public void testIfFalseDefaultCompile() {
	    pre("""
	    	(def iftest (fn* [] 
				            (if false
				        		"one")))
	
	        """);
	    
	    // Compiled
	    assertEquals(null, eval("(eval (iftest))"));
	}
	
	@Test
	public void testIfFalseDefaultEval() {
    
	    // Eval
	    assertEquals(null, eval("(eval (if false \"one\"))"));
	}


}
