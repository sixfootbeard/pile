package pile.core.binding;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.SwitchPoint;
import java.util.Arrays;

import pile.collection.PersistentHashMap;
import pile.core.Metadata;
import pile.core.Namespace;
import pile.core.SettableRef;

public class NativeDynamicBinding<T> implements Binding<T>, SettableRef<T> {
    //@formatter:off
    public static NativeDynamicBinding<Namespace> NAMESPACE = new NativeDynamicBinding<>("*ns*"); 
    public static NativeDynamicBinding<InputStream> STANDARD_IN = new NativeDynamicBinding<>("*in*", System.in); 
    public static NativeDynamicBinding<OutputStream> STANDARD_OUT = new NativeDynamicBinding<>("*out*", System.out); 
    //@formatter:on

    public static NativeDynamicBinding[] values() {
        return new NativeDynamicBinding[] { NAMESPACE, STANDARD_IN, STANDARD_OUT };
    }

    private final ThreadLocal<T> threadLocal;
    private final String name;

    NativeDynamicBinding(String name) {
        this.name = name;
        this.threadLocal = new ThreadLocal<>();
    }

    NativeDynamicBinding(String name, T initial) {
        this.name = name;
        this.threadLocal = ThreadLocal.withInitial(() -> initial);
    }

    @Override
    public PersistentHashMap meta() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Metadata withMeta(PersistentHashMap newMeta) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public SwitchPoint getSwitchPoint() {
        return null; // TODO??
    }

    @Override
    public BindingType getType() {
        return BindingType.DYNAMIC;
    }

    @Override
    public T deref() {
        return threadLocal.get();
    }

    @Override
    public void set(T newRef) {
        threadLocal.set(newRef);
    }

    public String getName() {
        return name;
    }

    @Override
    public String namespace() {
        return "pile.core";
    }

}
