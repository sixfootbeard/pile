package pile.core.indy;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Map;

import pile.core.Keyword;
import pile.core.Namespace;
import pile.core.RuntimeRoot;
import pile.core.binding.Binding;
import pile.core.binding.BindingType;

/**
 * Links method calls. Only used for
 * <ol>
 * <li>Import methods
 * <li>Native methods
 * </ol>
 * 
 * @author John
 *
 */
public class PileMethodLinker {

    public static final Keyword STATIC_KEY = Keyword.of("pile.core", "static-binding");

    private static final MethodHandle GET_HIDDEN_COMPILED, GET_HIDDEN_NATIVE;

    static {
        try {
            Lookup lookup = CompiledMethod.COMPILED_LOOKUP;

            GET_HIDDEN_COMPILED = lookup
                    .findVarHandle(CompiledMethod.class, "compiledMethod", HiddenCompiledMethod.class)
                    .toMethodHandle(VarHandle.AccessMode.GET);

            GET_HIDDEN_NATIVE = lookup.findVarHandle(CompiledMethod.class, "nativeMethod", HiddenNativeMethod.class)
                    .toMethodHandle(VarHandle.AccessMode.GET);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * 
     * @param caller
     * @param name
     * @param type
     * @param namespaceStr The namespace of the fn name
     * @param flags        {@link LinkOptions}
     * @return
     * @throws Throwable
     */
    public static CallSite bootstrap(Lookup caller, String name, MethodType type, String namespaceStr, int flags)
            throws Throwable {

        final int paramCount = type.parameterCount();
        final Namespace ns = RuntimeRoot.get(namespaceStr);
        final Binding binding = ns.lookup(name);
        final Object val = binding.deref();
        if (val instanceof CompiledMethod deref) {
            final MethodHandle toCall = find(deref, paramCount);
            if (deref.isCompiled()) {
                if (isStatic(binding)) {
                    return new ConstantCallSite(toCall);
                } else {
                    return ImportRelinkingCallSite.make(toCall, binding.getSwitchPoint(), ns, name);
                }
            } else if (deref.isNative()) {
                throw new RuntimeException("Unimplemented");
            } else {
                throw new RuntimeException("Unexpected method binding type");
            }
        }
        throw new IllegalStateException("Cannot link to non-method binding");

    }

    static final MethodHandle find(CompiledMethod compiled, int paramCount) throws Throwable {

        HiddenCompiledMethod hidden = (HiddenCompiledMethod) GET_HIDDEN_COMPILED.invoke(compiled);

        if (hidden != null) {
            final MethodHandle toCall;
            Map<Integer, MethodHandle> methodTable = hidden.airityHandles;
            MethodHandle methodHandle = methodTable.get(paramCount);
            if (methodHandle != null) {
                // Direct arity match
                toCall = methodHandle;
            } else {
                MethodHandle varargs = hidden.varArgsMethod;
                if (varargs != null) {
                    MethodHandle modHandle = varargs;
                    toCall = modHandle;
                } else {
                    Integer kwArgSize = hidden.kwArgsAirity;
                    if (kwArgSize != null && kwArgSize <= paramCount) {
                        MethodHandle modHandle = hidden.kwArgsMethod.asCollector(Object[].class,
                                paramCount - kwArgSize);
                        toCall = modHandle;
                    } else {
                        // TODO Unrolled kw args
                        throw new IllegalArgumentException("Couldn't figure out how to link method");
                    }
                }
            }
            return toCall;
        }

        HiddenNativeMethod nativeMethod = (HiddenNativeMethod) GET_HIDDEN_NATIVE.invoke(compiled);
        if (nativeMethod != null) {
            throw new RuntimeException("NYI");
        }

        throw new IllegalStateException("Shouldn't happen");

    }

    public static boolean isStatic(Binding b) {
        return (boolean) b.meta().get(PileMethodLinker.STATIC_KEY, false);
    }
}
