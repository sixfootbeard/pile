package pile.core.compiler;

import static pile.core.compiler.CompilerBinding.METHOD_LOCALS;

import java.util.List;

import org.objectweb.asm.Type;

import pile.collection.PersistentList;
import pile.core.Metadata;
import pile.core.parse.Parser;
import pile.core.parse.Parser.Literal;
import pile.core.parse.Parser.TypeTag;

public class Helpers {

	static int findLocal(String strSym) {
		List<String> allLocals = METHOD_LOCALS.deref();
		int i = 1; // this
		for (String local : allLocals) {
			if (strSym.equals(local)) {
				return i;
			}
			++i;
		}

		return -1;
	}

	static void expectLit(Object lit, Object eq) {
		if (!eq.equals(lit(lit).getVal())) {
			throw error("Expected literal '" + eq + "', found: " + eq);
		}
	}

	static PersistentList expectList(Object list) {
		if (list instanceof PersistentList pl) {
			return pl;
		}
		throw error("Expected list, found: " + list);
	}

	static PersistentList expectVector(Object list) {
		expectType(list, Parser.TypeTag.VEC);
		if (list instanceof PersistentList pl) {
			return pl;
		}
		throw error("Expected vector, found: " + list);
	}

	static void expectType(Object list, TypeTag type) {
		if (list instanceof Metadata meta) {
			TypeTag foundtype = (TypeTag) Parser.TAG_KEY.call(meta.meta());
			if (foundtype == type) {
				return;
			}
			throw error("Expected type=" + type + ", found type=" + foundtype);
		}
		throw error("Expected type=" + type + ", object not meta:" + list);
	}

	static Literal lit(Object o) {
		if (o instanceof Literal lit) {
			return lit;
		}
		throw error("Expected form '" + o + "' to be a literal");
	}

	static RuntimeException error(String string) {
		return new RuntimeException(string);
	}

	static String str(Object first) {
		Literal lit = lit(first);
		TypeTag typeTag = (TypeTag) lit.meta().get(Parser.TAG_KEY);
		if (typeTag.equals(Parser.TypeTag.STRING)) {
			return (String) lit.getVal();
		}
		throw error("Expected string literal, found: " + lit.getVal());
	}

	static String strSym(Object first) {
		Literal lit = lit(first);
		TypeTag typeTag = (TypeTag) lit.meta().get(Parser.TAG_KEY);
		if (typeTag.equals(Parser.TypeTag.SYMBOL)) {
			return (String) lit.getVal();
		}
		throw error("Expected string literal, found: " + lit.getVal());
	}

	static void ensureSize(String name, PersistentList form, int size) {
		if (form.count() != size) {
			throw Helpers.error("Unexpected size: " + form.count() + ", expected : " + size + " for " + name);
		}
	}

	static Number number(Object arg) {
		Literal lit = lit(arg);
		ensure(lit, TypeTag.NUMBER);
		return (Number) lit.getVal();
	}

	static void ensure(Literal lit, TypeTag tag) {
		TypeTag typeTag = (TypeTag) lit.meta().get(Parser.TAG_KEY);
		if (!typeTag.equals(tag)) {
			throw new RuntimeException("Bad type:" + tag);
		}

	}

	static Type[] getObjectTypeArray(int sizes) {
		Type[] args = new Type[sizes];
		for (int i = 0; i < sizes; ++i) {
			args[i] = Helpers.OBJECT_TYPE;
		}
		return args;
	}

	static final Type OBJECT_TYPE = Type.getType(Object.class);

}
