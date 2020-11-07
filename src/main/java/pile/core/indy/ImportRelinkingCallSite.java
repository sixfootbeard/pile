package pile.core.indy;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.SwitchPoint;

import pile.core.Namespace;
import pile.core.binding.Binding;

public class ImportRelinkingCallSite extends MutableCallSite {

    private static final MethodHandle RELINK;

    static {
        try {
            RELINK = lookup().findVirtual(ImportRelinkingCallSite.class, "relink", methodType(void.class));
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    static ImportRelinkingCallSite make(MethodHandle handle, SwitchPoint sp, Namespace ns, String method) {
        ImportRelinkingCallSite cs = new ImportRelinkingCallSite(handle.type(), ns, method);
        MethodHandle relink = RELINK.bindTo(cs).asCollector(Object[].class, cs.type().parameterCount())
                .asType(cs.type());
        MethodHandle spComposed = sp.guardWithTest(handle, relink);
        cs.setTarget(spComposed);
        return cs;
    }

    private final Namespace ns;
    private final String method;
    private final MethodType type;

    public ImportRelinkingCallSite(MethodType type, Namespace ns, String method) {
        super(type); // argument type +1?
        this.type = type;
        this.ns = ns;
        this.method = method;
    }

    @SuppressWarnings("unused")
    private Object relink(Object[] args) throws Throwable {
        Binding binding = ns.getLocal(method);

        final CompiledMethod deref = (CompiledMethod) binding.deref();
        
        if (deref.isCompiled()) {
            
        }

        final SwitchPoint sp = binding.getSwitchPoint();

        final MethodHandle relink = RELINK.bindTo(this).asCollector(Object[].class, this.type().parameterCount())
                .asType(this.type());

        MethodHandle found = PileMethodLinker.find(deref, type.parameterCount());
        MethodHandle newTarget = sp.guardWithTest(found, relink);
        setTarget(newTarget);

        return found.invokeWithArguments(args);
    }

}
