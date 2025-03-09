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

import static java.lang.invoke.MethodHandles.*;
import static pile.compiler.Helpers.*;
import static pile.nativebase.NativeCore.*;
import static pile.util.CollectionUtils.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.SwitchPoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;

import org.objectweb.asm.Opcodes;

import pile.collection.PersistentHashMap;
import pile.collection.PersistentList;
import pile.collection.PersistentVector;
import pile.compiler.AbstractClassCompiler;
import pile.compiler.CompilerState;
import pile.compiler.CompilerState.AnnotationData;
import pile.compiler.DeferredCompilation;
import pile.compiler.Helpers;
import pile.compiler.MethodCollector;
import pile.compiler.MethodCollector.MethodArity;
import pile.compiler.ParameterParser;
import pile.compiler.ParameterParser.ParameterList;
import pile.compiler.SingleMethodCompilerBuilder;
import pile.compiler.annotation.GeneratedMethod;
import pile.compiler.annotation.PileVarArgs;
import pile.core.CoreConstants;
import pile.core.ISeq;
import pile.core.Keyword;
import pile.core.Namespace;
import pile.core.ProtocolMethod;
import pile.core.RuntimeRoot;
import pile.core.RuntimeRoot.ProtocolMethodDescriptor;
import pile.core.binding.BindingType;
import pile.core.binding.ImmutableBinding;
import pile.core.exception.PileCompileException;
import pile.core.method.HiddenCompiledMethod;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.TypeTag;
import pile.core.runtime.generated_classes.LookupHolder;

public class DefProtocolForm extends AbstractListForm {

    public static final Keyword EXTEND_METHOD_KEY = Keyword.of("pile.core", "extend-classes");

    public record ProtocolForm(Class<?> type, List<ProtocolMethodRecord> methods) {
    };

    public record ProtocolMethodRecord(String name, List<ProtocolMethodArity> parse, String doc) {
    };

    private record ProtocolMethodArity(PersistentVector args, Optional<PersistentList> body) {
    };

    public DefProtocolForm(PersistentList form) {
        super(form);
    }

