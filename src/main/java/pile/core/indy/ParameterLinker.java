package pile.core.indy;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import pile.core.PCall;

/**
 * Links parameters that are used as function calls. These calls are potentially
 * going to involve more dynamic call sites than imported functions so they are
 * handled differently.
 * 
 * <pre>
 * (defn foo [some-fn] (some-fn "a" 1 true))
 * </pre>
 * 
 * <ul>
 * <li>{@link MethodBinding} - An import somewhere else was passed in to this
 * function.
 * <li>{@link PCall} - A generic callable function, possibly with
 * {@link CallableLink}.
 * <li>Rest - Should fail as they are not callable.
 * 
 * @author John
 *
 */
public class ParameterLinker {

    private static final MethodHandle ENSURE_PCALL;
    private static final MethodHandle CALL_PCALL;

    static {
        try {
            ENSURE_PCALL = MethodHandles.lookup().findStatic(ParameterLinker.class, "ensurePcall",
                    MethodType.methodType(boolean.class, Class.class));
            CALL_PCALL = MethodHandles.lookup().findVirtual(PCall.class, "invoke",
                    MethodType.methodType(Object.class, Object[].class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    private static final boolean ensurePcall(Class<?> clazz) {
        return clazz.isAssignableFrom(PCall.class);
    }

    /**
     * 
     * @param caller
     * @param name
     * @param type
     * @param namespaceStr The namespace of the fn name
     * @param flags        {@link LinkOptions}
     * @return
     */
    public static CallSite bootstrap(Lookup caller, String name, MethodType type, String namespaceStr, int flags) {
        // TODO Support MethodHandle
        // TODO Eventually try to link directly to CallableLink, fall back to PCall.
        MethodHandle guarded = MethodHandles.guardWithTest(ENSURE_PCALL, CALL_PCALL,
                MethodHandles.throwException(void.class, MustBePCallException.class));
        return new ConstantCallSite(guarded);
    }
}
