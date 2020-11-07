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
package pile.core.parse;

import static org.junit.Assert.*;
import static pile.nativebase.NativeCore.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Ignore;
import org.junit.Test;

import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.collection.PersistentVector;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.core.AbstractTest;
import pile.core.Cons;
import pile.core.ISeq;
import pile.core.Keyword;

public class CompilerTest extends AbstractTest {
	
	private static final int _12L = 12;

    public static void pre(String s) {
		try {
			Compiler.evaluate(parse(s));
		} catch (Throwable e) {
			e.printStackTrace();
			fail();
		}
	}

    @Test
    public void test() {
        pre("(def abcd \"foo\")");
        assertEquals("foo", eval("(eval abcd)"));
    }

    private Object eval(String string) {
		try {
			return Compiler.evaluate(new CompilerState(), first(parse(string)));
		} catch (Throwable e) {
			e.printStackTrace();
			fail();
			return null;
		}
	}
    
    @Test
    public void testList() {
    	assertEquals(PersistentList.createArr(1L, 2L), eval("(list 2 1)"));
    }
    
    @Test
    public void testListCompile() {
    	pre("(def elist (fn* [a] (list 1 2 a)))");
    	assertEquals(PersistentList.createArr(3L, 2L, 1L), eval("(elist 3)"));
    }

	@Test
    public void testLet() {
        pre("""
       		(let* [a "foo"]
               (def z a))
            """);
        assertEquals("foo", eval("(eval z)"));
    }
	
	@Test
    public void testDefLet() {
        pre("""
        	(def dlet (fn* [b]
		       		(let* [a "foo"]
		               a)))
            """);
        assertEquals("foo", eval("(eval (dlet nil))"));
    }
	
	@Test
    public void testDefLetLong() {
        pre("""
        	(def dlet (fn* [b]
		       		(let* [a 2]
		               a)))
            """);
        assertEquals(2, eval("(eval (dlet nil))"));
    }

    @Test
    public void testDef() {
        pre("""
            (def domee (fn* [a b] a))
            """);
        assertEquals("foo", eval("""
					        		(let* [foo (domee "foo" "bat")]
					                	foo)
        						 """));
    }
    
    @Test
    public void testDefDyn() {
        pre("""
            (def st 1)
            (def stf (fn* [] st))
            """);
        assertEquals(1, eval("(eval (stf))"));
    }
    
    
    @Test
    public void testDefRecursive() {
        pre("""
            (def rec 
        		(fn* ([a] a) 
        			 ([a b] (rec b))))
            """);
        assertEquals("abc", eval("(rec 1 \"abc\")"));
    }
    
    @Test
    public void testDefFinalRecursive() {
        pre("""
            (def ^:final rec 
		        		(fn* ([a] a) 
		        			 ([a b] (rec b))))
		            """);
        assertEquals("abc", eval("(rec 1 \"abc\")"));
    }

    @Test
    public void testDefRefNonLocal() {
        pre("""
            (def someval "1")
            """);
        assertEquals("1", eval("(eval someval)"));
    }

    @Test
    public void testDefCallFn() {
        pre("""
            (def dome (fn* [a] a))
            """);
        assertEquals("1", eval("""
        					   (eval (dome "1"))
        					   """));
    }
    
    @Test
    public void testDefCallFnEquals() {
        pre("""
            (def ift (fn* [a] (if (= a "stuff") "foo" "bar")))
            """);
        assertEquals("foo", eval("(eval (ift \"stuff\"))"));
        assertEquals("bar", eval("(eval (ift \"notstuff\"))"));
    }
    
    @Test
    public void testNativeMultiType() {
        assertSeqEquals("Cons failed", new Cons(true), eval("(cons true nil)"));
    }
    
    @Test
    public void testDefNativeMultiTypeCompile() {
        pre("""
            (def dnmtp (fn* [a] (cons a nil)))
            """);
        assertSeqEquals("Compiled Cons", new Cons("foo"), eval("(dnmtp \"foo\")"));
    }
    
