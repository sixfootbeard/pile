package pile.nativebase;

import java.util.Iterator;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

import pile.collection.PersistentList;
import pile.core.Cons;
import pile.core.ISeq;
import pile.core.Seqable;

public class NativeCore {

    public static ISeq cons(Object head, Seqable<?> s) {
        return new Cons(head, s.seq());
    }

    public static ISeq cons(Object head, Object s) {
        if (s == null) {
            return new PersistentList().conj(head);
        }
        return new Cons(head, seq(s));
    }

    public static ISeq seq(Seqable s) {
        return s.seq();
    }

    public static ISeq seq(Object o) {
        if (o == null) {
            return null;
        } else if (o.getClass().isArray()) {
            Object[] arr = (Object[]) o;
            return seqSized(arr, 0, arr.length, (a, i) -> a[i]);
        } else if (o instanceof CharSequence cs) {
            return seq(cs);
        } else if (o instanceof Iterable it) {
            return seq(it);
        } else if (o instanceof Map m) {
            return seq(m);
        } else if (o instanceof Seqable s) {
            return seq(s);
        } else {
            Class c = o.getClass();
            throw new IllegalArgumentException("Don't know how to create ISeq from: " + c.getName());
        }
    }

    public static ISeq seq(CharSequence cs) {
        return seqSized(cs, 0, cs.length(), (b, i) -> b.charAt(i));
    }

    public static ISeq seq(Iterable iter) {
        return seq(iter.iterator());
    }

    public static ISeq seq(Map map) {
        return seq(map.entrySet());
    }

    @SuppressWarnings("rawtypes")
    private static <T> ISeq seqSized(T t, int current, int size, BiFunction<T, Integer, Object> create) {
        if (current == size) {
            return null;
        }
        return new ISeq() {

            @Override
            public Object first() {
                return create.apply(t, current);
            }

            @Override
            public ISeq next() {
                return seqSized(t, current + 1, size, create);
            }
        };
    }

    private static ISeq seq(Iterator iter) {
        if (!iter.hasNext()) {
            return null;
        }

        Object current = iter.next();

        return new ISeq() {

            @Override
            public Object first() {
                return current;
            }

            @Override
            public ISeq next() {
                return seq(iter);
            }
        };
    }

    public static Object first(ISeq is) {
        return is.first();
    }

    public static Object first(Object is) {
        ISeq seq = seq(is);
        if (seq == null) {
            return null;
        }
        return first(seq);
    }
    
    public static ISeq more(ISeq is) {
        return is.more();
    }

    public static Object second(ISeq is) {
        return is.more().first();
    }

    public static void prn(Object s) {
        System.out.println(s.toString());
        // TODO Remove this and wrap with method combinators
//        return null;
    }
    
    public static void prnall(Object... s) {
        for (Object o : s) {
            System.out.print(o);
        }
        System.out.println();
        // TODO Remove this and wrap with method combinators
//        return null;
    }

}
