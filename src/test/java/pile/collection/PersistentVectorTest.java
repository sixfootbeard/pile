package pile.collection;

import static org.junit.Assert.*;

import org.junit.Test;

public class PersistentVectorTest {

    @Test
    public void test() {
        PersistentVector<String> vec = PersistentVector.empty();
        vec = vec.push("foo");
        assertEquals("foo", vec.get(0));
    }

    @Test
    public void testMultiLevel() {
        PersistentVector<String> vec = PersistentVector.empty();
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
        PersistentVector<String> vec = PersistentVector.empty();
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
    
}
