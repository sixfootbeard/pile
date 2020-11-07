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
package pile.core.indy;

import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;
import static pile.core.indy.IndyHelpers.*;

import java.lang.invoke.MethodHandles.Lookup;
import java.util.Optional;

import org.objectweb.asm.ConstantDynamic;

import pile.core.ConstForm;
import pile.util.ConstantDynamicBootstrap;

public record CompilerFlags(int monomorphicMissThreshold, int polymorphicChainThreshold, int megamorphicSizeThreshold) implements ConstForm<ConstantDynamic> {

	public CompilerFlags() {
		this(5, 3, 5);
	}
	
	public ConstantDynamic toCondy() {
		String descriptor = getMethodDescriptor(getType(CompilerFlags.class), getType(Lookup.class), STRING_TYPE,
				getType(Class.class), INT_TYPE, INT_TYPE, INT_TYPE);
		return makeCondy("makeFlags", CompilerFlags.class, "bootstrap", descriptor, CompilerFlags.class,
				monomorphicMissThreshold, polymorphicChainThreshold, megamorphicSizeThreshold);
	}

	@ConstantDynamicBootstrap
	public static CompilerFlags bootstrap(Lookup lookup, String name, Class<CompilerFlags> clazz,
			int monomorphicMissThreshold, int polymorphicChainThreshold, int megamorphicSizeThreshold) {
		return new CompilerFlags(monomorphicMissThreshold, polymorphicChainThreshold, megamorphicSizeThreshold);
	}

	@Override
	public Optional<ConstantDynamic> toConst() {
		return Optional.of(toCondy());
	}
}
