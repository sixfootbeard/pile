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
import static pile.core.binding.NativeDynamicBinding.*;
import static pile.nativebase.NativeCore.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;

import pile.collection.PersistentList;
import pile.compiler.ClassSlot;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.LoopCompileTarget;
import pile.compiler.LoopEvaluationTarget;
import pile.compiler.LoopTargetType;
import pile.compiler.MethodStack;
import pile.compiler.MethodStack.TypeRecord;
import pile.core.Namespace;
import pile.core.binding.IntrinsicBinding;
import pile.core.exception.PileCompileException;
import pile.core.exception.PileExecutionException;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.TypeTag;

public class RecurForm implements Form {

    private static final String RECUR_NOT_IN_TAIL_POSITION = "recur not in tail position";
    
    private final PersistentList form;
    private final Namespace ns;

    public RecurForm(PersistentList form) {
        this.ns = NAMESPACE.getValue();
        this.form = form;
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {

        return new DeferredCompilation(TypeTag.SEXP, IntrinsicBinding.RECUR, (cs) -> {
        
            if (! cs.isCompiledRecurPosition()) {
                throw new PileCompileException(RECUR_NOT_IN_TAIL_POSITION, LexicalEnvironment.extract(form));
            }

            MethodVisitor mv = cs.getCurrentMethodVisitor();
            LoopCompileTarget target = cs.lastLoopTarget();
            GeneratorAdapter generatorAdapter = cs.getCurrentGeneratorAdapter();
            MethodStack stack = cs.getMethodStack();

            LoopTargetType loopTargetType = target.ltt();

            int expectedSize = target.size();

            if (form.count() != expectedSize + 1) {
                throw new PileCompileException(
                        wrongArityRecurMessage(expectedSize),
                        LexicalEnvironment.extract(form));
            }

            Iterator fit = form.iterator();
            fit.next(); // recur
            
            List<ClassSlot> params = target.params();
            
            List<Class<?>> recurTypes = new ArrayList<>();
            // Evaluate all new values first, pushing them all
            int idx = 0; 
            while (fit.hasNext()) {
                Object valueSyntax = fit.next();
                Compiler.compile(cs, valueSyntax);
                ClassSlot recurSlotType = params.get(idx);
                // RETHINK Allowing this
                TypeRecord topRecord = popNoInfinite(stack, valueSyntax, "Recur value cannnot be an infinite loop");
                Class<?> stackTopCompilable = toCompilableType(topRecord.javaClass());
                
                Class<?> recurType = recurSlotType.type();
                if (! (stackTopCompilable.equals(recurType)) && ! recurType.isAssignableFrom(stackTopCompilable)) {
                    coerce(generatorAdapter, stackTopCompilable, recurType);
                }
                stack.push(recurType);
                
                ++idx;
            }
            
            // push type info up
            target.recurTypes().add(recurTypes);
            
            // Rebind all values in reverse order
            for (int i = params.size() - 1; i >= 0; --i) {
                var localSlot = params.get(i).slot();
//                System.out.println("recur: storing into " + localSlot);
                switch (loopTargetType) {
                    case LOCALS:
                        generatorAdapter.storeLocal(localSlot);
                        break;
                    case METHOD_ARGS:
                        generatorAdapter.storeArg(localSlot);
                        break;
                    default:
                        throw error("Unexpected enum:" + loopTargetType);
                }
            }
            mv.visitJumpInsn(Opcodes.GOTO, target.loopStart());
            stack.pushInfiniteLoop();
        });
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
    
        if (! cs.isEvalRecurPosition()) {
            throw new PileCompileException(RECUR_NOT_IN_TAIL_POSITION, LexicalEnvironment.extract(form));
        }

        LoopEvaluationTarget target = cs.lastLoopEvalTarget();
        target.doRecur().set(true);

        int expectedSize = target.size();

        if (form.count() != expectedSize + 1) {
            throw new PileExecutionException(wrongArityRecurMessage(expectedSize), LexicalEnvironment.extract(form));
        }

        Iterator fit = form.iterator();
        fit.next(); // recur

        List<Object> args = Compiler.evaluateArgs(cs, next(form));
        target.args().addAll(args);

        return null;

    }

    private String wrongArityRecurMessage(int expectedSize) {
        return "Wrong size recur. Expected=" + expectedSize + ", found=" + (form.count() - 1);
    }

}
