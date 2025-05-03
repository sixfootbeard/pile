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

import static pile.util.CollectionUtils.*;

import java.io.InputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import pile.collection.PersistentCollection;
import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.collection.PersistentSet;
import pile.collection.PersistentVector;
import pile.compiler.Compiler;
import pile.compiler.Helpers;
import pile.compiler.ParameterParser.ParameterList;
import pile.compiler.math.BinaryComparisonMethod;
import pile.compiler.math.BinaryMathMethod;
import pile.compiler.math.BinaryPredicateMethod;
import pile.compiler.math.NumberHelpers;
import pile.compiler.math.NumberMethods;
import pile.compiler.math.ShiftMethod;
import pile.compiler.math.UnaryMathMethod;
import pile.compiler.math.finder.BinaryMethodFinder;
import pile.compiler.math.finder.NegateMethodFinder;
import pile.compiler.math.finder.OverflowBinaryMathMethodFinder;
import pile.compiler.math.finder.UnaryMathMethodFinder;
import pile.core.binding.Binding;
import pile.core.binding.BindingType;
import pile.core.binding.ImmutableBinding;
import pile.core.binding.NativeDynamicBinding;
import pile.core.exception.PileCompileException;
import pile.core.exception.PileInternalException;
import pile.core.exception.UnlinkableMethodException;
import pile.core.indy.PileMethodLinker;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.method.FunctionUtils;
import pile.core.method.HiddenNativeMethod;
import pile.core.method.LinkableMethod;
import pile.core.parse.ParserConstants;
import pile.core.parse.PileParser;
import pile.nativebase.IndirectMethod;
import pile.nativebase.NativeArrays;
import pile.nativebase.NativeAsync;
import pile.nativebase.NativeBinding;
import pile.nativebase.NativeCore;
import pile.nativebase.NativeData;
import pile.nativebase.NativeMacro;
import pile.nativebase.NativeMath;
import pile.nativebase.NativeString;
import pile.nativebase.NativeTime;
import pile.nativebase.NoLink;
import pile.nativebase.PileDoc;
import pile.nativebase.Precedence;
import pile.nativebase.PureFunction;
import pile.nativebase.RenamedMethod;
import pile.util.CollectionUtils;
import pile.util.CommonConstants;
import pile.util.Pair;

/**
 * Loads all the methods available as part of the stdlib (pile.core.*) once.
 * <ol>
 * <li>Precompiled {@link #MATH_METHODS}.
 * <li>Native Methods ({@link NativeCore}, et al)
 * <li>{@link NativeDynamicBinding Native Dynamic Bindings}
 * <li>Pile Standard Library (core.pile, et al)
 * </ol>
 *
 */
public class StandardLibraryLoader {

    private static final Logger LOG = LoggerSupplier.getLogger(StandardLibraryLoader.class);

    private static final Library CORE_LIBRARY = 
            new Library(CommonConstants.PILE_CORE_NS, List.of(NativeMath.class, NativeCore.class, NativeArrays.class), "/pile/core.pile");

    // @formatter:off
    private static final List<Library> EXTRA_LIBRARIES = List.of(
            new Library("pile.core.time", List.of(NativeTime.class), "/pile/time.pile"),
            new Library("pile.core.string", List.of(NativeString.class), "/pile/string.pile"),
            new Library("pile.core.async", List.of(NativeAsync.class), "/pile/async.pile"),
            new Library("pile.core.io", List.of(), "/pile/io.pile"),
            new Library("pile.core.data", List.of(NativeData.class))
    ); 
    
