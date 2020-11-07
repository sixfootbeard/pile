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
package pile.core.method;

import static java.lang.invoke.MethodType.*;
import static pile.compiler.Helpers.*;
import static pile.util.CollectionUtils.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import pile.core.PileMethod;
import pile.core.indy.CallSiteType;
import pile.core.indy.CompilerFlags;

public class MethodComposition implements PileMethod {

    private static final Optional<CallSite> EMPTY = Optional.empty();
    
    // Order here is reverse of syntax, eg. 
    // (comp a b c) => [c b a] 
    private final List<LinkableMethod> methods;
    
    public MethodComposition(LinkableMethod first, LinkableMethod andThen) {
        this.methods = new ArrayList<>();
        methods.add(first);
        methods.add(andThen);
    }

    private MethodComposition(List<LinkableMethod> methods) {
        this.methods = methods;
    }
    
    private MethodComposition(MethodComposition other, LinkableMethod andThen) {
        this.methods = new ArrayList<>();
        methods.addAll(other.methods);
        methods.add(andThen);
    }

    @Override
    public Object invoke(Object... args) throws Throwable {
        var it = methods.iterator();
        var firstMethod = it.next();
        Object result = firstMethod.invoke(args);
        while (it.hasNext()) {
            LinkableMethod next = it.next();
            result = next.invoke(result);
        }
        return result;        
    }
    
    @Override
    public LinkableMethod andThen(LinkableMethod other) {
        if (other instanceof MethodComposition mc) {
            List<LinkableMethod> methods = new ArrayList<>();
            methods.addAll(this.methods);
            methods.addAll(mc.methods);
            return new MethodComposition(methods);
        } else {
            return new MethodComposition(this, other);
        }
    }
    
    @Override
    public Optional<CallSite> staticLink(CallSiteType csType, MethodType staticTypes, long anyMask) {
        if (csType == CallSiteType.PLAIN) {
            var currentType = staticTypes;
            var it = methods.iterator();
            var firstMethod = it.next();
            Optional<CallSite> maybeCs = firstMethod.staticLink(csType, currentType, anyMask);
            if (maybeCs.isEmpty()) {
                return EMPTY;
            }
            var cs = maybeCs.get();
            currentType = methodType(Object.class, cs.type().returnType());
            
            MethodHandle composed = cs.dynamicInvoker();
            
            while (it.hasNext()) {
                LinkableMethod next = it.next();
                maybeCs = next.staticLink(CallSiteType.PLAIN, currentType, anyMask);
                
                if (maybeCs.isEmpty()) {
                    return EMPTY;
                }
                cs = maybeCs.get();
                composed = MethodHandles.filterReturnValue(composed, cs.dynamicInvoker());
                currentType = methodType(Object.class, cs.type().returnType());
            }
            return Optional.of(new ConstantCallSite(composed));
        }
        return Optional.empty();
    }
    
    @Override
    public CallSite dynamicLink(CallSiteType csType, MethodType staticTypes, long anyMask,
            CompilerFlags flags) {
        if (csType == CallSiteType.PLAIN) {
            var currentType = staticTypes;
            var it = methods.iterator();
            var firstMethod = it.next();
            CallSite dyn = firstMethod.dynamicLink(csType, currentType, anyMask, flags);
            currentType = methodType(Object.class, dyn.type().returnType());
            MethodHandle composed = dyn.dynamicInvoker();
            
            while (it.hasNext()) {
                LinkableMethod next = it.next();
                // TODO Check current type twice here being right? 
                dyn = next.dynamicLink(CallSiteType.PLAIN, currentType, anyMask, flags); 
                
                composed = MethodHandles.filterReturnValue(composed, dyn.dynamicInvoker());
                currentType = methodType(Object.class, dyn.type().returnType());
            }
            return new ConstantCallSite(composed);
        }
        return PileMethod.super.dynamicLink(csType, staticTypes, anyMask, flags);
    }

    @Override
    public boolean acceptsArity(int arity) {
        return methods.get(0).acceptsArity(arity);
    }

}