    @Test
    public void testDefInt() {
        pre("""
            (def letint (fn* [a] (let* [b 1] b)))
            """);
        assertEquals("Compiled Int", 1, eval("(eval (letint 1))"));
    }
    
    @Test
    public void testDefNativeMultiTypeDoubleCons() {
        pre("""
            (def dcons (fn* [a] (cons "foo" (cons a nil))))
            """);
        assertSeqEquals("Double cons not equals", new Cons("foo", new Cons("bar")), eval("(dcons \"bar\")"));
        assertSeqEquals("Double cons not equals", new Cons("foo", new Cons(12)), eval("(dcons 12)"));
    }
    
    @Test
    public void testMethodRelink() {
        pre("""
            (def dorelink (fn* [a b] (cons a b)))
            """);
        assertSeqEquals("result not equal", new Cons("baz", new Cons("bat")), 
        		eval("(dorelink \"baz\" (cons \"bat\" nil))"));
        assertSeqEquals("result not equal", new Cons("bar"), 
        		eval("(dorelink \"bar\" nil)"));
    }
    
    @Test
    public void testMethodRelinkSeq() {
        pre("""
            (def dorelink (fn* [a] (seq a)))
            """);
        assertSeqEquals("result not equal", ISeq.of(1, 2), 
                eval("(dorelink [1 2])"));
        assertSeqEquals("result not equal", ISeq.of('a', 'b', 'c'), 
                eval("(dorelink \"abc\")"));
    }
    
    @Test
    public void testMethodFunctionPointer() {
        pre("""
                (def fnfirst (fn* [f a] (f a)))
                """);

        assertEquals("a", eval("(eval (fnfirst first (cons \"a\" nil)))"));
        assertEquals("b", eval("(eval (fnfirst second (cons \"a\" (cons \"b\" nil))))"));
    }
    
    @Test
    public void testMethodInlineFunctionPointer() {
        pre("""
    		(def ffn (fn* [] ((fn* [a] a) "foobar")))
            """);
        assertEquals("foobar", eval("(eval (ffn))"));
    }
    
    @Test
    public void testMethodInlineFunctionPointerRef() {
        pre("""
            (def somestr "foobar")
            (def mifpr (fn* [a] a))
            (def mipr (fn* [a] (a somestr)))
            """);
        assertEquals("foobar", eval("(eval (mipr mifpr))"));
    }
    
    @Test
    public void testMethodVector() {
        pre("(def comp-vec (fn* [a b] (vector a b)))");
        assertEquals(PersistentVector.createArr(1, 2), eval("(comp-vec 1 2)"));
    } 
    
    @Test
    public void testMethodVectorMixed() {
        pre("(def comp-vec (fn* [a] (vector a \"foo\")))");
        assertEquals(PersistentVector.createArr(1, "foo"), eval("(comp-vec 1)"));
    } 
    
    @Test
    public void testMethodVectorLiteral() {
        pre("(def retlit (fn* [] [1 2]))");
        assertEquals(PersistentVector.createArr(1, 2), eval("(retlit)"));
    }
    
    @Test
    public void testMethodVectorLiteralMixed() {
        pre("(def retlit (fn* [] [1 :idk]))");
        assertEquals(PersistentVector.createArr(1, Keyword.of(null, "idk")), eval("(retlit)"));
    }
    
    @Test
    public void testMethodVectorLiteralMixedLet() {
        pre("(def retlit (fn* [] (let* [a 1] [a :idk])))");
        assertEquals(PersistentVector.createArr(1, Keyword.of(null, "idk")), eval("(retlit)"));
    }
    
    @Test
    public void testMethodVectorLiteralMixedArg() {
        pre("(def retlit (fn* [a] [a :idk]))");
        assertEquals(PersistentVector.createArr(1, Keyword.of(null, "idk")), eval("(retlit 1)"));
    }
    
