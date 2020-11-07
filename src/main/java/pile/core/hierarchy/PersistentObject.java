package pile.core.hierarchy;

import java.util.function.Function;

import pile.collection.PersistentHashMap;
import pile.core.Metadata;

public abstract class PersistentObject<T> {

    private int computedHash = 0;
    private String computedStr = null;

    protected abstract int computeHash();

    protected abstract String computeStr();

    @Override
    public int hashCode() {
        int local = computedHash;
        if (local == 0) {
            computedHash = computeHash();
        }
        return computedHash;
    }

    @Override
    public String toString() {
        String local = computedStr;
        if (local == null) {
            computedStr = computeStr();
        }
        return computedStr;
    }

}
