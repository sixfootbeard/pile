package pile.core.adapter;

import pile.core.ISeq;

public class ArraySeq<T> implements ISeq<T> {

    private final T[] arr;
    private int index;

    public ArraySeq(T[] arr, int index) {
        super();
        this.arr = arr;
        this.index = index;
    }

    @Override
    public T first() {
        return arr[index];
    }

    @Override
    public ISeq<T> next() {
        if (index + 1 == arr.length) {
            return null;
        }
        return new ArraySeq<T>(arr, index + 1);
    }

}
