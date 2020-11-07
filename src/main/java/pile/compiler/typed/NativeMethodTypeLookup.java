package pile.compiler.typed;

import java.util.List;
import java.util.function.Function;

import pile.util.Pair;

public class NativeMethodTypeLookup<T> extends DynamicTypeLookup<T> {

    public NativeMethodTypeLookup(Function<T, TypeVarArg> tx) {
        super(tx);
    }

    @Override
    protected Pair<TypeVarArg, T> merge(Pair<TypeVarArg, T> leftPair, Pair<TypeVarArg, T> rightPair,
            List<Class<?>> statics) {
        // natives are already in preference order. We're still using the type lookup to
        // find contention.
        return leftPair;
    }

}
