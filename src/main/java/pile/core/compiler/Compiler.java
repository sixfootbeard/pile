package pile.core.compiler;

import static pile.core.binding.NativeDynamicBinding.*;
import static pile.core.compiler.CompilerBinding.*;
import static pile.core.compiler.Helpers.ensureSize;
import static pile.core.compiler.Helpers.expectList;
import static pile.nativebase.NativeCore.first;
import static pile.nativebase.NativeCore.more;
import static pile.nativebase.NativeCore.second;

import java.lang.invoke.SwitchPoint;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import pile.collection.PersistentList;
import pile.core.Cons;
import pile.core.ISeq;
import pile.core.Metadata;
import pile.core.Namespace;
import pile.core.RuntimeRoot;
import pile.core.Symbol;
import pile.core.binding.Binding;
import pile.core.binding.BindingType;
import pile.core.binding.ImmutableBinding;
import pile.core.binding.IntrinsicBinding;
import pile.core.binding.NativeDynamicBinding;
import pile.core.indy.CompiledMethod;
import pile.core.parse.Parser;
import pile.core.parse.Parser.TypeTag;
import pile.util.Pair;

public class Compiler {

	public static void evaluateTop(PersistentList<Object> forms) {
		final Namespace ns;
		Iterator<Object> it = forms.iterator();
		Object firstForm = it.next();
		if (firstForm instanceof PersistentList nsform) {
			Helpers.expectLit(first(nsform), "ns");

			String nsStr = (String) Helpers.lit(second(nsform)).getVal();
			ns = RuntimeRoot.defineOrGet(nsStr);
			NativeDynamicBinding.NAMESPACE.set(ns);
		} else {
			throw Helpers.error("First form must be a sexp");
		}

		while (it.hasNext()) {
			PersistentList form = Helpers.expectList(it.next());
			ns.addForm(form);
			new SExpr(form).evaluateForm();
		}
	}

	static Object evaluate(Object arg) {
		Metadata lit = (Metadata) arg;
		TypeTag typeTag = (TypeTag) lit.meta().get(Parser.TAG_KEY);
		switch (typeTag) {
		case FALSE:
			return false;
		case TRUE:
			return true;
		case NUMBER:
			return (Number) Helpers.number(arg);
		case STRING:
			return (String) Helpers.lit(lit).getVal();
		case SEXP:
			return new SExpr(Helpers.expectList(arg)).evaluateForm();
		case SYMBOL:
			return new SymbolExpr(arg).evaluateForm();
		default:
			throw Helpers.error("Unimplemented");
		}

	}

	public static void compile(MethodVisitor mv, Object arg) {
		Metadata lit = (Metadata) arg;
		TypeTag typeTag = (TypeTag) lit.meta().get(Parser.TAG_KEY);
		switch (typeTag) {
		case FALSE:
			mv.visitInsn(Opcodes.ICONST_0);
			break;
		case TRUE:
			mv.visitInsn(Opcodes.ICONST_1);
			break;
//            case NUMBER:
		// TODO primitive numbers?
//                return (Number) Helpers.number(arg);
		case STRING:
			mv.visitLdcInsn(arg);
			break;
		case SEXP:
			new SExpr(expectList(arg)).compileForm(mv);
			break;
		case SYMBOL:
			new SymbolExpr(arg).compileForm(mv);
			break;

		default:
			throw Helpers.error("Unimplemented: " + typeTag);
		}
	}

}
