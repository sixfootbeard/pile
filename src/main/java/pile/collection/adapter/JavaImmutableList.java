package pile.collection.adapter;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import pile.collection.Associative;
import pile.collection.Counted;

public abstract class JavaImmutableList<E> extends AbstractList<E> implements Counted, Associative<Integer, E> {

    @Override
    public E get(int index) {
        return get(index, null);
    }
    
    @Override
    public int size() {
        return count();
    }

}
