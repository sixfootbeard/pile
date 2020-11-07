/**
 * Copyright 2023 John Hinchberger
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pile.compiler;

import static pile.compiler.Helpers.*;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.MethodVisitor;

import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.collection.PersistentSet;
import pile.collection.PersistentVector;
import pile.compiler.MethodStack.TypeRecord;
import pile.compiler.form.BooleanForm;
import pile.compiler.form.CharForm;
import pile.compiler.form.CollectionLiteralForm;
import pile.compiler.form.Form;
import pile.compiler.form.KeywordForm;
import pile.compiler.form.NullForm;
import pile.compiler.form.NumberForm;
import pile.compiler.form.RegexForm;
import pile.compiler.form.SExpr;
import pile.compiler.form.StringForm;
import pile.compiler.form.SymbolForm;
import pile.core.ISeq;
import pile.core.Keyword;
import pile.core.Metadata;
import pile.core.exception.PileSyntaxErrorException;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.TypeTag;

public class Compiler {

	public static Object evaluate(PersistentList<Object> forms) throws Throwable {
		CompilerState cs = new CompilerState();
		Object last = null;
		for (Object form : forms) {
			last = evaluate(cs, form);
//			var meta = ((Metadata)form).meta();
//			System.out.println(meta);
//			System.out.println(form);
		}
		return last;
	}

	public static Object evaluate(CompilerState cs, Object arg) throws Throwable {
    	Form form = getForm(arg);
    	Object o = form.evaluateForm(cs);
    	return o;
    }

    public static List<Object> evaluateArgs(CompilerState cs, ISeq args) throws Throwable {
		List out = new ArrayList<>();
		for (Object part : ISeq.iter(args)) {
			
			Object ev = Compiler.evaluate(cs, part);
			out.add(MacroEvaluated.unwrap(ev));
		}
		return out;
	}

	public static void compile(CompilerState cs, Object arg) {
		compileDefer(cs, arg).compile().accept(cs);
	}

	public static List<TypeRecord> compileArgs(CompilerState cs, ISeq args) {
    	MethodStack stack = cs.getMethodStack();
    	MethodVisitor mv = cs.getCurrentMethodVisitor();
    
    	int count = 0;
    	for (Object arg : ISeq.iter(args)) {
    	    handleLineNumber(mv, args);
    		Compiler.compile(cs, arg);
    		count++;
    	}
    
    	List<TypeRecord> typeRecords = stack.popN(count);
    
    	return typeRecords;
    }

    public static DeferredCompilation compileDefer(CompilerState compilerState, Object arg) {
		return getForm(arg).compileForm(compilerState);
	}

	public static MacroEvaluated macroEval(CompilerState cs, Object arg, Keyword context) throws Throwable {
		return getForm(arg).macroEvaluateForm(cs, context);
		
	}
	
	public static DeferredCompilation macroCompileDefer(CompilerState cs, Object arg, Keyword context) {
		return getForm(arg).macroCompileForm(cs, context);
	}
	
	public static void macroCompile(CompilerState cs, Object arg, Keyword context) {
		macroCompileDefer(cs, arg, context).compile().accept(cs);
	}


	public static List<TypeRecord> macroCompileArgs(CompilerState cs, ISeq seq, Keyword context) {
		MethodStack stack = cs.getMethodStack();

		int count = 0;
		for (Object arg : ISeq.iter(seq)) {
			Compiler.macroCompile(cs, arg, context);
			count++;
		}

		List<TypeRecord> typeRecords = stack.popN(count);

		return typeRecords;
	}
	
	public static List<Object> macroEvaluateArgs(CompilerState cs, ISeq args, Keyword context) throws Throwable {
        List<Object> out = new ArrayList<>();
        for (Object part : ISeq.iter(args)) {
            
            MacroEvaluated ev = Compiler.macroEval(cs, part, context);
            out.add(MacroEvaluated.unwrap(ev));
        }
        return out;
    }

	private static Form getForm(Object o) {
		TypeTag typeTag = getTag(o);
		switch (typeTag) {
			case NIL:
				return new NullForm();
			case FALSE:
				return new BooleanForm(false);
			case CHAR:
				return new CharForm((Character) o);
			case TRUE:
				return new BooleanForm(true);
			case NUMBER:
				return new NumberForm((Number) o);
			case STRING:
				return new StringForm((String) o);
			case REGEX:
				return new RegexForm(o);
			case SEXP:
			    // FIXME All forms should take ISeq, not PersistentList
				return new SExpr(toList(o));
			case SYMBOL:
				return new SymbolForm(o);
			case KEYWORD:
				return new KeywordForm(o);
			case MAP:
				return new CollectionLiteralForm<>(TypeTag.MAP, (PersistentMap) o, CollectionLiteralForm.MAP_DESCRIPTOR);
			case VEC:
				return new CollectionLiteralForm<>(TypeTag.VEC, (PersistentVector) o, CollectionLiteralForm.VEC_DESCRIPTOR);
			case SET:
				return new CollectionLiteralForm<>(TypeTag.SET, (PersistentSet) o, CollectionLiteralForm.SET_DESCRIPTOR);
			default:
				throw new PileSyntaxErrorException("Bad tag type", LexicalEnvironment.extract(o));
		}
	}

}
