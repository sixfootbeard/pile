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
import static pile.util.CollectionUtils.*;

import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;

import pile.collection.PersistentList;
import pile.collection.PersistentVector;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.Constants;
import pile.compiler.DeferredCompilation;
import pile.compiler.ParameterParser;
import pile.compiler.ParameterParser.MethodParameter;
import pile.compiler.ParameterParser.ParameterList;
import pile.compiler.Scopes.ScopeLookupResult;
import pile.core.Namespace;
import pile.core.PileMethod;
import pile.core.Symbol;
import pile.core.binding.Binding;
import pile.core.exception.PileCompileException;
import pile.core.method.GenericMethod;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.ParserConstants;

public class DefImplForm extends AbstractListForm {

    private static final Symbol FN_SYM = new Symbol("pile.core", "fn");

    public DefImplForm(PersistentList form) {
        super(form);
    }

    public DefImplForm(PersistentList form, Namespace ns) {
        super(form, ns);
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {
        throw new PileCompileException("defimpl: compile unsupported", LexicalEnvironment.extract(form));
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
        // (defimpl method-name args+types body)
        Optional<LexicalEnvironment> env = LexicalEnvironment.extract(form);
        
        ImplRecord parse = parse();
        ScopeLookupResult slr = cs.getScope().lookupSymbolScope(parse.methodSym());
        final Object val;
        switch (slr.scope()) {
            case NAMESPACE:
                val = ((Binding)slr.val()).getValue();
                break;
            default:
                throw new PileCompileException("Cannot defimpl a method not at NAMESPACE scope", env);
        }
        if (val instanceof GenericMethod gm) {
            PersistentVector<Object> argsAndTypes = parse.argsAndTypes();
            ParameterParser pp = new ParameterParser(ns, argsAndTypes);
            ParameterList pl = pp.parse();
            MethodType methodType = pl.toMethodType(Object.class);
            List<Class<?>> types = methodType.parameterList();
            PersistentList body = parse.body();
            PersistentList syntheticFnSyntax = body.conj(argsAndTypes).conj(FN_SYM);
            PileMethod compiledFunction = (PileMethod) Compiler.evaluate(cs, syntheticFnSyntax);
            if (pl.isVarArgs()) {
                gm.updateVarArgs(types, compiledFunction);
            } else {
                gm.update(types, compiledFunction);
            }
            
            return null;
        } else {
            throw new PileCompileException("defimpl: Target must be a generic method: " + slr.fullSym(), env);
        }
        
    }
    
    private ImplRecord parse() {
        Symbol methodSym = expectSymbol(form.pop().head());
        PersistentVector pv = expectVector(form.pop().pop().head());
        var body = form.pop().pop().pop();
        return new ImplRecord(methodSym, pv, body);
    }
    
    private record ImplRecord(Symbol methodSym, PersistentVector<Object> argsAndTypes, PersistentList<Object> body) {}

    public static final String DOCUMENTATION = """
            Define an implementation of a generic function.
            
            ;; (defgeneric methodname doc? arg-lists)
            ;; (defimpl methodname typed-arg-list body)
            (defgeneric to-string [from])
            (defimpl to-string [^Integer from] (Integer/toString from))
            (to-string 12)
            ;; "12"
             
            """;

}
