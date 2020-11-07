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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import pile.collection.PersistentArrayVector;
import pile.collection.PersistentList;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.ParameterParser;
import pile.compiler.ParameterParser.ParameterList;
import pile.core.Namespace;
import pile.core.PileMethod;
import pile.core.RuntimeRoot;
import pile.core.RuntimeRoot.ProtocolMethodDescriptor;
import pile.core.RuntimeRoot.ProtocolRecord;
import pile.core.Symbol;
import pile.core.binding.Binding;
import pile.core.exception.PileCompileException;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.ParserConstants;

public class ExtendsForm extends AbstractListForm {

    private static final Logger LOG = LoggerSupplier.getLogger(ExtendsForm.class);

    private static final Symbol FN_SYM = new Symbol("fn*");

    public ExtendsForm(PersistentList form) {
        super(form);
    }

    public ExtendsForm(PersistentList form, Namespace ns) {
        super(form, ns);
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {
        throw new PileCompileException("extends: compile unsupported", LexicalEnvironment.extract(form));
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
        // (extend java.lang.Number
        // Dateable
        // (to-ms [this] (new Date (.longValue this))))

        // {Dateable : {java.lang.Number : {to-ms : [MethodHandle]}}}

        // 1: Base Class
        // 2: Protocol
        // 3+: Methods

        try {
            record NamedBinding(String name, Binding binding) {};

            Iterator<Object> fit = form.iterator();
            fit.next(); // extend*

            Object extendsRaw = fit.next();
            var protocolSym = expectSymbol(fit.next());
            var extendsClass = extendsRaw == null ? null : expectSymbol(extendsRaw).getAsClass(ns);
            var protocolClass = protocolSym.getAsClass(ns);

            ProtocolRecord protoMeta = RuntimeRoot.getProtocolMetadata(protocolClass);
            Map<ProtocolMethodDescriptor, Boolean> pmdMap = new HashMap<>(protoMeta.protoMethodsToDefault());

            List<NamedBinding> bindings = new ArrayList<>();

            Map<String, PileMethod> methodMap = new HashMap<>();

            // Extend
            while (fit.hasNext()) {
                Object methodForm = fit.next();
                PersistentList seq = (PersistentList) methodForm;

                String methodName = strSym(first(seq));
                PersistentList form = seq.pop();

                // [this a b]
                PersistentArrayVector args = (PersistentArrayVector) first(form);
                removeMethod(pmdMap, methodName, args);

                if (extendsClass != null) {
                    // Optimization: Add type hint to 'this' parameter in methods
                    // this
                    Symbol thisForm = (Symbol) first(args);
                    // ^Type this
                    Symbol classSym = new Symbol(extendsClass.getName());
                    Symbol thisType = (Symbol) thisForm
                            .updateMeta(meta -> meta.assoc(ParserConstants.ANNO_TYPE_KEY, classSym));

                    args = args.assoc(0, thisType);
                }
                var fnForm = form.pop().conj(args).conj(FN_SYM);
                MethodForm method = new MethodForm(fnForm);
                var cm = method.evaluateForm(cs);

                methodMap.put(methodName, cm);
                bindings.add(new NamedBinding(methodName, ns.getLocal(methodName)));
            }

            for (var entry : pmdMap.entrySet()) {
                if (!entry.getValue()) {
                    // Actually allowed in java too, just throws an AbstractMethodError at runtime.
                    LOG.warn("Missing override on method: %s", entry.getKey());
                }
            }

            RuntimeRoot.extendProtocol(protocolClass, extendsClass, methodMap);
        } catch (Throwable t) {
            throw new PileCompileException("Couldn't extend protocol", LexicalEnvironment.extract(form), t);
        }

        return null;
    }

    private void removeMethod(Map<ProtocolMethodDescriptor, Boolean> pmdMap, String methodName,
            PersistentArrayVector args) {
        ParameterParser pp = new ParameterParser(ns, args);
        ParameterList pl = pp.parse();
        ProtocolMethodDescriptor pmd = new ProtocolMethodDescriptor(methodName, pl.args().size(), pl.isVarArgs());
        Boolean maybeIsDefault = pmdMap.remove(pmd);
        if (maybeIsDefault == null) {
            throw new PileCompileException("Bad override in extends: " + pmd, LexicalEnvironment.extract(form));
        }
    }
}
