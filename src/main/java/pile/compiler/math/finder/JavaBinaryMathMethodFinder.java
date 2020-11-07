package pile.compiler.math.finder;

import static java.lang.invoke.MethodType.*;
import static pile.compiler.Helpers.*;

import java.lang.invoke.MethodType;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import pile.compiler.math.NumberHelpers;
import pile.util.ComparableUtils;

public class JavaBinaryMathMethodFinder implements BinaryMethodFinder {    

    private final Comparator<Class> cmp;
    private final Set<Class<?>> order;
    private final Class<?> minimum;

    public JavaBinaryMathMethodFinder(List<Class<?>> order, Class<?> minimum) {
        Map<Class<?>, Integer> orderMap = new HashMap<>();
        int i = 0;
        for (Class<?> c : order) {
            orderMap.put(c, i);
            ++i;
        }
        this.cmp = Comparator.comparingInt(orderMap::get);
        this.order = new HashSet<>(order);
        this.minimum = minimum;
    }

    @Override
    public Optional<MethodType> findTarget(Class<?> lhs, Class<?> rhs) {
        if (order.contains(lhs) && order.contains(rhs)) {
            Class maxArgs = ComparableUtils.max(lhs, rhs, cmp);
            Class maxAll = ComparableUtils.max(maxArgs, minimum, cmp);
            Class<?> primMax = toPrimitive(maxAll);
            Class<?> returnType = getReturnType(primMax);
            return Optional.of(methodType(returnType, primMax, primMax));
        }
        return Optional.empty();
    }

    protected Class<?> getReturnType(Class<?> primMax) {
        return primMax;
    }

}
