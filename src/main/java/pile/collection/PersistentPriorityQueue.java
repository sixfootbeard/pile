/**
 * Copyright 2026 John Hinchberger
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
package pile.collection;

import java.util.AbstractCollection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;

import org.objectweb.asm.ConstantDynamic;

import pile.core.Conjable;
import pile.core.ISeq;
import pile.core.Metadata;
import pile.nativebase.NativeCore;

/**
 * Persistent Binomial Min-Heap.
 */
public class PersistentPriorityQueue<E> extends AbstractCollection<E> implements PersistentCollection<E>, Conjable<E> {

    private static final long serialVersionUID = 6710438371386427298L;

    private final Comparator<E> cmp;
    private final LinkedNode<TreeNode<E>> root;
    private final PersistentMap meta;
    private final int size;
    private final Least<E> least;

    public PersistentPriorityQueue(Comparator<E> cmp) {
        this(null, cmp, PersistentMap.EMPTY, 0, null);
    }

    public PersistentPriorityQueue(Comparator<E> cmp, PersistentMap meta) {
        this(null, cmp, meta, 0, null);
    }

    public PersistentPriorityQueue(LinkedNode<TreeNode<E>> root, Comparator<E> cmp, PersistentMap meta, int size,
            Least<E> least) {
        super();
        this.root = root;
        this.cmp = cmp;
        this.meta = meta;
        this.size = size;
        this.least = least;
    }

    public E min() {
        if (size == 0) {
            // TODO ?? maybe null
            throw new NoSuchElementException();
        }
        return least.least();
    }

    public PersistentPriorityQueue<E> popMin() {
        if (size == 1) {
            return new PersistentPriorityQueue<E>(cmp, meta);
        } else {
            // Find the node which contains the min element root and break it up. We can
            // copy the remaining tail roots unchanged from the prior collection.
            final List<TreeNode<E>> collected = new ArrayList<>();
            final E leastValue = this.least.least();
            LinkedNode<TreeNode<E>> current = root;

            TreeNode<E> currentTreeNode = null;
            while (current != null) {
                currentTreeNode = current.getValue();
                if (cmp.compare(leastValue, currentTreeNode.getValue()) == 0) {
                    // break up;
                    break;
                } else {
                    collected.add(currentTreeNode);
                }

                current = current.getNext();
            }
            return popAt(collected, currentTreeNode, current == null ? null : current.getNext());
        }
    }

