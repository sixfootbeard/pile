package pile.core.binding;

import java.lang.invoke.SwitchPoint;

import pile.collection.PersistentHashMap;
import pile.core.Metadata;

public record ImmutableBinding(String ns, BindingType type, Object ref, PersistentHashMap meta, SwitchPoint sp)
        implements Binding {

    @Override
    public PersistentHashMap meta() {
        return meta;
    }

    @Override
    public Metadata withMeta(PersistentHashMap newMeta) {
        return new ImmutableBinding(ns, type, ref, newMeta, sp);
    }

    @Override
    public Object deref() {
        return ref;
    }

    @Override
    public SwitchPoint getSwitchPoint() {
        return sp;
    }

    @Override
    public BindingType getType() {
        return type;
    }
    
    @Override
    public String namespace() {
        return ns;
    }

}
