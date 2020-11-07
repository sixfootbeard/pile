package pile.core.binding;

import java.lang.invoke.SwitchPoint;

import pile.collection.PersistentHashMap;
import pile.core.Metadata;

public class Unbound implements Binding {

    private Unbound() {
        // TODO Auto-generated constructor stub
    }

    @Override
    public PersistentHashMap meta() {
        return null;
    }

    @Override
    public Metadata withMeta(PersistentHashMap newMeta) {
        throw new IllegalStateException();
    }

    @Override
    public Object deref() {
        return null;
    }

    @Override
    public SwitchPoint getSwitchPoint() {
        return null;
    }

    @Override
    public BindingType getType() {
        return null;
    }
    
    @Override
    public String namespace() {
        return null;
    }

    public static Unbound INSTANCE = new Unbound();

}
