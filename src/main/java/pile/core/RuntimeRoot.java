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
import java.lang.invoke.SwitchPoint;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import pile.collection.PersistentList;
import pile.compiler.Compiler;
import pile.compiler.MethodCollector.MethodArity;
import pile.compiler.form.Nil;
import pile.compiler.typed.TypedHelpers;
import pile.core.binding.NativeDynamicBinding;
import pile.core.exception.UnlinkableMethodException;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.method.HiddenCompiledMethod;
import pile.core.parse.PileParser;

/**
 * Holds all the {@link Namespace}s that make up the runtime of this language.
 *
 */
public class RuntimeRoot {

    private static final Logger LOG = LoggerSupplier.getLogger(RuntimeRoot.class);

    // Roots are namespaces that are always 'available' to newly created namespaces.
    // Functionally, Namespace#referFrom is called.
    private static final List<Namespace> ROOTS = new CopyOnWriteArrayList<>();
    private static final Map<String, Namespace> NAMESPACES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, ProtocolRecord> PROTOCOLS = new ConcurrentHashMap<>();

    /**
     * Only allow one thread to initialize a namespace.
     */
    private static final Map<String, CountDownLatch> LOADING_NS = new ConcurrentHashMap<>();

    // Reentrant TL
    private static final ThreadLocal<String> DEFINING_NS = new ThreadLocal<>();

    // above needs initialized before this is called.
    static {
        StandardLibraryLoader.loadRoot();
    }

    public static Namespace defineOrGet(String name) {
        var oldNs = NativeDynamicBinding.NAMESPACE.deref();
        try {
            CountDownLatch ourLatch = new CountDownLatch(1);
            CountDownLatch old = LOADING_NS.putIfAbsent(name, ourLatch);
            if (old == null) {
                LOG.debug("Creating namespace: %s", name);
                var oldDef = DEFINING_NS.get();
                DEFINING_NS.set(name);
                try {
                    // Natives first
                    NAMESPACES.putIfAbsent(name, new Namespace(name, ROOTS));
                    loadClasspathFile(name);
                } finally {
                    ourLatch.countDown();
                    DEFINING_NS.set(oldDef);
                    LOG.debug("Completed creating namespace: %s", name);
                }
            } else {
                var maybeDefining = DEFINING_NS.get();
                if (!name.equals(maybeDefining)) {
                    LOG.debug("Waiting on load: ns=%s, defining=%s", name, maybeDefining);
                    try {
                        old.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted waiting for ns to load");
                    }
                }
            }
            return NAMESPACES.get(name);
        } finally {
            if (oldNs != null) {
                NativeDynamicBinding.NAMESPACE.set(oldNs);
            }
        }
    }