    // Symbols will exist as their short name
    private static final Set<Class<?>> CLASS_SYMBOLS = Set.of(String.class, Integer.class, Long.class, 
            Short.class, Date.class, RuntimeException.class, Throwable.class, Exception.class, System.class,
            Pattern.class, Character.class, Number.class, Boolean.class, Object.class, Stream.class,
            Class.class,
            // TODO Remove
            PrintStream.class,
            // Pile 
            PersistentCollection.class, PersistentVector.class, PersistentMap.class, PersistentList.class,
            PersistentSet.class, Symbol.class, Keyword.class, ISeq.class, Seqable.class, Var.class,
            Multimethod.class, Namespace.class, LazySequence.class,
            // Exceptions
            UnlinkableMethodException.class, IllegalArgumentException.class
            );
    private static final Map<String, Class<?>> RENAMED_CLASSES = Map.of("long", Long.TYPE, "int", Integer.TYPE, "short",
            Short.TYPE, "byte", Byte.TYPE, "float", Float.TYPE, "double", Double.TYPE);
    private static final Map<String, Class<?>> ARRAY_CLASSES = Map.of("longs", long[].class, "ints", int[].class, "shorts",
            short[].class, "bytes", byte[].class, "floats", float[].class, "doubles", double[].class, "objects", Object[].class
            // Arrays
            );
                       
    private static Map<String, PileMethod> CORE_METHODS = Map.of(
//            "aget", new ArrayGetMethod()
    );
    
    private static final BinaryMethodFinder OVERFLOW_MATH_METHOD = new OverflowBinaryMathMethodFinder(Integer.TYPE);
    
    private static final UnaryMathMethodFinder UNARY_DOUBLE = _ -> Optional.of(MethodType.methodType(Double.TYPE, Double.TYPE));

    private static final List<NumberOp> MATH_METHODS = List.of(
            new NumberOp("+", new BinaryMathMethod("plus")),
            new NumberOp("-", new BinaryMathMethod("minus")),
            new NumberOp("*", new BinaryMathMethod("multiply")),
            new NumberOp("/", new BinaryMathMethod("divide")),
            new NumberOp("rem", new BinaryMathMethod("remainder")),
            // new NumberOp("==", new ComparisonMethod("numEquals")),
            
            // overflow
            new NumberOp("+'", new BinaryMathMethod(NumberMethods.class, "plusOverflow", 
                    OVERFLOW_MATH_METHOD)),
            new NumberOp("-'", new BinaryMathMethod(NumberMethods.class, "minusUnderflow", 
                    OVERFLOW_MATH_METHOD)),
            new NumberOp("*'", new BinaryMathMethod(NumberMethods.class, "multiplyOverflow", 
                    OVERFLOW_MATH_METHOD)),
            new NumberOp("/'", new BinaryMathMethod(NumberMethods.class, "divideOverflow", 
                    OVERFLOW_MATH_METHOD)),
            
            // 
            new NumberOp("compareNum", new BinaryComparisonMethod("compare")), 
            new NumberOp("negate", new UnaryMathMethod(NumberMethods.class, "negate",
                    new NegateMethodFinder(UnaryMathMethodFinder.EXACT))), 
            new NumberOp("negate'", new UnaryMathMethod(NumberMethods.class, "negateSafe", 
                    new NegateMethodFinder(UnaryMathMethodFinder.NUMBER))), 
            
        
            new NumberOp("<", new BinaryPredicateMethod("lessThan")),
            new NumberOp(">", new BinaryPredicateMethod("greaterThan")),
            new NumberOp("<=", new BinaryPredicateMethod("lessThanEquals")),
            new NumberOp(">=", new BinaryPredicateMethod("greaterThanEquals")),
            
            new NumberOp("abs", new UnaryMathMethod(Math.class, "abs", UnaryMathMethodFinder.only(UnaryMathMethodFinder.EXACT, NumberHelpers.getDFLI()::contains))),
            
            new NumberOp("acos", new UnaryMathMethod(Math.class, "acos", UNARY_DOUBLE)),
            new NumberOp("asin", new UnaryMathMethod(Math.class, "asin", UNARY_DOUBLE)),
            new NumberOp("atan", new UnaryMathMethod(Math.class, "atan", UNARY_DOUBLE)),
            new NumberOp("cos", new UnaryMathMethod(Math.class, "cos", UNARY_DOUBLE)),
            new NumberOp("cosh", new UnaryMathMethod(Math.class, "cosh", UNARY_DOUBLE)),
            new NumberOp("log", new UnaryMathMethod(Math.class, "log", UNARY_DOUBLE)),
            new NumberOp("log10", new UnaryMathMethod(Math.class, "log10", UNARY_DOUBLE)),
            new NumberOp("sin", new UnaryMathMethod(Math.class, "sin", UNARY_DOUBLE)),
            new NumberOp("sinh", new UnaryMathMethod(Math.class, "sinh", UNARY_DOUBLE)),
            new NumberOp("tan", new UnaryMathMethod(Math.class, "tan", UNARY_DOUBLE)),
            new NumberOp("tanh", new UnaryMathMethod(Math.class, "tanh", UNARY_DOUBLE)),
            
            new NumberOp("bit-shift-left", new ShiftMethod("shiftLeft")),
            new NumberOp("bit-shift-right", new ShiftMethod("shiftRight")),
            new NumberOp("bit-shift-right-logical", new ShiftMethod("shiftRightLogical")));
    // @formatter:on

