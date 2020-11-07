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
package pile.core.indy;

import static java.lang.invoke.MethodType.*;
import static org.junit.Assert.*;

import java.lang.invoke.MethodType;

import org.junit.Before;
import org.junit.Test;

import pile.compiler.typed.TypeMatcher;

public class TypeMatcherTest {

	MethodType type;
	TypeMatcher matcher;

	@Before
	public void setup() {
	}

	@Test
	public void test() {
		type = methodType(Object.class, String.class);
		matcher = new TypeMatcher(type);
		assertTrue(matcher.isCandidate(MethodType.methodType(Object.class, CharSequence.class)));
		assertTrue(matcher.isCandidate(MethodType.methodType(Object.class, String.class)));
		assertFalse(matcher.isCandidate(MethodType.methodType(Object.class, Integer.class)));
	}
	
	@Test
	public void testRev() {
		type = methodType(Object.class, String.class);
		matcher = new TypeMatcher(type);
		assertTrue(matcher.isCallableWithArgs(MethodType.methodType(Object.class, String.class)));
		assertTrue(matcher.isCallableWithArgs(MethodType.methodType(String.class, String.class)));
		assertTrue(matcher.isCallableWithArgs(MethodType.methodType(Object.class, Void.class)));
		assertTrue(matcher.isCallableWithArgs(MethodType.methodType(Void.class, Void.class)));
		assertFalse(matcher.isCallableWithArgs(MethodType.methodType(Object.class, Integer.class)));
	}
	
	   @Test
	    public void testRevRet() {
	        type = methodType(Number.class, CharSequence.class);
	        matcher = new TypeMatcher(type);
	        assertTrue(matcher.isCallableWithArgs(MethodType.methodType(Number.class, CharSequence.class)));
            assertTrue(matcher.isCallableWithArgs(MethodType.methodType(Number.class, String.class)));
            assertTrue(matcher.isCallableWithArgs(MethodType.methodType(Integer.class, String.class)));
            assertTrue(matcher.isCallableWithArgs(MethodType.methodType(Integer.class, CharSequence.class)));
	    }

	
	@Test
	public void testObject() {
		type = methodType(Object.class, String.class);
		matcher = new TypeMatcher(type);
		assertTrue(matcher.isCandidate(MethodType.methodType(Object.class, Object.class)));
	}
	
	@Test
	public void testSubType() {
		type = methodType(Object.class, Number.class);
		matcher = new TypeMatcher(type);
		assertFalse(matcher.isCandidate(MethodType.methodType(Object.class, Integer.class)));
	}

}