    public static Namespace get(String name) {
        var maybeDefining = DEFINING_NS.get();
        if (!name.equals(maybeDefining)) {
            try {
                CountDownLatch latch = LOADING_NS.get(name);
                if (latch != null) {
                    if (latch.getCount() > 0) {
                        // racy but w/e
                        LOG.debug("Waiting on ns: %s", name);
                    }
                    latch.await();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted waiting for ns to load");
            }
        }
        return NAMESPACES.get(name);
    }

    /**
     * A root namespace is expected to only be defined once by convention and is
     * automatically imported into all other namespaces.
     * 
     * @param nsStr
     * @param ns
     */
    static void defineRoot(String nsStr, Namespace ns) {
        if (!ROOTS.add(ns)) {
            throw new IllegalArgumentException("Cannot redefine root namespace: " + nsStr);
        }
        LOG.debug("Defining (autoimported) root namespace: %s", nsStr);

        // By convention, root namespace are only loaded during standard library
        // initialization so they don't need to be protected. Also, some of the
        // initialization code will attempt to get the root namespace while loading so
        // we don't want to deadlock.
        LOADING_NS.put(nsStr, new CountDownLatch(0));
        NAMESPACES.put(nsStr, ns);
    }

    static void define(String nsStr, Namespace ns) {
        LOG.debug("Defining root namespace: %s", nsStr);
        LOADING_NS.put(nsStr, new CountDownLatch(0));
        NAMESPACES.put(nsStr, ns);
    }

    public static void require(String name) {
        defineOrGet(name);
    }

    public static ProtocolRecord getProtocolMetadata(Class<?> protocol) {
        return PROTOCOLS.get(protocol);
    }
    
    public static void defineProtocol(Class<?> clazz, Map<ProtocolMethodDescriptor, Boolean> protoMethodsToDefault,
            Map<String, HiddenCompiledMethod> companionMethods) {
        PROTOCOLS.put(clazz, new ProtocolRecord(clazz, protoMethodsToDefault, new LinkedHashMap<>(), companionMethods, new HashMap<>(), new SwitchPoint()));
    }
    
    public static void extendProtocol(Class<?> protocolClass, Class<?> extendsClass,
            Map<String, PileMethod> methodMap) {
        var key = extendsClass == null ? Nil.class : extendsClass;
        AtomicReference<SwitchPoint> ref = new AtomicReference<>();
        PROTOCOLS.compute(protocolClass, (k, v) -> {
            if (v == null) {
                throw new IllegalStateException("Undefined protocol: " + protocolClass);
            }
            ref.set(v.switchPoint());
            
            LinkedHashMap<Class, Map<String, PileMethod>> newCMap = new LinkedHashMap<>(v.extendsClasses());
            newCMap.put(key, methodMap);
            
            return v.withExtendsClasses(newCMap);            
        });

        SwitchPoint maybe = ref.get();
        if (maybe != null) {
            SwitchPoint.invalidateAll(new SwitchPoint[] { maybe });
        }
    }

    public static void extendProtocolMetadata(Class<?> protocol, Class<?> extendsClass, Map<String, HiddenCompiledMethod> methodMap) {
          
        AtomicReference<SwitchPoint> ref = new AtomicReference<>();
        PROTOCOLS.compute(protocol, (k, v) -> {
            Map<String, HiddenCompiledMethod> methodCopy = new HashMap<>(methodMap);
            Map<String, PileMethod> out = new HashMap<>(methodMap);
            for (var defaultMethod : v.companionMethods().entrySet()) {
                String defaultName = defaultMethod.getKey();
                HiddenCompiledMethod arity = defaultMethod.getValue();
                HiddenCompiledMethod maybeHCM = methodCopy.get(defaultName);
                HiddenCompiledMethod merged = maybeHCM.withDefaults(arity);
                out.put(defaultName, merged);
            }

            ref.set(v.switchPoint());
            
            LinkedHashMap<Class, Map<String, PileMethod>> copied = new LinkedHashMap<>(v.extendsClasses());
            copied.put(extendsClass == null ? Nil.class : extendsClass, out);
            return new ProtocolRecord(protocol, v.protoMethodsToDefault(), copied, v.companionMethods(), 
                    v.preferences(), new SwitchPoint());
        });

        SwitchPoint maybe = ref.get();
        if (maybe != null) {
            SwitchPoint.invalidateAll(new SwitchPoint[] { maybe });
        }
    }

    public static void addPreference(Class<?> protocol, Class<?> higher, Class<?> lower) {
        AtomicReference<SwitchPoint> ref = new AtomicReference<>();
        PROTOCOLS.compute(protocol, (k, v) -> {
            if (v == null) {
                throw new IllegalStateException("Undefined protocol: " + protocol);
            }
            ref.set(v.switchPoint());
    
            Map<Class, Class> prefs = new HashMap<>(v.preferences());
            prefs.put(lower, higher);
            return v.withPreferences(prefs);
        });
    }

    public static PileMethod lookupExtensionClass(ProtocolRecord baseMap, Class<?> rawBase, String methodName) {
        var base = (rawBase == null) ? Nil.class : rawBase;
        return  baseMap.findImplClass(base)
                       .map(c -> baseMap.extendsClasses().get(c))
                       .map(m -> m.get(methodName))
                       .or(() -> getDefaultMethod(baseMap, methodName))
                       .orElseThrow(() -> new UnlinkableMethodException("No suitable protocol impl exists for base=" + base + ", protocol=" + baseMap.protoClass()))
                       ;
    }
    
    private static Optional<HiddenCompiledMethod> getDefaultMethod(ProtocolRecord pr, String methodName) {
        return mget(pr.companionMethods(), methodName);
    }
    
    public static Map<String, Namespace> getMap() {
        return Collections.unmodifiableMap(NAMESPACES);
    }

    /**
     * Only should be called with {@link CountDownLatch}.
     * 
     * @param name
     * @return
     */
    private static boolean loadClasspathFile(String name) {
        // Load rest, if any
        String resourceName = "/" + name.replace('.', '/').concat(".pile");
        URL resource = RuntimeRoot.class.getResource(resourceName);

        try (var fname = NativeDynamicBinding.COMPILE_FILENAME.withUpdate(name + ".pile")) {
            if (resource != null) {
                try (InputStream stream = resource.openStream()) {
                    PersistentList forms = PileParser.parse(stream, resourceName);
                    Compiler.evaluate(forms);
                    return true;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return false;
    }

    public interface UpdateProto {
        public void update(Map<Class, Map<String, PileMethod>> methods, Map<String, MethodArity> companionMethods);
    }


    public record ProtocolMethodDescriptor(String name, int arity, boolean varArgs) {}


    public record ProtocolRecord(Class<?> protoClass, Map<ProtocolMethodDescriptor, Boolean> protoMethodsToDefault, 
            LinkedHashMap<Class, Map<String, PileMethod>> extendsClasses,
            Map<String, HiddenCompiledMethod> companionMethods,
            Map<Class, Class> preferences,
            SwitchPoint switchPoint) {
    
        /**
         * Find the most specific class which satisfies the provided receiver type.
         * 
         * @param base
         * @return
         */
        public Optional<Class> findImplClass(Class<?> base) {
            // Object as a last resort
            var candidates = extendsClasses.keySet().stream()
                                .filter(c -> ! Object.class.equals(c))
                                .filter(c -> c.isAssignableFrom(base))
                                .collect(Collectors.toCollection(LinkedList::new));
            
            if (!preferences().isEmpty()) {
                Iterator<Class> it = candidates.iterator();
                while (it.hasNext()) {
                    Class candidate = it.next();
                    Class higher = preferences().get(candidate);
                    if (higher != null && candidates.contains(higher)) {
                        it.remove();
                    }
                }
            }
            
            return candidates.stream()
                    .reduce(TypedHelpers::chooseNarrow)
                    .or(() -> sget(extendsClasses.keySet(), Object.class));
        }

        public ProtocolRecord withExtendsClasses(LinkedHashMap<Class, Map<String, PileMethod>> newCMap) {
            return new ProtocolRecord(protoClass, protoMethodsToDefault, newCMap, companionMethods, preferences, new SwitchPoint());
        }

        public ProtocolRecord withPreferences(Map<Class, Class> prefs) {
            return new ProtocolRecord(protoClass, protoMethodsToDefault, extendsClasses, companionMethods, prefs, new SwitchPoint());
        }
    
    }
}
