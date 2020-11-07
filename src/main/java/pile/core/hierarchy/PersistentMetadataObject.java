package pile.core.hierarchy;

import java.util.function.Function;

import pile.collection.PersistentHashMap;
import pile.core.Metadata;

public abstract class PersistentMetadataObject<T extends Metadata> extends PersistentObject<T> implements Metadata {

    private final PersistentHashMap meta;

    protected abstract T copyWithMeta(PersistentHashMap newMeta);

    public PersistentMetadataObject(PersistentHashMap meta) {
        this.meta = meta;
    }

    @Override
    public PersistentHashMap meta() {
        return meta;
    }

    @Override
    public Metadata withMeta(PersistentHashMap newMeta) {
        return copyWithMeta(newMeta);
    }

}
