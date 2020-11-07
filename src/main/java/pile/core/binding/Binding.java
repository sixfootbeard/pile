package pile.core.binding;

import java.lang.invoke.SwitchPoint;

import pile.core.Deref;
import pile.core.Keyword;
import pile.core.Metadata;
import pile.core.Namespace;

/**
 * A binding is a particular value inserted into a {@link Namespace}. Bindings
 * may be static or non-static. If they are static they may not be rebound.
 *
 */
public interface Binding<T> extends Metadata, Deref<T> {

    SwitchPoint getSwitchPoint();

    BindingType getType();
    
    String namespace();

    

}