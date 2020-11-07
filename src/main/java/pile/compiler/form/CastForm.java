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

import org.objectweb.asm.Opcodes;

import pile.collection.PersistentList;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.MethodStack;
import pile.core.Namespace;
import pile.core.binding.IntrinsicBinding;
import pile.core.exception.PileCompileException;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.TypeTag;

public class CastForm extends AbstractListForm {

    public CastForm(Object form) {
        super((PersistentList) form);
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {
        // (cast class-sym expr)
        DeferredCompilation defer = Compiler.compileDefer(compilerState, nth(form, 2));
        return new DeferredCompilation(TypeTag.SEXP, IntrinsicBinding.CAST, cs -> {
            handleLineNumber(cs.getCurrentMethodVisitor(), form);
            defer.compile().accept(cs);
            MethodStack stack = cs.getMethodStack();
            Class<?> topClazz = stack.pop();
            var castClass = expectSymbol(second(form)).getAsClass(ns);
            if (!(topClazz.equals(castClass))) {
                try { 
                    cs.getCurrentGeneratorAdapter().cast(getType(topClazz), getType(castClass));
                } catch (IllegalArgumentException e) {
                    throw new PileCompileException("Bad cast", LexicalEnvironment.extract(form), e);
                }
            }
            stack.push(castClass);
            
        });

    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
        // (cast class-sym expr)
        var evaluated = Compiler.evaluate(cs, nth(form, 2));
        var clazz = expectSymbol(second(form)).getAsClass(ns);
        return clazz.cast(evaluated);
    }

}
