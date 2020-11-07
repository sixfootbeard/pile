package pile.core.compiler;

import static pile.core.compiler.CompilerBinding.*;
import static pile.core.binding.NativeDynamicBinding.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import pile.collection.PersistentList;
import pile.core.Namespace;
import pile.util.Pair;

public class LetExpr implements Form {

    private final PersistentList form;
    private final Namespace ns;

    public LetExpr(PersistentList form) {
        this.ns = NAMESPACE.deref();
        this.form = form;
    }

    @Override
    public void compileForm(MethodVisitor mv) {
        // (let [a 1 b 2] (...))
        Iterator fit = form.iterator();
        fit.next(); // let

        // bindings
        PersistentList localBindings = Helpers.expectVector(fit.next());

        int count = localBindings.count();
        if (count % 2 != 0) {
            throw Helpers.error("Bindings size should be a multiple of 2, size=" + count);
        }
        List<String> locals = METHOD_LOCALS.deref();
        int index = locals.size();
        Iterator bit = localBindings.iterator();
        while (bit.hasNext()) {
            String sym = Helpers.strSym(bit.next());
            Compiler.compile(mv, bit.next());
            locals.add(sym);
            mv.visitVarInsn(Opcodes.ASTORE, index);
            ++index;            
        }

        Object exp = fit.next();
        Compiler.compile(mv, exp);
    }

    @Override
    public Object evaluateForm() {
        // (let [a 1 b 2] (...))
        Iterator fit = form.iterator();
        fit.next(); // let

        // bindings
        PersistentList localBindings = Helpers.expectVector(fit.next());

        int count = localBindings.count();
        if (count % 2 != 0) {
            throw Helpers.error("Bindings size should be a multiple of 2, size=" + count);
        }
        List<Pair<String,Object>> locals = LOCALS.deref();

//        List<Pair<String, Object>> toPush = new ArrayList<>();

        Object exp = fit.next();
        Iterator bit = localBindings.iterator();
        while (bit.hasNext()) {
            String sym = Helpers.strSym(bit.next());
            Object val = Compiler.evaluate(bit.next());
            locals.add(new Pair<>(sym, val));
        }

        LOCALS.set(locals);

        return Compiler.evaluate(exp);

    }
}
