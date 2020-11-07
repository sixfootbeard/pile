package pile.core;

import java.util.function.Supplier;

public class Concat<T> implements ISeq<T> {

    ISeq<T> pre;
    Supplier<ISeq<T>> post;

    public Concat(ISeq<T> pre, ISeq<T> post) {
        this(pre, () -> post);
    }
    
    public Concat(ISeq<T> pre, Supplier<ISeq<T>> post) {
        this.pre = pre;
        this.post = post;
    }

    @Override
    public T first() {
        return pre.first();
    }

    @Override
    public ISeq<T> next() {
        ISeq<T> next = pre.next();
        if (next == null) {
            return post.get();
        }
        return new Concat(next, post);
    }

}