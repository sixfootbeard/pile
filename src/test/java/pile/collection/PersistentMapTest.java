package pile.collection;

import static org.junit.Assert.*;

import java.util.AbstractMap;
import java.util.Map.Entry;

import org.junit.Test;

import pile.core.ISeq;

public class PersistentMapTest {

    @Test
    public void test() {
        PersistentHashMap<String, Integer> map = new PersistentHashMap<>();

        map = map.assoc("foo", 1);
        map = map.assoc("bar", 11);

        assertEquals(1, map.get("foo").intValue());
        assertEquals(11, map.get("bar").intValue());
        
        ISeq<Entry<String,Integer>> seq = map.seq();
        assertEquals(entry("bar", 11), seq.first());
        assertEquals(entry("foo", 1), seq.next().first());
        assertEquals(null, seq.next().next());
    }

    private Entry<String, Integer> entry(String string, int i) {
        return new AbstractMap.SimpleImmutableEntry<>(string, i);
    }

}
