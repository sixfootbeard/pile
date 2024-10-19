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
package pile.core.indy;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;
import static pile.compiler.Helpers.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.util.Optional;

import pile.compiler.Helpers;
import pile.compiler.form.VarForm;
import pile.core.Keyword;
import pile.core.Metadata;
import pile.core.Namespace;
import pile.core.PCall;
import pile.core.RuntimeRoot;
import pile.core.Symbol;
import pile.core.Var;
import pile.core.binding.Binding;
import pile.core.binding.BindingType;
import pile.core.exception.PileCompileException;
import pile.core.exception.PileInternalException;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.method.AbstractRelinkingCallSite;
import pile.core.method.LinkableMethod;
import pile.core.parse.LexicalEnvironment;
import pile.core.runtime.generated_classes.LookupHolder;
import pile.util.InvokeDynamicBootstrap;

/**
 * Links method calls for symbols with unambiguous references to namespace
 * methods:
 * 
 * <pre>
 * Yes: 
 * (pile.core/str "a" "b")
 * No:
 * (defn foo [f] (f "a" "b"))
 * </pre>
 *
 */
public class PileMethodLinker {


    private static final Logger LOG = LoggerSupplier.getLogger(PileMethodLinker.class);

    public static final Keyword MACRO_KEY = Keyword.of("macro");
    public static final Keyword FINAL_KEY = Keyword.of(null, "final");
    public static final Keyword RETURN_TYPE_KEY = Keyword.of("pile.core", "return-type");

    /**
     * Try to generate a {@link MethodHandle} from the current binding value.
     * 
     * @param bindingValue
     * @param type
     * @return
     */
    private static Optional<MethodHandle> tryGenerateMethodHandle(Object bindingValue, MethodType type,
            long anyMask) {
        Optional<MethodHandle> newHandle = Optional.empty();
        if (bindingValue instanceof LinkableMethod deref) {
            newHandle = deref.staticLink(CallSiteType.PLAIN, type, anyMask).map(CallSite::dynamicInvoker);
        }
        return newHandle;
    }

    /**
     * Try to generate a callable handle either a {@link BindingType#VALUE} or
     * {@link BindingType#DYNAMIC} value.
     * 
     * @param namespaceStr Namespace the binding exists in.
     * @param name         Name of the binding in the namespaces
     * @param binding      The current value of the binding.
     * @param callSiteType The expected type the returned method handle should
     *                     match.
     * @param anyMask 
     * @return A {@link MethodHandle} which invokes the value of the implied
     *         binding, or empty if the current binding type is invalid.
     * @throws Exception
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     */
    private static Optional<MethodHandle> tryGenerateCallableHandle(String namespaceStr, String name,
            final Binding binding, MethodType callSiteType, long anyMask)
            throws Exception, NoSuchMethodException, IllegalAccessException {

        final BindingType type = Binding.getType(binding);
        
        return switch (type) {
            // TODO Reference case
            case DYNAMIC, SCOPED ->
                // Dynamically lookup binding & return a PCall variant
                Optional.of(generateCallableDynamicHandle(namespaceStr, name, callSiteType));                
            case VALUE ->
                tryGenerateMethodHandle(binding.getValue(), callSiteType, anyMask);
            default ->
                Optional.empty();
        };
        
    }

    /**
     * Generate a method handle from a {@link BindingType#DYNAMIC} binding at the
     * provided ns/name. The result may need guarded by a {@link SwitchPoint}, if
     * non-final.
     * 
     * @param namespaceStr Namespace of the binding.
     * @param name         Name of the binding in the namespace.
     * @param callSiteType Type the resulting method handle should be.
     * 
     * @return
     * @throws Exception
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     * @implNote We target {@link PCall#invoke(Object...)} which is not optimal.
     */
    public static MethodHandle generateCallableDynamicHandle(String namespaceStr, String name, MethodType callSiteType)
            throws Exception, NoSuchMethodException, IllegalAccessException {
        Lookup caller = LookupHolder.PRIVATE_LOOKUP;
        Binding bind = Namespace.getIn(RuntimeRoot.get(namespaceStr), name);
        
        MethodHandle deref = caller.findVirtual(Binding.class, "getValue", methodType(Object.class));
        MethodHandle bindGet = insertArguments(deref, 0, bind).asType(methodType(PCall.class));
        MethodHandle pcall = caller.findVirtual(PCall.class, "invoke", methodType(Object.class, Object[].class));
        MethodHandle foldArguments = foldArguments(pcall, bindGet);
        MethodHandle collected = foldArguments.asCollector(Object[].class, callSiteType.parameterCount());

        return collected.asType(callSiteType);
    }

