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

import static java.util.Objects.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import pile.collection.PersistentMap;
import pile.core.binding.Binding;
import pile.core.binding.BindingType;
import pile.core.binding.ImmutableBinding;
import pile.core.binding.ThreadLocalBinding;
import pile.core.indy.CallSiteType;
import pile.core.indy.CompilerFlags;
import pile.core.method.LinkableMethod;
import pile.nativebase.method.PileInvocationException;

public abstract class AbstractVar<T> implements Var<T> {

    protected final Namespace ns;
    protected final String name;

    public AbstractVar(Namespace ns, String name) {
        super();
        this.ns = requireNonNull(ns, "namespace cannot be null");
        this.name = requireNonNull(name, "name cannot be null");
    }
    
    @Override
    public Namespace getNamespace() {
        return ns;
    }
    
    @Override
    public String getName() {
        return name;
    }

    protected abstract Binding<T> bind();

    protected PileMethod method() {
        T value = bind().getValue();
        if (value instanceof PileMethod meth) {
            return meth;
        } else {
            throw new IllegalArgumentException("Var " + toString() + " does not refer to a callable function.");
        }
    }
    
    @Override
    public void set(T newRef) {
        Binding bind = bind();
        BindingType type = Binding.getType(bind);
        switch (type) {
            case VALUE -> ns.define(name, new ImmutableBinding(ns.getName(), newRef));
            case DYNAMIC -> ((ThreadLocalBinding) bind).set(newRef);
            default -> throw new PileInvocationException("Cannot set value of binding with type:" + type);
        }        
    }

    @Override
    public void update(PCall fn) throws Throwable {
        Binding bind = bind();
        BindingType type = Binding.getType(bind);
        switch (type) {
//            case VALUE -> ns.define(name, new ImmutableBinding(nsStr, newRef));
            case DYNAMIC -> ((ThreadLocalBinding) bind).update(fn);
            default -> throw new PileInvocationException("Cannot update value of binding with type:" + type);
        }  
    }
    
    
    @Override
    public String toString() {
        return String.format("#'%s/%s", ns.getName(), name);
    }

    public Object invoke(Object... args) throws Throwable {
        return method().invoke(args);
    }

    public Object applyInvoke(Object... args) throws Throwable {
        return method().applyInvoke(args);
    }

    public Optional<Class<?>> getReturnType() {
        return method().getReturnType();
    }

    public Optional<Class<?>> getReturnType(CallSiteType csType, MethodType staticTypes, long anyMask) {
        return method().getReturnType(csType, staticTypes, anyMask);
    }

    public Optional<CallSite> staticLink(CallSiteType csType, MethodType staticTypes, long anyMask) {
        return method().staticLink(csType, staticTypes, anyMask);
    }
    
    public CallSite dynamicLink(CallSiteType csType, MethodType statictypes, long anyMask, CompilerFlags flags) {
        return method().dynamicLink(csType, statictypes, anyMask, flags);
    }

    public boolean acceptsArity(int arity) {
        return method().acceptsArity(arity);
    }
    
    @Override
    public LinkableMethod andThen(LinkableMethod nextFn) {
        return method().andThen(nextFn);
    }
    
}
