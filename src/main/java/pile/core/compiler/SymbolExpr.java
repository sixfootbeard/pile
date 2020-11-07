package pile.core.compiler;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static pile.core.binding.NativeDynamicBinding.NAMESPACE;
import static pile.core.compiler.CompilerBinding.LOCALS;
import static pile.core.compiler.Helpers.findLocal;
import static pile.core.compiler.Helpers.strSym;

import java.util.List;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import pile.core.Deref;
import pile.core.Namespace;
import pile.core.binding.Binding;
import pile.util.Pair;

public class SymbolExpr implements Form {

	private static final String NAMESPACE_GET_METHOD = Type.getMethodDescriptor(Type.getType(Binding.class),
			Type.getType(String.class));
	private static final String DEREF_METHOD = Type
			.getMethodDescriptor(Type.getType(Object.class)/* , Type.getType(Deref.class) */);

	private final Object toEval;

	public SymbolExpr(Object toEval) {
		this.toEval = toEval;
	}

	@Override
	public void compileForm(MethodVisitor method) {
		// locals
		String strSym = strSym(toEval);
		int localSlot = findLocal(strSym);
		if (localSlot != -1) {
			method.visitVarInsn(ALOAD, localSlot);
		} else {
			// ~~ static
			// symbol:str
			method.visitLdcInsn(strSym);
			// ns:namespace
			method.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getType(Namespace.class).getInternalName(), "lookup",
					NAMESPACE_GET_METHOD, false);
			// binding:binding
			method.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getType(Deref.class).getInternalName(), "deref",
					DEREF_METHOD, true);
			// val
		}


	}

	@Override
	public Object evaluateForm() {
		return lookupValue(NAMESPACE.deref(), Helpers.strSym(toEval));
	}

	static Object lookupValue(Namespace ns, String strSym) {
		// Locals
		List<Pair<String, Object>> deref = LOCALS.deref();
		if (deref != null) {
			for (Pair<String, Object> pair : deref) {
				if (pair.left().equals(strSym)) {
					return pair.right();
				}
			}
		}

		// ns + import
		Binding lookup = ns.lookup(strSym);
		if (lookup != null) {
			return lookup.deref();
		}

		return null;
	}

}
