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

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;
import static pile.compiler.Helpers.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import pile.collection.PersistentHashMap;
import pile.collection.PersistentMap;
import pile.collection.PersistentVector;
import pile.compiler.form.SymbolForm;
import pile.compiler.form.VarForm;
import pile.core.exception.PileCompileException;
import pile.core.indy.CallSiteType;
import pile.core.indy.CompilerFlags;
import pile.core.indy.InlineCacheCallSite;
import pile.core.indy.guard.JavaGuardBuilder;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.method.AbstractRelinkingCallSite;
import pile.util.MethodHandleHelpers;
import pile.util.Pair;

public class Multimethod implements PileMethod {

    private static final Keyword DEFAULT_KEYWORD = Keyword.of(null, "default");

    private static final Logger LOG = LoggerSupplier.getLogger(Multimethod.class);

    private final PileMethod keyFn;
    private final AtomicReference<Pair<SwitchPoint, PersistentMap<Object, PileMethod>>> keysRef;
    private final String name;
    private final Namespace ns;
    private final Var<Hierarchy> hierarchyVar;

    public Multimethod(Namespace ns, Var<Hierarchy> hierarchyVar, String name, PileMethod method) {
        super();
        this.keyFn = method;
        this.keysRef = new AtomicReference<>(new Pair<>(new SwitchPoint(), PersistentMap.empty()));
        this.ns = ns;
        this.name = name;
        this.hierarchyVar = hierarchyVar;        
    }
    
    @Override
    public boolean acceptsArity(int arity) {
        return keyFn.acceptsArity(arity);
    }

    @Override
    public CallSite dynamicLink(CallSiteType csType, MethodType staticTypes, long anyMask, CompilerFlags flags) {
        return switch (csType) {
            case PLAIN -> new MultimethodCallsite(this, staticTypes, flags);
            case PILE_VARARGS -> PileMethod.super.dynamicLink(csType, staticTypes, anyMask, flags);
            default -> throw new PileCompileException("Unknown callsite type: " + csType);
        };
    }

    public void addKey(Object key, PileMethod fn) {
        update(copy -> copy.assoc(key, fn));
    }

    public void removeKey(Object key) {
        update(copy -> copy.dissoc(key));
    }
    
    public PersistentMap<Object,PileMethod> getMethods() {
        return keysRef.get().right();
    }

    @Override
    public Object invoke(Object... args) throws Throwable {
        var keys = keysRef.get().right();
        return innerCall(keyFn, hierarchyVar.deref(), keys, args);
    }
    
    @Override
    public Object applyInvoke(Object... args) throws Throwable {
        var keys = keysRef.get().right();
        return innerApplyCall(keyFn, hierarchyVar.deref(), keys, args);
    }

    @Override
    public String toString() {
        return ns.getName() + "/" + name;
    }

    private void update(UnaryOperator<PersistentMap<Object, PileMethod>> fn) {
        for (;;) {
            var ref = keysRef.get();
            var newMap = fn.apply(ref.right());
            if (keysRef.compareAndSet(ref, new Pair<>(new SwitchPoint(), newMap))) {
                var sp = ref.left();
                LOG.trace("Invalidating multimethod switchpoint: %s/%s", ns.getName(), name);
                SwitchPoint.invalidateAll(new SwitchPoint[] { sp });
                break;
            }
        }
    }
    
    private static Object innerApplyCall(PileMethod keyFn, Hierarchy hierarchy, Map<Object, PileMethod> keys, Object... args) throws Throwable {
        Object key = keyFn.applyInvoke(args);
        PileMethod toRun = lookupTargetFunction(hierarchy, keys, key);
        return toRun.applyInvoke(args);
    }

    private static Object innerCall(PileMethod keyFn, Hierarchy hierarchy, Map<Object, PileMethod> keys, Object... args) throws Throwable {
        Object key = keyFn.invoke(args);
        PileMethod toRun = lookupTargetFunction(hierarchy, keys, key);
        return toRun.invoke(args);
    }
    
    private static Optional<PileMethod> lookupTargetFunctionOpt(Hierarchy hierarchy, Map<Object, PileMethod> keys, Object key) {
        PileMethod toRun = keys.get(key);
        if (toRun == null) {
            if (key instanceof PersistentVector pv) {
                toRun = matchVector(hierarchy, keys, pv);
            } else {
                if (toRun == null) {
                    toRun = matchSubtypes(hierarchy, keys, key);
                }
                if (toRun == null) {
                    toRun = keys.get(DEFAULT_KEYWORD);
                }
            }
        }
        return Optional.ofNullable(toRun);
    }