    // Guarded by static class lock
    private static boolean isLoaded = false;

    /**
     * Load the root namespace. Threadsafe.
     */
    public static synchronized void loadRoot() {
        try {
            if (isLoaded) {
                return;
            }
            LOG.debug("Loading Root (once)");
            long start = System.currentTimeMillis();

            // TODO security

            Lookup lookup = MethodHandles.lookup();

            final Map<String, Namespace> namespaces = new HashMap<>();

            Namespace rootNs = new Namespace(CommonConstants.PILE_CORE_NS);
            namespaces.put(rootNs.getName(), rootNs);
            RuntimeRoot.defineRoot(rootNs.getName(), rootNs);

            loadCoreValues(rootNs);
            loadMathMethods(rootNs);
            loadCoreMethods(rootNs);

            // Dynamics
            for (var dyn : NativeDynamicBinding.values()) {
                rootNs.define(dyn.getName(), dyn);
            }

            // Class symbols
            CLASS_SYMBOLS.forEach(cs -> rootNs.createClassSymbol(cs.getSimpleName(), cs));
            RENAMED_CLASSES.forEach((name, clazz) -> rootNs.createClassSymbol(name, clazz));
            ARRAY_CLASSES.forEach((name, clazz) -> rootNs.createClassSymbol(name, clazz));

            // Native protos
            // loadSeq(lookup, rootNs);

            long nativeTime = 0;
            long parseTime = 0;
            long compileTime = 0;

            // Core: native + source
            var lr = loadLibrary(lookup, namespaces, CORE_LIBRARY);
            nativeTime += lr.nativeTime();
            parseTime += lr.parseTime();
            compileTime += lr.compiledTime();

            for (var extraLib : EXTRA_LIBRARIES) {
                Namespace ns = new Namespace(extraLib.ns(), List.of(rootNs));
                namespaces.put(ns.getName(), ns);
                RuntimeRoot.define(ns.getName(), ns);
                var cl = loadLibrary(lookup, namespaces, extraLib);

                nativeTime += cl.nativeTime();
                parseTime += cl.parseTime();
                compileTime += cl.compiledTime();
            }

            long end = System.currentTimeMillis();
            // FIXME Debug
            LOG.info("Loaded total stdlib in %d ms [native=%dms, parsed=%dms, compiled=%dms]", end - start, nativeTime,
                    parseTime, compileTime);

            // TODO Exception handling
            isLoaded = true;
        } catch (Throwable t) {
            t.printStackTrace();
            // TODO Exit?
        }
    }

