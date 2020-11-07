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

import java.util.Optional;

import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.core.parse.TypeTag;

public class CharForm implements Form {
	
	private final Character c;	
	
	public CharForm(Character c) {
		this.c = c;
	}

	@Override
	public DeferredCompilation compileForm(CompilerState compilerState) {
		return new DeferredCompilation(TypeTag.CHAR, c, Optional.of(c), cs -> {
			cs.getCurrentMethodVisitor().visitLdcInsn(c);
			cs.getMethodStack().pushConstant(Character.TYPE);
		});
	}

	@Override
	public Character evaluateForm(CompilerState cs) throws Throwable {
		return c;
	}

}
