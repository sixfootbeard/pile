package pile.collection.adapter;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import pile.collection.Associative;
import pile.collection.Counted;
import pile.core.Seqable;
import pile.util.Pair;

public abstract class JavaImmutableMap<K, V> extends AbstractMap<K, V>
        implements Associative<K, V>, Counted, Seqable<Entry<K, V>> {
    
    @Override
    public Set<Entry<K, V>> entrySet() {
        return new AbstractSet<Map.Entry<K,V>>() {

            @Override
            public Iterator<Entry<K, V>> iterator() {
                return JavaImmutableMap.this.seq().iterator();
            }

            @Override
            public int size() {
                return JavaImmutableMap.this.count();
            }
        };
    }
   

}