    private static PileMethod lookupTargetFunction(Hierarchy hierarchy, Map<Object, PileMethod> keys, Object key) {
        return lookupTargetFunctionOpt(hierarchy, keys, key)
                .orElseThrow(() -> new IllegalArgumentException("No method to run for key: " + key));
    }

    private static PileMethod matchVector(Hierarchy hierarchy, Map<Object, PileMethod> keys, PersistentVector pv) {
        int size = pv.size();
        outer:
        for (var entry : keys.entrySet()) {
            var candidate = entry.getKey();
            if (candidate instanceof PersistentVector candpv && size == candpv.size()) {
                for (int i = 0; i < size; ++i) {
                    var candelem = candpv.get(i);
                    var pvelem = pv.get(i);
                    if (! hierarchy.isAChild(pvelem, candelem)) {
                        continue outer;
                    }                    
                }
                return entry.getValue();
            }
        }
        return null;
    }

    private static PileMethod matchSubtypes(Hierarchy hierarchy, Map<Object, PileMethod> keys, Object key) {
        
        for (var entry : keys.entrySet()) {
            if (hierarchy.isAChild(key, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    /**
     * Call site for multimethods.
     * <ol>
     * <li>Cold - Calls {@link PCall#invoke(Object...)}.
     * <li>Monomorphic - Will cache the result of a key-to-function resolution so if
     * the keying function always returns the same key we have a fast path to the
     * right handle. Both the keying function and target handle will be directly
     * linked.
     * <li>Unoptimized - Same as cold
     * 
     *
     */
    private static class MultimethodCallsite extends InlineCacheCallSite {
    
        private final Multimethod mm;

        public MultimethodCallsite(Multimethod mm, MethodType type, CompilerFlags flags) {
            super(type, 10, flags);
            this.mm = mm;
        }

        @Override
        protected MethodHandle makeCold(Object[] args, MethodType methodType) throws Throwable {
            MethodHandle invoke = lookup().findVirtual(PileMethod.class, "invoke",
                    methodType(Object.class, Object[].class));
            return invoke.bindTo(mm).asCollector(Object[].class, methodType.parameterCount()).asType(methodType);
        }

        @Override
        protected MethodHandle makeMono(Object[] args, MethodType methodType) throws Throwable {
            var relinkKeySlot = dropArguments(relink, 0, Object.class);
        
            Object key = mm.keyFn.invoke(args);

            Pair<SwitchPoint, PersistentMap<Object, PileMethod>> pair = mm.keysRef.get();
            
            Optional<PileMethod> targetFunction = lookupTargetFunctionOpt(mm.hierarchyVar.deref(), pair.right(), key);
            
            final MethodHandle multiMethodTarget = targetFunction.map(pm -> pm.link(CallSiteType.PLAIN, type(), 0, flags).dynamicInvoker())
                                                                 .orElseGet(() -> getExceptionHandle(type(), IllegalArgumentException.class,
                                                                         IllegalArgumentException::new, "No matching keys for multimethod and no default method set."));

            // (Object, a, b, c) // key slot
            MethodHandle multiMethodTargetKeySlot = dropArguments(multiMethodTarget, 0, Object.class);

            JavaGuardBuilder guardBuilder = new JavaGuardBuilder(multiMethodTargetKeySlot, relinkKeySlot, type().insertParameterTypes(0, Object.class));
            if (key != null) {
                guardBuilder.guardNotNull(0);
            }
            guardBuilder.guardEquals(0, key);
            // (Object, a, b, c)
            MethodHandle guarded = guardBuilder.getHandle();
            
            MethodHandle keyHandle = mm.keyFn.link(CallSiteType.PLAIN, type(), 0, flags).dynamicInvoker();
            
            MethodHandle folded = foldArguments(guarded, keyHandle);
            
            SwitchPoint sp = pair.left();

            return sp.guardWithTest(folded, relink);
        }

        @Override
        protected MethodHandle makeUnopt(Object[] args, MethodType methodType) throws Throwable {
            // TODO Not sure if there's anything more to do here.
            return makeUnopt(args, methodType);
        }
    }
    

}
