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
import static pile.util.CollectionUtils.*;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.objectweb.asm.Type;

import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.collection.PersistentVector;
import pile.compiler.CompilerState;
import pile.compiler.DefTypeClassCompiler;
import pile.compiler.DeferredCompilation;
import pile.compiler.Helpers;
import pile.compiler.MethodDefiner;
import pile.compiler.MethodDefiner.MethodRecord;
import pile.compiler.ParameterParser;
import pile.compiler.ParameterParser.MethodParameter;
import pile.compiler.ParameterParser.ParameterList;
import pile.compiler.Scopes.ScopeLookupResult;
import pile.compiler.annotation.GeneratedMethod;
import pile.compiler.annotation.PileVarArgs;
import pile.compiler.typed.StaticTypeLookup;
import pile.compiler.typed.TypedHelpers;
import pile.core.CoreConstants;
import pile.core.ISeq;
import pile.core.Keyword;
import pile.core.Symbol;
import pile.core.binding.Binding;
import pile.core.exception.PileCompileException;
import pile.core.exception.PileSyntaxErrorException;
import pile.core.method.GenericMethod;
import pile.core.parse.LexicalEnvironment;

public class DefTypeForm extends AbstractListForm {

    private static final Keyword SPECIALIZATION_KW = Keyword.of("specialize");
    private static final List<Class<?>> METHOD_ANNOTATIONS = List.of(GeneratedMethod.class);
    private static final List<Class<?>> VARARGS_METHOD_ANNOTATIONS = List.of(GeneratedMethod.class, PileVarArgs.class);

    private static final List<Class<?>> EMPTY_LIST = Collections.emptyList();
    private static final Type ISEQ_TYPE = getType(ISeq.class);

    public DefTypeForm(PersistentList form) {
        super(form);
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {
        throw new PileCompileException("deftype: compile unsupported", LexicalEnvironment.extract(form));
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
        // name args (interface methods*)*
        String typeName = strSym(second(form));
        final List<Class<?>> interfaces = new ArrayList<>();
        List<MethodRecord> methods = new ArrayList<>();
        SuperArgs superArgs = null;

        // Collect interfaces to pre-define for the class we're going to create
        Iterator<Object> it = ISeq.iter(form.pop().pop().pop().seq()).iterator();
        while (it.hasNext()) {
            Object o = it.next();
            if (o instanceof Symbol sym) {
                var clazz = sym.getAsClass(ns);
                if (clazz.isInterface()) {
                    interfaces.add(clazz);
                } else {
                    Object maybeArgs = it.next();
                    PersistentVector vectorArgs = expect(maybeArgs, Helpers.IS_VECTOR, "Supertype must be followed by a vector of arguments");
                    superArgs = new SuperArgs(clazz, vectorArgs);
                }
            } else if (o instanceof PersistentList pl) {
                methods.addAll(MethodDefiner.parseNamed(ns, pl));
            } else if (SPECIALIZATION_KW.equals(o)) {
                List<SpecializedMethod> specs = gatherSpecs(cs, it);
                for (SpecializedMethod spec : specs) {
                    GenericMethod gm = spec.gm();
                    var clazz = gm.getIfaceClass();
                    if (clazz != null) {
                        interfaces.add(clazz);
                        ParameterParser pp = new ParameterParser(ns, spec.args());
                        ParameterList pl = pp.parse();
                        methods.add(new MethodRecord(gm.getMethodName(), pl, spec.body()));
                    }
                }
            } else {
                var lex = LexicalEnvironment.extract(o, form);
                throw new PileCompileException("deftype forms must be either symbols or lists", lex);
            }
        }

        // FIXME Should we use the name of the type the user wanted?
        ParameterParser consArgs = new ParameterParser(ns, ISeq.iter(seq(nth(form, 2))));
        ParameterList parsedCons = consArgs.parse();
        var comp = new DefTypeClassCompiler(ns, typeName, CoreConstants.GEN_PACKAGE);
        Class superType = superArgs == null ? Object.class : superArgs.superType();
        try (var exit = comp.enterClass(cs, superType, interfaces)) {
            comp.setFieldList(parsedCons);
            
            if (superArgs != null) {
                ParameterParser pp = new ParameterParser(ns, superArgs.args());
                ParameterList parsed = pp.parse();
                StaticTypeLookup<Constructor> stl = new StaticTypeLookup<>(TypedHelpers::ofConstructor);
                List<Class<?>> staticTypes = mapL(parsed.args(), MethodParameter::type);
                
                // TODO LExical env
                Constructor targetCons = stl.findSingularMatchingTarget(staticTypes,
                        TypedHelpers.findConstructors(superType))
                        .orElseThrow(() -> new PileSyntaxErrorException("Could not find unambiguous constructor to call"));
                ParameterList consTypes = ParameterParser.from(targetCons);
                
                comp.setSuperTypeConstructorCall(consTypes, superArgs.args());
            }
            comp.defineConstructor(cs);

            // Define all the methods
            MethodDefiner definer = new MethodDefiner();
            definer.defineMethods(cs, comp, interfaces, methods);

            
            comp.exitClass(cs);
            Class<?> clazz = comp.getCompiledClass();
            ns.createClassSymbol(clazz.getSimpleName(), clazz);
        }
        return null;
    }

    private List<SpecializedMethod> gatherSpecs(CompilerState cs, Iterator<Object> it) {
        // (deftype .... :specialize |(method-to-specialize [arg] ... )
        List<SpecializedMethod> out = new ArrayList<>();
        while (it.hasNext()) {
            Object raw = it.next();
            PersistentList list = expectList(raw);
            ScopeLookupResult slr = cs.getScope().lookupSymbolScope(expectSymbol(first(list)));
            Object symVal = ((Binding)slr.val()).getValue();
            if (symVal instanceof GenericMethod gm) {
                PersistentVector args = expectVector(list.pop().head());
                PersistentList body = list.pop().pop();
                SpecializedMethod meth = new SpecializedMethod(gm, args, body);
                out.add(meth);
            } else {
                throw new PileCompileException("deftype specialization must be a generic method", LexicalEnvironment.extract(raw, form));
            }
            
        }
        return out;
    }

    record SuperArgs(Class superType, PersistentVector args) {}

    public record ParsedForm(SuperArgs superArgs, PersistentMap<Class<?>, PersistentVector<?>> mappedTypes,
            Class<?> currentClass, PersistentVector<?> currentForms) {
    }
    
    private record SpecializedMethod(GenericMethod gm, PersistentVector args, PersistentList body) {}
    
    public static String DOCUMENTATION = """
            Creates a new named type.
            
            This type may define fields, an optional supertype, and any number of interfaces and methods.
            
            ;; (deftype TypeName [type constructor arguments]
            ;;     Supertype [supertype constructor arguments]
            ;;     Interface0
            ;;     (ifacefn [this] ...)
            ;;     Interface1
            ;;     (otherfn [this a b] ...))
            
            ;; Define an empty iterator implementation with no fields.
            (deftype EmptyIter [] 
               java.util.Iterator 
               (hasNext [this] false) 
               (next [this] (throw (java.util.NoSuchElementException.)))) 
            """;

}
