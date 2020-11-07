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

import static pile.compiler.Helpers.*;

import pile.core.PCall;
import pile.core.exception.PileException;
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

    default V getValue(K key) {
        return get(key, null);
    }

    @Override
    default Object invoke(Object... args) throws Throwable {
        ensureEx(args.length == 1, PileException::new, () -> "Wrong number of args. Expected=1, Saw=" + args.length);
        Object first = args[0];
        // TODO how to handle key type w/o private class getter?
        return getValue((K) first);
    }

}