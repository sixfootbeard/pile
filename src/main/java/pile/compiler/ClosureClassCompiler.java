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
package pile.compiler;

import static java.lang.invoke.MethodType.*;
import static org.objectweb.asm.Opcodes.*;
import static pile.compiler.Helpers.*;
import static pile.util.CollectionUtils.*;

import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import pile.compiler.CompilerState.ClosureRecord;
import pile.core.Namespace;

/**
 * <ol>
 * <li>enterClass
 * <li>createSingleMethod*
 * <li>{@link #defineConstructor(CompilerState)}
 * <li>{@link #exitClass(CompilerState)}
 * </ol>
 * 
 */
public class ClosureClassCompiler extends AbstractClassCompiler {

    public ClosureClassCompiler(Namespace ns) {
        super(ns);
    }

    public ClosureClassCompiler(Namespace ns, String className, String internalName) {
        super(ns, className, internalName);
    }

    public void defineConstructor(CompilerState cs) {
        ensure(Object.class.equals(superType), "Closure must have object supertype");

        ClassVisitor writer = cs.getCurrentVisitor();

        Map<String, ClosureRecord> closureSymbols = cs.getClosureSymbols();

        List<Class<?>> closureTypes = mapL(closureSymbols.values(), cr -> toCompilableType(cr.type()));
        MethodType methodType = methodType(void.class, closureTypes);

        MethodVisitor cons = writer.visitMethod(ACC_PUBLIC | ACC_SYNTHETIC, "<init>", methodType.descriptorString(),
                null, null);

        cons.visitCode();
        cons.visitVarInsn(ALOAD, 0);
        cons.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        int index = 1;
        for (Entry<String, ClosureRecord> f : closureSymbols.entrySet()) {
            Class<?> type = toCompilableType(f.getValue().type());

            // FIXME the indexes here are probably wrong, use GA methods instead.
            cons.visitVarInsn(ALOAD, 0);
            cons.visitVarInsn(ALOAD, index);
            cons.visitFieldInsn(PUTFIELD, cs.getCurrentInternalName(), f.getValue().memberName(),
                    Type.getDescriptor(type));
            ++index;
        }

        cons.visitInsn(RETURN);
        cons.visitMaxs(0, 0);
        cons.visitEnd();

    }

}
