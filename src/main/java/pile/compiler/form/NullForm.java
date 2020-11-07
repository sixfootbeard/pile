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

import static pile.compiler.Helpers.*;
import static pile.core.indy.IndyHelpers.*;

import java.lang.invoke.MethodHandles.Lookup;
import java.util.Optional;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Opcodes;

import pile.compiler.CompilerState;
import pile.compiler.Constants;
import pile.compiler.DeferredCompilation;
import pile.core.ConstForm;
import pile.core.parse.TypeTag;
import pile.util.ConstantDynamicBootstrap;

public class NullForm implements Form {

	public NullForm() {
	}

	@Override
	public DeferredCompilation compileForm(CompilerState compilerState) {
		return new DeferredCompilation(TypeTag.NIL, Constants.nullCondy(), (cs) -> {
			cs.getCurrentMethodVisitor().visitInsn(Opcodes.ACONST_NULL);
			cs.getMethodStack().pushNull();
		});
	}

	@Override
	public Object evaluateForm(CompilerState cs) throws Throwable {
		return null;
	}
}
