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
import static pile.nativebase.NativeCore.*;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.form.Form;
import pile.core.binding.IntrinsicBinding;
import pile.core.parse.TypeTag;

public class ThrowForm implements Form {

	private final Object form;

	public ThrowForm(Object form) {
		this.form = form;
	}
	
	@Override
	public DeferredCompilation compileForm(CompilerState compilerState) {
		// (throw (new RuntimeException (str "err")))
		return new DeferredCompilation(TypeTag.SEXP, IntrinsicBinding.THROW_FORM, cs -> {			
			MethodVisitor mv = cs.getCurrentMethodVisitor();
			// Push exception, hopefully
			Compiler.compile(cs, second(form));
			var clazz = cs.getMethodStack().peek();
			
			if (! Throwable.class.isAssignableFrom(clazz)) {
			    mv.visitTypeInsn(Opcodes.CHECKCAST, getType(Throwable.class).getInternalName());
			}
			// TODO Not sure this makes sense
			// For things like (if (= 1 2) "3" (throw ...)) what's the stack type after if?
			cs.getMethodStack().popR();
			cs.getMethodStack().push(Object.class);
			
			// throw
			mv.visitInsn(Opcodes.ATHROW);			
		});
	}

	@Override
	public Object evaluateForm(CompilerState cs) throws Throwable {
		var ex = (Throwable) Compiler.evaluate(cs, second(form));
		throw ex;
	}

}