    private static LoadResult loadLibrary(Lookup lookup, Map<String, Namespace> nativeNamespaces, Library lib)
            throws IllegalAccessException, InvocationTargetException {
        Namespace ns = nativeNamespaces.computeIfAbsent(lib.ns(), Namespace::new);

        try (var curNs = NativeDynamicBinding.NAMESPACE.withUpdate(ns)) {
            
            LibraryMethods results = new LibraryMethods();

            long nativeStart = System.currentTimeMillis();
            // native methods
            for (var clz : lib.clazz()) {
                results = results.merge(gatherMethods(clz));
            }
            
            for (var e : results.methods().entrySet()) {
                define(ns, lookup, e.getKey(), e.getValue());
            }
            for (var e : results.indirectMethods().entrySet()) {
                defineIndirect(ns, lookup, e.getKey(), e.getValue());
            }

            // native bindings
            List<Pair<String, Binding>> nativeBinds = new ArrayList<>();
            for (var clz : lib.clazz()) {
                gatherBindings(clz, nativeBinds);
            }
            for (var pair : nativeBinds) {
                ns.define(pair.left(), pair.right());
            }

            long mid = System.currentTimeMillis();
            long nativeTime = mid - nativeStart;

            long parseTime = 0;
            long compileTime = 0;

            // source files
            for (var resourceName : lib.sourceFiles()) {
                LOG.debug("Loading core source: %s", resourceName);
                URL resource = RuntimeRoot.class.getResource(resourceName);
                int lastIndexOf = resourceName.lastIndexOf("/");
                String fname = resourceName.substring(lastIndexOf + 1);

                // Who thought this API was a good idea
                if (resource != null) {
                    try (InputStream stream = resource.openStream();
                            var ig = NativeDynamicBinding.COMPILE_FILENAME.withUpdate(fname)) {
                        PileParser parser = new PileParser();

                        long startParse = System.currentTimeMillis();
                        LOG.debug("Parsing %s", resourceName);
                        PersistentList forms = parser.parse(stream, resourceName);
                        long libParseTime = System.currentTimeMillis() - startParse;
                        parseTime += libParseTime;
                        LOG.debug("Parsed %s in %d ms", resourceName, libParseTime);

                        long startCompile = System.currentTimeMillis();
                        Compiler.evaluate(forms);
                        long libCompileTime = System.currentTimeMillis() - startCompile;
                        compileTime += libCompileTime;
                        LOG.debug("Compiled %s in %d ms", resourceName, libCompileTime);

                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }

                } else {
                    throw new PileCompileException("Missing core source: " + resourceName);
                }
            }

            return new LoadResult(ns, parseTime, nativeTime, compileTime);
        }

    }

    private static void gatherBindings(Class<?> clz, List<Pair<String, Binding>> nativeBinds) {
        Arrays.stream(clz.getFields()).filter(f -> (f.getModifiers() & Modifier.STATIC) > 0)
                .filter(f -> f.getAnnotation(NativeBinding.class) != null).map(f -> {
                    NativeBinding anno = f.getAnnotation(NativeBinding.class);
                    String name = anno.value();
                    Binding bind;
                    try {
                        bind = (Binding) f.get(clz);
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        throw new PileInternalException(
                                "Native binding must be, naturally, actually a binding (what are you doing?).", e);
                    }
                    return new Pair<>(name, bind);
                }).forEach(nativeBinds::add);
        ;

    }

    private static void loadCoreMethods(Namespace ns) {
        String nsName = ns.getName();

        for (var rec : CORE_METHODS.entrySet()) {
            PersistentMap meta = PersistentMap.EMPTY;
            meta = meta.assoc(PileMethodLinker.FINAL_KEY, true);
            
            var methodName = rec.getKey();

            ImmutableBinding bind = new ImmutableBinding(nsName, BindingType.VALUE, rec.getValue(), meta,
                    new SwitchPoint());

            ns.define(methodName, bind);
        }
        
    }
    
