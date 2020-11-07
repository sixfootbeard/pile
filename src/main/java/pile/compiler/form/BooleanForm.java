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
import static pile.core.indy.IndyHelpers.*;

import java.lang.invoke.MethodHandles.Lookup;
import java.util.Optional;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Opcodes;

import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.core.parse.TypeTag;
import pile.util.ConstantDynamicBootstrap;

public class BooleanForm implements Form {

	private final int code;
	private final TypeTag tag;
	private boolean b;

	public BooleanForm(boolean b) {
		this.b = b;
		code = b ? Opcodes.ICONST_1 : Opcodes.ICONST_0;
		tag = b ? TypeTag.TRUE : TypeTag.FALSE;
	}

	@Override
	public DeferredCompilation compileForm(CompilerState compilerState) {
		ConstantDynamic condy = toCondy(b);
		
		return new DeferredCompilation(tag, Boolean.valueOf(b), Optional.of(condy), (cs) -> {
			cs.getCurrentMethodVisitor().visitInsn(code);
			cs.getMethodStack().pushConstant(boolean.class);
		});
	}

	public static ConstantDynamic toCondy(boolean b) {
		String cdesc = getConstantBootstrapDescriptor(Boolean.class, getType(Integer.class));
		return makeCondy("bootstrap", BooleanForm.class, "bootstrap", cdesc, Boolean.class, b ? 1 : 0);
	}
	
	@Override
	public Boolean evaluateForm(CompilerState cs) throws Throwable {
		return b;
	}
	
	@ConstantDynamicBootstrap
	public static Boolean bootstrap(Lookup lookup, String name, Class<Boolean> clazz, Integer val) {
		return val.intValue() == 0 ? Boolean.FALSE : Boolean.TRUE;
	}

}
