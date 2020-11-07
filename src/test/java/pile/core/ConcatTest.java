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
package pile.core;

import static org.junit.Assert.*;

import org.junit.Test;

import pile.collection.PersistentList;
import pile.compiler.Helpers;

public class ConcatTest {

	@Test
	public void test() {
		var cc = Helpers.concat(ISeq.of(1), ISeq.of(2));
		assertTrue(ISeq.equals(PersistentList.createArr(2, 1).seq(), cc));
	}
	
	@Test
	public void testFour() {
		var cc = Helpers.concat(ISeq.of(1, 2), ISeq.of(3, 4));
		assertTrue(ISeq.equals(PersistentList.reversed(1, 2, 3, 4).seq(), cc));
	}
	
	@Test
	public void testThree() {
		var cc = Helpers.concat(ISeq.of(1), ISeq.of(2, 3));
		assertTrue(ISeq.equals(PersistentList.reversed(1, 2, 3).seq(), cc));
	}
	
	@Test
	public void testThreeEnd() {
		var cc = Helpers.concat(ISeq.of(1, 2), ISeq.of(3));
		assertTrue(ISeq.equals(PersistentList.reversed(1, 2, 3).seq(), cc));
	}
	
	@Test
	public void testEmptyFirst() {
		var cc = Helpers.concat(null, ISeq.of(2));
		assertTrue(ISeq.equals(PersistentList.createArr(2).seq(), cc));
	}
	
	@Test
	public void testEmptyNext() {
		var cc = Helpers.concat(ISeq.of(1), (ISeq) null);
		assertTrue(ISeq.equals(PersistentList.createArr(1).seq(), cc));
	}

}
