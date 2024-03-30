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
package pile.nativebase;

import static java.lang.invoke.MethodHandles.*;
import static java.util.Objects.*;
import static pile.compiler.Helpers.*;
import static pile.util.CollectionUtils.*;

import java.io.PrintStream;
import java.io.Reader;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Gatherer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import pile.collection.Associative;
import pile.collection.Counted;
import pile.collection.FMap;
import pile.collection.PersistentArrayVector;
import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.collection.PersistentSet;
import pile.collection.PersistentVector;
import pile.collection.SingleMap;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.Helpers;
import pile.compiler.form.SExpr;
import pile.compiler.specialization.StrCatSpecializer;
import pile.compiler.typed.FunctionalInterfaceAdapter;
import pile.core.AbstractSeq;
import pile.core.Atom;
import pile.core.Conjable;
import pile.core.Cons;
import pile.core.ConsSequence;
import pile.core.Coroutine;
import pile.core.Coroutine.CoroutineSync;
import pile.core.Hierarchy;
import pile.core.ISeq;
import pile.core.JavaMethod;
import pile.core.Keyword;
import pile.core.Metadata;
import pile.core.Named;
import pile.core.Namespace;
import pile.core.NativeValue;
import pile.core.PCall;
import pile.core.PileMethod;
import pile.core.Ref;
import pile.core.ReversibleSeq;
import pile.core.RuntimeRoot;
import pile.core.RuntimeRoot.ProtocolRecord;
import pile.core.Seqable;
import pile.core.SettableRef;
import pile.core.StandardLibraryLoader;
import pile.core.Streamable;
import pile.core.Symbol;
import pile.core.binding.Binding;
import pile.core.binding.BindingType;
import pile.core.binding.ImmutableBinding;
import pile.core.binding.NativeDynamicBinding;
import pile.core.indy.CallSiteType;
import pile.core.indy.CompilerFlags;
import pile.core.indy.PileMethodLinker;
import pile.core.log.LogLevel;
import pile.core.method.FunctionUtils;
import pile.core.method.LinkableMethod;
import pile.core.parse.ParserConstants;
import pile.core.parse.ParserResult;
import pile.core.parse.PileParser;
import pile.core.runtime.ArrayGetMethod;
import pile.nativebase.method.PileInvocationException;

