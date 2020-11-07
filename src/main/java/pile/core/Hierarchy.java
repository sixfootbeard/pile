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

import static java.util.Objects.*;

import java.lang.invoke.SwitchPoint;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import pile.collection.PersistentMap;
import pile.collection.PersistentSet;

/**
 * Immutable hierarchy.
 * @author john
 *
 */
public class Hierarchy {

    private final PersistentMap<Keyword, Keyword> childToParentMap;// = PersistentMap.empty();
    private final PersistentMap<Keyword, PersistentSet<Keyword>> parentToChildMap;// = PersistentMap.empty();
    
    public Hierarchy() {
        this(PersistentMap.empty(), PersistentMap.empty());
    }
    

    private Hierarchy(PersistentMap<Keyword, Keyword> childToParentMap,
            PersistentMap<Keyword, PersistentSet<Keyword>> parentToChildMap) {
        super();
        this.childToParentMap = childToParentMap;
        this.parentToChildMap = parentToChildMap;
    }

    public Hierarchy addRelationship(Object child, Object parent) {
        Class<? extends Object> childClass = child.getClass();
        Class<? extends Object> parentClass = parent.getClass();
        
        if (childClass.equals(Keyword.class) && parentClass.equals(Keyword.class)) {
            keywordAddRelationship((Keyword) child, (Keyword) parent);
        }
        if (childClass.equals(Class.class) && parentClass.equals(Class.class)) {
            return this;
        }
        throw new IllegalArgumentException("Bad relationship type.");
    
    }

    public boolean isAChild(Object child, Object parent) {
        requireNonNull(child, "child may not be null");
        requireNonNull(parent, "parent may not be null");
        
        Class<? extends Object> childClass = child.getClass();
        Class<? extends Object> parentClass = parent.getClass();
        if (childClass.equals(Keyword.class) && parentClass.equals(Keyword.class)) {
            return keywordIsAChild((Keyword) child, (Keyword) parent);
        }
        if (childClass.equals(Class.class) && parentClass.equals(Class.class)) {
            return classIsAChild((Class) child, (Class) parent);
        }
        throw new IllegalArgumentException("Bad relationship type.");
    }
    
    public PersistentSet<Object> descendants(Object tag) {
        
        Set<Keyword> accumulated = new HashSet<>();
        Deque<Keyword> state = new LinkedList<>();
        
        state.addAll(parentToChildMap.get(tag));
        
        while (! state.isEmpty()) {
            Keyword first = state.pollFirst();
            PersistentSet<Keyword> newChildren = parentToChildMap.get(first);
            if (newChildren != null) {
                state.addAll(newChildren);
                accumulated.addAll(newChildren);
            }
        }
        
        return PersistentSet.fromIterable(accumulated);
    }
    
    public PersistentSet<Object> ancestors(Keyword tag) {
        
        Set<Keyword> accumulated = new HashSet<>();
        Keyword current = tag;
        
        for (;;) {
            Keyword parent = childToParentMap.get(current);
            if (parent != null) {
                accumulated.add(parent);
                current = parent;
            } else {
                break;
            }
        }
        
        
        return PersistentSet.fromIterable(accumulated);
    }

    private Hierarchy keywordAddRelationship(Keyword child, Keyword parent) {


        var newChildToParentMap = childToParentMap.assoc(child, parent);

        PersistentSet<Keyword> children = parentToChildMap.get(parent);
        if (children == null) {
            children = PersistentSet.empty();
        }
        children = children.conj(child);
        var newParentToChildMap = parentToChildMap.assoc(parent, children);
    
        return new Hierarchy();

    }

    private boolean classIsAChild(Class child, Class parent) {
        return parent.isAssignableFrom(child);
    }

    private boolean keywordIsAChild(Keyword child, Keyword parent) {
        // TODO Check reverse to ensure no loop?
        var current = child;
        for (;;) {
            Keyword maybeParent = childToParentMap.get(current);
            if (maybeParent != null) {
                if (maybeParent.equals(current)) {
                    return true;
                }
                current = maybeParent;
            } else {
                break;
            }
        }
        return false;
    }

}
