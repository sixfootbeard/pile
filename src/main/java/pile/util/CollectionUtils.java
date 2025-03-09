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
package pile.util;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import pile.core.ISeq;

public class CollectionUtils {

    public static <K, V> Collector<Pair<K, V>, Map<K, List<V>>, Map<K, List<V>>> toMultiMap() {
        
        return new Collector<Pair<K,V>, Map<K,List<V>>, Map<K,List<V>>>() {

            @Override
            public Supplier<Map<K, List<V>>> supplier() {
                return HashMap::new;
            }

            @Override
            public BiConsumer<Map<K, List<V>>, Pair<K, V>> accumulator() {
                return (map, pair) -> {
                    K key = pair.left();
                    V value = pair.right();
                    map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
                };
            }

            @Override
            public BinaryOperator<Map<K, List<V>>> combiner() {
                BiConsumer<Map<K, List<V>>, Pair<K, V>> acc = accumulator();
                return (l, r) -> {
                    for (var entry : l.entrySet()) {
                        var k = entry.getKey();
                        for (var v : entry.getValue()) {
                            acc.accept(r, new Pair<>(k, v));
                        }
                    }
                    return r;
                };
            }

            @Override
            public Function<Map<K, List<V>>, Map<K, List<V>>> finisher() {
                return Function.identity();
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Set.of();
            }
        
        };
    
    }

    public static <I, O, C extends Collection<O>> C map(Collection<I> c, Function<I, O> fn, Supplier<C> newColl) {
        return c.stream().map(fn).collect(Collectors.toCollection(newColl));
    }

    public static <I, O> O[] mapA(Collection<I> c, Function<I, O> fn, IntFunction<O[]> generator) {
        return c.stream().map(fn).toArray(generator);
    }

    public static <I, O> O[] mapA(I[] c, Function<I, O> fn, IntFunction<O[]> generator) {
        return Arrays.stream(c).map(fn).toArray(generator);
    }

    public static <I, O> List<O> mapL(Collection<I> c, Function<I, O> fn) {
        return map(c, fn, ArrayList::new);
    }

    public static <I, O> List<O> mapF(Collection<I> c, Function<? super I, O> fn, Predicate<? super O> filter) {
        return c.stream().map(fn).filter(filter).toList();
    }

    public static <I> List<I> mapF(Collection<? extends I> c, Predicate<? super I> filter) {
        return mapF(c, Function.identity(), filter);
    }
    
    public static <I, O> List<O> mapL(Iterable<I> c, Function<I, O> fn, Predicate<O> filter) {
        var it = StreamSupport.stream(c.spliterator(), false);
        return it.map(fn).filter(filter).toList();
    }
    
    public static <K, V, O> Map<K, O> mapV(Map<K, V> input, Function<V, O> valFn) {
        return input.entrySet().stream()
                .collect(Collectors.toMap(Entry::getKey, valFn.compose(Entry::getValue)));
    }
    
    public static <K, V, O> NavigableMap<K, O> mapVN(Map<K, V> input, Function<V, O> valFn) {
        return input.entrySet().stream().collect(
                Collectors.toMap(Entry::getKey, valFn.compose(Entry::getValue), (oldv, newv) -> newv, TreeMap::new));
    }
    
    public static <K, V, KO, VO> Map<KO, VO> mapKV(Map<K, V> input, BiFunction<K, V, Entry<KO, VO>> fn) {
        return input.entrySet().stream()
                .map(k -> fn.apply(k.getKey(), k.getValue()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }
    
    public static <K, V, KO, VO> NavigableMap<KO, VO> mapKVN(Map<K, V> input, BiFunction<K, V, Entry<KO, VO>> fn) {
        return input.entrySet().stream()
                .map(k -> fn.apply(k.getKey(), k.getValue()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (oldv, newv) -> newv, TreeMap::new));
    }
    
    public static <I> List<I> mapFilterL(Iterable<I> c, Predicate<I> filter) {
        var it = StreamSupport.stream(c.spliterator(), false);
        return it.filter(filter).toList();
    }

    public static <K, V, OK, OV> Map<OK, OV> map(Map<K, V> in, BiFunction<K, V, Entry<OK, OV>> fn) {
        return in.entrySet().stream().map(entry -> fn.apply(entry.getKey(), entry.getValue()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    public static <K, V> Entry<K, V> entry(K k, V v) {
        return new AbstractMap.SimpleImmutableEntry<>(k, v);
    }
    
    public static <V> Iterator<V> reverseIterator(List<V> source) {
        return new Iterator<V>() {
        
            int idx = source.size();

            @Override
            public boolean hasNext() {
                return idx > 0;
            }

            @Override
            public V next() {
                if (! hasNext()) {
                    throw new NoSuchElementException();
                }
                --idx;
                return source.get(idx);
            }
        };
    }
    
    public static <T> void reorder(int[] indexes, T[] source, T[] dest) {
        for (int i = 0; i < indexes.length; ++i) {
            dest[i] = source[indexes[i]];
        }        
    }
    
    public static <T> int[] indexSort(T[] source, Comparator<T> cmp) {
        record IndexItem<T> (int idx, T item) {};

        List<IndexItem<T>> withIndex = new ArrayList<>();

        int idx = 0;
        for (T t : source) {
            withIndex.add(new IndexItem<>(idx, t));
            ++idx;
        }

        Collections.sort(withIndex, Comparator.comparing(IndexItem::item, cmp));

        int[] indexes = new int[withIndex.size()];
        int i = 0;
        for (IndexItem<T> indexItem : withIndex) {
            indexes[i] = indexItem.idx();
            ++i;
        }
        return indexes;
    }
    
    public static <C extends Collection<E>, E> C colCopy(C source, UnaryOperator<E> elemClone, Supplier<C> cons) {
        C out = cons.get();
        for (var e : source) {
            out.add(elemClone.apply(e));
        }
        return out;
    }
    
    public static <K, V> Optional<V> mget(Map<K, V> m, K k) {
        if (m.containsKey(k)) {
            return Optional.of(m.get(k));
        } else {
            return Optional.empty();
        }
    }
    
    public static <K> Optional<K> sget(Set<K> m, K k) {
        if (m.contains(k)) {
            return Optional.of(k);
        } else {
            return Optional.empty();
        }
    }
    
    public static Iterator<Pair<Object, Object>> pairIter(ISeq src) {
        List<Pair<Object, Object>> out = new ArrayList<>();
        pairEach(src, (l, r) -> out.add(new Pair<>(l, r)));
        return out.iterator();
    }
    
    public static void pairEach(ISeq src, BiConsumer cons) {
        while (src != null) {
            Object first = src.first();
            ISeq next = src.next();
            if (next == null) {
                throw new IllegalArgumentException("Should be mod 2");
            }
            Object second = next.first();
            cons.accept(first, second);
            src = next.next();
        }
    }
    
    public static <C> boolean all(Collection<C> col, Predicate<C> p) {
        return col.stream().allMatch(p);
    } 


    public static <C> boolean any(Collection<C> col, Predicate<C> p) {
        return col.stream().anyMatch(p);
    } 
}