    private static void loadCoreValues(Namespace ns) {
        for (var entry : NativeValue.values()) {
            var name = entry.getName();
            var value = entry.getValue();
            PersistentMap meta = PersistentMap.EMPTY;
            meta = meta.assoc(PileMethodLinker.FINAL_KEY, true);

            ImmutableBinding bind = new ImmutableBinding(ns.getName(), BindingType.VALUE, value, meta, new SwitchPoint());

            ns.define(name, bind);
        }
    }

    private static void loadMathMethods(Namespace ns) {
        String nsName = ns.getName();

        for (NumberOp rec : MATH_METHODS) {
            PersistentMap meta = PersistentMap.EMPTY;
            meta = meta.assoc(PileMethodLinker.FINAL_KEY, true);

            ImmutableBinding bind = new ImmutableBinding(nsName, BindingType.VALUE, rec.methodName(), meta,
                    new SwitchPoint());

            ns.define(rec.sym(), bind);
        }
    }

//    private static void loadSeq(Lookup lookup, Namespace ns) throws IllegalAccessException {
//        final String methodName = "seq*";
//        
//        var seqMethod = Protocols.SEQ;
//        
//        Binding bind = new ImmutableBinding(ns.getName(), BindingType.VALUE, Protocols.SEQ, PersistentMap.EMPTY,
//                new SwitchPoint());
//        ns.define(methodName, bind);
//        RuntimeRoot.defineProtocol(Seqable.class);
//        RuntimeRoot.updateProtocolMetadata(Seqable.class, classMethodMap -> {
//            classMethodMap.put(Iterable.class, Map.of(methodName, FunctionUtils.of(Iterable.class, ISeq.class, iter -> (ISeq) seqMethod.invoke(iter.iterator()))));
//            classMethodMap.put(Iterator.class, Map.of(methodName, FunctionUtils.of(Iterator.class, ISeq.class, iter -> NativeCore.seqIterator((Iterator) iter))));
//            classMethodMap.put(ISeq.class, Map.of(methodName, FunctionUtils.of(ISeq.class, ISeq.class, iseq -> iseq)));
//            classMethodMap.put(Map.class, Map.of(methodName, FunctionUtils.of(Map.class, ISeq.class, map -> NativeCore.seqIterator(map.entrySet().iterator()))));
//            classMethodMap.put(CharSequence.class, Map.of(methodName, FunctionUtils.of(CharSequence.class, ISeq.class, cs -> ISeq.seqSized(cs, 0, cs.length(), (b, i) -> b.charAt(i)))));
//            classMethodMap.put(Map.Entry.class, Map.of(methodName, FunctionUtils.of(Map.Entry.class, ISeq.class, e -> ISeq.of(e.getKey(), e.getValue()))));
//            classMethodMap.put(Stream.class, Map.of(methodName, FunctionUtils.of(Stream.class, ISeq.class, s -> (ISeq) seqMethod.invoke(s.iterator()))));
//            return classMethodMap;
//        });
//    }

    private static LibraryMethods gatherMethods(final Class<?> staticBase) {
        Map<String, List<Method>> methodNames = new HashMap<>();
        Map<String, Method> indirectMethods = new HashMap<>();

        Method[] methods = staticBase.getMethods();
        for (Method m : methods) {
            int mods = m.getModifiers();
            if (Modifier.isStatic(mods) && Modifier.isPublic(mods)) {

                String name = m.getName();
                name = name.replaceAll("_", "-");
                RenamedMethod renamed = m.getAnnotation(RenamedMethod.class);
                if (renamed != null) {
                    name = renamed.value();
                }
                if (m.isAnnotationPresent(NoLink.class)) {
                    continue;
                }
                if (m.isAnnotationPresent(IndirectMethod.class)) {
                    if (indirectMethods.put(name, m) != null) {
                        throw new IllegalArgumentException("Only one indirect method name allowed:" + name);
                    }
                } else {
                    methodNames.computeIfAbsent(name, k -> new ArrayList<>()).add(m);
                }
            }
        }
        return new LibraryMethods(methodNames, indirectMethods);
    }

