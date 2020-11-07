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

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Optional;

import pile.compiler.typed.Any;
import pile.core.PCall;
import pile.core.PileMethod;
import pile.core.indy.CallSiteType;
import pile.core.indy.CompilerFlags;
import pile.core.indy.OpaqueFunctionLinker;

public interface LinkableMethod extends PCall {

    /**
     * 
     * @return A return type for any possible invocation of this method.
     */
    public default Optional<Class<?>> getReturnType() {
        return Optional.empty();
    }

    /**
     * Return the return type of the method, if possible to deduce from the static
     * types and method type.
     * 
     * @param csType      What type the callsite is.
     * @param staticTypes The statically known types of the method call
     * @param anyMask     Mask for methodtype for {@link Any}.
     * @return The return type of the method call, or empty.
     */
    public default Optional<Class<?>> getReturnType(CallSiteType csType, MethodType staticTypes, long anyMask) {
        return getReturnType().or(() -> staticLink(csType, staticTypes, anyMask).map(mh -> mh.type().returnType()));
    }

    /**
     * 
     * @param arity The potential number of arguments to call this method with.
     * @return True if this method accepts this arity, false if not.
     */
    public boolean acceptsArity(int arity);

    /**
     * Try to link against the statically known types. If there are multiple choices
     * then return empty.
     * 
     * @param csType      What type the callsite is.
     * @param staticTypes The statically known types. Could be completely generic
     *                    eg. all Object.
     * @param anyMask     Mask for methodtype for {@link Any}.
     * @return A CallSite, if the statically known types are enough to disambiguate
     *         all possible targets, otherwise empty.
     */
    public default Optional<CallSite> staticLink(CallSiteType csType, MethodType staticTypes, long anyMask) {
        return Optional.empty();
    }

    /**
     * Runtime link against the statically known types and the actual seen types.
     * 
     * @param csType      What type the callsite is.
     * @param statictypes The types known at compile time. This can help limit the
     *                    bounds of necessary guards if you know that the actual
     *                    type can only vary up to a point.
     * @param anyMask     Bitmask representing which arguments have an unspecified
     *                    type. See {@link Any}.
     * @return A callsite that can handle <b>all</b> invocations of this method with
     *         the specified static types.
     */
    public default CallSite dynamicLink(CallSiteType csType, MethodType statictypes, long anyMask,
            CompilerFlags flags) {
        MethodHandle linked = invokeLink(csType, this);
        MethodHandle formed = linked.asCollector(Object[].class, statictypes.parameterCount()).asType(statictypes);
        return new ConstantCallSite(formed);
    }
    
    public default CallSite link(CallSiteType csType, MethodType statictypes, long anyMask, CompilerFlags flags) {
        return staticLink(CallSiteType.PLAIN, statictypes, anyMask)
                .orElseGet(() -> dynamicLink(CallSiteType.PLAIN, statictypes, anyMask, flags));
    }

    /**
     * Create a new function which is the composition of calling this function and
     * then calling the next function with the result.
     * 
     * @param nextFn
     * @return
     */
    public default LinkableMethod andThen(LinkableMethod nextFn) {
        return new MethodComposition(this, nextFn);
    }
    
    public default boolean isPure() {
        return false;
    }

    public static MethodHandle invokeLink(CallSiteType csType, PCall m) {
        var handle = switch (csType) {
            case PLAIN -> OpaqueFunctionLinker.CALL_PCALL;
            case PILE_VARARGS -> OpaqueFunctionLinker.CALL_APPLY;
        };
        MethodHandle bound = handle.bindTo(m);
        bound = bound.withVarargs(handle.isVarargsCollector());
        return bound;
    }

}
