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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.List;

import pile.core.indy.CallSiteType;
import pile.core.indy.CompilerFlags;
import pile.core.indy.InlineCacheCallSite;
import pile.core.indy.guard.FullTypeGuard;

/**
 * {@link CallSite} for {@link HiddenNativeMethod}.
 *
 */
public class NativeMethodInlineCacheCallSite extends InlineCacheCallSite {

    private final List<MethodType> types = null;
    private final HiddenNativeMethod meth;

    public NativeMethodInlineCacheCallSite(HiddenNativeMethod meth, MethodType type, CompilerFlags flags) {
        super(type, 10, flags);
        this.meth = meth;
    }

    /**
     * Caches a single method handle target {@link FullTypeGuard guarded} to
     * fallback and relink.
     */
    @Override
    protected MethodHandle makeMono(Object[] args, MethodType methodType) {
        // Dont' really need to store the type here, if it fails then we need to make
        // another one anyway
        return meth.tryLink(methodType)
                   .map(h ->  FullTypeGuard.getSubtypeGuard(args, type()).guard(h, relink))
                   .orElseThrow(() -> new UnsatisfiableMethodCall(methodType));
    }

    @Override
    protected MethodHandle makeUnopt(Object[] args, MethodType methodTypes) throws Throwable {
        MethodHandle invokeLink = LinkableMethod.invokeLink(CallSiteType.PLAIN, meth);
        MethodHandle out = invokeLink.asCollector(Object[].class, methodTypes.parameterCount())
                                     .asType(type());
        return out;
    }

}
