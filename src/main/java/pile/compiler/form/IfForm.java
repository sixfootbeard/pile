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

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;
import static pile.nativebase.NativeCore.*;

import java.util.function.BiConsumer;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import pile.collection.PersistentList;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.Helpers;
import pile.compiler.MethodStack;
import pile.compiler.typed.Any;
import pile.core.Keyword;
import pile.core.binding.IntrinsicBinding;
import pile.core.parse.TypeTag;

public class IfForm implements Form {

	private final PersistentList form;

	public IfForm(PersistentList form) {
		this.form = form;
	}

	@Override
	public DeferredCompilation compileForm(CompilerState complierState) {
		return new DeferredCompilation(TypeTag.SEXP, IntrinsicBinding.IF, (cs) -> {
		    compile(cs, Compiler::compile);
		});
	}
	
	@Override
	public DeferredCompilation macroCompileForm(CompilerState compilerState, Keyword context) {
	    return new DeferredCompilation(TypeTag.SEXP, IntrinsicBinding.IF, (cs) -> {
	        compile(cs, (compst, arg) -> Compiler.macroCompile(cs, arg, context));
	    });
	}
	
	private void compile(CompilerState cs, BiConsumer<CompilerState, Object> fn) {
        MethodVisitor mv = cs.getCurrentMethodVisitor();
        MethodStack stack = cs.getMethodStack();

        // test
        Object testSyntax = second(form);
        handleLineNumber(mv, testSyntax);
        fn.accept(cs, testSyntax);

        Class<?> testTypeClass = stack.peek();          

        if (!testTypeClass.equals(boolean.class)) {
            CompilerState.boxTop(cs);
            mv.visitMethodInsn(INVOKESTATIC, Type.getType(Helpers.class).getInternalName(), "ifCheck",
                    Type.getMethodDescriptor(Type.getType(boolean.class), OBJECT_TYPE), false);
        }
        stack.pop();

        mv.visitInsn(ICONST_1);
        Label elseLabel = new Label();
        Label nextInsn = new Label();
        mv.visitJumpInsn(IF_ICMPNE, elseLabel);

        // then
        Object thenSyntax = ssecond(form);
        handleLineNumber(mv, thenSyntax);
        fn.accept(cs, thenSyntax);
        CompilerState.boxTop(cs);
        Class<?> thenClass = stack.pop();
        mv.visitJumpInsn(GOTO, nextInsn);

        // else
        final Class<?> elseClass;
        mv.visitLabel(elseLabel);
        if (form.count() == 4) {
            Object elseSyntax = fnext(nnext(form));
            handleLineNumber(mv, elseSyntax);
            fn.accept(cs, elseSyntax);
            CompilerState.boxTop(cs);
            elseClass = stack.pop();
        } else {
            mv.visitInsn(Opcodes.ACONST_NULL);
            elseClass = Any.class;
        }

        mv.visitLabel(nextInsn);

        if (thenClass.equals(elseClass)) {
            stack.push(thenClass);
        } else {            
            if (thenClass.isAssignableFrom(elseClass)) {
                stack.push(thenClass);
            } else if (elseClass.isAssignableFrom(thenClass)) {
                stack.push(elseClass);
            } else {
                stack.pushAny();
            }
        }
	}

	private Class<?> box(GeneratorAdapter ga, Class<?> thenClass) {
		Class<?> wrapper = Helpers.primitiveToWrapper(thenClass);
		ga.box(getType(thenClass));
		return wrapper != null ? wrapper : thenClass;
	}

	@Override
	public Object evaluateForm(CompilerState cs) throws Throwable {
		// (if (eval) (then) (else))
		Object result = Compiler.evaluate(cs, second(form));
		if (ifCheck(result)) {
			return Compiler.evaluate(cs, ssecond(form));
		} else {
			if (form.count() == 4) {
				return Compiler.evaluate(cs, fnext(nnext(form)));
			} else {
				return null;
			}
		}
	}
	
	public static String DOCUMENTATION = """
            The 'if' expression tests a condition and returns the result of the 'then' branch if true, and the 'else' branch if false.
            
            ;; (if test then else)
            
            (if (< 3 10) "less than" "greater than")
            ;; "less than"
            """;

}
