package pile.core.indy;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import pile.core.PCall;

/**
 * Wrapper for compiled or native methods.
 *
 */
public class CompiledMethod implements PCall {

    static final Lookup COMPILED_LOOKUP = MethodHandles.lookup();

    // can't put kw/var in here otherwise they might be called with the wrong
    // calling conventions.
    private final HiddenCompiledMethod compiledMethod;
    private final HiddenNativeMethod nativeMethod;

    public CompiledMethod(HiddenCompiledMethod compiled) {
        super();
        this.compiledMethod = compiled;
        this.nativeMethod = null;
    }

    public CompiledMethod(HiddenNativeMethod compiled) {
        super();
        this.compiledMethod = null;
        this.nativeMethod = compiled;
    }

    @Override
    public Object invoke(Object... args) {
        if (isCompiled()) {
            return compiledMethod.invoke(args);
        } else if (isNative()) {
            return nativeMethod.invoke(args);
        } else {
            throw new RuntimeException("Shouldn't happen");
        }
    }

    public boolean isNative() {
        return nativeMethod != null;
    }

    public boolean isCompiled() {
        return compiledMethod != null;
    }

}