/**
 * This class contains methods that will be available in "pile.core"
 * {@link Namespace}.
 * 
 * @see StandardLibraryLoader
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class NativeCore {

    private static final class RandomNumberGeneratorHolder {
        static final Random HOLDER = new Random();
    }

    private static final PersistentMap FINAL_META = PersistentMap.createArr(PileMethodLinker.FINAL_KEY, true);
    private static final AtomicLong SYM_SUFFIX = new AtomicLong();
    public static final Keyword RESOLVED_NS = Keyword.of("pile.core", "resolved-ns");
    public static final int LAST = Integer.MAX_VALUE;
    
    @PileDoc("Evaluates the provided syntax (not strings). See read for parsing strings into syntax.")
    public static Object eval(Object syntax) throws Throwable {
        return Compiler.evaluate(new CompilerState(), syntax);
    }
    
    @Precedence(0)
    @PileDoc("Reads a single piece of syntax from the provided string/reader.")
    public static Object read(String s) {
        return read_string(s);
    }
    
    @Precedence(1)
    @PileDoc("Reads a single piece of syntax from the provided string/reader.")
    public static Object read(Reader s) {
        var pr = PileParser.parseSingle(s);
        return pr.map(ParserResult::result).orElse(null);
    }
    
    @PileDoc("Reads a single piece of syntax from the provided string.")
    public static Object read_string(String s) {
        var pr = PileParser.parseSingle(s);
        return pr.result();
    }
    
    @PileDoc("Takes any number of arguments and just returns.")
    public static void pass(Object... ignored) {
        return;
    }
    
    @PileDoc("Provides a point to attach a breakpoint in java code.")
    public static void debug() {
        return;
    }
    
    @RenamedMethod("catch")
    public static void ncatch(Object... ignored) {
        throw new PileInvocationException("catch called outside of a try block");
    }
    
    @RenamedMethod("finally")
    public static void nfinally(Object... ignored) {
        throw new PileInvocationException("finally called outside a try block");
    }
    
    @PileDoc("Calls the provided function with the supplied arguments in which the last element is a sequence.")
    public static Object apply(Object fn, Object... args) throws Throwable {
        return ((PCall)fn).applyInvoke(args);
    }
    


    public static Object macroexpand(Object syntax) throws Throwable {
        // RETHINK scoping problems when compiled?
        return SExpr.macroExpand(new CompilerState(), NativeDynamicBinding.NAMESPACE.getValue(), syntax);
    }

    public static Object macroexpand1(Object syntax) throws Throwable {
        return SExpr.macroExpandOne(new CompilerState(), NativeDynamicBinding.NAMESPACE.getValue(), syntax);
    }

    @PileDoc("Creates a symbol with the provided name.")
    @Precedence(0)
    public static Symbol sym(String name) {
        return new Symbol(name);
    }

    @PileDoc("Creates a symbol with the provided namespace and name.")
    @Precedence(1)
    public static Symbol sym(String ns, String name) {
        return new Symbol(ns, name);
    }

    @PileDoc("Generates a new unique symbol.")
    @Precedence(1)
    public static Symbol gensym() {
        return gensym("P__");
    }

    @PileDoc("Generates a new unique symbol with the provided prefix.")
    @Precedence(LAST)
    public static Symbol gensym(String prefix) {
        requireNonNull(prefix, "Prefix may not be null");
        var s = prefix + SYM_SUFFIX.getAndIncrement();
        return sym(s);
    }
    
    @PileDoc("Returns the annotated type on the syntax.")
    public static Object annotated_type(Object o) {        
        return switch (o) {
            case Metadata m -> m.meta().get(ParserConstants.ANNO_TYPE_KEY);
            default -> null;
        };        
    }

    
    public static void alias(Symbol alias, Symbol sym) {
        Namespace ns = NativeDynamicBinding.NAMESPACE.deref();
        Binding newBinding = new ImmutableBinding(ns.getName(), BindingType.VALUE, sym, FINAL_META, new SwitchPoint());
        ns.define(alias.getName(), newBinding);
    }
        
    public static void require_one(Object o) {
        ISeq seq = seq(o);
        Object nsSym = first(seq);
        ISeq maybeMap = next(seq);
        PersistentMap optMap = maybeMap == null ? PersistentMap.EMPTY : PersistentMap.fromIterable(maybeMap);
        require_one(expectSymbol(nsSym), optMap);
    }
    
    public static void require_one(Symbol namespace, PersistentMap extra) {
        Namespace thisNs = NativeDynamicBinding.NAMESPACE.deref();
        
        String nsName = namespace.getName();
        Namespace otherNs = RuntimeRoot.defineOrGet(nsName);
        
        var as = extra.get(Keyword.of("as"));
        if (as != null) {
            Symbol localRename = (Symbol) as;
            thisNs.define(localRename.getName(), new ImmutableBinding(thisNs.getName(), otherNs));
        }
        var refer = extra.get(Keyword.of("refer"));
        Map<Symbol, Symbol> renameMap = (Map<Symbol, Symbol>) extra.get(Keyword.of("rename"));
        if (refer != null) {
            var referList = expectVector(refer);
            for (Object o : referList) {
                Symbol otherSym = expectSymbol(o);
                if (renameMap != null) {
                    otherSym = renameMap.getOrDefault(otherSym, otherSym);
                }
                thisNs.referOne(otherNs, otherSym.getName(), otherSym.getName());
            }
        }
    }

    // 
    @PileDoc("Update the settable ref to the result of calling the provided function.")
    public static void update(Object ref, Object function) throws Throwable {
        if (ref instanceof SettableRef sr) {
            if (function instanceof PCall pcall) {
                sr.update(pcall);
            }
        }
    }

    @PileDoc("Dereferences the provided Ref or Future.")
    @Precedence(0)
    public static Object deref(Object o) throws Throwable {
        return switch (o) {
            case Ref ref -> ref.deref();
            case Future f -> f.get();
            default -> null;
        };
    }
    
    @Precedence(1)
    public static Object deref(Object o, long time, Object rawUnit)
            throws Throwable {
        TimeUnit unit = toEnum(rawUnit, TimeUnit.class);
        return switch (o) {
            case Ref ref -> ref.deref(time, unit);
            case Future f -> f.get(time, unit);
            default -> null;
        };        
    }

    private static <T extends Enum<T>> T toEnum(Object rawUnit, Class<T> clazz) {
        requireNonNull(rawUnit, "Cannot convert to enum: null");
        if (clazz.isAssignableFrom(rawUnit.getClass())) {
            return clazz.cast(rawUnit);
        }
        if (rawUnit instanceof Keyword k) {
            return Enum.valueOf(clazz, k.getName().toUpperCase());
        }
        throw new IllegalArgumentException("Cannot convert '" + rawUnit.getClass() + "' to enum: " + rawUnit);
    }

    @PileDoc("Unconditionally sets the settable ref to the new value.")
    @RenamedMethod("reset!")
    public static void resetRef(SettableRef ref, Object newVal) {
        ref.set(newVal);
    }

    @PileDoc("Creates a new atom with a nil initial value.")
    @Precedence(1)
    public static Atom atom() {
        return atom(null);
    }

    @PileDoc("Creates a new atom with the provided initial value.")
    @Precedence(LAST)
    public static Atom atom(Object initial) {
        return new Atom<>(initial);
    }

    @PileDoc("Tests whether the argument is a sequence.")
    @RenamedMethod("seq?")
    public static boolean isSeq(Object o) {
        return o instanceof ISeq;
    }
    
    @PileDoc("Returns the result of reversing the sequence.")
    public static ISeq reverse(Object seqable) {
        ISeq seq = seq(seqable);
        if (seq == ISeq.EMPTY) {
            return ISeq.EMPTY;
        }
        return seq.reverse();
    }
    
    @PileDoc("Returns true if the provided argument is not nil.")
    @RenamedMethod("some?")
    public static boolean isSome(Object o) {
        return o != null;
    }

    @PileDoc("Attaches the metdata to the provided value")
    @Precedence(1)
    @RenamedMethod("with-meta")
    public static Metadata withMeta(Metadata m, PersistentMap pm) {
        return m.withMeta(pm);
    }

    @Precedence(LAST)
    @RenamedMethod("with-meta")
    public static Metadata withMeta(Object o, Object meta) {
        if (meta instanceof PersistentMap pm) {
            if (o instanceof Metadata m) {
                return withMeta(m, pm);
            }
            throw new IllegalArgumentException("Base not instanceof Metadata: " + (o == null ? "null" : o.toString()));
        }
        throw new IllegalArgumentException("Metadata not a map: " + (meta == null ? null : meta.getClass()));
    }

    @PileDoc("Returns the metadata associated with the argument, or nil.")
    public static PersistentMap meta(Object o) {
        if (o instanceof Metadata m) {
            return m.meta();
        }
        return null;
    }

    @PileDoc("Creates a new persistent list from the arguments.")
    public static PersistentList list(Object... parts) {    
        // OPTIMIZE
        return PersistentList.fromList(Arrays.asList(parts));
    }

    @PileDoc("Creates a new persistent vector from the arguments.")
    public static PersistentVector vector(Object... parts) {
        // OPTIMIZE
        return PersistentVector.createArr(parts);
    }

    @PileDoc("Creates a new persistent map from the arguments.")
    @RenamedMethod("hash-map")
    public static PersistentMap hashMap(Object... parts) {
        // OPTIMIZE
        return PersistentMap.createArr(parts);
    }

    @PileDoc("Creates a new persistent set from the arguments.")
    @RenamedMethod("hash-set")
    public static PersistentSet hashSet(Object... parts) {
        // OPTIMIZE
        return PersistentSet.createArr(parts);
    }
    
    @PileDoc("Returns true if the argument is nil, false otherwise.")
    @RenamedMethod("empty?")
    public static boolean isEmpty(Object o) {
        return seq(o) == null;
    }

    @PileDoc("Returns a random integer between 0 (inclusive) and the provided value (exclusive).")
    @RenamedMethod("rand-int")
    public static int randInt(int n) {
        return RandomNumberGeneratorHolder.HOLDER.nextInt(n);
    }

    @PileDoc("Returns a random double between 0.0 (inclusive) and 1.0 (exclusive).")
    public static double rand() {
        return RandomNumberGeneratorHolder.HOLDER.nextDouble();
    }

    @PileDoc("Returns true if the provided argument is an instance of the provided class.")
    @RenamedMethod("instance?")
    public static boolean isInstance(Class<?> clazz, Object o) {
        Objects.requireNonNull(clazz, "Class base cannot be null");
        return o == null ? false : clazz.isAssignableFrom(o.getClass());

    }

    @PileDoc("Creates a new persistent list with the provided head and (unrealized) tail sequence.")
    @Precedence(LAST)
    public static ISeq cons(Object head, Object tail) {
        return switch (tail) {
            case null -> new Cons(head, null);
            case ISeq is -> new Cons(head, is);
            case Seqable s -> new ConsSequence(head, s);
            default -> throw new IllegalArgumentException("cons: Invalid tail");
        };
    }

    @Precedence(LAST)
    @RenamedMethod("conj*")
    public static Conjable conj(Object base, Object s) {
        return switch (base) {
            case null -> PersistentList.EMPTY.conj(s);
            case Conjable conj -> conj.conj(s);
            default -> throw new RuntimeException("Type not conj-able: " + base.getClass());
        };
    }

    @Precedence(1)
    @RenamedMethod("assoc*")
    public static <K, V> Associative<K, V> assoc(Associative<K, V> base, K k, V v) {
        if (base == null) {
            return new SingleMap<>(k, v);
        }
        return base.assoc(k, v);
    }


    @Precedence(LAST)
    @RenamedMethod("assoc*")
    public static <K, V> Associative<K, V> assoc(Object base, K k, V v) {
        return switch (base) {
            case null -> new SingleMap<>(k, v);
            case Associative assoc -> assoc.assoc(k, v);
            default -> throw new RuntimeException("Type not assoc-able: " + base.getClass());   
        };        
    }

    @PileDoc("Merges the rhs map into the lhs as if calling assoc on all the items of the rhs map.")
    public static <K, V> PersistentMap<K, V> merge(PersistentMap<K, V> lhs, PersistentMap<K, V> rhs) {
        for (var entry : rhs.entrySet()) {
            lhs = lhs.assoc(entry.getKey(), entry.getValue());
        }
        return lhs;
    }
    
    @PileDoc("""
            Merges the rhs map into the lhs. Calls the provided function on conflict with (lhs_value, rhs_value)
            
              (def a {:a 1 :b 3})
              (def b {:b 4 :z 12})
              (merge-with a b +)
              ;; {:a 1 :b 7 :z 12}
            """)
    public static PersistentMap<Object, Object> merge_with(PersistentMap<Object, Object> lhs,
            PersistentMap<Object, Object> rhs, PCall f) throws Throwable {
        for (var entry : rhs.entrySet()) {
            final Object outVal;
            Object key = entry.getKey();
            if (lhs.containsKey(key)) {
                outVal = f.invoke(lhs.get(key), entry.getValue());
            } else {
                outVal = entry.getValue();
            }
            lhs = lhs.assoc(entry.getKey(), outVal);
        }
        return lhs;
    }

    public static PCall seq() {
        return FunctionUtils.of(Stream.class, ISeq.class, NativeCore::seq);
    }
    
    @Precedence(0)
    public static ISeq seq(ISeq s) {
        return s;
    }    

    @Precedence(1)
    public static ISeq seq(Map<Object, Object> map) {
        return seqIterator(map.entrySet().iterator());
    }
    
    @RenamedMethod("seq")
    @Precedence(2)
    public static ISeq seqSeqable(Seqable s) {
        return s.seq();
    }

    @Precedence(3)
    public static ISeq seq(CharSequence cs) {
        return ISeq.seqSized(cs, 0, cs.length(), (b, i) -> b.charAt(i));
    }

    @Precedence(4)
    public static ISeq seq(Map.Entry entry) {
        return ISeq.of(entry.getKey(), entry.getValue());
    }

    @Precedence(5)
    public static <T> ISeq<T> seq(Iterable<T> iter) {
        return seqIterator(iter.iterator());
    }    

    @Precedence(6)
    public static <T> ISeq<T> seq(Stream<T> iter) {
        return seqIterator(iter.iterator());
    }

//    @NoLink // TODO Eventually
    @PileDoc("Returns a new sequence from the provided value. Supports all collection types, arrays, strings, and streams.")
    @Precedence(LAST)
    public static ISeq seq(Object o) {
        return switch (o) {
            case null -> null;
            case ISeq is -> is;
            case Map m -> seq(m);
            case Seqable s -> s.seq();
            case Object arr when arr.getClass().isArray() -> ISeq.seqSized(arr, 0, count(arr),
                    (Object t, Integer idx) -> get(t, idx));
            case CharSequence cs -> seq(cs);
            case Map.Entry entry -> seq(entry);
            case Iterable it -> seq(it);
            case Stream s -> seq(s);
            default -> throw new IllegalArgumentException("Don't know how to create ISeq from: " + o.getClass().getName() + ": " + o);
        };
    }

    public static ISeq seqIterator(Iterator iter) {
        if (!iter.hasNext()) {
            return null;
        }

        Object current = iter.next();

        return new AbstractSeq() {
        
            private Optional<ISeq> next = null;

            @Override
            public Object first() {
                return current;
            }

            @Override
            public synchronized ISeq next() {
                if (next == null) {
                    var ev = seqIterator(iter);
                    next = Optional.ofNullable(ev);
                }
                return next.orElse(null);                
            }
        };
    }
    
    private static class LocalConsumer implements Consumer<Object> {
    
        private Object local = null;

        @Override
        public void accept(Object t) {
            local = t;
        }
        
        public Object getLocal() {
            if (local == null) {
                throw new IllegalArgumentException();
            }
            var tmp = local;
            local = null;
            return tmp;
        }
    
    } 
    
    public static Stream<Object> stream_partition(Stream<Object> s, int n) {
        return s.gather(Gatherer.ofSequential(() -> new ArrayList<>(), (list, item, down) -> {
            if (list.size() < n) {
                list.add(item);
            } else {
                down.push(list);
                list = new ArrayList<>();
            }
            return true;
        }));
    }

    @PileDoc("Looks up the provided key in an associative structure or provided index in a list structure.")
    @Precedence(1)
    public static Object get(List a, Object key) {
        return get(a, key, null);
    }

    @Precedence(2)
    public static Object get(Map a, Object key) {
        if (a == null) {
            return null;
        }
        return a.get(key);
    }

    @Precedence(3)
    public static Object get(String a, Object key) {
        return get(a, key, null);
    }

    @Precedence(4)
    public static Object get(Set a, Object key) {
        return get(a, key, null);
    }

    @Precedence(LAST)
    public static Object get(Object base, Object key) {
        return switch (base) {
            case null -> null;
            case Map map -> get(map, key);
            case List l -> get(l, key);
            case Set set -> get(set, key);
            case String s -> get(s, key);
            case Object arr when arr.getClass().isArray() -> arrayGet(arr, key);
            default -> null;
        };
    }

    private static Object arrayGet(Object base, Object key) {
        try {
            return ARRAY_GET.invoke(base, key);
        } catch (Throwable e) {
            throw new RuntimeException("Couldn't get array element");
        }
    }

    @Precedence(1)
    public static Object get(List a, Object key, Object ifNone) {
        if (a == null) {
            return null;
        }
        int index = getIndex(key);
        if (index != -1) {
            Object val = a.get(index);
            if (val != null) {
                return val;
            }
        }
        return ifNone;
    }

    @Precedence(2)
    public static Object get(Map a, Object key, Object ifNone) {
        if (a == null) {
            return ifNone;
        }
        return a.getOrDefault(key, ifNone);
    }

    @Precedence(3)
    public static Object get(String a, Object key, Object ifNone) {
        if (a == null) {
            return null;
        }

        int index = getIndex(key);
        if (index != -1 && index < a.length()) {
            return a.charAt(index);
        }
        return ifNone;
    }

    @Precedence(4)
    public static Object get(Set a, Object key, Object ifNone) {
        if (a == null) {
            return null;
        }

        if (a.contains(key)) {
            return key;
        }
        return ifNone;
    }
    
    private static final ArrayGetMethod ARRAY_GET = new ArrayGetMethod();

    @Precedence(LAST)
    public static Object get(Object base, Object key, Object ifNone) throws Throwable {
        if (base == null) {
            return ifNone;
        }
        if (base instanceof Map map) {
            return get(map, key, ifNone);
        } else if (base instanceof List list) {
            return get(list, key, ifNone);
        } else if (base instanceof Set set) {
            return get(set, key, ifNone);
        } else if (base instanceof String s) {
            return get(s, key, ifNone);
        } else if (base.getClass().isArray()) {
            return ARRAY_GET.invoke(base, key, ifNone);
        } else {
            return ifNone;
           
        }
    }

    private static int getIndex(Object key) {
        if (key instanceof Long index) {
            return index.intValue();
        }
        if (key instanceof Integer index) {
            return index;
        }
        return -1;
    }
    
    @Precedence(0)
    @RenamedMethod("contains?")
    public static boolean contains(List col, Object key) {
        // TODO ehhh this is kind of silly
        if (key instanceof Integer i) {
            return col.size() <= i;
        }
        return false;
    }
    
    @Precedence(1)
    @RenamedMethod("contains?")
    public static boolean contains(Map col, Object key) {
        return col.containsKey(key);
    }
    
    @Precedence(2)
    @RenamedMethod("contains?")
    public static boolean contains(Set col, Object key) {
        return col.contains(key);
    }

    @Precedence(1)
    public static Keyword keyword(String name) {
        return Keyword.of(null, name);
    }

    @Precedence(2)
    public static Keyword keyword(String ns, String name) {
        return Keyword.of(ns, name);
    }

    @PileDoc("Returns the nth index in the provided list/sequence, or nil if there are not enough elements.")
    public static Object nth(Object o, int index) {
        if (o instanceof List list) {
            if (index < list.size()) {
                return list.get(index);
            } else {
                return null;
            }
        }
        ISeq<?> seq = seq(o);
        for (int i = 0; i < index && seq != null; ++i) {
            seq = seq.next();
        }
        if (seq != null) {
            return seq.first();
        } else {
            return null;
        }
    }

    @PileDoc("Converts the argument to a sequence and returns the first element, or nil if there are none.")
    @Precedence(1)
    public static <T> T first(ISeq<T> is) {
        return is == null ? null : is.first();
    }

    @Precedence(LAST)
    public static Object first(Object is) {
        ISeq seq = seq(is);
        if (seq == null) {
            return null;
        }
        return first(seq);
    }

    public static ISeq nnext(Object is) {
        return next(next(is));
    }

    @PileDoc("Returns a sequence of everything except the first element.")
    @Precedence(1)
    public static <T> ISeq<T> next(ISeq<T> is) {
        return is == null ? null : is.next();
    }

    @Precedence(LAST)
    public static <T> ISeq<T> next(Object s) {
        return next(seq(s));
    }

    public static Object fnext(Object o) {
        return first(next(o));
    }

    public static <T> T last(ISeq<T> is) {
        T last = is.first();
        ISeq<T> next = is.next();
        while (next != null) {
            last = next.first();
            next = next.next();
        }
        return last;
    }

    @Precedence(1)
    public static ISeq more(ISeq is) {
        ISeq next = is.next();
        if (next == null) {
            return ISeq.EMPTY;
        }
        return next;
    }

    @Precedence(LAST)
    public static ISeq more(Object is) {
        return more(seq(is));
    } 

    @Precedence(1)
    public static Object second(ISeq is) {
        return first(more(is));
    }

    @Precedence(LAST)
    public static Object second(Object is) {
        ISeq seq = seq(is);
        if (seq == null) {
            return null;
        }
        return second(seq);
    }

    @Precedence(1)
    public static Object ssecond(ISeq is) {
        return more(more(is)).first();
    }

    @Precedence(LAST)
    public static Object ssecond(Object is) {
        ISeq seq = seq(is);
        if (seq == null) {
            return null;
        }
        return ssecond(seq);
    }

    public static Object fsecond(Object is) {
        ISeq seq = seq(is);
        if (seq == null) {
            return null;
        }
        return first(second(seq));
    }

    // ~~ logic
    @RenamedMethod("=")
    public static boolean equals(Object lhs, Object rhs) {
        if (lhs instanceof Number lhsnum) {
            if (rhs instanceof Number rhsnum) {
                return numEquals(lhsnum, rhsnum);
            }
        }
        if (lhs != null) {
            return lhs.equals(rhs);
        } else {
            return rhs == null;
        }
    }

    public static int compare(Object lhs, Object rhs) {
        if (lhs instanceof Number lhsnum) {
            if (rhs instanceof Number rhsnum) {
                return numCompare(lhsnum, rhsnum);
            }
        }

        if (lhs instanceof Comparable c) {
            return c.compareTo(rhs);
        }
        throw new IllegalArgumentException("Compare LHS must be Comparable");
    }
    
    private static final Set<Class<?>> INTEGRAL = Set.of(Byte.class, Short.class, Integer.class, Long.class);
    private static final Set<Class<?>> FLOATING = Set.of(Float.class, Double.class);

    private static int numCompare(Number lhsnum, Number rhsnum) {
        if (INTEGRAL.contains(lhsnum.getClass()) && INTEGRAL.contains(rhsnum.getClass())) {
            long ll = lhsnum.longValue();
            long lr = rhsnum.longValue();
            return Long.compare(ll, lr);
        }
        
        if (FLOATING.contains(lhsnum.getClass()) && FLOATING.contains(rhsnum.getClass())) {
            var dl = lhsnum.doubleValue();
            var dr = rhsnum.doubleValue();
            return Double.compare(dl, dr);
        } 
        throw new IllegalArgumentException("Incomparable number types");
    }

    public static int hash(Object o) {
        Objects.requireNonNull(o, "Cannot hash null");
        if (o instanceof Number n) {
            return numHash(n);
        }
        return o.hashCode();
    }

    private static int numHash(Number n) {
        return Double.hashCode(n.doubleValue());
    }

    private static boolean numEquals(Number lhsnum, Number rhsnum) {
        if (either(lhsnum, rhsnum, Double.class)) {
            return lhsnum.doubleValue() == rhsnum.doubleValue();
        } else if (either(lhsnum, rhsnum, Float.class)) {
            return lhsnum.floatValue() == rhsnum.floatValue();
        } else if (either(lhsnum, rhsnum, Long.class)) {
            return lhsnum.longValue() == rhsnum.longValue();
        } else {
            return lhsnum.intValue() == rhsnum.intValue();
        }
    }

    private static boolean either(Number lhs, Number rhs, Class<? extends Number> num) {
        return num.equals(lhs.getClass()) || num.equals(rhs.getClass());
    }

    @PileDoc("Returns true if both objects are the same, or are both null.")
    @RenamedMethod("same?")
    public static boolean same(Object lhs, Object rhs) {
        return lhs == rhs;
    }

    // ~~ Regex
    @RenamedMethod("re-matcher")
    public static Matcher regexMatcher(Pattern p, CharSequence s) {
        return p.matcher(s);
    }

    @RenamedMethod("re-find")
    public static PersistentArrayVector<String> regexFind(Matcher m) {
        m.find();
        return regexGroups(m);
    }

    @RenamedMethod("re-groups")
    public static PersistentArrayVector<String> regexGroups(Matcher m) {
        PersistentArrayVector<String> p = new PersistentArrayVector<>();
        for (int i = 0; i < m.groupCount(); ++i) {
            p = p.push(m.group(i));
        }
        return p;
    }
    // ~~ Printing

//	public static void prn(Object s) {
//	    System.out.print(Objects.toString(s));
//	}
//	
//	public static void prnln(Object s) {
//		System.out.println(s.toString());
//	}
//	
    public static void prn(Object... s) {
        PrintStream out = NativeDynamicBinding.STANDARD_OUT.deref();
        for (Object o : s) {
            out.print(o);
        }
        out.println();
    }

//	public static void prnlnall(Object... s) {
//		for (Object o : s) {
//			System.out.print(o);
//		}
//		System.out.println();
//	}

    /**
     * Concatenates string parts.
     * 
     * @param parts
     * @return
     * @see StrCatSpecializer
     */
    public static String str(Object... parts) {
        var len = parts.length;
        switch (len) {
            case 0:
                return "";
            case 1:
                return String.valueOf(parts[0]);
            case 2:
                return String.valueOf(parts[0]).concat(String.valueOf(parts[1]));
            default:
                StringBuilder sb = new StringBuilder();
                for (Object o : parts) {
                    sb.append(o);
                }
                return sb.toString();
        }
    }

