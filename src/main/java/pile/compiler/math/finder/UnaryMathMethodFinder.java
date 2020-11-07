package pile.compiler.math.finder;

import java.lang.invoke.MethodType;

public interface UnaryMathMethodFinder {

    public MethodType findTarget(Class<?> arg);

}
