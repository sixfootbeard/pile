package pile.compiler.math.finder;

import java.lang.invoke.MethodType;
import java.util.Optional;

public interface BinaryMethodFinder {

    public Optional<MethodType> findTarget(Class<?> lhs, Class<?> rhs);

}