    private static void define(Namespace ns, Lookup lookup, String name, List<Method> methods)
            throws IllegalAccessException {

        Map<Integer, List<Method>> mm = methods.stream()
                .map(m -> new Pair<>(m.getParameterCount(), m))
                .collect(CollectionUtils.toMultiMap());

        final Map<Integer, List<MethodHandle>> airityHandles = new HashMap<>();
        final List<PersistentVector<Symbol>> argLists = new ArrayList<>();
        MethodHandle varArgsMethod = null;
        int varArgsAirity = -1;

        Class<?> parent = null;
        
        AllSet macroSet = new AllSet();
        AllSet pureSet = new AllSet();
        String classSrc = null;
        String doc = null;

        for (var arity : mm.entrySet()) {
            Integer args = arity.getKey();
            List<Method> methodList = arity.getValue();
            if (methodList.size() > 1) {
                if (any(methodList, m -> m.getAnnotation(Precedence.class) == null)) {
                    throw new PileInternalException("Method " + name + " should have a @Precendence");
                }
                Collections.sort(methodList, Comparator.comparingInt(m -> m.getAnnotation(Precedence.class).value()));
            }
            for (var method : methodList) {
                PersistentVector<Symbol> argList = createArgList(method);
                argLists.add(argList);
                
                MethodHandle mh = lookup.unreflect(method);
                MethodType methodType = mh.type();
                int parameterCount = methodType.parameterCount();
                if (mh.isVarargsCollector()) {
                    if (varArgsMethod != null) {
                        throw new PileInternalException("Can only have one varargs native: " + name);
                    }
                    mh = filterVoidReturns(mh);
                    // reset varargs in case this was void filtered.
                    mh = mh.asVarargsCollector(Object[].class);
                    varArgsMethod = mh;
                    varArgsAirity = parameterCount - 1;
                } else {
                    mh = filterVoidReturns(mh);
                    airityHandles.computeIfAbsent(parameterCount, k -> new ArrayList<>()).add(mh);
                }

                boolean hasMacroAnno = method.isAnnotationPresent(NativeMacro.class);
                boolean hasPureAnno = method.isAnnotationPresent(PureFunction.class);

                macroSet.update(hasMacroAnno);
                pureSet.update(hasPureAnno);
                
                PileDoc methodDoc = method.getAnnotation(PileDoc.class);
                if (methodDoc != null) {
                    doc = methodDoc.value();
                }

                if (parent == null) {
                    parent = mh.type().returnType();
                } else {
                    parent = Helpers.findParentType(parent, mh.type().returnType());
                }
                if (classSrc == null) {
                    classSrc = method.getDeclaringClass().getName();
                }
            }
        }
        
        boolean macro = macroSet.get(false, "Macro setting must be set on all or none of the methods");
        boolean pure = pureSet.get(false, "Pure must be set on all or none of the methods");

        HiddenNativeMethod nativeMethod = new HiddenNativeMethod(airityHandles, varArgsAirity, varArgsMethod, parent, pure);

        PersistentMap meta = PersistentMap.EMPTY;
        meta = meta.assoc(PileMethodLinker.FINAL_KEY, true);
        meta = meta.assoc(PileMethodLinker.MACRO_KEY, macro);
        meta = meta.assoc(CommonConstants.NATIVE_SOURCE, true);
        meta = meta.assoc(CommonConstants.ARG_LIST, argLists);
        
        if (classSrc != null) {
            meta = meta.assoc(ParserConstants.FILENAME_KEY, classSrc);
        }
        if (!Object.class.equals(parent)) {
            meta = meta.assoc(PileMethodLinker.RETURN_TYPE_KEY, parent);
        }
        if (doc != null) {
            meta = meta.assoc(CommonConstants.DOC, doc);
        }
        Binding meth = new ImmutableBinding(ns.getName(), BindingType.VALUE, nativeMethod, meta, new SwitchPoint());

        ns.define(name, meth);
    }

