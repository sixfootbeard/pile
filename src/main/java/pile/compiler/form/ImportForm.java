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

import static pile.compiler.Helpers.*;
import static pile.nativebase.NativeCore.*;

import pile.collection.PersistentList;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.core.ISeq;
import pile.core.exception.PileCompileException;
import pile.core.parse.LexicalEnvironment;

public class ImportForm extends AbstractListForm {

    public ImportForm(PersistentList form) {
        super(form);
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {
        throw notYetImplemented("import compile");
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
        for (var part : next(form)) {
            var type = type(part);
            switch (type) {
                case SYMBOL: {
                    var clazz = loadClass(strSym(part));
                    ns.createClassSymbol(clazz.getSimpleName(), clazz);        
                    break;
                }
                case SEXP: {
                    var base = strSym(first(part));
                    ISeq<Object> suffixes = next(part);
                    for (var suffix : suffixes) {
                        var sym = base + "." + strSym(suffix);
                        var clazz = loadClass(sym);
                        ns.createClassSymbol(clazz.getSimpleName(), clazz);        
                    }
                    break;
                }
                default:
                    var lex = LexicalEnvironment.extract(part, form);
                    throw new PileCompileException("Invalid import type '" + type + "', expected SYMBOL or SEXP", lex);
                
            }
        }
        
        return null;
    }

}
