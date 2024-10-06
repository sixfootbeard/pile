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

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;

import java.lang.invoke.MethodType;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import pile.collection.PersistentVector;
import pile.compiler.ParameterParser.MethodParameter;
import pile.compiler.ParameterParser.ParameterList;
import pile.core.Namespace;

/**
 * Defining a type (deftype):
 * <ol>
 * <li>enterClass
 * <li>{@link #setFieldList(ParameterList)}
 * <li>{@link #setSuperTypeConstructorCall(ParameterList, PersistentVector)}?
 * <li>{@link #defineConstructor(CompilerState)}
 * <li>createSingleMethod*
 * <li>{@link #exitClass(CompilerState)}
 * </ol>
 * 
 * @author john
 *
 */
public class DefTypeClassCompiler extends AbstractClassCompiler {

    // required, but may be empty
    private ParameterList fieldList;

    // optional, may be null;
    private ParameterList superTypeConstructorTypes;
    private PersistentVector superTypeConstructorArgumentSyntax;

    public DefTypeClassCompiler(Namespace ns) {
        super(ns);
    }

    public DefTypeClassCompiler(Namespace ns, String className, String internalName) {
        super(ns, className, internalName);
    }

    public void setFieldList(ParameterList fieldList) {
        this.fieldList = fieldList;
    }

    public void setSuperTypeConstructorCall(ParameterList superTypeConstructorTypes,
            PersistentVector superTypeConstructorArgumentSyntax) {
        this.superTypeConstructorTypes = superTypeConstructorTypes;
        this.superTypeConstructorArgumentSyntax = superTypeConstructorArgumentSyntax;
    }

    public void defineConstructor(CompilerState cs) {
        ensure(cs.getClosureSymbols().isEmpty(),
                () -> "Cannot 'deftype' with closed over variables: " + cs.getClosureSymbols().keySet());
        // Fields
        createFields(cs, fieldList);

        // Constructor
        innerDefineConstructor(cs);
    }

    private void innerDefineConstructor(CompilerState cs) {

        MethodStack stack = cs.getMethodStack();
        MethodVisitor cons = cs.enterMethod("<init>", void.class, ACC_PUBLIC, fieldList);
        try {
            GeneratorAdapter ga = cs.getCurrentGeneratorAdapter();
            cons.visitCode();

            cons.visitVarInsn(ALOAD, 0);
            if (superTypeConstructorTypes == null) {
                // TODO verify object supertype
                cons.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            } else {
                MethodType superConsMethodType = superTypeConstructorTypes.toMethodType(void.class);
                // TODO Check lengths
                for (int i = 0; i < superTypeConstructorTypes.args().size(); ++i) {
                    MethodParameter param = superTypeConstructorTypes.args().get(i);
                    Object syntax = superTypeConstructorArgumentSyntax.get(i);
                    Compiler.compile(cs, syntax);

                    // RETHINK Might allow this
                    Class<?> top = popNoInfinite(stack, syntax, "deftype: Constructor argument is an infinite loop.")
                            .javaClass();
                    ga.cast(getType(top), param.getCompilableType());
                    stack.push(param.type());
                }
                cons.visitMethodInsn(INVOKESPECIAL, getType(superType).getInternalName(), "<init>",
                        superConsMethodType.descriptorString(), false);
            }

            // Assign fields
            int index = 0;
            for (MethodParameter ar : fieldList.args()) {
                // Constructor field
                cons.visitVarInsn(ALOAD, 0);
                ga.loadArg(index);
                ga.putField(Type.getType("L" + cs.getCurrentInternalName() + ";"), ar.name(), ar.getCompilableType());
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
