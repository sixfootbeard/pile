package pile.compiler.math.finder;

import java.util.List;

public class JavaBinaryPredicateMathMethodFinder extends JavaBinaryMathMethodFinder {

    public JavaBinaryPredicateMathMethodFinder(List<Class<?>> order, Class<?> minimum) {
        super(order, minimum);
    }
    
    @Override
    protected Class<?> getReturnType(Class<?> primMax) {
        return boolean.class;
    }

    

}
