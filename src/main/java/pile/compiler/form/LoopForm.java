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
import static pile.util.CollectionUtils.*;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import pile.collection.PersistentList;
import pile.compiler.ClassSlot;
import pile.compiler.CloseNoThrow;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.Helpers;
import pile.compiler.LoopCompileTarget;
import pile.compiler.LoopEvaluationTarget;
import pile.compiler.LoopTargetType;
import pile.compiler.MethodStack;
import pile.compiler.RecurId;
import pile.compiler.Scopes;
import pile.compiler.MethodStack.TypeRecord;
import pile.compiler.form.LetForm.LetScopeRecord;
import pile.compiler.typed.Any;
import pile.core.ISeq;
import pile.core.Namespace;
import pile.core.Seqable;
import pile.core.Symbol;
import pile.core.binding.IntrinsicBinding;
import pile.core.binding.NativeDynamicBinding;
import pile.core.exception.PileCompileException;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.TypeTag;

public class LoopForm implements Form {

    private record LoopBindingSlot(LetScopeRecord lf, Class<?> actualType) {};

	private final PersistentList form;
	private final Namespace ns;

	public LoopForm(PersistentList form) {
		this.ns = NAMESPACE.getValue();
		this.form = form;
	}

	@SuppressWarnings("rawtypes")
    @Override
	public DeferredCompilation compileForm(CompilerState compilerState) {


		return new DeferredCompilation(TypeTag.SEXP, IntrinsicBinding.LET, (cs) -> {
		    // (loop [a 1 b 2] (...))
		    Iterator fit = form.iterator();
		    fit.next(); // loop
		    
		    // bindings
		    var localBindings = Helpers.expectVector(fit.next());
		    
		    int count = localBindings.count();
		    if (count % 2 != 0) {
		        throw new PileCompileException("Bindings size should be a multiple of 2, size=" + count, LexicalEnvironment.extract(localBindings, form));
		    }
		    Iterator bit = localBindings.iterator();
		    
			MethodVisitor mv = cs.getCurrentMethodVisitor();
			GeneratorAdapter ga = cs.getCurrentGeneratorAdapter();
			MethodStack methodStack = cs.getMethodStack();
			
			boolean lastTailPositionValue = cs.isCompiledRecurPosition();
			

			Scopes scope = cs.getScope();
			scope.enterScope(VarScope.METHOD_LET);
			

			try {
				List<LoopBindingSlot> scopes;
				try (CloseNoThrow c = NativeDynamicBinding.BOXED_NUMBERS.withUpdate(true)) {
					// Loop types unstable, must box numbers
					scopes = compileBindings(cs, mv, ga, methodStack, scope, bit);
				}
                List<ClassSlot> classSlots = mapL(scopes,
                        ls -> new ClassSlot(ls.lf().tr().javaClass(), ls.lf().index()));

                cs.setCompileRecurPosition(true);

				Label loopStart = new Label();
				Label endLabel = new Label();
				LoopCompileTarget lt = new LoopCompileTarget(loopStart, count / 2, LoopTargetType.LOCALS,
						classSlots, new ArrayList<>());

				mv.visitLabel(loopStart);
				cs.pushLoopTarget(lt);
				try {
					Object exp = fit.next();
					Compiler.compile(cs, exp);
					mv.visitLabel(endLabel);
					
					// TODO Reconcile the loopslot actual type vs. recur types.
					
                    // FIXME The fact that the original type is object may inhibit correct static
                    // types. We need to be able to try the actual types first.

					LetForm.createLocalVariableMetadata(mv, endLabel, mapL(scopes, LoopBindingSlot::lf));
				} finally {
					cs.popLoopTarget();
					cs.setCompileRecurPosition(lastTailPositionValue);
				}
			} finally {
				scope.leaveScope();
			}
		});
	}

