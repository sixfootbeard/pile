package pile.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import pile.nativebase.NativeLoader;

public class RuntimeRoot {

    private static final Map<String, Namespace> namespaces = new ConcurrentHashMap<>();
    private static final List<Namespace> roots = new CopyOnWriteArrayList<>();

    // above needs initialized before this is called.
    static {
        NativeLoader.loadRoot();
    }

    public static Namespace defineOrGet(String name) {
        namespaces.putIfAbsent(name, new Namespace(name, roots));
        return get(name);
    }

    public static Namespace get(String name) {
        return namespaces.get(name);
    }

    /**
     * A root namespace is expected to only be defined once by convention and is
     * automatically imported into all other namespaces.
     * 
     * @param nsStr
     * @param ns
     */
    public static void defineRoot(String nsStr, Namespace ns) {
        namespaces.put(nsStr, ns);
        roots.add(ns);
    }

}
