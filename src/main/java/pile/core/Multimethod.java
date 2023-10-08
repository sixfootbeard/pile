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

    public static PileMethod lookupTargetFunction(Hierarchy hierarchy, Map<Object, PileMethod> keys, Object key) {
        PileMethod toRun = keys.get(key);
        if (toRun == null) {
            for (var entry : keys.entrySet()) {
                if (hierarchy.isAChild(key, entry.getKey())) {
                    return entry.getValue();
                }
            }
            toRun = keys.get(DEFAULT_KEYWORD);
        }
        if (toRun == null) {
            throw new IllegalArgumentException("No method to run for key: " + key);
        }
        return toRun;
    }
    
    /**
     * Call site for multimethods.
     * <ol>
     * <li>Cold - Calls {@link PCall#invoke(Object...)}.
     * <li>Monomorphic - Will cache the result of a key-to-function resolution so if
     * the keying function always returns the same key we have a fast path to the
     * right handle. Both the keying function and target handle will be directly
     * linked.
     * <li>Polymorphic - TODO
     * <li>Unoptimized - Simply calls the keying function and lookup functions using
     * the method handle combinators.
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
            
            PileMethod targetFunction = lookupTargetFunction(mm.hierarchyVar.deref(), pair.right(), key);
            
            final MethodHandle multiMethodTarget;
            if (targetFunction == null) {
                // TODO Print key but what about infinite seqs?
                multiMethodTarget = getExceptionHandle(type(), PileCompileException.class,
                        PileCompileException::new, "No matching keys for multimethod");
            } else {
                var toCall = targetFunction;

                // (a, b, c)
                CallSite cs = toCall.link(CallSiteType.PLAIN, type(), 0, flags);
                multiMethodTarget = cs.dynamicInvoker();
            }

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
            Pair<SwitchPoint,PersistentMap<Object,PileMethod>> local = mm.keysRef.get();
            PersistentMap<Object, PileMethod> methods = local.right();
            
            Var<Hierarchy> hierarchyVar = mm.hierarchyVar;
            MethodHandle resolvedValue = SymbolForm.bootstrap(lookup(), hierarchyVar.getName(), methodType(Object.class),
                    hierarchyVar.getNamespace().getName()).dynamicInvoker().asType(methodType(Hierarchy.class));
            
            // PileMethod.class (Hierarchy.class, Map.class, Object.class)
            MethodHandle lookupTarget = lookup().findStatic(Multimethod.class, "lookupTargetFunction",
                    methodType(PileMethod.class, Hierarchy.class, Map.class, Object.class));
            // PileMethod.class  (Map.class, Object.class)
            MethodHandle boundVar = collectArguments(lookupTarget, 0, resolvedValue);
            // PileMethod.class, (Object.class)
            MethodHandle boundMethods = insertArguments(boundVar, 0, methods);
            
            // Object(PileMethod, Object[])
            MethodHandle invoke = lookup().findVirtual(PileMethod.class, "invoke", methodType(Object.class, Object[].class));
            // Object (Object, Object[])
            MethodHandle keyed = collectArguments(invoke, 0, boundMethods);
            
            var keyFn = mm.keyFn;
            // Object (a, b, c)
            MethodHandle keyHandle = keyFn.link(CallSiteType.PLAIN, type(), 0, flags).dynamicInvoker();
            
            // Object (a, b, c, Object[])
            MethodHandle appliedKeyFn = collectArguments(keyed, 0, keyHandle);
            
            // Object (a, b, c, Object, Object, Object)
            MethodHandle collected = appliedKeyFn.asCollector(type().parameterCount(), Object[].class, type().parameterCount());
            
            // Object (a, b, c, a, b, c)
            MethodHandle typedCollected = collected.asType(type().appendParameterTypes(type().parameterList()));
            
            MethodHandle duped = MethodHandleHelpers.duplicateParameters(typedCollected, type());
            
            SwitchPoint sp = local.left();

            return sp.guardWithTest(duped, relink);
        }
    }
    

}
