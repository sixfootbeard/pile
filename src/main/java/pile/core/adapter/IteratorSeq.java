package pile.core.adapter;

import java.util.Iterator;
import java.util.NoSuchElementException;

import pile.core.ISeq;

public class IteratorSeq<T> implements Iterator<T> {

    private ISeq<T> seq;

    public IteratorSeq(ISeq<T> seq) {
        super();
        this.seq = seq;
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        T t = seq.first();
        seq = seq.next();
        return t;

    }

    @Override
    public boolean hasNext() {
        return seq != null;
    }

}
