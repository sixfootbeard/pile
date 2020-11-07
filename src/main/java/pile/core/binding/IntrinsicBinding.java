package pile.core.binding;

import java.lang.invoke.SwitchPoint;

import pile.collection.PersistentHashMap;
import pile.core.Metadata;

public enum IntrinsicBinding implements Binding {
    DEF("def"),
    FN("fn*"),
    LET("let*"),
    DO("do"),
    
    ;

    private final String name;

    IntrinsicBinding(String name) {
        this.name = name;
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
    public Object deref() {
        return this;
    }

    @Override
    public SwitchPoint getSwitchPoint() {
        return null;
    }

    @Override
    public BindingType getType() {
        return BindingType.INTRINSIC;
    }

    @Override
    public String namespace() {
        return "pile.core";
    }
    
    public String getName() {
        return name;
    }

}
