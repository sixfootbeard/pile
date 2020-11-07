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
package pile.core;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import pile.collection.PersistentMap;
import pile.core.binding.Binding;
import pile.core.indy.CallSiteType;
import pile.core.indy.CompilerFlags;
import pile.core.method.AbstractRelinkingCallSite;

public class IndirectVar<T> extends AbstractVar<T> {

    public IndirectVar(Namespace ns, String name) {
        super(ns, name);        
    }

    private final Binding binding() {
        return Namespace.getIn(ns, name);
    }

    @Override
    public PersistentMap meta() {
        return binding().meta();
    }

    @Override
    public T deref(long time, TimeUnit unit) throws Throwable {
        return (T) binding().getValue();
    }

    @Override
    public Metadata withMeta(PersistentMap newMeta) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T deref() {
        return (T) binding().getValue();
    }

    
    @Override
    protected Binding<T> bind() {
        return binding();
    }
    
    @Override
    public Optional<CallSite> staticLink(CallSiteType csType, MethodType staticTypes, long anyMask) {
        return super.staticLink(csType, staticTypes, anyMask)
                    .map(cs -> {
                          return new AbstractRelinkingCallSite(staticTypes) {
                              @Override
                              protected MethodHandle findHandle(Object[] args) throws Throwable {
                                  SwitchPoint sp = binding().getSwitchPoint();
                                  return sp.guardWithTest(cs.dynamicInvoker(), relink);                                  
                              }
                          };
                      });
        
    }
    
    @Override
    public CallSite dynamicLink(CallSiteType csType, MethodType statictypes, long anyMask, CompilerFlags flags) {
        var cs = super.dynamicLink(csType, statictypes, anyMask, flags);
        return new AbstractRelinkingCallSite(statictypes) {            
            @Override
            protected MethodHandle findHandle(Object[] args) throws Throwable {
                SwitchPoint sp = binding().getSwitchPoint();
                return sp.guardWithTest(cs.dynamicInvoker(), relink);   
            }
        };
    }

}
