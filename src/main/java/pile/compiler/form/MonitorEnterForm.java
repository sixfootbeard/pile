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

import static pile.nativebase.NativeCore.*;

import org.objectweb.asm.commons.GeneratorAdapter;

import pile.collection.PersistentList;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.core.Namespace;
import pile.core.exception.PileCompileException;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.TypeTag;

public class MonitorEnterForm extends AbstractListForm {

    public MonitorEnterForm(PersistentList form, Namespace ns) {
        super(form, ns);
    }

    public MonitorEnterForm(PersistentList form) {
        super(form);
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {
        return new DeferredCompilation(TypeTag.SEXP, null, cs -> {
            GeneratorAdapter ga = cs.getCurrentGeneratorAdapter();
            var sym = second(form);
            Compiler.compile(cs, sym);
            cs.getMethodStack().pop();
            ga.monitorEnter();
            // Instead of an empty stack we leave null:
            Compiler.compile(cs, null);
        });
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
        throw new PileCompileException(
                "monitor-enter: Evaluation not supported. Use the locking intrinsic instead of this directly.",
                LexicalEnvironment.extract(form));
    }

}
