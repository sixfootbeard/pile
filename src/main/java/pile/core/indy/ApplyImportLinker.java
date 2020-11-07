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
import pile.core.ISeq;
import pile.core.IndirectVar;
import pile.core.Namespace;
import pile.core.RuntimeRoot;
import pile.core.Symbol;
import pile.core.Var;
import pile.core.binding.Binding;
import pile.core.exception.PileCompileException;
import pile.core.exception.PileException;
import pile.core.method.AbstractRelinkingCallSite;
import pile.core.method.LinkableMethod;
import pile.nativebase.NativeCore;
import pile.util.InvokeDynamicBootstrap;

public class ApplyImportLinker {

    @InvokeDynamicBootstrap
    public static CallSite bootstrap(Lookup caller, String method, MethodType type, CompilerFlags flags, Symbol sym, Long anyMask) throws Exception {

        String ns = sym.getNamespace();
        String name = sym.getName();
        
        Namespace namespace = RuntimeRoot.get(ns);
        
        Binding bind = Namespace.getIn(namespace, name);
        
        if (bind.getValue() instanceof LinkableMethod lm) {
            Optional<CallSite> staticTarget = lm.staticLink(CallSiteType.PILE_VARARGS, type, anyMask);

            if (PileMethodLinker.isFinal(bind) && staticTarget.isPresent()) {
                return staticTarget.get();
            }
        }

        return new AbstractRelinkingCallSite(type) {                
            @Override
            protected MethodHandle findHandle(Object[] args) throws Throwable {
                Namespace namespace = RuntimeRoot.get(ns);
                Binding bind = Namespace.getIn(namespace, name);
                SwitchPoint sp = bind.getSwitchPoint();

                final MethodHandle h;
                if (bind.getValue() instanceof LinkableMethod lm) {
                    h = lm.dynamicLink(CallSiteType.PILE_VARARGS, type, anyMask, flags).dynamicInvoker();
                } else {
                    h = getExceptionHandle(type, PileCompileException.class, PileCompileException::new, "Apply target must be a function: " + ns + "/" + name);
                }

                var guarded = sp.guardWithTest(h, relink);
                return guarded;
            }
        };       
    }
}
