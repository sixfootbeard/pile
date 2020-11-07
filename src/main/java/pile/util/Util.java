package pile.util;

public class Util {

    
    public static boolean falsy(Object o) {
        if (o instanceof Boolean b) {
            return b == false;
        }
        return false;
    }
    
    public static boolean truthy(Object o) {
        return ! falsy(o);
    }
}
