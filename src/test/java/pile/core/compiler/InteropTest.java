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

import java.util.HashMap;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import pile.compiler.CloseNoThrow;
import pile.compiler.InteropException;
import pile.core.AbstractTest;
import pile.core.Keyword;
import pile.core.Namespace;
import pile.core.PCall;
import pile.core.RuntimeRoot;
import pile.core.binding.Binding;
import pile.core.binding.NativeDynamicBinding;
import pile.core.exception.UnlinkableMethodException;
import pile.core.exception.PileCompileException;
import pile.core.exception.PileSyntaxErrorException;
import pile.core.indy.DeferredCompiledException;
import pile.test.classes.InterfaceWithDefaults;

public class InteropTest extends AbstractTest {

	private Namespace ns;

	@Before
	public void setup() {
		ns = RuntimeRoot.get(nsStr);
	}

	@Test
	public void testIntegerStaticFieldCompiled() {
		pre("""
				(def ibytes (fn* [] (. Integer -TYPE)))
				""");
		assertEquals(int.class, eval("(ibytes)"));
	}
	
	@Test(expected = PileCompileException.class)
	public void testIntegerStaticFieldCompiledFieldError() {
		pre("""
				(def ibytes (fn* [] (. Integer -BADFIELD)))
				""");
		assertEquals(int.class, eval("(eval (ibytes))"));
	}
	
	@Test(expected = PileCompileException.class)
	public void testIntegerStaticFieldCompiledFieldErrorStatic() {
		pre("""
				(def ibytes (fn* [] (. Integer -value)))
				""");
		assertEquals(int.class, eval("(eval (ibytes))"));
	}
	
	@Test(expected = PileCompileException.class)
	public void testIntegerStaticFieldCompiledClassError() {
		pre("""
				(def ibytes (fn* [] (. BadClass -BADFIELD)))
				""");
		assertEquals(int.class, eval("(eval (ibytes))"));
	}
	
	@Test
	public void testIntegerStaticFieldEval() {
		assertEquals(int.class, eval("(. Integer -TYPE)"));
	}

	@Test(expected = UnlinkableMethodException.class)
	public void testIntegerStaticMethodCompiledError() {
		pre("""
				(def ibytes (fn* [] (. Integer badMethod)))
				""");

//		try { 
			eval("(eval (ibytes))");
//			fail("Should fail");
//		} catch (Throwable t) {
//			t.printStackTrace();
//			assertEquals(DeferredCompiledException.class, t.getCause().getClass());
//		}
	}
	
	@Test(expected = UnlinkableMethodException.class)
	public void testIntegerStaticMethodCompiledErrorDefer() {
		try (CloseNoThrow update = NativeDynamicBinding.DEFER_ERRORS.withUpdate(true)) {
			pre("""
					(def ibytes (fn* [] (. Integer badMethod)))
					""");
		}
//		try { 
			eval("(eval (ibytes))");
//			fail("Should fail");
//		} catch (Throwable t) {
//			assertEquals(DeferredCompiledException.class, t.getCause().getClass());
//		}
	}
	
	@Test
	public void testStringInstanceMethodLiteralCompiled() {
		pre("""
				(def ibytes (fn* [] (. "foobar" indexOf "b" )))
				""");
		assertEquals(3, eval("(eval (ibytes))"));
	}

	@Test
	public void testStringInstanceMethodLiteralEval() {
		assertEquals(3, eval("(eval (. \"foobar\" indexOf \"b\"))"));
	}

	@Test
	public void testStringInstanceMethodLet() {
		pre("""
				(def iof (fn* []
					(let* [s "foobar"]
						(. s indexOf "b" ))))
				""");
		assertEquals(3, eval("(eval (iof))"));
	}

	@Test
	public void testStringInstanceMethodArg() {
		pre("""
				(def iof (fn* [s]
						(. s indexOf "b" )))
				""");
		assertEquals(3, eval("(eval (iof \"foob\"))"));
		assertEquals(1, eval("(eval (iof [1 \"b\" 12]))"));
		assertEquals(0, eval("(eval (iof \"bar\"))"));
	}

	@Test
	public void testStringInstanceArgBase() {
		pre("""
				(def iof (fn* [b s]
						(. b indexOf s )))
				""");
		assertEquals(0, eval("(eval (iof [\"foob\" \"barf\"] \"foob\"))"));
		assertEquals(-1, eval("(eval (iof [] \"bar\"))"));
	}

	@Test
	public void testIntegerInteropCall() {
		pre("""
				(def ibytes (fn* [] (. Integer (parseInt "12"))))
				""");
		assertEquals(12, eval("(eval (ibytes))"));
	}

	@Test
	public void testIntegerInteropCallArg() {
		pre("""
				(def ibytes (fn* [a] (. Integer (parseInt a))))
				""");
		assertEquals(12, eval("(eval (ibytes \"12\"))"));
	}

	@Test
	public void testIntegerInteropCallUnsplice() {
		pre("""
				(def ibytes (fn* [] (. Integer parseInt "12")))
				""");
		assertEquals(12, eval("(eval (ibytes))"));
	}

