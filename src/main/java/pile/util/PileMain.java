package pile.util;

import pile.core.Namespace;
import pile.core.RuntimeRoot;
import pile.core.binding.Binding;
import pile.core.indy.CompiledMethod;
import pile.nativebase.NativeLoader;

public class PileMain {

    public static void main(String[] args) {
        Binding prn = RuntimeRoot.get("pile.core").lookup("prn");
        CompiledMethod method = (CompiledMethod) prn.deref();
        method.invoke("foo");

    }

}
