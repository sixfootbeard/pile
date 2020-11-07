package pile.collection;

import pile.core.PCall;
import pile.core.indy.CallableLink;
import pile.util.Pair;

public interface Associative<K, V> extends PCall {


    Pair<K, V> entryAt(K key);

    Associative<K, V> assoc(K key, V val);

    Associative<K, V> dissoc(K key, V val);

    Associative<K, V> dissoc(K key);

    default V get(K key, V ifNone) {
        Pair<K,V> entryAt = entryAt(key);
        if (entryAt == null) {
            return ifNone;
        } else {
            return entryAt.right();
        }
    }
    
    default boolean containsKey(K key) { 
        return entryAt(key) != null;
    }

    @CallableLink
    default V get(K key) {
        return get(key, null);
    }

    @Override
    default Object invoke(Object... args) {
        if (args.length > 1) {
            throw new IllegalArgumentException("Wrong number of arguments");
        }
        Object first = args[0];
        // TODO how to handle key type w/o private class getter?
        return get((K) first);
    }

}