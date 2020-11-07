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
import static pile.compiler.Helpers.*;

import java.lang.invoke.MethodHandles.Lookup;
import java.util.Optional;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.core.Keyword;
import pile.core.exception.InvariantFailedException;
import pile.core.exception.PileInternalException;
import pile.core.parse.TypeTag;
import pile.util.ConstantDynamicBootstrap;

public class KeywordForm implements Form {

	private final Keyword keyword;

	public KeywordForm(Object arg) {
		this.keyword = (Keyword) arg;
	}

	@Override
	public DeferredCompilation compileForm(CompilerState compilerState) {		
		
		// ConstantDynamic
		ConstantDynamic cons = keyword.toConst()
				.orElseThrow(internalErrorGen("Keyword should have a constant form"));
		return new DeferredCompilation(TypeTag.KEYWORD, keyword, Optional.of(cons), (cs) -> {
			MethodVisitor mv = cs.getCurrentMethodVisitor();
			mv.visitLdcInsn(cons);
			cs.getMethodStack().pushConstant(Keyword.class);
	
		});
	}

	@Override
	public Object evaluateForm(CompilerState cs) throws Throwable {
		return keyword;
	}
	
	@ConstantDynamicBootstrap
	public static Keyword bootstrap(Lookup lookup, String name, Class<Keyword> clazz, String... parts) {
		if (parts.length == 1) {
			return Keyword.of(null, parts[0]);	
		} else if (parts.length == 2) {
			return Keyword.of(parts[0], parts[1]);	
		} else {
		    throw new PileInternalException("Unexpected number of keyword arguments, expected 1 or 2, found: " + parts.length);
		}
		
	}

}