//    @RenamedMethod("to-array")
//    @Precedence(0)
//    public Object[] toArray(Collection<?> coll) {
//        if (coll == null) {
//            return EMPTY_ARRAY;
//        } else {
//            return coll.toArray();
//        }
//    }
//    
//    @RenamedMethod("to-array")
//    @Precedence(1)
//    public Object[] toArray(Iterable coll) {
//        if (coll == null) {
//            return EMPTY_ARRAY;
//        } else {
//            List<Object> out = new ArrayList<>();
//            coll.forEach(out::add);
//            return out.toArray();
//        }
//    }
//    
//    @RenamedMethod("to-array")
//    @Precedence(2)
//    public Object[] toArray(Object[] coll) {
//        if (coll == null) {
//            return EMPTY_ARRAY;
//        } else {
//            return coll;
//        }
//    }
//    
//    @RenamedMethod("to-array")
//    @Precedence(3)
//    public Object[] toArray(Map<?, ?> map) {
//        if (map == null) {
//            return EMPTY_ARRAY;
//        } else {
//            return map.entrySet().toArray();
//        }
//    }
//    
//    @RenamedMethod("to-array")
//    @Precedence(4)
//    public char[] toArray(String map) {
//        if (map == null) {
//            return new char[0];
//        } else {
//            return map.toCharArray();
//        }
//    }
//    
//    @RenamedMethod("to-array")
//    @Precedence(LAST)
//    public Object toArray(Object unk) {
//        if (unk == null) {
//            return EMPTY_ARRAY;
//        } else if (unk instanceof Collection col) {
//            return toArray(col);
//        } else if (unk instanceof Iterable it) {
//            return toArray(it);
//        } else if (unk instanceof Map map) {
//            return toArray(map);
//        } else if (unk instanceof String str) {
//            return toArray(str);
//        } else if (unk.getClass().isArray()) {
//            return unk;
//        } else {
//            throw new RuntimeException("Cannot convert " + unk.getClass() +" into an array.");
//        }
//    }

    @RenamedMethod("nil?")
    public static boolean isNil(Object o) {
        return o == null;
    }

    @RenamedMethod("false?")
    public static boolean isFalse(Object o) {
        return Boolean.FALSE.equals(o);
    }

    @RenamedMethod("true?")
    public static boolean isTrue(Object o) {
        return Boolean.TRUE.equals(o);
    }

    @RenamedMethod("keyword?")
    public static boolean isKeyword(Object o) {
        return isInstance(Keyword.class, o);
    }
    
    @RenamedMethod("vec?")
    public static boolean isVector(Object o) {
        return isInstance(PersistentVector.class, o);
    }
    
    @RenamedMethod("map?")
    public static boolean isMap(Object o) {
        return isInstance(PersistentMap.class, o);
    }
    
    @RenamedMethod("list?")
    public static boolean isList(Object o) {
        return isInstance(PersistentList.class, o);
    }

    @RenamedMethod("symbol?")
    public static boolean isSymbol(Object o) {
        return isInstance(Symbol.class, o);
    }

    @Precedence(0)
    public static List subvec(List v, Object start) {
        return subvec(v, start, v.size());
    }

    @Precedence(1)
    public static List subvec(List v, Object s, Object e) {
        int start = getIndex(s);
        int end = getIndex(e);
        ensureEx(end >= start, IllegalArgumentException::new, "End index must be greater than or equal to start");
        return v.subList(start, end);
    }

    public static String name(Named sym) {
        return sym.getName();
    }

    private static final Object[] EMPTY_ARRAY = new Object[0];
    
    @RenamedMethod("proxy?")
    public static boolean isProxy(Object o) {
        return o == null ? false : Proxy.isProxyClass(o.getClass());
    }

    /**
     * Create a dynamic {@link Proxy}.
     * 
     * @param namesAndMethods Method names and implementations.
     * @param interfaces      The interfaces this proxy will implement.
     * @return
     */
    public static Object proxy(PersistentVector<Class<?>> interfaces,
            PersistentMap<String, Object> namesAndMethods) {

        Class[] implementedInterfaces = interfaces.toArray(Class[]::new);
        Map<String, LinkableMethod> methodMap = mapKV(namesAndMethods, (name, fns) -> {
            Object[] vals;
            if (fns instanceof List l) {
                vals = l.toArray();
            } else {
                vals = new Object[] { fns };
            } 
            LinkableMethod lm = fn_delegate(vals);
            return entry(name, lm);
        });

        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (args == null) {
                    args = EMPTY_ARRAY;
                }
                LinkableMethod toCall = methodMap.get(method.getName());

                if (toCall != null) {
                    if (toCall.acceptsArity(args.length)) {
                        return toCall.invoke(args);
                    }
                }

                // toCall == null
                // See Proxy#Methods Duplicated in Multiple Proxy Interfaces
                if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }
                throw new UnsupportedOperationException("Method not implemented: " + method);
            }
        };

        return Proxy.newProxyInstance(NativeCore.class.getClassLoader(), implementedInterfaces, handler);
    }

    @PileDoc("This function simply returns its argument.")
    public static <T> T identity(T input) {
        return input;
    }

    @PileDoc("This returns a function which always returns the provided argument.")
    public static <T> PileMethod constantly(T input) {
        return new PileMethod() {

            private final Class<?> clazz = (input == null) ? Object.class : input.getClass();

            @Override
            public Optional<Class<?>> getReturnType() {
                return Optional.of(clazz);
            }

            @Override
            public Object invoke(Object... args) {
                return input;
            }
            
            @Override
            public Object applyInvoke(Object... args) {
                return input;
            }

            @Override
            public boolean acceptsArity(int arity) {
                return true;
            }
            
            @Override
            public Optional<CallSite> staticLink(CallSiteType csType, MethodType staticTypes, long anyMask) {
                return Optional.of(new ConstantCallSite(constant(clazz, input)));
            }
            
            @Override
            public CallSite dynamicLink(CallSiteType csType, MethodType statictypes, long anyMask,
                    CompilerFlags flags) {
                return new ConstantCallSite(constant(clazz, input));
            }
            
            // TODO method composition using partial?
        };
    }


    
    public static ISeq nthnext(Object o, int count) {
        ISeq seq = seq(o);
        for (int i = 0; i < count && seq != null; ++i) {
            seq = seq.next();
        }
        return seq;
    }
    
    public static boolean not(Object o) {
        return ! Helpers.ifCheck(o);
    }
    
    @PileDoc("Returns the number of elements in the collection/sequence/string. Single arg is for streams.")
    @Precedence(0)
    public static int count(Counted c) {
        return c.count();
    }
    
    @Precedence(1)
    public static int count(Collection c) {
        return c.size();
    }
    
    @Precedence(2)
    public static int count(Map c) {
        return c.size();
    }
    
    @Precedence(3)
    public static int count(ISeq s) {
        int i = 0;
        while (s != null) {
            s = s.next();
            ++i;
        }
        return i;
    }
    
    @Precedence(4)
    public static int count(Object o) {
        return switch (o) {
            case Object obj when obj.getClass().isArray() -> countArray(o);
            // TODO Rest of the cases
            default -> count(seq(o));
        };

    }

    private static int countArray(Object o) {
        try {
            return (int) arrayLength(o.getClass()).invoke(o);
        } catch (Throwable e) {
            throw new IllegalArgumentException("Shouldn't happen");
        }
    }
    
    public static PileMethod count() {
        return FunctionUtils.of(Stream.class, long.class, Stream::count);
    }
    
    @PileDoc("""
            Returns a composition of calling each function, from right to left, 
            passing the results of the prior function call to the next function.            
            
            (def odd? (comp not even?))            
            """)
    public static LinkableMethod comp(Object... methods) {
        LinkableMethod out = null;
        for (int i = methods.length - 1; i >= 0; --i) {
            var lm = (LinkableMethod) methods[i];
            if (out == null) {
                out = lm;
            } else {
                out = out.andThen(lm);
            }
        }
        return out;
    }
    
    @PileDoc("Returns a map from namespace names to the namespace itself.")
    public static Map<String, Namespace> ns_map() {
        return PersistentMap.from(RuntimeRoot.getMap());
    }
    
    @PileDoc("""
            Creates a new function which delegates all calls to the first function that could accept 
            those arguments (by arity match only).
            """)
    public static LinkableMethod fn_delegate(Object... fns) {
        if (fns.length == 0) {
            throw new IllegalArgumentException("Must have at least one function to create delegate from");
        }
        if (fns.length == 1) {
            return (LinkableMethod) fns[0];
        }
        
    
        List<LinkableMethod> methods = new ArrayList<>();
        for (Object object : fns) {
            methods.add((LinkableMethod) object);
        }
        
        return new LinkableMethod() {
            
            @Override
            public Optional<CallSite> staticLink(CallSiteType csType, MethodType staticTypes, long anyMask) {
                if (csType == CallSiteType.PLAIN) {
                    // Only static link if there's a single delegate that supports this arity.
                    LinkableMethod candidate = null;
                    for (LinkableMethod linkableMethod : methods) {
                        if (linkableMethod.acceptsArity(staticTypes.parameterCount())) {
                            if (candidate == null) {
                                candidate = linkableMethod;
                            } else {
                                return Optional.empty();
                            }
                        }
                    }
                    return Optional.of(candidate).flatMap(lm -> lm.staticLink(csType, staticTypes, anyMask));
                }
                // TODO Not sure how to handle apply here?
                return Optional.empty();
            }
            
            @Override
            public Object invoke(Object... args) throws Throwable {
                var argCount = args.length;
                for (LinkableMethod linkableMethod : methods) {
                    if (linkableMethod.acceptsArity(argCount)) {
                        return linkableMethod.invoke(args);
                    }
                }
                throw new IllegalArgumentException("No acceptable delegate found");
            }
            
            @Override
            public Object applyInvoke(Object... args) throws Throwable {
                var argCount = args.length;
                for (LinkableMethod linkableMethod : methods) {
                    if (linkableMethod.acceptsArity(argCount)) {
                        return linkableMethod.applyInvoke(args);
                    }
                }
                throw new IllegalArgumentException("No acceptable delegate found");
            }
            
            @Override
            public boolean acceptsArity(int arity) {
                for (LinkableMethod linkableMethod : methods) {
                    if (linkableMethod.acceptsArity(arity)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }
    
    @PileDoc("Sets the current log level from a keyword. Accepts :trace, :debug, :info, :warn, :error.")
    public static void set_log_level(Keyword v) {
        String name = v.getName();
        LogLevel ll = LogLevel.valueOf(name.toUpperCase());
        NativeDynamicBinding.ROOT_LOG_LEVEL.set(ll);
    }
    
    @Precedence(0)
    public static Map<String, Binding> ns_publics(Namespace ns) {
        return ns.getOurs();
    }
    
    @Precedence(1)
    public static Map<String, Binding> ns_publics(String ns) {
        Namespace namespace = RuntimeRoot.get(ns);
        return ns_publics(namespace);
    }
    
    @PileDoc("Returns a sequence of keys in the provided map.")
    public static ISeq keys(Map map) {
        return seq(map.keySet());
    }
    
    
    @PileDoc("Returns a sequence of values in the provided map.")
    public static ISeq vals(Map map) {
        return seq(map.values());
    }
    
    @PileDoc("Sorts the provided collection with the default comparator function (compare...)")
    @Precedence(0)
    public static ISeq sort(Collection o) {
        return sort(NativeCore::compare, o);
    }
    
    @PileDoc("Sorts the provided collection with the provided comparator.")
    @Precedence(1)
    public static ISeq sort(Comparator cmp, Collection o) {
        Object[] arr = o.toArray();
        Arrays.sort(arr, cmp);
        return seq(arr);
    }
    
    public static Object fmap(PCall tx, FMap src) throws Throwable {
        return src.fmap(tx);
    }
    
    @PileDoc("Sleeps the current thread until the provided milliseconds or duration have elapsed.")
    @Precedence(0)
    public static void sleep(int ms) throws InterruptedException {
        Thread.sleep(ms);
    }
    
    @Precedence(1)
    public static void sleep(Duration duration) throws InterruptedException {
        Thread.sleep(duration);
    }
    
    @PileDoc("Returns the class of the provided object, or null.")
    @RenamedMethod("class")
    public static Class getClass(Object o) {
        return o == null ? null : o.getClass();
    }
    
    public static <E extends Enum<E>> E enum_of(Class<E> e, Object v) {
        if (v instanceof Keyword kw) {
            String name = kw.getName();
            return Enum.valueOf(e, name.toUpperCase());
        } else if (v.getClass().equals(e)) {
            return (E) v;
        } else {
            throw new IllegalArgumentException("Invalid enum: " + e + " of " + v);            
        }
    }
    
    @RenamedMethod("instanceof?")
    public static boolean isInstanceOf(Class clz, Object o) {
        return o == null ? false : clz.isAssignableFrom(o.getClass());
    }
    
    @PileDoc("Returns true if the provided binding is dynamic, false otherwise.")
    @RenamedMethod("dynamic?")
    public static boolean isDynamic(Binding b) {
        return Binding.getType(b) == BindingType.DYNAMIC;
    }
    
    @PileDoc("Returns true if the provided binding is final, false otherwise.")
    @RenamedMethod("final?")
    public static boolean isFinal(Binding b) {
        return PileMethodLinker.isFinal(b);
    }
    
    @PureFunction
    public static PileMethod java_method(Class c, String name) throws Exception {
        return JavaMethod.of(c, name);
    }
    
    // ============
    // Hierarchies
    // ============
    
    public static Hierarchy make_hierarchy() {
        return new Hierarchy();
    }
    
    @RenamedMethod("isa?")
    public static boolean isA(Hierarchy hierarchy, Object child, Object parent) {
        return hierarchy.isAChild(child, parent);
    }
    
    @RenamedMethod("isa?")
    public static boolean isA(Object child, Object parent) {
        return isA(NativeValue.HIERARCHY.getValue(), child, parent);
    }
    
    public static PersistentSet<Object> descendants(Hierarchy hierarchy, Object tag) {
        return hierarchy.descendants(tag);
    }
    
    public static PersistentSet<Object> descendants(Object tag) {
        return descendants(NativeValue.HIERARCHY.getValue(), tag);
    }
    
    public static PersistentSet<Object> ancestors(Hierarchy hierarchy, Object raw) {
        if (raw instanceof Keyword tag) {
            return hierarchy.ancestors(tag);
        }
        // TODO class
        throw new IllegalArgumentException();
    }
    
    public static PersistentSet<Object> ancestors(Object tag) {
        return descendants(NativeValue.HIERARCHY.getValue(), tag);
    }
    
    private static final Map<Class<?>, MethodHandle> ADAPTER_CACHE = new ConcurrentHashMap<>();
    

    @PileDoc("""
            Transforms a java instance of a single abstract method type into a pile function type 
            which is natively callable.
            
            (import java.util.Comparator)
            (def java-cmp (Comparator/naturalOrder))
            (def call-cmp (to-fn java-cmp))
            (call-cmp 55 66)
            ;; -1
            """)
    public static PCall to_fn(Object o) throws Throwable {
        requireNonNull(o);
        Class<? extends Object> clazz = o.getClass();
        Class<?>[] intf = clazz.getInterfaces();
        if (intf.length != 1) {
            throw new IllegalArgumentException("Unexpected multiple interfaces");
        }
        Class<?> samType = intf[0];
        MethodHandle cons = ADAPTER_CACHE.get(samType);
        if (cons == null) {
            cons = computeAdapter(samType, o);
        }
        var bound = cons.asType(cons.type().changeParameterType(0, clazz)).bindTo(o);
        return FunctionUtils.ofJavaMethodHandle(bound);        
    }

    private static MethodHandle computeAdapter(Class<? extends Object> clazz, Object o) throws Throwable {
        Method m = FunctionalInterfaceAdapter.findMethodToImplement(clazz);
        MethodHandle handle = lookup().unreflect(m);
        var pre = ADAPTER_CACHE.putIfAbsent(clazz, handle);
        return pre == null ? handle : pre;
    }
    
    private static class RepeatSeq<T> extends AbstractSeq<T> implements Streamable, ReversibleSeq<T> {
    
        private final long count;
        private final T value;

        public RepeatSeq(long count, T value) {
            super();
            this.count = count;
            this.value = value;
        }

        @Override
        public T first() {
            return value;
        }

        @Override
        public ISeq<T> next() {
            var next = count - 1;
            return next == 0 ? null : new RepeatSeq<>(next, value);
        }

        @Override
        public Stream toStream() {
            return Stream.generate(() -> value).limit(count);
        }

        @Override
        public ISeq<T> reverse() {
            return this;
        }
    
    }
    
    @PileDoc("Returns a fixed-size sequence which repeatedly returns the provided element")
    public static <T> ISeq<T> repeat(long count, T o) {
        if (count == 0) {
            return ISeq.EMPTY;
        } else {
            return new RepeatSeq<>(count, o);            
        }
    }
    
    @PileDoc("Returns true if the argument is a function, false otherwise.")
    @RenamedMethod("function?")
    public static boolean isFunction(Object o) {
        return (o instanceof PileMethod);
    }
    
    @PileDoc("Loads, but does not initialize, the full class name defined by the provided string and defines a shortname symbol with class as its value.")
    public static void load_class(String s) throws ClassNotFoundException {
        Class<?> clazz = Helpers.loadClass(s);
        Namespace ns = NativeDynamicBinding.NAMESPACE.deref();
        ns.createClassSymbol(clazz.getSimpleName(), clazz);        
    }
    
    // ===============
    // Protocol
    // ===============
    
    public static List<Class<?>> extenders(Class<?> protoClass) {
        ProtocolRecord pr = RuntimeRoot.getProtocolMetadata(protoClass);
        if (pr == null) {
            return null;
        }
        List<Class<?>> local = new ArrayList<>();
        pr.extendsClasses().keySet().forEach(local::add);
        return PersistentVector.fromList(local);
    }
    
    @RenamedMethod("extends?")
    public static boolean extendsProtocol(Class<?> protocolClass, Class<?> type) {
        if (protocolClass != null && protocolClass.isAssignableFrom(type)) {
            return true;
        }
        ProtocolRecord meta = RuntimeRoot.getProtocolMetadata(protocolClass);
        return meta.findImplClass(type).isPresent();
    }
    
    @RenamedMethod("satisfies?")
    public static boolean satisfiesProtocol(Class<?> protocolClass, Object val) {
        if (val == null) {
            return false;
        }
        return extendsProtocol(protocolClass, val.getClass());
    }
    
    public static void prefer_protocol(Class proto, Class higher, Class lower) {
        RuntimeRoot.addPreference(proto, higher, lower);
    }

    private static class EnumeratedSpliterator implements Spliterator {

        private boolean initialState = true;
        private Spliterator delegate;

        private int index;

        public EnumeratedSpliterator(Spliterator delegate, int index) {
            super();
            this.delegate = delegate;
            this.index = index;
        }

        @Override
        public boolean tryAdvance(Consumer action) {
            initialState = false;
            int local = index;
            Consumer c = (v) -> action.accept(PersistentVector.createArr(local, v));
            boolean hasElem = delegate.tryAdvance(c);
            ++index;
            return hasElem;
        }

        @Override
        public Spliterator trySplit() {
            // if the spliterator is ordered but not subsized we're never going to know how
            // many elements to skip when we do a split, so no splits.
            if (initialState && delegate.hasCharacteristics(SUBSIZED)) {
                Spliterator maybe = delegate.trySplit();
                if (maybe != null) {
                    index += maybe.estimateSize();
                    return maybe;
                }
            }
            return null;
        }

        @Override
        public long estimateSize() {
            return delegate.estimateSize();
        }

        @Override
        public int characteristics() {
            return delegate.characteristics();
        }

        public void forEachRemaining(Consumer action) {
            delegate.forEachRemaining(action);
        }

        public long getExactSizeIfKnown() {
            return delegate.getExactSizeIfKnown();
        }

        public boolean hasCharacteristics(int characteristics) {
            return delegate.hasCharacteristics(characteristics);
        }

        public Comparator getComparator() {
            return delegate.getComparator();
        }

    }

    public static Stream enumerate_stream(Stream in) {
        if (in.isParallel()) {
            Spliterator split = in.spliterator();
            Stream out;
            if (split.hasCharacteristics(Spliterator.ORDERED)) {
                Spliterator withEnum = new EnumeratedSpliterator(split, 0);
                out = StreamSupport.stream(withEnum, true);
            } else {
                out = StreamSupport.stream(split, true);
                var count = new AtomicInteger();
                out = out.map(v -> PersistentVector.createArr(count.getAndIncrement(), v));
            }
            out.onClose(in::close);
            return out;
        } else {
            var count = new AtomicInteger();
            return in.map(v -> PersistentVector.createArr(count.getAndIncrement(), v));
        }
    }
    
    private static class RangeSeq extends AbstractSeq implements Streamable, ReversibleSeq {

        private final int cur, max;
        
        public RangeSeq(int start, int count) {
            this.cur = start;
            this.max = count;
        }

        @Override
        public Object first() {
            return cur;
        }

        @Override
        public ISeq next() {
            if (cur + 1 == max) {
                return ISeq.EMPTY;
            } else {
                return new RangeSeq(cur + 1, max);
            }
        }

        @Override
        public Stream toStream() {
            return IntStream.range(cur, max).boxed();
            
        }

        @Override
        public ISeq reverse() {
            return new RangeReverseSeq(max - 1, cur, max);
        }
    
    }
    
    private static class RangeReverseSeq extends AbstractSeq implements Streamable, ReversibleSeq {

        private final int cur, min, max;

        public RangeReverseSeq(int cur, int min, int max) {
            super();
            this.cur = cur;
            this.min = min;
            this.max = max;
        }

        @Override
        public Object first() {
            return cur;
        }

        @Override
        public ISeq next() {
            if (cur == min) {
                return ISeq.EMPTY;
            } else {
                return new RangeReverseSeq(cur - 1, min, max);
            }
        }

        @Override
        public Stream toStream() {
            return IntStream.iterate(cur, i -> i >= min, i -> i - 1).boxed();
        }

        @Override
        public ISeq reverse() {
            return new RangeSeq(min, cur);
        }
    
    }
    
    public static ISeq range(int count) {
        return range(0, count);
    }
    
    public static ISeq range(int start, int count) {
        if (start == count) {
            return ISeq.EMPTY;
        } else {
            return new RangeSeq(start, count);
        }
    }
    
    // Coroutine
    
    @PileDoc("""
            Creates a new coroutine calling the provided function which may yield values.
            The coroutine does not start running until execution is resumed the first time.
            """)
    public static Coroutine coroutine(PCall fn) {
        var c = new Coroutine(new CoroutineSync(), fn);
        c.run();
        return c;
    }
    
    @PileDoc("""
             Yields a value within a coroutine and waits for the next resume. Must only be called while executing a coroutine, 
             otherwise an IllegalStateException is thrown.
             """)
    public static void yield(Object o) throws InterruptedException {
        CoroutineSync sync;
        try {
            sync = Coroutine.SYNC_LOCAL.get();
        } catch (NoSuchElementException e) {
            throw new IllegalStateException("Cannot yield; not in a coroutine.");
        }
        sync.putValueAndSleep(o);
    }
    
    @PileDoc("""
            Resumes execution of a coroutine. This call blocks until the coroutine yields a value, terminates, or throws an exception. 
            This method returns either the yielded value, nil if the coroutine completed, or throws a PileExecutionException wrapping
            the exception thrown in the coroutine.
            """)
    public static Object resume(Coroutine c) throws InterruptedException {
        return c.resume();
    }
    
}
