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

import static java.util.Objects.*;
import static pile.compiler.Helpers.*;
import static pile.core.binding.NativeDynamicBinding.*;

import java.util.Map;

import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.core.ISeq;
import pile.core.Keyword;
import pile.core.Namespace;
import pile.core.PCall;
import pile.core.RuntimeRoot;
import pile.core.Symbol;
import pile.core.binding.Binding;
import pile.core.exception.PileCompileException;

/**
 * Set a namespace via {@link RuntimeRoot#defineOrGet(String)}, which may create
 * and read in default code from the classpath.
 *
 */
public class NamespaceForm implements Form {

    /**
     * A map from keywords to the symbols resolving to functions which handle the
     * additional syntax.
     */
    private static final Map<Keyword, Symbol> HANDLER_MAP;
    private static final Symbol QUOTE_SYM = new Symbol("pile.core", "quote");
    static {
        PersistentMap<Keyword, Symbol> local = PersistentMap.empty();
        local = local.assoc(Keyword.of("require"), new Symbol("pile.core", "require"));
        local = local.assoc(Keyword.of("refer"), new Symbol("pile.core", "refer"));
        local = local.assoc(Keyword.of("import"), new Symbol("pile.core", "import"));
        HANDLER_MAP = local;
    }

    private final PersistentList form;

    public NamespaceForm(PersistentList form) {
        this.form = form;
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {
        throw notYetImplemented("namespace: compiled form");
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
        // (ns a.b.c (:require ...) (:refer ...) (:import ...))
        ISeq seq = form.seq();
        Object nsName = seq.next().first();

        String nsStr = strSym(nsName);
        NAMESPACE.set(RuntimeRoot.defineOrGet(nsStr));

        // Extras
        ISeq extras = seq.next().next();
        for (Object extra : ISeq.iter(extras)) {
            PersistentList extraList = expectList(extra);
            Keyword kw = expectKeyword(extraList.head());
            Symbol fnSym = HANDLER_MAP.get(kw);
            // TODO Error
            requireNonNull(fnSym, () -> "Unknown namespace keyword: " + kw);
//            Binding binding = Namespace.getIn(RuntimeRoot.get(fnSym.getNamespace()), fnSym.getName());
//            PCall local = (PCall) binding.getValue();
            PersistentList toEval = extraList.pop().conj(fnSym);
            Compiler.evaluate(cs, toEval);            
        }

        return null;
    }

    public static String DOCUMENTATION = """
            Sets the current namespace.
            
            ;; (ns full-namespace-symbol)
            (ns a.b.c)
            """;

}