    public static boolean isFinal(Metadata b) {
        return (boolean) b.meta().get(PileMethodLinker.FINAL_KEY, false);
    }
    
    public static boolean isMacro(Metadata b) {
        return (boolean) b.meta().get(PileMethodLinker.MACRO_KEY, false);
    }
    
    

    /**
     * 
     * @param caller
     * @param name
     * @param callSiteType
     * @param namespaceStr The namespace of the fn name
     * @param flags        {@link LinkOptions}
     * @return
     * @throws Throwable
     */
    @InvokeDynamicBootstrap
    public static CallSite bootstrap(Lookup caller, String ignored, MethodType callSiteType, Symbol sym,
            CompilerFlags flags, long anyMask, boolean allConstant) throws Throwable {
            
        String namespaceStr = sym.getNamespace();
        String name = sym.getName();
            
        LOG.trace("Linking: %s/%s", namespaceStr, name);
    
        final Namespace ns = RuntimeRoot.get(namespaceStr);
        final Binding binding = Namespace.getIn(ns, name);
        
        if (binding == null) {
            // RETHINK With AOT this may happen.
            throw new PileInternalException("No binding: " + ns +"/" + name);
        }
        
        final Optional<MethodHandle> handle = tryGenerateCallableHandle(namespaceStr, name, binding, callSiteType,
                anyMask);
            
        final boolean isFinal = isFinal(binding);
        final boolean isPure = binding.getValue() instanceof LinkableMethod lm && lm.isPure();
    
        var m = handle.map(h -> h.asType(callSiteType))
                      .flatMap(h -> {
                          CallSite cs = null;
                            if (isFinal) {
                                if (allConstant && isPure) {
                                    cs = new AbstractRelinkingCallSite(callSiteType) {                                        
                                        @Override
                                        protected MethodHandle findHandle(Object[] args) throws Throwable {
                                            LinkableMethod lm = (LinkableMethod) binding.getValue();
                                            Object resultValue = lm.invoke(args);
                                            var h = constant(resultValue.getClass(), resultValue);
                                            return dropArgumentsToMatch(h, 0, callSiteType.parameterList(), 0);
                                        }
                                    };
                                } else {
                                    // found method handle, final
                                    // either not all constant or not pure, so just ccs
                                    cs = new ConstantCallSite(h);
                                }
                            }
                            return Optional.ofNullable(cs);
                        }).orElseGet(() ->
                        // no method handle
                            new ImportRelinkingCallSite(callSiteType, anyMask, ns.getName(), name, allConstant, flags)
                        );
        
        if (DebugHelpers.isDebugEnabled()) {
            Optional<LexicalEnvironment> maybeLex = LexicalEnvironment.extract(sym);
            if (maybeLex.isPresent()) {
                LexicalEnvironment lex = maybeLex.get();
                String msg = String.format("Error calling '%s/%s' [file=%s, line=%d, column=%d]", namespaceStr, name,
                        lex.getSource(), lex.getLineAt(), lex.getCharAtInLine());
                var h = DebugHelpers.catchWithMessage(m.dynamicInvoker(), msg);
                m = new ConstantCallSite(h);       
            }
        }
        
        return m;
    
    }

    @InvokeDynamicBootstrap
    public static CallSite bootstrap(Lookup caller, String method, MethodType type, LinkableMethod callableConstant)
            throws Exception {
        return callableConstant.staticLink(CallSiteType.PLAIN, type, 0L).orElseThrow(
                () -> new RuntimeException("Callsites for callable constants must have static linkable methods"));
    }
}
