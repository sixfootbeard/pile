package pile.core.runtime;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

public class LookupHolder {
    public static final Lookup LOOKUP = MethodHandles.lookup();
}
