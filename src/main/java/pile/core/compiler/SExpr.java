package pile.core.compiler;

import static org.objectweb.asm.Opcodes.*;
import static pile.core.binding.NativeDynamicBinding.*;
import static pile.nativebase.NativeCore.*;
import static pile.core.compiler.Helpers.*;

import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import pile.collection.PersistentList;
import pile.core.ISeq;
import pile.core.Namespace;
import pile.core.binding.Binding;
import pile.core.binding.IntrinsicBinding;
import pile.core.indy.CompiledMethod;
import pile.core.indy.PileMethodLinker;

public class SExpr implements Form {
    
    private static Method PML_METHOD;
    
    static {
        try {
            PML_METHOD = PileMethodLinker.class.getMethod("bootstrap", Lookup.class, String.class, MethodType.class, String.class, int.class);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new RuntimeException("SNH");
        }
    }
    

    private final PersistentList form;
    private final Namespace ns;

    public SExpr(PersistentList form) {
        this.ns = NAMESPACE.deref();
        this.form = form;
    }

    @Override
    public void compileForm(MethodVisitor mv) {
        String lit = Helpers.strSym(form.first());
        Binding binding = ns.lookup(lit);

        if (binding == null) {
            throw Helpers.error("No binding: " + lit);
        }

        switch (binding.getType()) {
            case INTRINSIC: {
                IntrinsicBinding intrinsic = (IntrinsicBinding) binding.deref();
                switch (intrinsic) {
                    case DEF:
                        // TODO top check
                        new DefExpr(form).compileForm(mv);
                        break;
                    case FN:
                        new MethodExpr(ns, form).compileForm(mv);
                        break;
                    case LET:
                        new LetExpr(form).compileForm(mv);
                        break;
                    case DO:
                    	new DoForm(form).compileForm(mv);
                    	break;

                    default:
                        throw Helpers.error("Unexpected intrinsic:" + intrinsic.getName());
                }
            }
            case VALUE:
                Object object = binding.deref();
                if (object instanceof CompiledMethod method) {
                    // method
                    ISeq more = more(form);
                    int formCount = 0;
                    for (Object arg : more) {
                        Compiler.compile(mv, arg);
                        ++formCount;
                    }
                    Handle h = new Handle(Opcodes.H_INVOKESTATIC, "pile/core/indy/PileMethodLinker", "bootstrap", Type.getMethodDescriptor(PML_METHOD), false);
                    String methodDescriptor = Type.getMethodDescriptor(OBJECT_TYPE, getObjectTypeArray(formCount));
                    mv.visitInvokeDynamicInsn(lit, methodDescriptor, h, ns.getName(), 0);
                }
        		break;
                // fall through
            default:
                throw Helpers.error("Found non method/intrinsic binding: " + binding.namespace() + "/" + lit + "@"
                        + binding.getType());
        }
    }

    @Override
    public Object evaluateForm() {
        String lit = Helpers.strSym(form.first());
        Binding binding = ns.lookup(lit);

        if (binding == null) {
            throw Helpers.error("No binding: " + lit);
        }

        switch (binding.getType()) {
            case INTRINSIC: {
                IntrinsicBinding intrinsic = (IntrinsicBinding) binding.deref();
                switch (intrinsic) {
                    case DEF:
                        // TODO top check
                        new DefExpr(form).evaluateForm();
                        return null;
                    case FN:
                        // (fn [a b] .. )
                        return new MethodExpr(ns, form).evaluateForm();
                    case LET:
                        return new LetExpr(form).evaluateForm();
                    case DO:
                    	return new DoForm(form).evaluateForm();
                    default:
                        throw Helpers.error("Unexpected/Unimplemented intrinsic:" + intrinsic.getName());
                }
            }
            case VALUE:
                Object object = binding.deref();
                if (object instanceof CompiledMethod method) {
                    // method
                    List<Object> args = new ArrayList<>();
                    ISeq more = more(form);
                    for (Object arg : more) {
                        args.add(Compiler.evaluate(arg));
                    }
                    return method.invoke(args.toArray());
                }
                // fall through

            default:
                throw Helpers.error("Found non method/intrinsic binding: " + binding.namespace() + "/" + lit + "@"
                        + binding.getType());
         
        }
        
    }

}
