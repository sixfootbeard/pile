package pile.nativebase;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.SwitchPoint;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import pile.collection.PersistentHashMap;
import pile.core.Namespace;
import pile.core.RuntimeRoot;
import pile.core.binding.Binding;
import pile.core.binding.BindingType;
import pile.core.binding.ImmutableBinding;
import pile.core.binding.IntrinsicBinding;
import pile.core.binding.NativeDynamicBinding;
import pile.core.indy.CompiledMethod;
import pile.core.indy.HiddenCompiledMethod;
import pile.core.indy.HiddenNativeMethod;
import pile.core.indy.PileMethodLinker;

/**
 * Loads all the methods available as part of the stdlib once.
 *
 */
public class NativeLoader {

    private static final String PILE_CORE_NS = "pile.core";
    
    private static final Map<String, Class<?>> NATIVE_SOURCES = new LinkedHashMap<>();

    static {
        NATIVE_SOURCES.put(PILE_CORE_NS, NativeCore.class);

        // TODO Math, etc
    }

    // Guarded by static class lock
    private static boolean isLoaded = false;

    /**
     * Load the root namespace. Threadsafe.
     */
    public static synchronized void loadRoot() {
        if (isLoaded) {
            return;
        }
        // TODO security?
        Lookup lookup = MethodHandles.lookup();
        for (Entry<String, Class<?>> entry : NATIVE_SOURCES.entrySet()) {
            final String nsStr = entry.getKey();
            final Class<?> staticBase = entry.getValue();
            final Namespace ns = new Namespace(nsStr);

            Method[] methods = staticBase.getMethods();
            for (Method m : methods) {
                if (is(m, Modifier.STATIC) && is(m, Modifier.PUBLIC)) {
                    // TODO remove this later
                    if (!isGeneric(m.getParameterTypes())) {
                        continue;
                    }

                    Binding methodBinding = compile(lookup, m);
                    ns.define(m.getName(), methodBinding);
                }
            }
            RuntimeRoot.defineRoot(nsStr, ns);
        }

        // dynamics
        Namespace root = RuntimeRoot.get(PILE_CORE_NS);
        for (var dyn : NativeDynamicBinding.values()) {
            root.define(dyn.getName(), dyn);
        }

        // intrinsics
        putIntrinics();

        // TODO Exception handling
        isLoaded = true;
    }

    private static void putIntrinics() {
        Namespace root = RuntimeRoot.get(PILE_CORE_NS);
        for (IntrinsicBinding i : IntrinsicBinding.values()) {
            root.define(i.getName(), i);
        }
    }

    private static boolean isGeneric(Class<?>[] parameterTypes) {
        for (Class<?> c : parameterTypes) {
            if (c != Object.class && c != Object[].class) {
                return false;
            }
        }
        return true;
    }

    private static Binding compile(Lookup lookup, Method m) {
        int count = m.getParameterCount();
        try {
            MethodHandle handle = lookup.unreflect(m);
            HiddenCompiledMethod compiled;
            if (m.getReturnType().equals(void.class)) {
            	handle = MethodHandles.filterReturnValue(handle, MethodHandles.constant(Object.class, null));
            }
            if (m.isVarArgs()) {
                handle = handle.withVarargs(true);
                compiled = new HiddenCompiledMethod(Map.of(), handle, count-1, null, -1, null);
            } else {
//                handle = handle.asSpreader(Object[].class, count);
                compiled = new HiddenCompiledMethod(Map.of(count, handle), null, -1, null, -1, null);
            }
            PersistentHashMap meta = new PersistentHashMap();
            meta = meta.assoc(PileMethodLinker.STATIC_KEY, true);
            Binding meth = new ImmutableBinding(PILE_CORE_NS, BindingType.VALUE, new CompiledMethod(compiled), meta, new SwitchPoint());
            return meth;
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Couldn't unreflect method " + m, e);
        }
    }

    private static boolean is(Method m, int f) {
        return (m.getModifiers() & f) > 0;
    }

}
