package pile.compiler.math.finder;

import static pile.compiler.Helpers.*;

import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.TreeSet;

import pile.compiler.math.NumberHelpers;

public class NegateMethodFinder implements UnaryMathMethodFinder {

    private final UnaryMathMethodFinder delegate;

    public NegateMethodFinder(UnaryMathMethodFinder delegate) {
        this.delegate = delegate;

    }

    @Override
    public Optional<MethodType> findTarget(Class<?> arg) {
        Class<?> t = toPrimitive(arg);
        if (Short.TYPE.equals(t) || Byte.TYPE.equals(t)) {
            t = Integer.TYPE;
        }
        TreeSet<Class<?>> valid = NumberHelpers.getBBDFLI();
        if (valid.contains(t)) {
            return delegate.findTarget(t);
        } else {
            return Optional.empty();
        }
        
    }

}
