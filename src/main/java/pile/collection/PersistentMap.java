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

import static org.objectweb.asm.Type.*;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import org.objectweb.asm.ConstantDynamic;

import pile.compiler.Constants;
import pile.compiler.form.CollectionLiteralForm;
import pile.core.Conjable;
import pile.core.ISeq;
import pile.core.PCall;
import pile.nativebase.NativeCore;

public abstract class PersistentMap<K, V> extends AbstractMap<K, V>
        implements PersistentCollection<Entry<K, V>>, Associative<K, V>, Conjable, FMap<PersistentMap> {
        
    protected static final int maxArrayMapSize = 8;

    protected static final ToIntFunction<Object> HASHER = NativeCore::hash;
    protected static final BiPredicate KEY_EQ = NativeCore::equals;
    protected static final BiPredicate VAL_EQ = NativeCore::equals;

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new AbstractSet<Map.Entry<K, V>>() {

            @Override
            public Iterator<Entry<K, V>> iterator() {
                return ISeq.iter(PersistentMap.this.seq()).iterator();
            }

            @Override
            public int size() {
                return PersistentMap.this.count();
            }
        };
    }

    @Override
    public abstract PersistentMap<K, V> withMeta(PersistentMap newMeta);

    @Override
    public PersistentMap<K, V> updateMeta(Function<PersistentMap, PersistentMap> update) {
        return this.updateMeta(update);
    }

    @Override
    public abstract PersistentMap<K, V> assoc(K key, V val);

    @Override
    public abstract PersistentMap<K, V> dissoc(K key);
    
    @Override
    public abstract PersistentMap<K, V> dissoc(K key, V val);    

    @Override
    public boolean containsKey(Object key) {
        return Associative.super.containsKey((K) key);
    }
    
    @Override
    public boolean acceptsArity(int arity) {
        return arity == 1;
    }

    @Override
    public V get(Object key) {
        var entry = entryAt((K) key);
        if (entry == null) {
            return null;
        } else {
            return entry.right();
        }
    }

    @Override
    public String toString() {
        if (count() == 0) {
            return "{}";
        }
        return super.toString();
    }

    @Override
    public Conjable conj(Object t) {
        ISeq seq = NativeCore.seq(t);
        Object key = seq.first();
        Object val = seq.next().first();
        Object empty = seq.next().next();
        if (empty != null) {
            throw new IllegalArgumentException("Conj size should be a pair");
        }
    
        return assocGeneric(key, val);
    }
    
    @Override
    public PersistentMap fmap(PCall tx) throws Throwable {
        // TODO Optimize this
        PersistentMap out = PersistentMap.empty();
        for (var entry : this.entrySet()) {
            Object res = tx.invoke(entry);
            // Result can be anything that is destructurable
            ISeq s = NativeCore.seq(res);
            var k = s.first();
            var v = s.next().first();
            
            out = out.assoc(k, v);
        }
        return out;
    }

    @Override
    public Optional<ConstantDynamic> toConst() {
        List<Object> parts = new ArrayList<>();
        for (Entry<?, ?> e : entrySet()) {
            Optional<?> key, val;
            key = Constants.toConstAndNull(e.getKey());
            val = Constants.toConstAndNull(e.getValue());
            
            if (key.isEmpty() || val.isEmpty()) {
                return Optional.empty();
            }
    
            parts.add(key.get());
            parts.add(val.get());
        }
        ConstantDynamic cform = new ConstantDynamic("map", getDescriptor(PersistentMap.class),
                CollectionLiteralForm.CONDY_BOOTSTRAP_HANDLE, parts.toArray());
        return Optional.of(cform);
    }
    
    

    // Method references don't like variadic targets, and we can't call the proper
    // array version as a variadic even though it's the same actual signature, go
    // figure.
    public static PersistentMap create(Object[] args) {
        var len = args.length;
        if (len == 0) {
            return EMPTY;
        } else if (len == 2) {
            return new SingleMap(args[0], args[1]);
        } else if (len < maxArrayMapSize) {
            return new PersistentArrayMap<>(args, EMPTY);
        } else {
            return createHashMap(Arrays.asList(args).iterator());
        }
    }

    public static PersistentMap createArr(Object... args) {
        return create(args);
    }

    public static PersistentMap fromIterable(Iterable result) {
        if (result instanceof Collection coll) {
            var len = coll.size();
            if (len == 0) {
                return EMPTY;
            } else if (len == 2) {
                var it = coll.iterator();
                return new SingleMap(it.next(), it.next());
            }
        }
        return createHashMap(result.iterator());
    }

    static PersistentMap createHashMap(Iterator it) {
        PersistentHashMap map = new PersistentHashMap<>();
        while (it.hasNext()) {
            var key = it.next();
            if (!it.hasNext()) {
                throw new IllegalArgumentException("Uneven number of map entries");
            }
            var val = it.next();
            map = map.assoc(key, val);
        }
        return map;
    }

    protected abstract Conjable assocGeneric(Object key, Object val);

    public static <K, V> PersistentMap<K, V> empty() {
        return EMPTY;
    }

    public static final PersistentMap EMPTY = new EmptyMap<>();

    public static <K, V> Map<K, V> from(Map<K, V> map) {
        PersistentMap<K, V> out = PersistentMap.EMPTY;
        for (var entry : map.entrySet()) {
            out = out.assoc(entry.getKey(), entry.getValue());
        }
        return out;
    }

}
