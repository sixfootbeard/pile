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

public class MultimethodTest extends AbstractTest {

	@Test
	public void testMM() {
		pre("""
				(defmulti getl (fn* [x] (get x :type)))
				(defmethod getl :a [x] "a")
				(defmethod getl :b [x] "b")
				(defmethod getl :default [x] "default")

				(def mmget (fn* [a] (getl a)))
				""");

		assertEquals("a", eval("(eval (getl {:type :a}))"));
		assertEquals("b", eval("(eval (getl {:type :b}))"));
		assertEquals("default", eval("(eval (getl {:type \"idk\"}))"));

		assertEquals("a", eval("(eval (mmget {:type :a}))"));
		assertEquals("b", eval("(eval (mmget {:type :b}))"));
		assertEquals("default", eval("(eval (mmget {:type \"idk\"}))"));

		pre("(def mmgetc (fn* [] (getl {:type :c})))");
		assertEquals("default", eval("(eval (mmgetc))"));
		pre("(defmethod getl :c [x] \"c\")");
		assertEquals("c", eval("(eval (mmgetc))"));
		
		pre("(remove-method getl :a)");
		assertEquals("default", eval("(eval (getl {:type :a}))"));
	}
	
	@Test
    public void testMMSizes() {
        pre("""
                (defmulti getl (fn* [x f s] (get x :type)))
                (defmethod getl :a [x f s] f)
                (defmethod getl :b [x f s] s)
                (defmethod getl :default [x f s] "default")

                (def mmget (fn* [a f s] (getl a f s)))
                """);

        assertEquals(1, eval("(eval (getl {:type :a} 1 2))"));
        assertEquals(2, eval("(eval (getl {:type :b} 1 2))"));
        assertEquals("default", eval("(eval (getl {:type \"idk\"} 1 2))"));

        assertEquals(1, eval("(eval (mmget {:type :a} 1 2))"));
        assertEquals(2, eval("(eval (mmget {:type :b} 1 2))"));
        assertEquals("default", eval("(eval (mmget {:type \"idk\"} 1 2))"));
    }

	@Test
	public void testMMVarArg() {
		pre("""
				(defmulti getl (fn* [x & _] (get x :type)))
				(defmethod getl :a [x f s] f)
				(defmethod getl :b [x f s] s)
				(defmethod getl :default [x f s] "default")

				(def mmget (fn* [a f s] (getl a f s)))
				""");

		assertEquals(1, eval("(eval (getl {:type :a} 1 2))"));
		assertEquals(2, eval("(eval (getl {:type :b} 1 2))"));
		assertEquals("default", eval("(eval (getl {:type \"idk\"} 1 2))"));

		assertEquals(1, eval("(eval (mmget {:type :a} 1 2))"));
		assertEquals(2, eval("(eval (mmget {:type :b} 1 2))"));
		assertEquals("default", eval("(eval (mmget {:type \"idk\"} 1 2))"));
	}

}
