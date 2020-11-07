package pile.core.indy;

import java.lang.invoke.MethodHandle;
import java.util.Map;

import pile.core.PCall;

/**
 * Wrapper for method handles for compiled methods. Don't leak.
 *
 */
public class HiddenCompiledMethod implements PCall {

    // can't put kw/var in here otherwise they might be called with the wrong
    // calling conventions.
    public final Map<Integer, MethodHandle> airityHandles;
    public final MethodHandle varArgsMethod;
    public final MethodHandle kwArgsMethod;
    public final Integer kwArgsAirity;
    public final Integer varArgsAirity;

    @SuppressWarnings("unused") // Used by the linker
    public final MethodHandle kwMethodUnpacked;

    public HiddenCompiledMethod(Map<Integer, MethodHandle> airityHandles, MethodHandle varArgsMethod,
            Integer varArgsAirity, MethodHandle kwArgsMethod, Integer kwArgsAirity, MethodHandle kwMethodUnpacked) {
        super();
        if (varArgsMethod != null && kwArgsMethod != null) {
            throw new IllegalArgumentException("Cannot use both kw and varargs");
        }
        this.airityHandles = airityHandles;
        this.varArgsMethod = varArgsMethod;
        this.varArgsAirity = varArgsAirity;
        this.kwArgsMethod = kwArgsMethod;
        this.kwArgsAirity = kwArgsAirity;
        this.kwMethodUnpacked = kwMethodUnpacked;
    }

    @Override
    public Object invoke(Object... args) {
        int argSize = args.length;
        MethodHandle methodHandle = airityHandles.get(argSize);
        if (methodHandle == null) {
            if (varArgsMethod != null && varArgsAirity <= args.length) {
                methodHandle = varArgsMethod;
            }
            if (kwArgsMethod != null && kwArgsAirity <= args.length) {
                methodHandle = kwArgsMethod;
            }
        } else {
            methodHandle = methodHandle.asSpreader(Object[].class, args.length);
        }
        if (methodHandle == null) {
            throw new IllegalStateException("Method called with the wrong number of arguments");
        }
        try {
            return methodHandle.invoke(args);
        } catch (Throwable e) {
            throw new RuntimeException("Unable to call method", e);
        }
    }

}
