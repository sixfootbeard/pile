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
import pile.core.Namespace;
import pile.core.binding.NativeDynamicBinding;

public class NewTest extends AbstractTest {

	private static final Integer EXPECTED = new Integer(12);

    @Test
	public void testNewEval() {
		assertEquals(EXPECTED, e("(new Integer (to-int 12))"));
	}
	
	@Test
	public void testNewEvalOther() {
		assertEquals(EXPECTED, e("(new Integer \"12\")"));
	}
	
	@Test
	public void testNewCompiled() {
		pre("""
				(def icons (fn* [] (new Integer (to-int 12))))
				""");
		assertEquals(EXPECTED, e("(icons)"));
	}
	
	@Test
	public void testNewCompiledRelink() {
		pre("""
				(def icons (fn* [a] (new Integer a)))
				""");
		assertEquals(EXPECTED, e("(icons (to-int 12))"));
		assertEquals(EXPECTED, e("(icons \"12\")"));
	}
	
	@Test
	public void testNewCompiledString() {
		pre("""
				(def icons (fn* [] (new Integer "12")))
				""");
		assertEquals(EXPECTED, e("(icons)"));
	}
	
	//	@Ignore // Dynamic-base constructors
	@Test
	public void testNewCompiledRelinkThree() {
		Namespace ns = NativeDynamicBinding.NAMESPACE.getValue();
		ns.createClassSymbol("ThreeCons", ThreeCons.class);
		
		pre("""
				(def icons (fn* [a] (new ThreeCons a)))
				""");
		
		assertEquals(int.class, e("(let* [tc (to-int 12)] (. (icons tc) clazz))"));
		assertEquals(String.class, e("(let* [tc \"foo\"] (. (icons tc) clazz))"));
	}

    public static class ThreeCons {
    	private final Class<?> clazz;
    	public ThreeCons(Object o) { this.clazz = o.getClass(); }
    	public ThreeCons(String o) { this.clazz = o.getClass(); }
    	public ThreeCons(int o) { this.clazz = int.class; }
    	
    	public Class<?> clazz() {
    		return clazz;
    	}
    }
	
}
