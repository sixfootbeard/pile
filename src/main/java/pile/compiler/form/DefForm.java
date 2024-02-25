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

import java.io.PrintStream;
import java.lang.invoke.SwitchPoint;
import java.util.List;

import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.collection.PersistentVector;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.Helpers;
import pile.core.Keyword;
import pile.core.Metadata;
import pile.core.Namespace;
import pile.core.Symbol;
import pile.core.binding.Binding;
import pile.core.binding.BindingType;
import pile.core.binding.ImmutableBinding;
import pile.core.binding.NativeDynamicBinding;
import pile.core.binding.ThreadLocalBinding;
import pile.core.binding.Unbound;
import pile.core.compiler.aot.AOTHandler;
import pile.core.compiler.aot.AOTHandler.AOTType;
import pile.core.exception.PileCompileException;
import pile.core.exception.PileSyntaxErrorException;
import pile.core.indy.PileMethodLinker;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.method.AbstractCompiledMethod;
import pile.core.parse.LexicalEnvironment;
import pile.nativebase.NativeCore;

@SuppressWarnings("rawtypes")
public class DefForm implements Form {

    private static final Logger LOG = LoggerSupplier.getLogger(DefForm.class);

    private final PersistentList form;

    public DefForm(PersistentList form) {
        this.form = form;
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {
        throw new PileCompileException("Cannot compile a def. Use (let ...) to create local variables.", LexicalEnvironment.extract(form));
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
    
        if (form.count() > 3) {
            String msg = "def form should have at most 3 parts (def symbol value).";
            if (form.count() == 4 && PersistentVector.class.isAssignableFrom(NativeCore.getClass(nth(form, 2)))) {
                msg += " Were you trying to define a function 'defn'?";
            }
            throw new PileSyntaxErrorException(msg, LexicalEnvironment.extract(form));
        }
        
        Symbol sym = expect(nth(form, 1), IS_SYMBOL, "Defined name must be a symbol."); 
        boolean isFinal = PileMethodLinker.isFinal(sym);
        boolean isMacro = PileMethodLinker.isMacro(sym);

        String name = sym.getName();
        
        var ns = NativeDynamicBinding.NAMESPACE.getValue();
        
        Object initializerValue = null;
        boolean hasinitializerValue = (form.count() == 3);
        
        // 
        BindingType type = determineType(sym);

        try (var cursym = NativeDynamicBinding.CURRENT_FN_SYM.with(old -> old.conj(sym))) {
        
            PersistentMap meta = PersistentMap.empty();
            meta = merge(meta, sym.meta());
            
            if (hasinitializerValue) {
                Object valueSyntax = first(nnext(form));

                // Copies the symbol meta to the binding since it's the easiest place to put
                // annotations:
                // (def ^:macro something [a b] ...)
                // FIXME Copies line numbers and things we might not want to override
                if (valueSyntax instanceof Metadata m) {
                    meta = merge(m.meta(), meta);
                }

                // Predefine unbound incase of a circular ref
                ns.define(name, new Unbound(ns.getName(), isFinal, isMacro));

                Object eval = null;
                if (AOTHandler.getAotType() == AOTType.READ) {
                    Class<?> maybeFn = AOTHandler.getAotFunctionClass(ns.getName(), name);
                    if (maybeFn != null) {
                        LOG.trace("AOT loaded %s/%s from %s", ns.getName(), name, maybeFn);
                        eval = MethodForm.collectAndCreateInstance(maybeFn, List.of());
                    } else {
                        LOG.trace("AOT not found for %s/%s", ns.getName(), name);
                    }                
                }
                if (eval == null) {
                    // No read AOT, actually compile.
                    eval = Compiler.evaluate(cs, valueSyntax);
                    if (AOTHandler.getAotType() == AOTType.WRITE && type == BindingType.VALUE) {
                        writeAOT(ns, name, eval);
                    }
                    
                }
                initializerValue = eval; //Compiler.evaluate(cs, valueSyntax);
            }
            meta = meta.assoc(Binding.BINDING_TYPE_KEY, type);
            
            final Binding bind = switch (type) {
                case VALUE -> new ImmutableBinding(ns.getName(), BindingType.VALUE, initializerValue, meta, new SwitchPoint());
                case DYNAMIC -> new ThreadLocalBinding<>(ns.getName(), name, initializerValue, meta, new SwitchPoint()); 
                default -> throw new PileCompileException("Unexpected binding type: " + type, LexicalEnvironment.extract(form));                   
            };
            ns.define(name, bind);

            return VarForm.getIn(ns, name);
        } catch (Throwable e) {
            String msg = String.format("Error while defining: %s / %s", ns.getName(), sym.getName());
            throw new PileCompileException(msg, LexicalEnvironment.extract(form), e);
        }
    }
    
    private static BindingType determineType(Metadata meta) {
        if (isKeywordTrue(meta.meta(), Keyword.of("dynamic"))) {
            return BindingType.DYNAMIC;
        } else if (isKeywordTrue(meta.meta(), Keyword.of("scoped"))) {
            return BindingType.SCOPED;
        } else {
            return BindingType.VALUE;
        } 
    }
    
    private void writeAOT(Namespace ns, String name, Object maybeHcm) throws Exception {    
        if (maybeHcm instanceof AbstractCompiledMethod hcm) {        
            Class<?> base = hcm.getBacking();
            if (base != null) {
                prn("Writing aot map entry: " + ns.getName() + "/" + name + "=" + base);
                AOTHandler.writeAotFunction(ns.getName(), name, base);
            }   
        }        
    }
    
    public static String DOCUMENTATION = """
            Defines a value assigned to a symbol name in the current namespace.
            
            (def symbol value)
            
            It's possible to simply define a symbol without an initial value.
            
            The symbol can be annotated:
            
            (def ^:final symbol value)   ;; This symbol cannot be redefined with (def ...) or (set! ...)
            (def ^:dynamic symbol value) ;; This symbol's value will be thread-local and can be changed with (set! ...) 
            """;
    

}
