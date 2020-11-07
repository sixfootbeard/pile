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

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Opcodes;

import pile.collection.PersistentList;
import pile.collection.PersistentVector;
import pile.compiler.ClassCompiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.Helpers;
import pile.compiler.MethodCollector;
import pile.compiler.ParameterParser;
import pile.compiler.CompilerState.AnnotationData;
import pile.compiler.MethodCollector.MethodArity;
import pile.compiler.ParameterParser.ParameterList;
import pile.compiler.annotation.GeneratedMethod;
import pile.compiler.annotation.PileVarArgs;
import pile.core.CoreConstants;
import pile.core.Namespace;
import pile.core.Symbol;
import pile.core.binding.ImmutableBinding;
import pile.core.exception.PileCompileException;
import pile.core.method.GenericMethod;
import pile.core.method.GenericMethod.GenericMethodTargets;
import pile.core.parse.LexicalEnvironment;
import pile.core.runtime.generated_classes.LookupHolder;

@SuppressWarnings("rawtypes")
public class DefGenericForm extends AbstractListForm {

    private static final AtomicInteger formSuffix = new AtomicInteger();

    public DefGenericForm(PersistentList form, Namespace ns) {
        super(form, ns);
    }

    public DefGenericForm(PersistentList form) {
        super(form);
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {
        throw new PileCompileException("defgeneric: compile unsupported", LexicalEnvironment.extract(form));
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
        // (defgeneric method-name doc? args-or-default-impl)
        // args = [arg0 ... argN]
        // default-impl = ([arg0 ... argN] body)
        Symbol nameSym = expectSymbol(nth(form, 1));
        String name = nameSym.getName();

        PersistentList arities;
        String doc = null;
        Object docOrRest = nth(form, 2);

        if (docOrRest instanceof String docStr) {
            doc = docStr;
            arities = form.pop().pop().pop();
        } else {
            arities = form.pop().pop();
        }

        Impls impls = parse(arities);
        List<Impl> ilist = impls.implList();
        
        final GenericMethod method;
        if (ilist.size() == 1 && ilist.get(0).args().count() == 1) {
            method = compileInterfaceMethod(cs, name, ilist.get(0).args());
        } else {
            method = compileMultipleMethods(cs, impls);
        }
        
        ImmutableBinding bind = new ImmutableBinding(ns.getName(), method);
        ns.define(name, bind);

        return VarForm.getIn(ns, name);
    }

    private GenericMethod compileMultipleMethods(CompilerState cs, Impls impls) {
        Set<Integer> arities = new HashSet<>();
        int varArgsArity = -1;

        for (var impl : impls.implList()) {
            ParameterParser pp = new ParameterParser(ns, impl.args());
            ParameterList pl = pp.parse();
            int size = pl.args().size();
            if (pl.isVarArgs()) {
                if (varArgsArity != -1) {
                    throw new PileCompileException("Cannot have multiple varargs forms", LexicalEnvironment.extract(form));
                }
                varArgsArity = size;
            } else {
                arities.add(size);
            }
        }
        GenericMethodTargets gmt = new GenericMethodTargets(arities, varArgsArity);
        return new GenericMethod(gmt);
    }

    private record Impl(PersistentVector args, Optional<PersistentList> defaultBody) {
    }

    private record Impls(List<Impl> implList) {
    }

    private Impls parse(PersistentList rest) {
        ArrayList<Impl> out = new ArrayList<>();
        while (rest.count() > 0) {
            Object head = rest.head();
            if (head instanceof PersistentVector pv) {
                Impl impl = new Impl(pv, Optional.empty());
                out.add(impl);
            } else if (head instanceof PersistentList pl) {
                throw new UnsupportedOperationException("generic method: default impls");
            } else {
                throw new PileCompileException("Unexpected generic method form", LexicalEnvironment.extract(head, form));
            }
            rest = rest.pop();
        }
        return new Impls(out);
    }

    private GenericMethod compileInterfaceMethod(CompilerState cs, String name, PersistentVector pv)
            throws IllegalAccessException, InstantiationException {
        String internal = CoreConstants.GEN_PACKAGE + "/" + "GenericMethod$" + formSuffix.getAndIncrement();

        cs.enterInterface(internal);
        try {
            ParameterParser parser = new ParameterParser(ns, pv);
            Class<?> returnType = Helpers.getTypeHint(pv, ns).orElse(ANY_CLASS);
            ParameterList parseRecord = parser.parse();// .popFirst();
            // no body
            List<AnnotationData> adata = new ArrayList<>();
            adata.add(new AnnotationData(GeneratedMethod.class, Map.of()));
            if (parseRecord.isVarArgs()) {
                adata.add(new AnnotationData(PileVarArgs.class, Map.of()));
            }
            cs.enterMethod(name, returnType, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, parseRecord.popFirst(), adata);
            cs.leaveMethod();

            byte[] classArray = cs.compileClass();
            ClassCompiler.printDebug(classArray);
            Class<?> clazz = LookupHolder.LOOKUP.defineClass(classArray);
            MethodCollector coll = new MethodCollector(clazz, LookupHolder.LOOKUP);
            Map<String, MethodArity> methodMap = coll.collectPileMethods();

            ensure(methodMap.size() == 1, "Shouldn't have more than one method");
            MethodArity methodArity = methodMap.get(name);
            Map<Integer, MethodHandle> arityHandles = methodArity.arityHandles();
            if (arityHandles.size() == 1) {
                Entry<Integer, MethodHandle> entry = arityHandles.entrySet().iterator().next();
                return new GenericMethod(clazz, name, entry.getValue());
            } else {
                ensure(methodArity.varArgsMethod() != null, "Should have a varargs if no normal methods are set");
                throw new UnsupportedOperationException("Generic method: varargs");
            }
        } finally {
            cs.leaveInterface();
        }

    }

}
