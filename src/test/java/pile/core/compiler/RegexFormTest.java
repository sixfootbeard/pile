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

import pile.collection.PersistentArrayVector;
import pile.core.AbstractTest;

public class RegexFormTest extends AbstractTest {
	
	@Test
	public void test() {
		pre("""
				(def stuff (fn* [a] (let* [p #"^([123])"
							 			   m (re-matcher p a)]
											  (re-find m))))
				""");
		PersistentArrayVector<String> p = (PersistentArrayVector) eval("(eval (stuff \"3\"))");
		assertEquals(1, p.count());
		assertEquals("3", p.get(0));
	}
	
	@Test(expected = RuntimeException.class)
	public void testWrongType() {
		pre("""
				(def stuff (fn* [a] (let* [p #"^([123])"
							 			   m (re-matcher p a)]
											  (re-find m))))
				""");
		PersistentArrayVector<String> p = (PersistentArrayVector) eval("(eval (stuff 12))");
	}

}
