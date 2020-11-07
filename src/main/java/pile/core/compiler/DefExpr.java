package pile.core.compiler;

import static pile.core.binding.NativeDynamicBinding.NAMESPACE;
import static pile.core.compiler.Helpers.ensureSize;

import java.lang.invoke.SwitchPoint;

import org.objectweb.asm.MethodVisitor;

import pile.collection.PersistentList;
import pile.core.Namespace;
import pile.core.binding.BindingType;
import pile.core.binding.ImmutableBinding;

public class DefExpr implements Form {
    
    private final PersistentList form;
    private final Namespace ns;

    public DefExpr(PersistentList form) {
        this.ns = NAMESPACE.deref();
        this.form = form;
    }

    @Override
    public void compileForm(MethodVisitor method) {
        throw new RuntimeException("Cannot compile def");
    }

    @Override
    public Object evaluateForm() {
        ensureSize("def", form, 3);
        String name = Helpers.strSym(form.next().first());
        Object eval = Compiler.evaluate(form.next().next().first());
        
        ImmutableBinding imm = new ImmutableBinding(ns.getName(), BindingType.VALUE, eval, null, new SwitchPoint());
        
        ns.define(name, imm);
        
        // top level
        return null;
    }

}
