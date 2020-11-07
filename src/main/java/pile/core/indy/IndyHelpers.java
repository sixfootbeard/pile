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

import static pile.compiler.Helpers.*;
import static pile.util.CollectionUtils.*;
import static java.lang.invoke.MethodType.*;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import pile.compiler.Helpers;
import pile.util.CollectionUtils;

public class IndyHelpers {

	private static final Handle ENUM_HANDLE = new Handle(Opcodes.H_INVOKESTATIC,
			Type.getType(ConstantBootstraps.class).getInternalName(), "enumConstant",
			Helpers.getConstantBootstrapDescriptor(Enum.class), false);

	private IndyHelpers() {}

	/**
	 * Creates an ASM condy form for the specified enum using
	 * {@link ConstantBootstraps}.
	 * 
	 * @param <E>
	 * @param e
	 * @return
	 */
	public static <E extends Enum<E>> ConstantDynamic forEnum(Enum<E> e) {
		return new ConstantDynamic(e.name(), Type.getDescriptor(e.getClass()), ENUM_HANDLE);
	}
	
	public static void condy(MethodVisitor mv, String methodName, Class<?> returnType, Class<?> bootstrapClass, String bootstrapMethodName,
			Object... extra) {
		
		Type[] types = mapA(extra, o -> Type.getType(o.getClass()), Type[]::new);

		String constantBootstrapDescriptor = getConstantBootstrapDescriptor(returnType, types);
		ConstantDynamic condy = makeCondy(methodName, bootstrapClass, bootstrapMethodName, constantBootstrapDescriptor, returnType,
				extra);
		mv.visitLdcInsn(condy);

	}

	public static ConstantDynamic makeCondy(String methodName, Class<?> bootstrapClass, String bootstrapMethodName,
			String constantBootstrapDescriptor, Class<?> returnType, Object... extra) {
		Handle h = new Handle(H_INVOKESTATIC, Type.getType(bootstrapClass).getInternalName(),
				bootstrapMethodName, constantBootstrapDescriptor, false);
		ConstantDynamic condy = new ConstantDynamic(methodName, getDescriptor(returnType), h, extra);
		return condy;
	}

	public static void indy(MethodVisitor mv, String methodName, Class<?> bootstrapClass, String bootstrapMethodName,
			Class<?> returnType, Type[] stackTypes, Object... extra) {
		
		Type[] types = mapA(extra, o -> Type.getType(o.getClass()), Type[]::new);

		Handle h = new Handle(H_INVOKESTATIC, Type.getType(bootstrapClass).getInternalName(),
				bootstrapMethodName, getBootstrapDescriptor(types), false);
		mv.visitInvokeDynamicInsn(methodName, Type.getMethodDescriptor(Type.getType(returnType), stackTypes), h, extra);

	}

    /**
     * Call an indy bootstrap using the extra static args but the bootstrap
     * signature is expected to have a vararg suffix after the 3 required args.
     * 
     * @param mv
     * @param methodName          The name of the method passed to the bootstrap
     *                            function (<b>not</b> the name of the bootstrap
     *                            function itself)
     * @param bootstrapClass      The bootstrap class
     * @param bootstrapMethodName The name of the bootstrap method to call
     * @param returnType          Return type of the function call
     * @param stackTypes          The types on the stack that, with the return type,
     *                            form the method type for the indy call.
     * @param extra               All the extra args, passed in the varargs segment.
     */
	public static void indyVarArg(MethodVisitor mv, String methodName, Class<?> bootstrapClass, String bootstrapMethodName,
            Class<?> returnType, Type[] stackTypes, Object... extra) {
        Handle h = new Handle(H_INVOKESTATIC, Type.getType(bootstrapClass).getInternalName(),
                bootstrapMethodName, getBootstrapDescriptor(types(List.of(Object[].class))), false);
        mv.visitInvokeDynamicInsn(methodName, Type.getMethodDescriptor(Type.getType(returnType), stackTypes), h, extra);

    }
	
	public static void indy(MethodVisitor mv, String methodName, Class<?> bootstrapClass,
			Class<?> returnType, Type[] stackTypes, Object... extra) {
		indy(mv, methodName, bootstrapClass, "bootstrap", returnType, stackTypes, extra);
	}
	
}
