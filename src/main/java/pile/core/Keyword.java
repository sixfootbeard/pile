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
import static org.objectweb.asm.Opcodes.*;
import static pile.compiler.Helpers.*;
import static pile.nativebase.NativeCore.*;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;

import pile.collection.Associative;
import pile.compiler.form.KeywordForm;
import pile.core.exception.PileCompileException;
import pile.core.hierarchy.PersistentObject;
import pile.core.indy.CallSiteType;
import pile.core.indy.CallableLink;
import pile.core.indy.guard.ReceiverTypeGuard;
import pile.core.method.LinkableMethod;
import pile.nativebase.NativeCore;
import pile.nativebase.method.PileInvocationException;
import pile.util.Pair;

public class Keyword extends PersistentObject<Keyword> implements PileMethod, Serializable, Comparable<Keyword>,
        Named, ConstForm<ConstantDynamic> {

    private static final Comparator<Keyword> COMPARATOR = Comparator.comparing(Keyword::getNamespace)
            .thenComparing(Keyword::getName);
    private static final Map<Pair<String, String>, Reference<Keyword>> GLOBAL_MAP = new ConcurrentHashMap<>();
    private static final ReferenceQueue<Keyword> REF = new ReferenceQueue<>();
    private static MethodHandle ASSOC_GET;

    static {
        Thread t = new Thread(() -> {
            for (;;) {
                TaggedReference poll;
                try {
                    poll = (TaggedReference) REF.remove();
                    // Just in case someone published a new one just atomically check the key.
                    GLOBAL_MAP.compute(poll.key, (k, ov) -> ov.get() == null ? null : ov);
                } catch (InterruptedException e) {
                    // Daemon threads are interrupted during polite shutdown, an info log here might
                    // give the wrong idea to the user of an actual error.
                    return;
                }
            }
        });
        t.setName("keyword-ref-culling");
        t.setDaemon(true);
        t.start();

        // Handles
        try {
            ASSOC_GET = lookup().findVirtual(Associative.class, "get",
                    methodType(Object.class, Object.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException("Couldn't find method", e);
        }
    }

    private static class TaggedReference extends WeakReference<Keyword> {

        private final Pair<String, String> key;

        public TaggedReference(Keyword referent, Pair<String, String> key, ReferenceQueue<? super Keyword> q) {
            super(referent, q);
            this.key = key;
        }

    }

    public static Keyword of(String name) {
        return of(null, name);
    }

    public static Keyword of(String ns, String name) {
        Pair<String, String> key = new Pair<>(ns, name);
        return GLOBAL_MAP.compute(key, (k, ov) -> {
            // No mapping or extant mapping was gc'd
            if (ov == null || ov.get() == null) {
                return new TaggedReference(new Keyword(ns, name), key, REF);
            } else {
                return ov;
            }
        }).get();
    }

    private final String namespace, name;

    private Keyword(String name) {
        this(null, name);
    }

    private Keyword(String namespace, String name) {
        this.namespace = namespace;
        this.name = Objects.requireNonNull(name, "Name may not be null");
    }

    public String getNamespace() {
        return namespace;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    protected int computeHash() {
        return Objects.hash(namespace, name);
    }

    @Override
    protected String computeStr() {
        return ":" + (namespace != null ? (namespace + "/") : "") + name;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @CallableLink
    public Object call(Associative assoc) {
        if (assoc == null) {
            return null;
        }
        return assoc.getValue(this);
    }
    
    @Override
    public boolean acceptsArity(int arity) {
        return arity == 2 || arity == 3;
    }

    @Override
    public Object invoke(Object... args) throws Throwable {
        var len = args.length;
        
        switch (len) {
            case 1: return NativeCore.get(args[0], this);
            case 2: return NativeCore.get(args[0], this, args[1]);
            default: throw new PileInvocationException("keyword: wrong number of method args, expected 1,2, found: " + len);
        }
    }

    @Override
    public Optional<CallSite> staticLink(CallSiteType csType, MethodType staticTypes, long anyMask) {
        if (csType == CallSiteType.PLAIN) {
        
            if (! Associative.class.isAssignableFrom(staticTypes.parameterType(0))) {
                return Optional.empty();
            }
            
            // receiver is Associative
        
            MethodHandle assocGet;
            int parameterCount = staticTypes.parameterCount();
            if (parameterCount == 1) {
                // (:a coll)
                // (assoc, object)
                assocGet = insertArguments(ASSOC_GET, 2, new Object[] { null });
            } else if (parameterCount == 2) {
                // (:a coll ifNone)
                assocGet = ASSOC_GET;
            } else {
                return Optional.empty();
//                throw new PileCompileException("Bad arity for keyword link:" + parameterCount);
            }
            
            // (col ifNone/nil)
            

            // static types:
            // (receiver)
            // (receiver, ifNone)
            
            var bound = insertArguments(assocGet, 1, this);
            var cast = bound.asType(staticTypes);
            
            return Optional.of(new ConstantCallSite(cast));
            
        }
        // TODO Apply
        return Optional.empty();

    }

    @Override
    public int compareTo(Keyword o) {
        return COMPARATOR.compare(this, o);
    }

    private Object readResolve() throws ObjectStreamException {
        return of(namespace, name);
    }

    @Override
    public Optional<ConstantDynamic> toConst() {
        String bootstrapDescriptor = getConstantBootstrapDescriptor(Keyword.class, Type.getType(String[].class));
        Handle h = new Handle(H_INVOKESTATIC, Type.getType(KeywordForm.class).getInternalName(), "bootstrap",
                bootstrapDescriptor, false);
        ConstantDynamic cons;
        if (getNamespace() == null) {
            cons = new ConstantDynamic("keyword", Type.getDescriptor(Keyword.class), h, getName());
        } else {
            cons = new ConstantDynamic("keyword", Type.getDescriptor(Keyword.class), h, getNamespace(), getName());
        }
        return Optional.of(cons);
    }

}