    @Test
    public void testMethodVectorLiteralWithLocals() {
        pre("(def retlitloc (fn* [a] [1 a]))");
        assertEquals(PersistentVector.createArr(1, 2), eval("(retlitloc 2)"));
    }
    
    @Test
    public void testMethodMapLiteral() {
        pre("(def retlit (fn* [b] {:a b}))");
        assertEquals(PersistentMap.createArr(Keyword.of(null, "a"), _12L), eval("(retlit 12)"));
    }
    
    @Test
    public void testMethodMapLiteralTwo() {
        pre("(def retlit (fn* [] {:a :b}))");
        assertEquals(Keyword.of(null, "b"), eval("(:a (retlit))"));
    }
    
    @Test
    public void testMethodMapLiteralKeyword() {
        pre("(def retlit (fn* [] (:a {:a :b})))");
        assertEquals(Keyword.of(null, "b"), eval("(retlit)"));
    }
    
    @Test
    public void testMethodMapLiteralBase() {
        pre("(def retlit (fn* [b] ({12 :a} b)))");
        assertEquals(Keyword.of(null, "a"), eval("(retlit 12)"));
    }

    @Test
    public void testMethodPrimitvie() {
        pre("""
                (defn pp [^long a] (+ a 5))
                """);
        assertEquals(8L, eval("(pp 3)"));
    }

    @Test
    public void testMethodClosure() {
        pre("""
                (def pp (fn* [a]
                	(fn* [b] (str a b))))
                """);
        assertEquals("foobar", eval("(eval ((pp \"foo\") \"bar\"))"));
    }
    
    @Test
    public void testMethodClosureAnno() {
        pre("""
                (def pp (fn* [^String a]
                    (fn* [b] (str a b))))
                """);
        assertEquals("foobar", eval("(eval ((pp \"foo\") \"bar\"))"));
    }
    
    @Test
    public void testMethodClosureThree() {
        pre("""
                (def pp (fn* [a]
		                	(fn* [b]
		                		(fn* [c] (str a b c)))))
                """);
        assertEquals("foobarbaz", eval("(eval (((pp \"foo\") \"bar\") \"baz\"))"));
    }
    
    @Test
    public void testMethodVectorInMethodLiteral() {
        pre("""
            (def mviml (fn* [a] a))
            """);
        assertEquals(PersistentVector.createArr(1, 2), eval("(eval (mviml [1 2]))"));
    }
    
    @Test
    public void testVarargs() {
    	pre("""
                (def vmultiarity (fn*
    				            	([a & b] b)))
            	(def vma1 (fn* [a] (vmultiarity a)))
            	(def vma2 (fn* [a b] (vmultiarity a b)))
    			(def vma3 (fn* [a b c] (vmultiarity a b c)))
                """);

    	
        assertSeqEquals("eval size=1", ISeq.EMPTY, eval("(vmultiarity 1)"));
        assertSeqEquals("eval size=2", ISeq.of(2), eval("(vmultiarity 1 2)"));
        assertSeqEquals("eval size=3", ISeq.of(2, 3), eval("(vmultiarity 1 2 3)"));
        
        assertSeqEquals("compile size=1", ISeq.EMPTY, eval("(vma1 1)"));
        assertSeqEquals("compile size=2", ISeq.of(2), eval("(vma2 1 2)"));
        assertSeqEquals("compile size=3", ISeq.of(2, 3), eval("(vma3 1 2 3)"));    	
    }
    
    @Test
    public void testVarargsFinal() {
    	pre("""
                (def ^:final vmultiarity (fn*
    				            	([a & b] b)))
            	(def vma1 (fn* [a] (vmultiarity a)))
            	(def vma2 (fn* [a b] (vmultiarity a b)))
    			(def vma3 (fn* [a b c] (vmultiarity a b c)))
                """);

    	
        assertSeqEquals("eval size=1", ISeq.EMPTY, eval("(vmultiarity 1)"));
        assertSeqEquals("eval size=2", ISeq.of(2), eval("(vmultiarity 1 2)"));
        assertSeqEquals("eval size=3", ISeq.of(2, 3), eval("(vmultiarity 1 2 3)"));
        
        assertSeqEquals("compile size=1", ISeq.EMPTY, eval("(vma1 1)"));
        assertSeqEquals("compile size=2", ISeq.of(2), eval("(vma2 1 2)"));
        assertSeqEquals("compile size=3", ISeq.of(2, 3), eval("(vma3 1 2 3)"));    	
    }
    
