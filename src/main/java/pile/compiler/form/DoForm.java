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
import static pile.nativebase.NativeCore.*;

import java.util.function.BiConsumer;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import pile.collection.PersistentList;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.MacroEvaluated;
import pile.compiler.MethodStack;
import pile.compiler.MethodStack.InfiniteRecord;
import pile.compiler.MethodStack.TypeRecord;
import pile.core.ISeq;
import pile.core.Keyword;
import pile.core.binding.IntrinsicBinding;
import pile.core.exception.PileCompileException;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.TypeTag;

public class DoForm implements Form {

	private final ISeq form;

	public DoForm(PersistentList form) {
        this.form = form.seq();
    }

	public DoForm(ISeq form) {
		this.form = form;
	}

	@Override
	public DeferredCompilation compileForm(CompilerState compilerState) {
		return defer(Compiler::compile);		
	}
	
	@Override
	public DeferredCompilation macroCompileForm(CompilerState compilerState, Keyword context) {
	    return defer((cs, obj) -> Compiler.macroCompile(cs, obj, context));	    
	}
	
	@Override
	public Object evaluateForm(CompilerState cs) throws Throwable {
	    return evaluate(Compiler::evaluate, cs);        
	}
	
	@Override
    public MacroEvaluated macroEvaluateForm(CompilerState state, Keyword context) throws Throwable {
	    return evaluate((cs, f) -> Compiler.macroEval(cs, f, context), state);
    }

    @SuppressWarnings("rawtypes")
    private DeferredCompilation defer(BiConsumer<CompilerState, Object> comp) {
       return new DeferredCompilation(TypeTag.SEXP, IntrinsicBinding.DO, (cs) -> {  
           MethodVisitor method = cs.getCurrentMethodVisitor();
           MethodStack stack = cs.getMethodStack();

           ISeq doBody = form.next();
           int count = count(doBody);
           if (count == 0) {
               return;
           }

           boolean prevTailValue = cs.isCompiledRecurPosition();
           cs.setCompileRecurPosition(false);

           // TODO OBO?
           for (int i = 0; i < count - 1; ++i) {
               var childElement = first(doBody);
               handleLineNumber(method, childElement);
               comp.accept(cs, childElement);
               doBody = doBody.next();
               switch (stack.popR()) {
                   case TypeRecord tr -> {
                       Type topType = Type.getType(tr.javaClass());
                       popStack(method, topType);
                   }
                   case InfiniteRecord _ -> {
                       stack.pushInfiniteLoop();
                   }
               }
           }
           cs.setCompileRecurPosition(prevTailValue);
           var childElement = first(doBody);
           handleLineNumber(method, childElement);
           comp.accept(cs, childElement);
        });
    }

    public static void popStack(MethodVisitor method, Type topType) {    	
    	if (topType.getSize() == 2) {
    		method.visitInsn(Opcodes.POP2);
    	} else if (topType.getSize() == 1) {
    		method.visitInsn(Opcodes.POP);
    	} else if (topType.getSize() == 0) {
            // void method with no return.
            // TODO Kinda feels like a reified void return on the stack is going to come
            // back to bite us eventually...
    	} else {
    		throw error("Invalid size of type on stack: " + topType);
    	}
    }

    interface EvalThrow<T> {
        T eval(CompilerState cs, Object f) throws Throwable;
    }

    @SuppressWarnings("rawtypes")
    private <T> T evaluate(EvalThrow<T> ev, CompilerState cs) throws Throwable {
        ISeq doBody = form.next();
        int count = count(doBody);
        boolean prevTailValue = cs.isEvalRecurPosition();
        cs.setEvalRecurPosition(false);
        
        T last = null;
        
        for (int i = 0; i < count; ++i) {
            if (i == count - 1) {
                cs.setEvalRecurPosition(prevTailValue);
            }
            last = ev.eval(cs, first(doBody));
            doBody = doBody.next();
        }
        
        return last;
    }
    
    public static String DOCUMENTATION = """
            Do form evaluates multiple expression while only yielding the last expressions result.
            
            ;; returns 1, but also prints foo
            (do 
                (prn "foo") 
                1)             
            """;

}
