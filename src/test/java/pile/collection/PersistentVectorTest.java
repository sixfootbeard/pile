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
package pile.collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static pile.nativebase.NativeCore.conj;

import java.util.Iterator;

import org.junit.Test;

import pile.core.Conjable;

public class PersistentVectorTest {
	
	@Test
	public void testEmptyIterator() {
		Iterator<Object> iterator = PersistentArrayVector.empty().iterator();
		assertFalse(iterator.hasNext());
	}

    @Test
    public void test() {
        PersistentArrayVector<String> vec = PersistentArrayVector.empty();
        vec = vec.push("foo");
        assertEquals("foo", vec.get(0));
    }

    @Test
    public void testMultiLevel() {
        PersistentArrayVector<String> vec = PersistentArrayVector.empty();
        for (int i = 0; i < 20; ++i) {
            vec = vec.push(Integer.toString(i));    
        }
        assertEquals(20, vec.count());
        assertEquals("14", vec.get(14));
        for (int i = 0; i < 20; ++i) {
            vec = vec.pop();
        }
        assertEquals(0, vec.count());
    }
    
    @Test
    public void testThreeLevel() {
        PersistentArrayVector<String> vec = PersistentArrayVector.empty();
        int max = 16*16+2;
        for (int i = 0; i < max; ++i) {
            vec = vec.push(Integer.toString(i));    
        }
        assertEquals(max, vec.count());
        assertEquals("44", vec.get(44));
        for (int i = 0; i < max; ++i) {
            vec = vec.pop();
        }
        assertEquals(0, vec.count());
    }
    
    @Test
    public void testConj() {
        PersistentVector<String> vec = PersistentArrayVector.empty();
        
        Conjable c = vec;
        
        for (int i = 0; i < 100; ++i) {
            c = conj(c, i);
        }
        
        
    }
    
}
