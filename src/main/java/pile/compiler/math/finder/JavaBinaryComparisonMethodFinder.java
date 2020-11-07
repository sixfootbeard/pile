package pile.compiler.math.finder;

import java.util.List;

public class JavaBinaryComparisonMethodFinder extends JavaBinaryMathMethodFinder {

    public JavaBinaryComparisonMethodFinder(List<Class<?>> order, Class<?> minimum) {
        super(order, minimum);
    }
    
    @Override
    protected Class<?> getReturnType(Class<?> primMax) {
        return int.class;
    }

}
