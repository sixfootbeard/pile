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

public class StringForm implements Form {

	private final String s;

	public StringForm(String s) {
		this.s = s;
	}

	@Override
	public DeferredCompilation compileForm(CompilerState compilerState) {
		return new DeferredCompilation(TypeTag.STRING, s, Optional.of(s), (cs) -> {
			cs.getCurrentMethodVisitor().visitLdcInsn(s);
			cs.getMethodStack().pushConstant(String.class);
		});
	}

	@Override
	public String evaluateForm(CompilerState cs) throws Throwable {
		return s;
	}

}