	List<LoopBindingSlot> compileBindings(CompilerState cs, MethodVisitor mv, GeneratorAdapter ga,
			MethodStack methodStack, Scopes scope, Iterator bindingIterator) {

		List<LoopBindingSlot> scopes = new ArrayList<>();

		while (bindingIterator.hasNext()) {
			Symbol sym = expectSymbol(bindingIterator.next());
			Class<?> symbolTypeHintClass = sym.getAnnotatedType(ns);
            String symbolName = Helpers.strSym(sym);

			DeferredCompilation defer = Compiler.compileDefer(cs, bindingIterator.next());
			defer.compile().accept(cs);
			Label startLabel = new Label();
			mv.visitLabel(startLabel);

			// rhs might be primitive, box it if so
			TypeRecord stackTypeRecord = methodStack.popR();
			Class<?> rhsClass = stackTypeRecord.clazz();
			
			// Must always use generic object for types for loop variables
			// TODO Eventually we may be able to observe if the recur targets use the same
			// types and optimize this, but for now use object.
			Class<?> symbolTypeHintCompilableClass = toCompilableType(symbolTypeHintClass);
			Class<?> rhsCompilableClass = toCompilableType(rhsClass);
			
			coerce(ga, rhsCompilableClass, symbolTypeHintCompilableClass);
			
			int index = ga.newLocal(Type.getType(symbolTypeHintCompilableClass));
			ga.storeLocal(index);

//			System.out.println("Creating loop variable " + symbolName + " [" + index + "]" + " with type "+ symbolTypeHintCompilableClass);

			scope.addCurrent(symbolName, /*TODO Check this type*/ symbolTypeHintCompilableClass , index, defer.ref());
			LetScopeRecord lsr = new LetScopeRecord(symbolName, new TypeRecord(symbolTypeHintCompilableClass), null, startLabel, index);
            scopes.add(new LoopBindingSlot(lsr, rhsClass));
		}

		return scopes;
	}

	@Override
	public Object evaluateForm(CompilerState cs) throws Throwable {

		// (loop [a 1 b 2] (...))
		Iterator fit = form.iterator();
		fit.next(); // loop
		
		boolean lastEvalTailPosition = cs.isEvalRecurPosition();

		// bindings
		var localBindings = Helpers.expectVector(fit.next());

		int count = localBindings.count();
		if (count % 2 != 0) {
			throw new PileCompileException("Bindings size should be a multiple of 2, size=" + count, LexicalEnvironment.extract(localBindings, form));
		}
		Iterator bit = localBindings.iterator();

		int bindingCount = count / 2;

		Object exp = fit.next();
		Scopes scope = cs.getScope();
		boolean first = true;
		List<Object> bindings = new ArrayList<>(bindingCount);
        // Repeatedly evaluate the body of the loop. The loop bindings are passed in via
        // a LoopEvaluationTarget record.
		for (;;) {
			// TODO Think about this.
			scope.enterScope(VarScope.NAMESPACE_LET);
			try {
				if (first) {
					LetForm.evaluateBindings(cs, localBindings);
				} else {
					updateBindings(scope, localBindings, bindings);
					bindings.clear();
				}
				
				cs.setEvalRecurPosition(true);

				LoopEvaluationTarget lt = new LoopEvaluationTarget(bindingCount, new AtomicBoolean(), bindings);
				cs.pushLoopEvalTarget(lt);
				Object result;
				try {
					result = Compiler.evaluate(cs, exp);
				} finally {
					cs.popLoopEvalTarget();
					cs.setEvalRecurPosition(lastEvalTailPosition);
				}
				if (!lt.doRecur().get()) {
					return result;
				}
				first = false;
			} finally {
				scope.leaveScope();
			}
		}

	}

	private void updateBindings(Scopes scope, Seqable localBindings, List<Object> bindings) {
		Iterator<Object> names = ISeq.iter(localBindings.seq()).iterator();
		for (Object newValue : bindings) {
			String sym = strSym(names.next());
			scope.addCurrent(sym, newValue.getClass(), Scopes.NO_INDEX, newValue);
			names.next(); // chuck
		}

	}

}