    private static void defineIndirect(Namespace ns, Lookup lookup, String methodName, Method method) throws IllegalAccessException, InvocationTargetException  {
        LinkableMethod fn = (LinkableMethod) method.invoke(null);
        
        String doc = null;
        PileDoc methodDoc = method.getAnnotation(PileDoc.class);
        if (methodDoc != null) {
            doc = methodDoc.value();
        }
        
        PersistentMap meta = PersistentMap.EMPTY;
        meta = meta.assoc(PileMethodLinker.FINAL_KEY, true);
        meta = meta.assoc(PileMethodLinker.MACRO_KEY, method.isAnnotationPresent(NativeMacro.class));
        meta = meta.assoc(ParserConstants.FILENAME_KEY, method.getDeclaringClass().toString());
        meta = meta.assoc(CommonConstants.NATIVE_SOURCE, true);

        if (doc != null) {
            meta = meta.assoc(CommonConstants.DOC, doc);
        }
        
        Binding meth = new ImmutableBinding(ns.getName(), BindingType.VALUE, fn, meta, new SwitchPoint());
        ns.define(methodName, meth);
    }

    private static PersistentVector<Symbol> createArgList(Method method) {
        List<Symbol> out = new ArrayList<>();
        boolean isVarArg = method.isVarArgs();
        for (Parameter p : method.getParameters()) {
            String name = p.getName();
            Class<?> type = p.getType();
            Symbol sym = new Symbol(name);
            if (! Object.class.equals(type)) {
                sym = sym.withTypeAnnotation(type);
            }
            out.add(sym);
        }
        if (method.isVarArgs()) {
            Symbol last = out.remove(out.size() - 1);
            out.add(new Symbol("&"));
            out.add(new Symbol(last.getName()));
        }
        return PersistentVector.fromList(out);
    }

    private static MethodHandle filterVoidReturns(MethodHandle mh) {
        if (mh.type().returnType().equals(void.class)) {
            // Fix void returns to make sure they return null instead of nothing
            mh = MethodHandles.filterReturnValue(mh, MethodHandles.constant(Object.class, null));
        }
        return mh;
    }

    private static class AllSet {
        private boolean invalid;
        private Boolean set = null;
    
        private void update(boolean newVal) {
            if (set == null) {
                set = newVal;
            } else {
                if (set ^ newVal) {
                    invalid = true;
                }
            }
        }
    
        private Boolean get(boolean defaultVal, String err) {
            if (invalid) {
                throw new IllegalArgumentException(err);
            } else if (set == null) {
                return defaultVal;
            } else {
                return set;
            }
        }
    }

    private static record LoadResult(Namespace loadedNs, long parseTime, long nativeTime, long compiledTime) {
    }

    /**
     * Native + sources
     * 
     * @author john
     *
     */
    private record Library(String ns, List<Class<?>> clazz, String... sourceFiles) {
    }
    
    private record LibraryMethods(Map<String, List<Method>> methods, Map<String, Method> indirectMethods) {
        public LibraryMethods() {
            this(Collections.emptyMap(), Collections.emptyMap());
        }
        
        public LibraryMethods merge(LibraryMethods other) {
            HashSet ourKeys = new HashSet<>(methods.keySet());
            HashSet theirKeys = new HashSet<>(other.methods.keySet());
            ourKeys.retainAll(theirKeys);
            if (! ourKeys.isEmpty()) {
                throw new IllegalArgumentException("Duplicate key in merge: " + ourKeys);
            }
        
            Map<String, List<Method>> newMethods = new HashMap<>();
            newMethods.putAll(methods());
            newMethods.putAll(other.methods()); 
            
            Map<String, Method> newIndirectMethods = new HashMap<>();
            newIndirectMethods.putAll(indirectMethods());
            newIndirectMethods.putAll(other.indirectMethods());
            
            return new LibraryMethods(newMethods, newIndirectMethods);            
        }
    }

    private record NumberOp(String sym, PileMethod methodName) {
    }

}
