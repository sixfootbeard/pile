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
package pile.compiler.specialization;

import static java.lang.invoke.MethodType.*;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;
import static pile.core.indy.IndyHelpers.*;
import static pile.nativebase.NativeCore.*;

import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;

import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import pile.collection.PersistentList;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.Helpers;
import pile.compiler.MethodStack;
import pile.compiler.MethodStack.TypeRecord;
import pile.compiler.Scopes.ScopeLookupResult;
import pile.compiler.form.VarScope;
import pile.compiler.typed.Any;
import pile.core.ISeq;
import pile.core.Symbol;
import pile.core.binding.Binding;
import pile.core.binding.NativeDynamicBinding;
import pile.core.indy.ApplyImportLinker;
import pile.core.indy.CallSiteType;
import pile.core.indy.CompilerFlags;
import pile.core.indy.PileMethodLinker;
import pile.core.method.LinkableMethod;
import pile.core.parse.TypeTag;

public class ApplyFormSpecialization implements FormSpecialization {

    public static final Symbol APPLY_SYM = new Symbol("pile.core", "apply");
    
    private final PersistentList form;

    public ApplyFormSpecialization(PersistentList form) {
        super();
        this.form = form;
    }

    @Override
    public boolean specialize(CompilerState cs) {    
        ISeq args = next(next(form));
        DeferredCompilation maybeFn = Compiler.compileDefer(cs, second(form));

        if (maybeFn.formType() == TypeTag.SYMBOL) {
            ScopeLookupResult slr = (ScopeLookupResult) maybeFn.ref();
            if (slr.scope() == VarScope.NAMESPACE) {
                Binding bind = (Binding) slr.val();
                final LinkableMethod method;
                if (bind.getValue() instanceof LinkableMethod lm) {
                    method = lm;
                } else {
                    // method target may not exist yet, or may not be a method (will eventually be
                    // an error but still try to link).
                    method = null;
                }

                MethodStack stack = cs.getMethodStack();
                List<TypeRecord> typeRecords = Compiler.compileArgs(cs, args);
                long anyMask = getAnyMask(typeRecords);
                Type[] parameterTypes = Helpers.getJavaTypeArray(typeRecords);
                Class[] classArray = getJavaClassArray(typeRecords);
                MethodType methodType = methodType(Object.class, classArray);

                CompilerFlags flags = NativeDynamicBinding.COMPLILER_FLAGS.getValue();
                Optional<Class<?>> returnType = Optional.ofNullable(method).flatMap(lm -> lm.getReturnType(CallSiteType.PILE_VARARGS, methodType, anyMask));
                Class<?> javaReturnType = returnType.orElse(Object.class);
                MethodVisitor mv = cs.getCurrentMethodVisitor();
                Handle h = new Handle(H_INVOKESTATIC, Type.getType(ApplyImportLinker.class).getInternalName(),
                        "bootstrap", getBootstrapDescriptor(getType(CompilerFlags.class), getType(Symbol.class), getType(Long.class)), false);
                String methodDescriptor = Type.getMethodDescriptor(Type.getType(javaReturnType), parameterTypes);
                mv.visitInvokeDynamicInsn("apply", methodDescriptor, h, flags.toCondy(), slr.fullSym().toConst().get(), anyMask);
                        
                stack.push(returnType.orElse(Any.class));

                return true;
                
            }
        }

        return false;
    }

}
