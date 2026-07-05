package pile.collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

import org.junit.Test;

import pile.core.ISeq;

public class PersistentPriorityQueueTest {

    @Test
    public void testAscending() {
        PersistentPriorityQueue<Integer> pq = new PersistentPriorityQueue<Integer>(Comparator.naturalOrder());

        for (int i = 0; i < 100; ++i) {
            pq = pq.conj(i);
        }

        assertEquals(100, pq.count());
        PersistentPriorityQueue<Integer> remaining = pq;
        for (int i = 0; i < 100; ++i) {
            Integer min = remaining.min();
            assertEquals(i, min.intValue());
            remaining = remaining.popMin();
        }
    }
    
    @Test
    public void testEmpty() {
        PersistentPriorityQueue<Integer> pq = new PersistentPriorityQueue<Integer>(Comparator.naturalOrder());

        assertEquals(0, pq.count());
        try {
            pq.min();
            fail();
        } catch (NoSuchElementException e) {
            // pass
        }
        
        ISeq<Integer> seq = pq.seq();
        assertEquals(ISeq.EMPTY, seq);
        assertEquals("[]", pq.toString());
    }
    
    @Test
    public void testRandom() {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 100; ++i) {
            items.add(i);
        }
        
        PersistentPriorityQueue<Integer> pq = new PersistentPriorityQueue<Integer>(Comparator.naturalOrder());
        Collections.shuffle(items, new Random(394857298L));
        for (var i : items) {
            pq = pq.conj(i);
        }

        assertEquals(100, pq.count());
        PersistentPriorityQueue<Integer> remaining = pq;
        for (int i = 0; i < 100; ++i) {
            Integer min = remaining.min();
            assertEquals(i, min.intValue());
            remaining = remaining.popMin();
        }
    }
    
    @Test
    public void testDescending() {
        PersistentPriorityQueue<Integer> pq = new PersistentPriorityQueue<Integer>(Comparator.naturalOrder());

        for (int i = 0; i < 100; ++i) {
            pq = pq.conj(99 - i);
        }

        assertEquals(100, pq.count());
        PersistentPriorityQueue<Integer> remaining = pq;
        for (int i = 0; i < 100; ++i) {
            Integer min = remaining.min();
            assertEquals(i, min.intValue());
            remaining = remaining.popMin();
        }
    }
    
    @Test
    public void testSeq() {
        PersistentPriorityQueue<Integer> pq = new PersistentPriorityQueue<Integer>(Comparator.naturalOrder());

        for (int i = 0; i < 100; ++i) {
            pq = pq.conj(i);
        }

        ISeq<Integer> seq = pq.seq();
        for (int i = 0; i < 100; ++i) {
            Integer seen = seq.first();
            assertEquals(i, seen.intValue());
            seq = seq.next();
        }
    }

    @Test
    public void testString() {
        PersistentPriorityQueue<Integer> pq = new PersistentPriorityQueue<Integer>(Comparator.naturalOrder());

        for (int i = 0; i < 5; ++i) {
            pq = pq.conj(i);
        }

        assertEquals("[4, 0, 2, 1, 3]", pq.toString());
    }

    @Test
    public void testIter() {
        PersistentPriorityQueue<Integer> pq = new PersistentPriorityQueue<Integer>(Comparator.naturalOrder());

        for (int i = 0; i < 5; ++i) {
            pq = pq.conj(i);
        }

        List<Integer> elems = new ArrayList<>();
        pq.iterator().forEachRemaining(elems::add);

        assertEquals(List.of(4, 0, 2, 1, 3), elems);
    }

    @Test
    public void testInto() {
        PersistentPriorityQueue<Integer> lower = new PersistentPriorityQueue<Integer>(Comparator.naturalOrder());
        for (int i = 0; i < 100; ++i) {
            lower = lower.conj(i);
        }

        PersistentPriorityQueue<Integer> upper = new PersistentPriorityQueue<Integer>(Comparator.naturalOrder());
        for (int i = 100; i < 200; ++i) {
            upper = upper.conj(i);
        }

        PersistentPriorityQueue<Integer> both = lower.into(upper);

        PersistentPriorityQueue<Integer> remaining = both;
        for (int i = 0; i < 200; ++i) {
            assertEquals(200 - i, remaining.count());
            Integer min = remaining.min();
            assertEquals(i, min.intValue());
            remaining = remaining.popMin();
        }
        assertEquals(0, remaining.count());
        assertTrue(remaining.isEmpty());
    }
    
    @Test
    public void testIntoColl() {
        PersistentPriorityQueue<Integer> lower = new PersistentPriorityQueue<Integer>(Comparator.naturalOrder());
        for (int i = 0; i < 100; ++i) {
            lower = lower.conj(i);
        }

        List<Integer> alist = new ArrayList<>();
        for (int i = 100; i < 200; ++i) {
            alist.add(i);
        }
        
        PersistentPriorityQueue<Integer> both = lower.into(alist);

        PersistentPriorityQueue<Integer> remaining = both;
        for (int i = 0; i < 200; ++i) {
            assertEquals(200 - i, remaining.count());
            Integer min = remaining.min();
            assertEquals(i, min.intValue());
            remaining = remaining.popMin();
        }
        assertEquals(0, remaining.count());
        assertTrue(remaining.isEmpty());
    }

}
