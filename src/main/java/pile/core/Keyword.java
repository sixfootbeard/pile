package pile.core;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

import pile.collection.Associative;
import pile.collection.PersistentHashMap;
import pile.core.hierarchy.PersistentObject;
import pile.core.indy.CallableLink;
import pile.util.Pair;

public class Keyword extends PersistentObject<Keyword> implements PCall {

    private static final Map<Pair<String, String>, Reference<Keyword>> GLOBAL_MAP = new ConcurrentHashMap<>();
    private static final ReferenceQueue<Keyword> REF = new ReferenceQueue<>();

    static {
        Thread t = new Thread(() -> {
            for (;;) {
                TaggedReference poll;
                try {
                    poll = (TaggedReference) REF.remove();
                    // Just in case someone published a new one just atomically check the key.
                    GLOBAL_MAP.compute(poll.key, (k, ov) -> ov.get() == null ? null : ov);
                } catch (InterruptedException e) {
                    System.err.println("Interrupted keyword culling thread");
                    e.printStackTrace();
                }
            }
        });
        t.setName("keyword-ref-culling");
        t.setDaemon(true);
        t.start();
    }

    private static class TaggedReference extends WeakReference<Keyword> {

        private final Pair<String, String> key;

        public TaggedReference(Keyword referent, Pair<String, String> key, ReferenceQueue<? super Keyword> q) {
            super(referent, q);
            this.key = key;
        }

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

    private Keyword(String namespace, String name) {
        this.namespace = Objects.requireNonNull(namespace, "Namespace may not be null");
        this.name = Objects.requireNonNull(name, "Name may not be null");
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    @Override
    protected int computeHash() {
        return Objects.hash(namespace, name);
    }

    @Override
    protected String computeStr() {
        return ":" + namespace + "/" + name;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @CallableLink
    public Object call(Associative assoc) {
        return assoc.get(this);
    }

    @Override
    public Object invoke(Object... args) {
        if (args.length != 1) {
            throw new IllegalArgumentException();
        }
        Object maybe = args[0];
        if (maybe instanceof Associative assoc) {
            return call(assoc);
        }
        return null;

    }

}