	@Test
	public void testIntegerInteropCallArgCoerce() {
		pre("""
				(def ibytes (fn* [a] (. Integer (valueOf a))))
				""");
		assertEquals(12, eval("(eval (ibytes (to-int 12)))"));
	}

	@Test
	public void testIntegerInteropCallArgCoerceBoth() {
		pre("""
				(def ibytes (fn* [a] (. Integer (valueOf a))))
				""");
		assertEquals(12, eval("(eval (ibytes \"12\"))"));
		assertEquals(12, eval("(eval (ibytes (to-int 12)))"));
		assertEquals(12, eval("(eval (ibytes \"12\"))"));
	}
	
	
	// instance
	@Test
	public void testIntegerEvalInteropCall() {
		assertEquals(12, eval("(eval (. Integer (parseInt \"12\")))"));
	}
	
	@Test
	public void testIntegerEvalInteropToString() {
		assertEquals("12", eval("(eval (. 12 (toString)))"));
	}
	
	public static class TestField {
		public String foo = "bar";
	}
	
	@Test
	public void testIntegerEvalInteropCompareTo() {
		assertEquals(-1, eval("(eval (. 12 (compareTo 13)))"));
	}
	
	@Test
	public void testIntegerEvalFieldInstance() {
		Namespace ns = NativeDynamicBinding.NAMESPACE.getValue();
		ns.createClassSymbol("TestField", TestField.class);
		assertEquals("bar", eval("(eval (. (new TestField) -foo))"));
	}
	
	@Test
	public void testHashMap() {
		NativeDynamicBinding.NAMESPACE.getValue().createClassSymbol("HashMap", HashMap.class);
		pre("""
				(def hmap (fn* [a] 
							(let* [hm (new HashMap)]
								(do
									(. hm (put :key a))
									hm)
								)))
				""");
		HashMap hashMap = (HashMap) eval("(hmap \"12\")");
		assertEquals("12", hashMap.get(Keyword.of(null, "key")));
	}
	
	@Test
	public void testTestField() {
		NativeDynamicBinding.NAMESPACE.getValue().createClassSymbol("TestField", TestField.class);
		pre("""
				(def tf (fn* [] (new TestField)))
				(def tfget (fn* [tf] (. tf -foo)))
				(def tfgettype (fn* [] (. (new TestField) -foo)))
				""");
		
		assertEquals("bar", eval("(eval (. (tf) -foo))"));
		assertEquals("bar", eval("(eval (tfget (new TestField)))"));
		assertEquals("bar", eval("(eval (tfgettype))"));
	}
	
	@Ignore // Don't support this right now
	@Test
	public void testTestFunction() throws Throwable {
		pre("""
				(def tf (fn* [a b] (a b)))
				""");
		
		Binding local = ns.getLocal("tf");		
		PCall deref = (PCall) local.getValue();		
		Function<String, String> fn = (in) -> "a" + in;
		
		Object out = deref.invoke(fn, "b");
		assertEquals("ab", out);
	}
	
	@Ignore // Don't support this right now
	@Test
	public void testTestFunctionChangeType() throws Throwable {
		pre("""
				(def tf (fn* [a b] (a b)))
				""");
		
		Binding local = ns.getLocal("tf");		
		PCall deref = (PCall) local.getValue();		
		Function<String, String> fn = (in) -> "a" + in;
		
		assertEquals("ab", deref.invoke(fn, "b"));
		
		PCall pfn = (in) -> "z" + in[0];		
		assertEquals("zb", deref.invoke(pfn, "b"));
	}
	
	
	public static class TestDoubleString {
		private final String a, b;

		public TestDoubleString(String a, String b) {
			super();
			this.a = a;
			this.b = b;
		}

		public String getA() {
			return a;
		}

		public String getB() {
			return b;
		}
	}
	
	@Test
	public void testConstructor() {
		ns.createClassSymbol(TestDoubleString.class.getSimpleName(), TestDoubleString.class);		
		eval("(new TestDoubleString nil \"b\")");		
		assertEquals(null, eval("(. (new TestDoubleString nil \"b\") getA)"));
		assertEquals("b", eval("(. (new TestDoubleString nil \"b\") getB)"));
		assertEquals(null, eval("(. (new TestDoubleString nil nil) getA)"));
		assertEquals(null, eval("(. (new TestDoubleString nil nil) getB)"));
	}

	@Test
    public void testProxyDefault() throws Exception {
        InterfaceWithDefaults out = (InterfaceWithDefaults) eval(
                "(proxy [pile.util.InterfaceWithDefaults] {\"foo\" (fn [a] \"foo\")})");
        assertEquals("foo", out.foo("ignored"));
        assertEquals("else", out.foo("ignored", "else"));
    }
    
	@Test
    public void testProxyDefaultOverride() throws Exception {
        InterfaceWithDefaults out = (InterfaceWithDefaults) eval(
                "(proxy [pile.util.InterfaceWithDefaults] {\"foo\" [(fn [a] \"foo\") (fn [a b] \"override\")]})");
        assertEquals("foo", out.foo("ignored"));
        assertEquals("override", out.foo("ignored", "else"));
    }

}
