package pile.core;

import java.util.function.Function;

import pile.collection.PersistentHashMap;

public interface Metadata {

    PersistentHashMap meta();
    Metadata withMeta(PersistentHashMap newMeta);
    
    default Metadata updateMeta(Function<PersistentHashMap, PersistentHashMap> update) {
        return withMeta(update.apply(this.meta()));   
    }

}
