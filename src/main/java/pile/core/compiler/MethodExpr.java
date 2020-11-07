package pile.core.compiler;

import static pile.core.compiler.CompilerBinding.*;
import static pile.core.compiler.Helpers.*;
import static org.objectweb.asm.Opcodes.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import pile.collection.PersistentList;
import pile.core.Deref;
import pile.core.Metadata;
import pile.core.Namespace;
import pile.core.binding.Binding;
import pile.core.indy.CompiledMethod;
import pile.core.indy.HiddenCompiledMethod;
import pile.core.parse.Parser;
import pile.core.parse.Parser.TypeTag;
import pile.core.runtime.LookupHolder;

public class MethodExpr implements Form {

	private static final String GEN_PACKAGE = "pile/core/runtime";

	private static final String NAMESPACE_GET_METHOD = Type.getMethodDescriptor(Type.getType(Binding.class),
			Type.getType(String.class));
	private static final String DEREF_METHOD = Type
			.getMethodDescriptor(Type.getType(Object.class)/* , Type.getType(Deref.class) */);

	private final Namespace ns;
	private final PersistentList form;

	public MethodExpr(Namespace ns, PersistentList form) {
		this.ns = ns;
		this.form = form;
	}

	private void defineConstructor(ClassVisitor writer) {
		MethodVisitor cons = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
		cons.visitCode();
		cons.visitVarInsn(ALOAD, 0);
		cons.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		cons.visitInsn(RETURN);
		cons.visitMaxs(0, 0);
		cons.visitEnd();
	}

	@Override
	public void compileForm(MethodVisitor method) {

	}

	@Override
	public Object evaluateForm() {
		// (fn [a b c] ...)
		Iterator fit = form.iterator();
		fit.next(); // fn
		List<String> locals = new ArrayList<>();
		PersistentList vars = expectVector(fit.next());
		for (Object o : vars) {
			String strSym = strSym(o);
			locals.add(strSym);
		}

		List<String> methodLocals = METHOD_LOCALS.deref();
		methodLocals.addAll(locals);
		METHOD_LOCALS.set(methodLocals);

		ClassWriter writer = getClassWriter();
		int sizes = vars.count();
		Type[] args = Helpers.getObjectTypeArray(sizes);
		String methodDescriptor = Type.getMethodDescriptor(Helpers.OBJECT_TYPE, args);
		MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "func", methodDescriptor, null, null);
		method.visitCode();

		Object toEval = fit.next();
		Metadata meta = (Metadata) toEval;
		TypeTag tag = (TypeTag) Parser.TAG_KEY.call(meta.meta());
		switch (tag) {
		case NUMBER:
		case STRING:
		case CHAR:
		case TRUE:
		case FALSE:
		case KEYWORD:
		case REGEX:
			Deref deref = (Deref) toEval;
			method.visitLdcInsn(deref.deref());
			break;
		case SYMBOL:
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
			break;
		case SEXP:
			new SExpr(expectList(toEval)).compileForm(method);
			break;
		default:
			throw error("Unimplemented: " + tag);
		}
		
		method.visitInsn(ARETURN);
		method.visitMaxs(1, 2);

		method.visitEnd();

		writer.visitEnd();
		byte[] classArray = writer.toByteArray();

		try {
			Map<Integer, MethodHandle> airityHandles = new HashMap<>();
			Class<?> clazz = LookupHolder.LOOKUP.defineClass(classArray);
			Object instance = clazz.newInstance();
			for (Method m : clazz.getDeclaredMethods()) {
				if (m.getName().equals("func")) {
					MethodHandle handle = MethodHandles.lookup().unreflect(m);
					handle = handle.bindTo(instance);
					int mCount = m.getParameterCount();
					airityHandles.put(mCount, handle);
				}
			}

			CompiledMethod meth = new CompiledMethod(new HiddenCompiledMethod(airityHandles, null, -1, null, -1, null));
			return meth;
		} catch (IllegalAccessException | InstantiationException e) {
			throw new RuntimeException(e);
		}
	}

	private ClassWriter getClassWriter() {
		ClassWriter writer = CLASS_WRITER.deref();
		if (writer == null) {
			writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			String className = "fclass$" + ns.getSuffix();
			String signature = GEN_PACKAGE + "/" + className;
			writer.visit(59, ACC_PUBLIC, signature, null, Type.getType(Object.class).getInternalName(), null);
			defineConstructor(writer);
		}
		return writer;
	}

}
