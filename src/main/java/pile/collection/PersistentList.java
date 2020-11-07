package pile.collection;

import java.util.List;
import java.util.function.Function;

import pile.core.Conjable;
import pile.core.Cons;
import pile.core.ISeq;
import pile.core.Metadata;
import pile.core.PObj;
import pile.core.Seqable;

public class PersistentList<T> implements PObj<PersistentList<T>>, Conjable<T>, Counted, Seqable<T>, ISeq<T> {

//    private

    private final Cons head;
    private final int size;
    private final PersistentHashMap meta;

    private PersistentList(PersistentHashMap meta, PersistentList other) {
        this.meta = meta;
        this.head = other.head;
        this.size = other.size;
    }

    private PersistentList(PersistentHashMap meta, Cons newHead, int size) {
        this.meta = meta;
        this.head = newHead;
        this.size = size;
    }

    public PersistentList(PersistentHashMap meta, int size) {
        this(meta, (Cons) null, size);
    }

    public PersistentList() {
        this(null, 0);
    }

    @Override
    public ISeq<T> seq() {
        return head;
    }

    @Override
    public int count() {
        return size;
    }

    @Override
    public PersistentList<T> conj(T t) {
        return new PersistentList<>(meta(), new Cons(t, head), size + 1);
    }

    @Override
    public T first() {
        if (head == null) {
            return null;
        }
        return (T) head.first();
    }

    @Override
    public ISeq<T> next() {
        if (head == null) {
            return null;
        }
        return head.next();
    }

    @Override
    public PersistentHashMap meta() {
        return meta;
    }

    @Override
    public PersistentList<T> withMeta(PersistentHashMap newMeta) {
        return new PersistentList<>(newMeta, head, size);
    }

    @Override
    public PersistentList<T> updateMeta(Function<PersistentHashMap, PersistentHashMap> update) {
        PersistentHashMap oldMeta = this.meta();
        if (oldMeta == null) {
            oldMeta = new PersistentHashMap<>();
        }
        return withMeta(update.apply(oldMeta));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("[");

        for (Object o : seq()) {
            sb.append(o);
            sb.append(", "); // TODO end
        }

        sb.append("]");

        return sb.toString();
    }

    @Override
    public PersistentList cons(Object o) {
        return new PersistentList(meta, new Cons(o, this.head), size + 1);
    }

    public static PersistentList<Object> fromList(List<Object> list) {
        Cons cons = null;
        for (int i = list.size() - 1; i >= 0; --i) {
            cons = new Cons(list.get(i), cons);
        }
        return new PersistentList<>(null, cons, list.size());
    }

}
