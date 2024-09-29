package pile.core.binding;

import java.lang.invoke.SwitchPoint;

import pile.collection.PersistentMap;
import pile.core.Metadata;

public class ScopedBinding<T> implements Binding<T> {

    private final ScopedValue<T> value = ScopedValue.newInstance();
    private final SwitchPoint sp = new SwitchPoint();
    
    private final PersistentMap meta;
    private final String ns;
    private final T initial;

    public ScopedBinding(String ns, T initial, PersistentMap meta) {
        super();
        this.meta = meta;
        this.ns = ns;
        this.initial = initial;
    }

    @Override
    public PersistentMap meta() {
        return meta;
    }

    @Override
    public Metadata withMeta(PersistentMap newMeta) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T getValue() {
        return value.orElse(initial);
    }
    
    public ScopedValue<T> getScopedValue() {
        return value;
    }

    @Override
    public SwitchPoint getSwitchPoint() {
        return sp;
    }

    @Override
    public String namespace() {
        return ns;
    }

}
