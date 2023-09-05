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

import pile.collection.PersistentList;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.core.Namespace;
import pile.core.Symbol;
import pile.core.parse.TypeTag;;

/**
 * Provides a lock form which can synchronize on a particular object before
 * running the expression.<br>
 * <br>
 * 
 * The compile form uses monitor-enter/monitor-exit intrinsics. The evaluate
 * form uses the synchronize directly.
 */
public class LockForm extends AbstractListForm {

    private static final String PILE_CORE_NS = "pile.core";
    
    private static final Symbol DO = new Symbol(PILE_CORE_NS, "do");
    private static final Symbol LET = new Symbol(PILE_CORE_NS, "let*");
    private static final Symbol TRY = new Symbol(PILE_CORE_NS, "try");
    private static final Symbol FINALLY = new Symbol(PILE_CORE_NS, "finally");
    private static final Symbol MONITOR_ENTER = new Symbol(PILE_CORE_NS, "monitor-enter");
    private static final Symbol MONITOR_EXIT = new Symbol(PILE_CORE_NS, "monitor-exit");

    public LockForm(PersistentList form, Namespace ns) {
        super(form, ns);
    }

    public LockForm(PersistentList form) {
        super(form);
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {
        return new DeferredCompilation(TypeTag.SEXP, null, cs -> {
            var base = second(form);
            var rest = form.pop().pop();
            Symbol tmp = gensym();
            
            // (locking x (exec))
            // =>
            // (let [tmp x]
            //   (monitor-enter tmp)
            //   (try (exec)
            //      (finally (monitor-exit tmp))))
            //   
            var l = list(LET, vector(tmp, base),
                        list(MONITOR_ENTER, tmp),
                        list(TRY, rest.conj(DO),
                                list(FINALLY, list(MONITOR_EXIT, tmp))));
            
            Compiler.compile(cs, l);
        });
        
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
        Object base = Compiler.evaluate(cs, second(form));
        synchronized (base) {
            return Compiler.evaluate(cs, form.pop().pop().head());
        }
    }
    
    public static String DOCUMENTATION = """
            Synchronizes on an object and evaluates the provided expression.
            
            ;; (locking object expr)
            (defn lock-run [obj f] (locking obj (f))
            """;

}
