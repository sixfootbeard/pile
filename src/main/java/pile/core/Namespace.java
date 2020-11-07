package pile.core;

import java.lang.invoke.SwitchPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import pile.collection.PersistentList;
import pile.core.binding.Binding;
import pile.core.binding.BindingType;
import pile.core.binding.Unbound;
import pile.core.indy.PileMethodLinker;
import pile.nativebase.NativeLoader;

public class Namespace {

    static {
        NativeLoader.loadRoot();
    }

    private record ImportedNS(Namespace ns, Optional<Predicate<String>> filter) {
    }

    private final LinkedList<ImportedNS> imports = new LinkedList<>();
    private final Map<String, Binding> ourBindings = new ConcurrentHashMap<>();
    private final AtomicLong suffix = new AtomicLong();
    private final String name;

    public Namespace(String name) {
        this(name, Collections.emptyList());
    }

    public Namespace(String name, List<Namespace> autoImports) {
        this.name = name;
        autoImports.forEach(ai -> imports.add(new ImportedNS(ai, null)));
    }

    /**
     * Define a binding in this namespace.
     * 
     * @param name The name of the binding
     * @param v    The value. May be null if nominally defining a binding to assist
     *             in circular definitions.
     */
    public void define(String name, Binding v) {
        AtomicReference<SwitchPoint> sp = new AtomicReference<>();
        AtomicBoolean doThrow = new AtomicBoolean(false);

        ourBindings.compute(name, (k, ov) -> {
            Binding set = v;
            if (set == null) {
                // null unsets mapping in compute, return singleton
                set = Unbound.INSTANCE;
            }

            // ~~
            if (ov == null) {
                return set;
            } else {
                if (PileMethodLinker.isStatic(ov)) {
                    // throw
                    doThrow.set(true);
                    return ov;
                } else {
                    // supplanted
                    sp.set(ov.getSwitchPoint());
                    return set;
                }
            }
        });

        if (doThrow.get()) {
            throw new IllegalArgumentException("Cannot override static binding");
        }

        SwitchPoint toInvalidate = sp.get();

        // TODO How to manage other vars in our namespace?
        if (toInvalidate != null) {
            SwitchPoint.invalidateAll(new SwitchPoint[] { toInvalidate });
        }
    }

    public Binding getLocal(String name) {
        return ourBindings.get(name);
    }

    public void importFrom(Namespace other) {
        imports.addFirst(new ImportedNS(other, Optional.empty()));
    }

    /**
     * Lookup what the current binding is of a symbol in this namespace.
     * 
     * @param symbol
     * @return
     */
    public Binding lookup(String symbol) {
        Binding ourBinding = ourBindings.get(symbol);
        if (ourBinding == null) {
            for (ImportedNS otherNs : imports) {
                Binding otherNsBinding = otherNs.ns.getLocal(symbol);
                // TODO Filtering, renaming
                if (otherNsBinding != null) {
                    return otherNsBinding;
                }
            }
        }
        return ourBinding;

    }

    public Set<String> keys() {
        // TODO Remove
        return ourBindings.keySet();
    }

    public void addForm(PersistentList form) {
        // TODO Auto-generated method stub

    }
    
    public String getName() {
        return name;
    }
    
    public long getSuffix() {
        return suffix.getAndIncrement();
    }

}
