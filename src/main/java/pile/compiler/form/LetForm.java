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
package pile.compiler.form;

import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;
import static pile.core.binding.NativeDynamicBinding.*;
import static pile.nativebase.NativeCore.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import pile.collection.PersistentList;
import pile.collection.PersistentVector;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.Helpers;
import pile.compiler.MethodStack;
import pile.compiler.Scopes;
import pile.compiler.MethodStack.InfiniteRecord;
import pile.compiler.MethodStack.TypeRecord;
import pile.compiler.typed.Any;
import pile.core.Namespace;
import pile.core.Symbol;
import pile.core.binding.IntrinsicBinding;
import pile.core.exception.PileCompileException;
import pile.core.exception.PileException;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.TypeTag;

public class LetForm implements Form {
    
    private static final Logger LOG = LoggerSupplier.getLogger(LetForm.class);

    private static final Symbol DO_SYM = new Symbol("pile.core", "do");

	record LetScopeRecord(String name, TypeRecord tr, String signature, Label start, int index) {
	
	    public String descriptor() {
	        return getType(tr.javaClass()).getDescriptor();
	    }
	}

	private record ProcessedForm(PersistentVector bindings, PersistentList doForm){}

    private final PersistentList form;
	private final Namespace ns;

	public LetForm(PersistentList form) {
		this.ns = NAMESPACE.getValue();
		this.form = form;
	}
	
	@Override
	public DeferredCompilation compileForm(CompilerState compilerState) {

	    ProcessedForm process = process();
//	    System.out.println(process);

		return new DeferredCompilation(TypeTag.SEXP, IntrinsicBinding.LET, (cs) -> {
			MethodVisitor mv = cs.getCurrentMethodVisitor();
			GeneratorAdapter ga = cs.getCurrentGeneratorAdapter();
			MethodStack methodStack = cs.getMethodStack();
			Scopes scope = cs.getScope();
			scope.enterScope(VarScope.METHOD_LET);

			Label endLabel = new Label();

			try {
				Iterator bit = process.bindings().iterator();
				List<LetScopeRecord> scopes = compileBindings(cs, ns, bit);

				Compiler.compile(cs, process.doForm());
				mv.visitLabel(endLabel);

				createLocalVariableMetadata(mv, endLabel, scopes);

			} finally {
				scope.leaveScope();
			}
		});

	}

	@Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
	    ProcessedForm process = process();
    
    	Scopes scope = cs.getScope();
    	
    	scope.enterScope(VarScope.NAMESPACE_LET);
    	try {
    		evaluateBindings(cs, process.bindings());
    		return Compiler.evaluate(cs, process.doForm());
    	} finally {
    		scope.leaveScope();
    	}
    
    }

    private ProcessedForm process() {
        // (let [a 1 b 2] (...))
        var local = form.pop();
    
        // bindings
        var localBindings = Helpers.expectVector(local.head());
        
        int count = localBindings.count();
        if (count % 2 != 0) {
            throw new PileCompileException("Bindings size should be a multiple of 2, size=" + count,
                    LexicalEnvironment.extract(local, form));
        }

        PersistentList rest = local.pop();
        ensureSyntax(rest.seq() != null, form, "Let form must have a body");
        var doForm = rest.conj(DO_SYM);

        return new ProcessedForm(localBindings, doForm);
    }

    static void createLocalVariableMetadata(MethodVisitor mv, Label endLabel, List<LetScopeRecord> scopes) {
        for (LetScopeRecord lsr : scopes) {
            mv.visitLocalVariable(lsr.name(), lsr.descriptor(), lsr.signature(), lsr.start(), endLabel, lsr.index());
        }
    }

    static List<LetScopeRecord> compileBindings(CompilerState cs, Namespace ns, Iterator bindingIterator) {

	    MethodVisitor mv = cs.getCurrentMethodVisitor();
	    GeneratorAdapter ga = cs.getCurrentGeneratorAdapter();
	    Scopes scope = cs.getScope();
	    MethodStack methodStack = cs.getMethodStack();
	    
	    boolean isInfinite = false;

		List<LetScopeRecord> scopes = new ArrayList<>();

		while (bindingIterator.hasNext()) {
			Symbol sym = expectSymbol(bindingIterator.next());
            String symbolName = Helpers.strSym(sym);

			final Object rhs = bindingIterator.next();
			
			// Optimization
			if (rhs instanceof Symbol rhsSym) {
			    if (sym.equals(rhsSym)) {
			        // (let [a a] ...) 
			        // don't create new local
			        LOG.trace("Eliding local redef of %s", rhs);
			        continue;
			    }
			}
						
            DeferredCompilation defer = Compiler.compileDefer(cs, rhs);
            handleLineNumber(mv, rhs);
			defer.compile().accept(cs);
			Label startLabel = new Label();
			mv.visitLabel(startLabel);
			
			var rawRecord = methodStack.popR();
            TypeRecord typeRecord = switch (rawRecord) {
                case TypeRecord tr -> tr;
                // RETHINK allowing this
                case InfiniteRecord _ -> throw new PileCompileException("Infinite loop in let binding expression.",
                        LexicalEnvironment.extract(rhs));
            };
			Class<?> javaClass = typeRecord.javaClass();
			Class<?> localClass = Helpers.getTypeHint(sym, ns)
			                        .map(hint -> {
			                            if (hint.isAssignableFrom(javaClass)) {
			                                return javaClass;
			                            } else if (javaClass.isAssignableFrom(hint)) {
			                                return hint;
			                            } else {
			                                return hint;
			                            }
                        			}).orElse(javaClass);
                        			
			Type javaType = Type.getType(localClass);
			if (! (javaClass.equals(localClass))) {
                if (! (javaClass.isAssignableFrom(localClass) || localClass.isAssignableFrom(javaClass))) {
                    // (let [^String s {:a :b}] ... )
                    LOG.warn("Ignoring impossible type hint: %s on a known %s", localClass, javaClass);
                    javaType = Type.getType(javaClass);
                }
			    ga.checkCast(javaType);
            }
			int index = ga.newLocal(javaType);
			ga.storeLocal(index);
			LOG.trace("Creating local variable %s [%s] with type %s", symbolName, index, javaType);

			var scopeClass = (typeRecord.clazz() == Any.class && localClass == Object.class) ? Any.class : localClass;
			scope.addCurrent(symbolName, scopeClass, index, defer.ref());
			scopes.add(new LetScopeRecord(symbolName, typeRecord, null, startLabel, index));
		}

		return scopes;
	}

	static void evaluateBindings(CompilerState cs, Iterable localBindings) throws Throwable {
		Scopes scope = cs.getScope();
		Iterator bit = localBindings.iterator();
		while (bit.hasNext()) {
			String sym = Helpers.strSym(bit.next());
			Object val = Compiler.evaluate(cs, bit.next());
			scope.addCurrent(sym, getSafeClass(val), Scopes.NO_INDEX, val);
		}
	}
}
