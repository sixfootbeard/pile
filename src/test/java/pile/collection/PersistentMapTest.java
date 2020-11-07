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

import static org.junit.Assert.*;
import static pile.compiler.Helpers.*;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.junit.Test;

import com.google.common.collect.Collections2;

import pile.core.ISeq;

public class PersistentMapTest {
	
	// Allow hash/eq control
	public static class EqHash {
		private String term;
		private int hash;

		public EqHash(String term, int hash) {
			super();
			this.term = term;
			this.hash = hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof EqHash eh) {
				return this.term.equals(eh.term);
			}
			throw error("bad usage: " + obj);
		}

		@Override
		public int hashCode() {
			return hash;
		}
	}

    @Test
    public void test() {
        PersistentHashMap<String, Integer> map = new PersistentHashMap<>();

        map = map.assoc("foo", 1);
        map = map.assoc("bar", 11);

        assertEquals(1, map.getValue("foo").intValue());
        assertEquals(11, map.getValue("bar").intValue());
        
        ISeq<Entry<String,Integer>> seq = map.seq();
        assertEquals(entry("bar", 11), seq.first());
        assertEquals(entry("foo", 1), seq.next().first());
        assertEquals(null, seq.next().next());
    }
    
    @Test
    public void testNull() {
        PersistentHashMap<String, Integer> map = new PersistentHashMap<>();

        map = map.assoc(null, 1);
        map = map.assoc("bar", 11);

        assertEquals(1, map.getValue(null).intValue());
        assertEquals(11, map.getValue("bar").intValue());
        
        ISeq<Entry<String,Integer>> seq = map.seq();
        assertEquals(entry(null, 1), seq.first());
        assertEquals(entry("bar", 11), seq.next().first());
        assertEquals(null, seq.next().next());
    }
    
    @Test
    public void testCollision() {
    	EqHash one = new EqHash("one", 1);
    	EqHash two = new EqHash("two", 1);
    	
    	PersistentHashMap<EqHash, String> map = PersistentHashMap.empty();
    	map = map.assoc(one, "one");
    	map = map.assoc(two, "two");
    	
    	assertEquals(2, map.count());
    	assertEquals("one", map.get(one));
    	assertEquals("two", map.get(two));
    }
    
    
    @Test
    public void testFull() {
    	var start = (1 << 31) >>> 6;
    	PersistentHashMap<EqHash, String> map = PersistentHashMap.empty();
    	for (int i = 0; i < 32; ++i) {
    		EqHash eq = new EqHash(Integer.toString(i), start++);
    		map = map.assoc(eq, eq.term);
    	}
    	
    	assertEquals(32, map.count());
    }
    
    @Test
    public void testEmptyIterator() {
    	PersistentHashMap<Object, Object> empty = new PersistentHashMap<>();
    	Iterator<Entry<Object, Object>> it = empty.entrySet().iterator();
    	assertFalse(it.hasNext());
    }
    
    @SuppressWarnings("unused")
	@Test
    public void testAll() {
        List<Entry<String, Integer>> all = List.of(entry(null, 0), entry("one", 1), entry("two", 2), entry("three", 3),
                entry("four", 4), entry("five", 5));
    	
    	var permutations = Collections2.permutations(all);
    	
    	for (var perm : permutations) {
    		PersistentMap<String, Integer> map = PersistentMap.empty();
    		List<Entry<String, Integer>> added = new ArrayList<>();
    		for (var e : perm) {
    			map = map.assoc(e.getKey(), e.getValue());
    			added.add(e);
    			checkAll(map, added);
    		}
    		var toRemove = Collections2.permutations(perm);
    		for (var removeList : toRemove) {
    		    PersistentMap<String, Integer> remStart = map;
    			List<Entry<String, Integer>> current = new ArrayList<>(perm);
    			for (var remove : removeList) {
    				remStart = remStart.dissoc(remove.getKey());
    				current.remove(remove);
    				try {
    					checkAll(remStart, current);
    				} catch (Exception e) {
    					String msg = "Failed " + perm + " remove: " + removeList + " ~ removing:" + remove;
    					throw new RuntimeException(msg, e);
    				}
    			}
    		}
    	}
    }

    private void checkAll(PersistentMap<String, Integer> map, List<Entry<String, Integer>> added) {
		for (var e : added) {
			assertEquals(added.size(), map.count());
			assertTrue(map.containsKey(e.getKey()));
			assertEquals(e.getValue(), map.get(e.getKey()));
		}
		map.seq();
		map.toString();
		map.hashCode();
		
	}

	private Entry<String, Integer> entry(String string, int i) {
        return new AbstractMap.SimpleImmutableEntry<>(string, i);
    }

}
