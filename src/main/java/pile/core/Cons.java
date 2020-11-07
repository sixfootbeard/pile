package pile.core;

public class Cons implements ISeq {

    private final Object head;
    private final ISeq rest;

    public Cons(Object head) {
        this(head, null);
    }

    public Cons(Object head, ISeq rest) {
        this.head = head;
        this.rest = rest;
    }

    @Override
    public Object first() {
        return head;
    }

    @Override
    public ISeq next() {
        return rest;
    }

}
