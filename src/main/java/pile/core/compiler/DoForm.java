package pile.core.compiler;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import pile.collection.PersistentList;

public class DoForm implements Form {

	private final PersistentList form;

	public DoForm(PersistentList form) {
		this.form = form;
	}

	@Override
	public void compileForm(MethodVisitor method) {
		for (Object o : form) {
			Compiler.compile(method, o);
			// TODO Could just leave this on the stack?
			method.visitInsn(Opcodes.POP);
		}
	}

	@Override
	public Object evaluateForm() {
		Object last = null;
		for (Object o : form) {
			last = Compiler.evaluate(o);
		}
		return last;
	}

}
