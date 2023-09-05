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
import static java.lang.invoke.MethodType.*;
import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;
import static pile.core.indy.IndyHelpers.*;
import static pile.nativebase.NativeCore.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.Helpers;
import pile.core.FinalVar;
import pile.core.IndirectVar;
import pile.core.Namespace;
import pile.core.RuntimeRoot;
import pile.core.Symbol;
import pile.core.Var;
import pile.core.binding.Binding;
import pile.core.binding.NativeDynamicBinding;
import pile.core.binding.ReferenceBinding;
import pile.core.binding.Unbound;
import pile.core.exception.ShouldntHappenException;
import pile.core.indy.PileMethodLinker;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.parse.TypeTag;
import pile.util.InvokeDynamicBootstrap;

public class VarForm implements Form {
    
    private static final Logger LOG = LoggerSupplier.getLogger(VarForm.class);

    private final Symbol sym;
    private final Namespace ns;

    public VarForm(Object o) {
        this(o, NativeDynamicBinding.NAMESPACE.getValue());
    }
    
    public VarForm(Object o, Namespace ns) {
        this.sym = expectSymbol(second(o));
        this.ns = ns;
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {
        var resolved = sym.maybeResolve(ns);
        return new DeferredCompilation(TypeTag.SEXP, resolved, cs -> {
            indy(cs.getCurrentMethodVisitor(), "getVar", VarForm.class, Var.class, Helpers.EMPTY_TYPE,
                    resolved.getNamespace(), resolved.getName());
            cs.getMethodStack().push(Var.class);
        });
    }

    @Override
    public Var evaluateForm(CompilerState cs) throws Throwable {
        var resolved = sym.maybeResolve(ns);
        Namespace ns = RuntimeRoot.get(resolved.getNamespace());
        return getIn(ns, sym.getName());
    }

    @InvokeDynamicBootstrap
    public static CallSite bootstrap(Lookup caller, String method, MethodType type, String nsStr, String name)
            throws Exception {
            
        LOG.trace("Linking to var: %s/%s", nsStr, name);
        
        Namespace ns = RuntimeRoot.get(nsStr);
        final Var v = getIn(ns, name);
        var bound = constant(Var.class, v);
        return new ConstantCallSite(bound);
    }

    public static Var getIn(Namespace ns, Symbol sym) {
        return ns.getVar(sym);
    }
    
    public static Var getIn(Namespace ns, String name) {
        return getIn(ns, new Symbol(name));
    }
    
    public static String DOCUMENTATION = """
            Creates a Var by looking up the provided symbol in the current namespace.            
            ;; (var symbol)
            
            ;; ns a.b.c
            (def foo 12)
            (var foo)
            ;; #'a.b.c/foo
            """;

}
