package pile.util;

/**
 * Just something to use in the repl help that has fields that can be set/get.
 * 
 * @author john
 *
 */
public class Coordinate {

    public static String PREFIX = "Coordinate";

    public int x, y;

    public Coordinate(int x, int y) {
        super();
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return PREFIX + " [x=" + x + ", y=" + y + "]";
    }

}