    public DefProtocolForm(PersistentList form, Namespace ns) {
        super(form, ns);
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {
        throw new PileCompileException("defprotocol: compile unsupported", LexicalEnvironment.extract(form));
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
        String className = strSym(second(form));
        ISeq methodSeq = nnext(form);

        String classDescriptor = CoreConstants.GEN_PACKAGE + "/" + className;
        
        final Class<?> clazz;

        // Build interface representing the protocol methods
        List<ProtocolMethodRecord> methods = collectMethodRecords(methodSeq);
        cs.enterInterface(classDescriptor);
        try {
            // Predefine symbols for default method targets
            for (var method : methods) {
                var methodName = method.name();
                ns.define(methodName, null);
            }

            for (var method : methods) {
                compileMethod(cs, method);
            }

            byte[] classArray = cs.compileClass();
            AbstractClassCompiler.printDebug(classArray);
            clazz = LookupHolder.PRIVATE_LOOKUP.defineClass(classArray);
        } finally {
            cs.leaveInterface();
        }
        
        MethodCollector collector = new MethodCollector(clazz, LookupHolder.PRIVATE_LOOKUP);
        Map<String, MethodArity> pileMethods = collector.collectPileMethods();
        Map<String, HiddenCompiledMethod> collectedMethods = mapV(pileMethods, ma -> new HiddenCompiledMethod(clazz, ma));
        
        // Compile the companion object which contains *only* the default methods. 
        boolean hasDefault = methods.stream().flatMap(pmr -> pmr.parse().stream())
                .anyMatch(pma -> pma.body().isPresent());
        Class<?> companionClass = null;
        Map<String, HiddenCompiledMethod> companionMethods = new HashMap<>();
        if (hasDefault) {
            companionClass = buildDefaultDelegate(cs, methods);
            Object base = companionClass.getConstructor().newInstance();
            MethodCollector companionCollector = new MethodCollector(companionClass, LookupHolder.PRIVATE_LOOKUP);
            for (var comp : companionCollector.collectPileMethods().entrySet()) {
                MethodArity arity = comp.getValue();
                
                NavigableMap<Integer, MethodHandle> boundArityHandles = mapKVN(arity.arityHandles(), (count, h) -> entry(count - 1, bind(h, base)));
                MethodHandle boundVarArgs = bind(arity.varArgsMethod(), base);
                MethodHandle boundkwArgs = bind(arity.kwArgsUnrolledMethod(), base);
                int varArgsSize = arity.varArgsSize() == -1 ? -1 : arity.varArgsSize() - 1;
                HiddenCompiledMethod hcm = new HiddenCompiledMethod(null, boundArityHandles,
                        boundVarArgs, varArgsSize, boundkwArgs);
                companionMethods.put(comp.getKey(), hcm);
            }

            for (var entry : companionMethods.entrySet()) {
                collectedMethods.merge(entry.getKey(), entry.getValue(), HiddenCompiledMethod::withDefaults);
            }
        }
        
        // finally, define all the protocol methods
        Map<ProtocolMethodDescriptor, Boolean> descriptorMap = new HashMap<>();
        for (ProtocolMethodRecord method : methods) {
            MethodArity arity = pileMethods.get(method.name());
            Map<ProtocolMethodDescriptor, Boolean> converted = toDescriptor(method.name(), method);
            descriptorMap.putAll(converted);
            
            HiddenCompiledMethod hcm = new HiddenCompiledMethod(null, arity.arityHandles(),
                    arity.varArgsMethod(), arity.varArgsSize(), null);
            ProtocolMethod pm = new ProtocolMethod(clazz, method.name(), hcm);
            ImmutableBinding imm = new ImmutableBinding(ns.getName(), BindingType.VALUE, pm,
                    PersistentHashMap.EMPTY, new SwitchPoint());
            ns.define(method.name(), imm);
        }

        RuntimeRoot.defineProtocol(clazz, descriptorMap, companionMethods);

        ProtocolForm pf = new ProtocolForm(clazz, methods);
        // Define class symbol
        ns.createClassSymbol(clazz.getSimpleName(), clazz);

        return pf;
    }
    
    private static MethodHandle bind(MethodHandle h, Object o) {
        if (h == null) {
            return null;
        }
        return insertArguments(h, 0, o);
    }

    private Class<?> buildDefaultDelegate(CompilerState cs, List<ProtocolMethodRecord> methods)
            throws IllegalAccessException {
        cs.enterClass(CoreConstants.GEN_PACKAGE + "/" + "DELEGATE$" + ns.getSuffix());
        try {
            for (var pmr : methods) {
                for (var method : pmr.parse()) {
                    method.body().ifPresent(body -> {
                        ParameterParser parser = new ParameterParser(ns, method.args());
                        Class<?> returnType = Helpers.getTypeHint(method.args(), ns).orElse(ANY_CLASS);
                        ParameterList parseRecord = parser.parse();
                        // default method body
                        SingleMethodCompilerBuilder builder = new SingleMethodCompilerBuilder(cs);
                        builder.withMethodName(pmr.name()).withReturnType(returnType).withParseRecord(parseRecord)
                               .withThisArgument(false).withAnnotation(GeneratedMethod.class).withBody(body)
                               .build();
                    });
                }
            }
            
            // Constructor
            AbstractClassCompiler.defineConstructor(cs, ParameterList.empty());
            
            byte[] classArray = cs.compileClass();
            AbstractClassCompiler.printDebug(classArray);
            return LookupHolder.PRIVATE_LOOKUP.defineClass(classArray);
        } finally {
            cs.leaveClass();
        }
    }

    private Map<ProtocolMethodDescriptor, Boolean> toDescriptor(String name, ProtocolMethodRecord method) {
        
        Map<ProtocolMethodDescriptor, Boolean>out = new HashMap<>();
        for (var pma : method.parse()) {
            ParameterParser parser = new ParameterParser(ns, pma.args());
            ParameterList pl = parser.parse();
            int arity = pl.args().size();
            boolean isDefault = pma.body().isPresent();
            boolean isVarArgs = pl.isVarArgs();
            
            ProtocolMethodDescriptor pmd = new ProtocolMethodDescriptor(name, arity, isVarArgs);
            out.put(pmd, isDefault);
        }
        return out;
    }
    

    private List<ProtocolMethodRecord> collectMethodRecords(ISeq methodSeq) {
         List<ProtocolMethodRecord> methods = new ArrayList<>();
    
        for (Object methodRaw : ISeq.iter(methodSeq)) {
            // (name doc? [args] body?)
            // (name doc? ([args] body?))
            PersistentList methodList = expectList(methodRaw);

            String name = strSym(first(methodList));

            List<ProtocolMethodArity> methodDefs = new ArrayList<>();

            String doc = null;
            Object second = second(methodList);
            var tag = getTag(second);
            if (tag == TypeTag.STRING) {
                doc = strSym(second);
                
                // reset
                methodList = methodList.pop();
                second = second(methodList);
                tag = getTag(second);
            }
            
            
            if (tag == TypeTag.SEXP) {
                // (name ([args] body?))
                var argsAndBodies = methodList.pop();
                for (var bodyForm : argsAndBodies) {
                    var bodyList = expectList(bodyForm);
                    var args = expectVector(bodyList.head());
                    var maybeBody = Optional.ofNullable(bodyList.pop());
                    methodDefs.add(new ProtocolMethodArity(args, maybeBody));
                }
            } else if (tag == TypeTag.VEC) {
                // (name [args] body?)
                PersistentList body = methodList.pop().pop();
                Optional<PersistentList> bodyOpt = body.seq() == null ? Optional.empty() : Optional.ofNullable(body);
                methodDefs.add(new ProtocolMethodArity(expectVector(second), bodyOpt));
            } else {
                throw new PileCompileException("Unexpected type tag: "+ tag, LexicalEnvironment.extract(second, methodSeq));
            }

            methods.add(new ProtocolMethodRecord(name, methodDefs, doc));
        }
        
        return methods;
    }

    private void compileMethod(CompilerState cs, ProtocolMethodRecord pmr) {

        for (var arity : pmr.parse()) {
            ParameterParser parser = new ParameterParser(ns, arity.args());
            Class<?> returnType = Helpers.getTypeHint(arity.args(), ns).orElse(ANY_CLASS);
            ParameterList parseRecord = parser.parse();//.popFirst();
            if (arity.body().isPresent()) {
                // default method body
                SingleMethodCompilerBuilder builder = new SingleMethodCompilerBuilder(cs);
                builder.withMethodName(pmr.name()).withReturnType(returnType).withParseRecord(parseRecord)
                        .withThisArgument(true).withAnnotation(GeneratedMethod.class).withBody(arity.body().get())
                        .build();
            } else {
                // no body
                List<AnnotationData> adata = new ArrayList<>();
                adata.add(new AnnotationData(GeneratedMethod.class, Map.of()));
                if (parseRecord.isVarArgs()) {
                    adata.add(new AnnotationData(PileVarArgs.class, Map.of()));
                }
                cs.enterMethod(pmr.name(), returnType, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                        parseRecord.popFirst(), adata);
                cs.leaveMethod();
            }

        }
    }

}
