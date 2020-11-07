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
package pile.compiler.form.exception;

import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;
import static pile.nativebase.NativeCore.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;

import pile.collection.PersistentList;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.ExceptionBlockTarget;
import pile.compiler.MethodStack;
import pile.compiler.Scopes;
import pile.compiler.CompilerState.SavedStack;
import pile.compiler.MethodStack.TypeRecord;
import pile.compiler.Scopes.ScopeLookupResult;
import pile.compiler.form.AbstractListForm;
import pile.compiler.form.DoForm;
import pile.compiler.form.VarScope;
import pile.core.Keyword;
import pile.core.Namespace;
import pile.core.Symbol;
import pile.core.binding.IntrinsicBinding;
import pile.core.parse.TypeTag;

public class TryForm extends AbstractListForm {

    private static final String PILE_NAMESPACE = "pile.core";

    private record CatchPart(Class<?> catchClass, Symbol eSym, Object cbody) {
    };

    private record TryParts(PersistentList<?> body, List<CatchPart> catches, Object finallyBody) {
    };

    public TryForm(Object form) {
        super(expectList(form));
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {
        // (try
        // (/ 1 0)
        // (catch ArithmeticException e (str (.getMessage e)))
        // (finally (prn "finally")))

        // 1 body
        // 0-N catch
        // 0/1 finally

        return new DeferredCompilation(TypeTag.SEXP, IntrinsicBinding.TRY_FORM, cs -> compile(cs, Compiler::compile));
    }
    
    @Override
    public DeferredCompilation macroCompileForm(CompilerState compilerState, Keyword context) {
        BiConsumer<CompilerState, Object> f = (cs, o) -> Compiler.macroCompile(cs, o, context);
        return new DeferredCompilation(TypeTag.SEXP, IntrinsicBinding.TRY_FORM, cs -> compile(cs, f));
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
        TryParts parts = parse(cs);

        Object out = null;
        try {
            out = Compiler.evaluate(cs, parts.body());
        } catch (Throwable t) {
            boolean handled = false;
            for (CatchPart cp : parts.catches()) {
                if (cp.catchClass().isAssignableFrom(t.getClass())) {
                    handled = true;
                    cs.getScope().enterScope(VarScope.NAMESPACE_LET);
                    try {
                        cs.getScope().addCurrent(cp.eSym().getName(), t.getClass(), Scopes.NO_INDEX, t);
                        return Compiler.evaluate(cs, cp.cbody());
                    } finally {
                        cs.getScope().leaveScope();
                    }
                }
            }
            if (!handled) {
                throw t;
            }
            // handle t
        } finally {
            Compiler.evaluate(cs, parts.finallyBody());
        }
        return out;
    }

    private TryParts parse(CompilerState cs) {
        // TODO Try to enforce ordering
        PersistentList body = expectList(second(form));
        List<CatchPart> catches = new ArrayList<>();
        Object finallyBody = null;

        for (Object f : nnext(form)) {
            Object first = first(f);
            Symbol sym = expectSymbol(first);
            ScopeLookupResult slr = cs.getScope().lookupSymbolScope(sym);

            ensure(PILE_NAMESPACE.equals(slr.namespace()),
                    "Wrong sym namespace, expected " + PILE_NAMESPACE + ", found " + slr.namespace());

            if ("catch".equals(sym.getName())) {
                var catchForm = expectList(f);
                // catch clazz var-sym body
                Symbol clazzSym = expectSymbol(second(catchForm));
                Class<?> clazz = clazzSym.getAsClass(ns);

                Symbol varSym = expectSymbol(nth(catchForm, 2));
                Object catchBody = catchForm.pop().pop().pop().head();

                CatchPart cp = new CatchPart(clazz, varSym, catchBody);
                catches.add(cp);
            } else if ("finally".equals(sym.getName())) {
                ensure(finallyBody == null, "Cannot have more than one finally block");
                finallyBody = second(f);
            } else {
                throw error("Expected catch/finally, found: " + sym);
            }
        }
        return new TryParts(body, catches, finallyBody);
    }

    private void compile(CompilerState cs, BiConsumer<CompilerState, Object> f) {
        MethodVisitor mv = cs.getCurrentMethodVisitor();
        GeneratorAdapter ga = cs.getCurrentGeneratorAdapter();
        Scopes scope = cs.getScope();
        MethodStack stack = cs.getMethodStack();

        // We must save the stack because even though on the JVM athrow is stack
        // clearing we only have expression semantics for (try) and must save the stack.
        SavedStack savedStack = cs.saveExistingStack();

        TryParts parts = parse(cs);

        Label beforeCleanFinally = new Label();
        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label throwingFinally = new Label();

        // try body
        ga.visitLabel(tryStart);
        f.accept(cs, parts.body());
        stack.pop();
        int returnValueIndex = ga.newLocal(OBJECT_TYPE);
        // TODO Type coercions
        ga.storeLocal(returnValueIndex);
        ga.goTo(beforeCleanFinally);
        ga.visitLabel(tryEnd);

        cs.pushTryStart(new ExceptionBlockTarget(tryStart, tryEnd));

        try {
            // next local is exception
            for (CatchPart cp : parts.catches()) {
                int index = ga.newLocal(getType(cp.catchClass()));
                Label startCatch = new Label();
                Label endCatch = new Label();
                
                ga.visitLabel(startCatch);
                ga.visitTryCatchBlock(tryStart, tryEnd, startCatch, getInternalName(cp.catchClass()));

                // astore_n <exception>
                ga.storeLocal(index);

                // metadata sym
                scope.enterScope(VarScope.METHOD_LET);
                scope.addCurrent(cp.eSym().getName(), cp.catchClass(), index, null);

                // Do body
                f.accept(cs, cp.cbody());
                Class<?> catchBodyResultValue = stack.pop();
                if (catchBodyResultValue.isPrimitive()) {
                    ga.box(getType(catchBodyResultValue));
                }
                ga.storeLocal(returnValueIndex);
                
                ga.visitLabel(endCatch);

                // finally (if present)
                if (parts.finallyBody() != null) {
                    ga.visitTryCatchBlock(startCatch, endCatch, throwingFinally, null);
                    f.accept(cs, parts.finallyBody());
                    DoForm.popStack(ga, stack);
                }
                ga.visitJumpInsn(Opcodes.GOTO, beforeCleanFinally);
                scope.leaveScope();
            }
            if (parts.finallyBody() != null) {
                // Throwing finally
                ga.visitTryCatchBlock(tryStart, tryEnd, throwingFinally, null);
                ga.visitLabel(throwingFinally);
                int toThrowIndex = ga.newLocal(getType(Throwable.class));
                ga.storeLocal(toThrowIndex);
                f.accept(cs, parts.finallyBody());
                ga.loadLocal(toThrowIndex);
                ga.throwException();
            }
            ga.visitLabel(beforeCleanFinally);
            
            if (parts.finallyBody() != null) {
                f.accept(cs, parts.finallyBody());
                DoForm.popStack(ga, stack);
            }
            // stack - restore
            cs.restoreStack(savedStack);
            ga.loadLocal(returnValueIndex);
            stack.push(Object.class);
        } finally {
            cs.popTryStart();
        }
    }
}
