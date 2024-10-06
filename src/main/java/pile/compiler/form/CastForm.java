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

import pile.collection.PersistentList;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.MethodStack;
import pile.compiler.MethodStack.InfiniteRecord;
import pile.compiler.MethodStack.TypeRecord;
import pile.core.Symbol;
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
        return new DeferredCompilation(TypeTag.SEXP, IntrinsicBinding.CAST, cs -> {
            MethodStack stack = cs.getMethodStack();

            handleLineNumber(cs.getCurrentMethodVisitor(), form);
            Compiler.compile(cs, nth(form, 2));

            switch (stack.popR()) {
                case TypeRecord tr -> {
                    Class<?> topClazz = tr.javaClass();
                    var castClass = getTargetClass();
                    if (!(topClazz.equals(castClass))) {
                        try {
                            cs.getCurrentGeneratorAdapter().cast(getType(topClazz), getType(castClass));
                        } catch (IllegalArgumentException e) {
                            // TODO Better msg
                            throw new PileCompileException("Bad cast", LexicalEnvironment.extract(form), e);
                        }
                    }
                    stack.push(castClass);
                }
                case InfiniteRecord _ -> {
                    stack.pushInfiniteLoop();
                }
            }
        });

    }

    private Class<?> getTargetClass() {
        Object classSym = second(form);
        Symbol sym = expect(classSym, IS_SYMBOL, "Cast class must be a symbol");
        return sym.getAsClass(ns);
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
        // (cast class-sym expr)
        var evaluated = Compiler.evaluate(cs, nth(form, 2));
        var clazz = getTargetClass();
        return clazz.cast(evaluated);
    }
    
    public static String DOCUMENTATION = """
            Casts the resulting expression to the provided type. This may throw a ClassCastException if it fails.  
            
            ;; (cast class-symbol expression)
            (cast java.lang.CharSequence "abcd")
            
            Since the compiler will always prefer the static types when choosing between candidates this can be useful in forcing a particular overloaded method to always be called.
            """;

}
