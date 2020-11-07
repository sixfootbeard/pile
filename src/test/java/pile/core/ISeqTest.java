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

import java.util.Iterator;

import org.junit.Test;

import pile.collection.PersistentArrayVector;
import pile.collection.PersistentVector;

public class ISeqTest {

    public static class TestSeq<T> extends AbstractSeq<T> {

        private final T first;
        private final ISeq<T> next;

        public TestSeq(T first, ISeq<T> next) {
            super();
            this.first = first;
            this.next = next;
        }

        @Override
        public T first() {
            return first;
        }

        @Override
        public ISeq<T> next() {
            return next;
        }
    }

    @Test
    public void testConcat() {
        ISeq<Integer> one = ISeq.single(1);
        ISeq<Integer> concat = one.concat(ISeq.single(2));
        assertEquals(1, concat.first().intValue());
        assertEquals(2, concat.next().first().intValue());
    }
    
    @Test
    public void testConcatMulti() {
        ISeq<Integer> concat = ISeq.of(1, 2).concat(ISeq.single(3));
        Iterator<Integer> it = ISeq.iter(concat).iterator();
        assertEquals(1, it.next().intValue());
        assertEquals(2, it.next().intValue());
        assertEquals(3, it.next().intValue());
        assertFalse(it.hasNext());        
    }
    
    @Test
    public void test() {
        TestSeq<Integer> one = new TestSeq<>(1, null);
        ISeq<Integer> concat = one.concat(new TestSeq<Integer>(2, null));
        assertEquals(1, concat.first().intValue());
        assertEquals(2, concat.next().first().intValue());
    }
    
    @Test
    public void testFlat() {
        TestSeq<PersistentVector<Integer>> one = new TestSeq<>(PersistentVector.createArr(3, 4), null);
        TestSeq<PersistentVector<Integer>> two = new TestSeq<>(PersistentVector.createArr(1, 2), one);
        ISeq<Integer> flatMap = two.flatMap(PersistentVector::seq);
        
        for (int i = 1; i <= 4; ++i) {
            assertEquals(i, flatMap.first().intValue());
            flatMap = flatMap.next();
        }
        
    }

}