    @Override
    public int count() {
        return size();
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public Iterator<E> iterator() {
        final Deque<TreeNode<E>> locals = new ArrayDeque<>();
        LinkedNode<TreeNode<E>> ptr = root;
        while (ptr != null) {
            locals.add(ptr.getValue());
            ptr = ptr.getNext();
        }
    
        return new Iterator<E>() {
    
            @Override
            public boolean hasNext() {
                return !locals.isEmpty();
            }
    
            @Override
            public E next() {
                TreeNode<E> first = locals.removeFirst();
                E out = first.getValue();
    
                TreeNode<E> childPtr = first.getChild();
                while (childPtr != null) {
                    locals.addLast(childPtr);
                    childPtr = childPtr.getSibling();
                }
    
                return out;
            }
    
        };
    }

    @Override
    public ISeq<E> seq() {
        if (size == 0) {
            return ISeq.EMPTY;
        }
        return new ISeq<E>() {
    
            @Override
            public E first() {
                return min();
            }
    
            @Override
            public ISeq<E> next() {
                return popMin().seq();
            }
    
        };
    }

    @Override
    public PersistentPriorityQueue<E> conj(E t) {
        if (size == 0) {
            TreeNode<E> tn = new TreeNode<>(null, null, 0, t);
            LinkedNode<TreeNode<E>> newRoot = new LinkedNode<>(tn);
            return new PersistentPriorityQueue<E>(newRoot, cmp, meta, 1, new Least<E>(t));
        } else {
            TreeNode<E> firstNode = root.getValue();
            if (firstNode.getOrder() > 0) {
                // Happens half the time during a continuous insert
                TreeNode<E> tn = new TreeNode<>(null, null, 0, t);
                LinkedNode<TreeNode<E>> newRoot = new LinkedNode<>(root, tn);
                Least<E> newLeast = cmp.compare(this.least.least(), t) < 0 ? this.least : new Least<>(t);
                return new PersistentPriorityQueue<>(newRoot, cmp, meta, size + 1, newLeast);
            } else {
                return insertInternal(t);
            }
        }
    }

    @Override
    public PersistentPriorityQueue<E> into(Object source) {
        if (source instanceof PersistentPriorityQueue pq && cmp == pq.cmp) {
            List<TreeNode<E>> newRoots = new ArrayList<>();
            foreach(this.root, newRoots::add);
            foreach(((PersistentPriorityQueue<E>) pq).root, newRoots::add);
            return createFromRoots(newRoots, null, size() + pq.size());
        } else {
            PersistentPriorityQueue<E> tmp = new PersistentPriorityQueue<>(cmp);
            ISeq seq = NativeCore.seq(source);
            while (seq != ISeq.EMPTY) {
                tmp = tmp.conj((E) seq.first());
                seq = seq.next();
            }
            return this.into(tmp);
        }
    }

    @Override
    public PersistentMap meta() {
        return meta;
    }

    @Override
    public PersistentPriorityQueue<E> withMeta(PersistentMap newMeta) {
        return new PersistentPriorityQueue<E>(root, cmp, newMeta, size, least);
    }

    @Override
    public boolean acceptsArity(int arity) {
        return false;
    }

    @Override
    public Object invoke(Object... args) throws Throwable {
        throw new RuntimeException("Not callable.");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        
        Iterator<E> it = iterator();
        if (it.hasNext()) {
            sb.append(it.next());
        }
        it.forEachRemaining(e -> {
            sb.append(", ");
            sb.append(e);
        });
        
        sb.append("]");
    
        return sb.toString();
    }

    @Override
    public Optional<ConstantDynamic> toConst() {
        return Optional.empty();
    }

    private <E> void foreach(LinkedNode<E> ln, Consumer<E> e) {
        while (ln != null) {
            e.accept(ln.getValue());
            ln = ln.getNext();
        }
    }

    /**
     * Create a new persistent vector, with the currentTreeNode being the root
     * containing the min element that should be popped..
     * 
     * @param collected       The roots that have been collected before finding the
     *                        min root. May be empty. The minRoot's children will
     *                        need to be incorporated into this set to create a new
     *                        root set.
     * @param currentTreeNode The root containing the min element. It's children
     *                        should be incorporated into the collected root set to
     *                        create a new root set.
     * @param next            The remaining roots that will be untouched by this
     *                        operation. They can be copied as the tail roots in the
     *                        new tree.
     * @return A new {@link PersistentPriorityQueue} with the above specified root
     *         set.
     */
    private PersistentPriorityQueue<E> popAt(List<TreeNode<E>> collected, TreeNode<E> currentTreeNode,
            LinkedNode<TreeNode<E>> next) {

        TreeNode<E> current = currentTreeNode.getChild();
        while (current != null) {
            collected.add(current);
            current = current.getSibling();
        }

        return createFromRoots(collected, next, size - 1);

    }

    /**
     * Create a new root set from the provided two root collections.
     * 
     * @param collected An unordered collection of roots from at most 2 trees.
     * @param next      A tail root set that is guaranteed to not overlap with the
     *                  collected root set.
     * @param newSize   The new number of elements in the combined root set.
     * @return A new {@link PersistentPriorityQueue} which incorporates the provided
     *         collected roots and tail root set.
     */
    private PersistentPriorityQueue<E> createFromRoots(List<TreeNode<E>> collected, LinkedNode<TreeNode<E>> next,
            int newSize) {
        Collections.sort(collected, Comparator.comparingInt(TreeNode::getOrder));
        Iterator<TreeNode<E>> iter = collected.iterator();
        List<TreeNode<E>> highlander = new ArrayList<>();
        if (!collected.isEmpty()) {
            TreeNode<E> prev = iter.next();
            while (iter.hasNext()) {
                TreeNode<E> eval = iter.next();
                final int prevOrder = prev.getOrder();
                final int evalOrder = eval.getOrder();
                if (prevOrder == evalOrder) {
                    prev = prev.combine(eval, cmp);
                } else if (prevOrder < evalOrder) {
                    highlander.add(prev);
                    prev = eval;
                } else {
                    // prevOrder > evalOrder
                    highlander.add(eval);
                    // prev = prev;
                }
            }
            highlander.add(prev);
        }
        TreeNode<E> minNode = highlander.isEmpty() ? null
                : Collections.min(highlander, Comparator.comparing(e -> e.getValue(), cmp));
        E newLeast = (minNode == null) ? null : minNode.getValue();

        LinkedNode<TreeNode<E>> ptr = next;
        while (ptr != null) {
            E ptrVal = ptr.getValue().getValue();
            if (newLeast == null || cmp.compare(ptrVal, newLeast) < 0) {
                newLeast = ptrVal;
            }

            ptr = ptr.getNext();
        }

        LinkedNode<TreeNode<E>> newRoot = createNewRoot(highlander, next);
        return new PersistentPriorityQueue<E>(newRoot, cmp, meta, newSize, new Least<>(newLeast));
    }

    private PersistentPriorityQueue<E> insertInternal(E t) {
        // TODO It's possible without tracking each order of tree to know the maximum
        // depth we would see in a recursive solution. That might be preferable to
        // creating a list here for small depths.

        final TreeNode<E> insertNode = new TreeNode<>(null, null, 0, t);
        final List<TreeNode<E>> collected = new ArrayList<>();

        LinkedNode<TreeNode<E>> current = root;
        TreeNode<E> carry = insertNode;

        while (current != null) {
            TreeNode<E> currentTreeNode = current.getValue();

            final int carryOrder = carry.getOrder();
            final int currentOrder = currentTreeNode.getOrder();

            if (carryOrder == currentOrder) {
                carry = carry.combine(currentTreeNode, cmp);
            } else if (carryOrder < currentOrder) {
                break;
            } else {
                // carryOrder > currentOrder
                collected.add(currentTreeNode);
            }

            current = current.getNext();
        }
        collected.add(carry);

        LinkedNode<TreeNode<E>> newRoot = createNewRoot(collected, current);
        Least<E> newLeast = cmp.compare(this.least.least(), t) < 0 ? this.least : new Least<>(t);
        return new PersistentPriorityQueue<E>(newRoot, cmp, meta, size + 1, newLeast);
    }

    /**
     * Create a new root set from the ordered prefix nodes and tail linked nodes.
     * 
     * @param collected
     * @param tail
     * @return
     */
    private LinkedNode<TreeNode<E>> createNewRoot(List<TreeNode<E>> collected, LinkedNode<TreeNode<E>> tail) {
        Collections.reverse(collected);

        LinkedNode<TreeNode<E>> next = tail;
        for (var tn : collected) {
            next = new LinkedNode<>(next, tn);
        }

        return next;

    }

    private static record Least<T>(T least) {
    };

    private static final class TreeNode<T> {
        private final TreeNode<T> sibling, child;
        private final int order;
        private final T value;

        public TreeNode(TreeNode<T> sibling, TreeNode<T> child, int order, T value) {
            super();
            this.sibling = sibling;
            this.child = child;
            this.order = order;
            this.value = value;
        }

        public TreeNode<T> combine(TreeNode<T> other, Comparator<T> cmp) {
            TreeNode<T> lesser, greater;

            if (cmp.compare(value, other.value) < 0) {
                lesser = this;
                greater = other;

            } else {
                lesser = other;
                greater = this;
            }

            TreeNode<T> newChild = new TreeNode<T>(lesser.getChild(), greater.getChild(), order, greater.getValue());
            TreeNode<T> newParent = new TreeNode<T>(null, newChild, order + 1, lesser.getValue());
            return newParent;
        }

        public TreeNode<T> getSibling() {
            return sibling;
        }

        public TreeNode<T> getChild() {
            return child;
        }

        public int getOrder() {
            return order;
        }

        public T getValue() {
            return value;
        }

    }

    private static final class LinkedNode<T> {
        private final LinkedNode<T> next;
        private final T value;

        public LinkedNode(T value) {
            super();
            this.next = null;
            this.value = value;
        }

        public LinkedNode(LinkedNode<T> next, T value) {
            super();
            this.next = next;
            this.value = value;
        }

        public LinkedNode<T> getNext() {
            return next;
        }

        public T getValue() {
            return value;
        }

    }

}
