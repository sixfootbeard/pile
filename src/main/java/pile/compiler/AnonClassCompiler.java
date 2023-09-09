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

import static java.util.Objects.*;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

import java.util.List;
import java.util.Map;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import pile.compiler.CompilerState.ClosureRecord;
import pile.compiler.ParameterParser.MethodParameter;
import pile.compiler.ParameterParser.ParameterList;
import pile.core.Namespace;

/**
 * Creates a new type with a target superconstuctor and closed over arguments.
 * 
 * <ol>
 * <li>enterClass
 * <li>{@link #setTargetSuperConstructor(ParameterList)}
 * <li>createSingleMethod*
 * <li>{@link #defineConstructor(CompilerState)}
 * <li>{@link #exitClass(CompilerState)}
 * </ol>
 * 
 * @author john
 *
 */
public class AnonClassCompiler extends AbstractClassCompiler {

    private ParameterList constructorArgs;

    public AnonClassCompiler(Namespace ns) {
        super(ns);
    }

    public AnonClassCompiler(Namespace ns, String className, String internalName) {
        super(ns, className, internalName);
    }

    public void setTargetSuperConstructor(ParameterList cons) {
        this.constructorArgs = cons;
    }

    /**
     * Define a constructor with an argument list superTargetConstructorArgs +
     * closureArgs.
     * 
     * @param cs
     */
    public void defineConstructor(CompilerState cs) {
        requireNonNull(constructorArgs, "Must set constructor args");

        Map<String, ClosureRecord> closureSymbols = cs.getClosureSymbols();

        // append (called constructor args) + (closure args)
        List<MethodParameter> closureArgs = toArgRecord(closureSymbols);

        final ParameterList base;
        if (constructorArgs == null) {
            base = ParameterList.empty();
        } else {
            base = constructorArgs;
        }

        ParameterList withClosureArgs = base.append(closureArgs);
        int flags = ACC_PUBLIC;
        if (withClosureArgs.isJavaVarArgs()) {
            flags |= ACC_VARARGS;
        }
        MethodVisitor cons = cs.enterMethod("<init>", void.class, flags, withClosureArgs);
        try {
            GeneratorAdapter ga = cs.getCurrentGeneratorAdapter();
            String internalSupertypeName = getType(superType).getInternalName();

            cons.visitCode();
            cons.visitVarInsn(ALOAD, 0);
            if (constructorArgs == null) {
                cons.visitMethodInsn(INVOKESPECIAL, internalSupertypeName, "<init>", "()V", false);
            } else {
                for (int i = 0; i < base.args().size(); ++i) {
                    // Load by slot index
                    ga.loadArg(i);
                }
                cons.visitMethodInsn(INVOKESPECIAL, internalSupertypeName, "<init>",
                        base.toMethodType(void.class).descriptorString(), false);
            }

            int index = base.args().size();
            for (MethodParameter ar : closureArgs) {
                ClosureRecord cr = closureSymbols.get(ar.name());
                // Constructor field
                cons.visitVarInsn(ALOAD, 0);
                ga.loadArg(index);
                ga.putField(Type.getType("L" + cs.getCurrentInternalName() + ";"), cr.memberName(),
                        ar.getCompilableType());
                ++index;
            }

            cons.visitInsn(RETURN);
            cons.visitMaxs(0, 0);
            cons.visitEnd();
        } finally {
            cs.leaveMethod();
        }
    }

}