    private void assertSeqEquals(String msg, ISeq seq, Object eval) {
    	if (eval instanceof ISeq out) {
    		assertSeqEquals(msg, seq, out);
    	}
		
	}
    
    private void assertSeqEquals(String msg, ISeq seq, ISeq eval) {
    	if (seq == null) {
    		assertNull(msg, eval);
    		return;
    	}
    	assertNotNull(msg, eval);
    	assertEquals(msg, seq.first(), eval.first());
    	assertSeqEquals(msg, seq.next(), eval.next());
	}

	@Test
    public void testMultiArity() {
        pre("""
            (def multiarity (fn*
				            	([a] "one")
				            	([a b] "two")))
        	(def ma1 (fn* [a] (multiarity a)))
        	(def ma2 (fn* [a b] (multiarity a b)))
            """);
        
        // Evaluated
        assertEquals("one", eval("(multiarity 1)"));
        assertEquals("two", eval("(multiarity 1 2)"));
        
        // Compiled
        assertEquals("one", eval("(ma1 1)"));
        assertEquals("two", eval("(ma2 1 2)"));
    }
	
	@Test
    public void testMultiArityMixed() {
        pre("""
            (def multiarity (fn*
				            	([a] a)
				            	([a & b] (first b))))
        	(def ma1 (fn* [a] (multiarity a)))
        	(def ma2 (fn* [a b] (multiarity a b)))
            """);
        
        // Evaluated
        assertEquals("one", eval("(multiarity \"one\")"));
        assertEquals("two", eval("(multiarity \"one\" \"two\")"));
        
        // Compiled
        assertEquals(1, eval("(ma1 1)"));
        assertEquals(2, eval("(ma2 1 2)"));
    }
    
    @Test
    public void testKeyword() {
        pre("""
    		(def tkey (fn* [] :something))
            """);
        Keyword s = (Keyword) eval("(eval (tkey))");
        assertEquals(Keyword.of(null, "something"), s);
    }
    
    @Test
    public void testKeywordStatic() {
        pre("""
    		(def ^:final tkey (fn* [] :something))
            """);
        Keyword s = (Keyword) eval("(eval (tkey))");
        assertEquals(Keyword.of(null, "something"), s);
    }
    
    @Ignore
    @Test
    public void testMacro() {
        pre("""
    		(defmacro unless [pred a b]
        		`(if (not ~pred) ~a ~b))
            """);
        
    }
    
    @Test
    public void testEval() throws Throwable {
        String s = "(eval \"a\")";
        assertEquals("a", Compiler.evaluate(new CompilerState(), first(parse(s))));
    }

    
	@Test
    public void testDoBranches() {
        pre("""
        	(def doiftest (fn* [] 
				            (do 
				            	(prn "a")
				            	(if true
				            		(prn "b")
				            		(prn "c"))
				            	12
				            )))
            """);
        
        // Compiled
        assertEquals(12, eval("(eval (doiftest))"));
    }
    
	@Test
	public void testConj() {
		pre("""
			(def tcon (fn* [] (conj [] :a)))
			""");
		assertEquals(PersistentVector.createArr(Keyword.of(null, "a")), 
				eval("(eval (tcon))"));
	}
	@Test
	public void testConjFour() {
		pre("""
			(def tcon (fn* [] (conj [] 1 2 3 4)))
			""");
		assertEquals(PersistentVector.createArr(1, 2, 3, 4), 
				eval("(eval (tcon))"));
	}
	
    private static PersistentList parse(String string) {
        PileParser p = new PileParser();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8))) {
            return p.parse(bais, "<stdin>");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
