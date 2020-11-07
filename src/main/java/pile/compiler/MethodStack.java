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
package pile.compiler;

import static pile.compiler.Helpers.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import pile.compiler.typed.Any;
import pile.core.Typed;
import pile.core.exception.MissingStackResultException;

/**
 * Current method stack. <br>
 * <br>
 * <ol>
 * <li>(enterMethod)
 * <li>...
 * <li>(leaveMethod)
 * </ol>
 * Multiple stacks may exist at one time but all methods interact with the last
 * {@link #enterMethod() opened} stack.
 * 
 */
public class MethodStack {

    public record TypeRecord(Class<?> clazz, boolean constant) {
    
        public TypeRecord(Class<?> clazz) {
            this(clazz, false);
        }
    
        public Class<?> javaClass() {
            return clazz() == Any.class ? Object.class : clazz();
        }
    
    };

    private record CurrentStack(Deque<TypeRecord> currentStack) {
    };

    private static final Deque<CurrentStack> stacks = new ArrayDeque<>();

    public void enterMethod() {
        stacks.addLast(new CurrentStack(new ArrayDeque<>()));
    }
    
    public void leaveMethod() {
        stacks.removeLast();
    }

    public void pushAny() {
        push(Any.class);
    }
    
    public void pushNull() {
        push(Any.class, true);
    }

    public void push(Class<?> type) {
        push(type, false);
    }
    
    public void pushConstant(Class<?> type) {
        push(type, true);
    }
    
    public void push(Class<?> type, boolean constant) {
        Objects.requireNonNull(type, "Type cannot be null");
        stacks.getLast().currentStack().addLast(new TypeRecord(type, constant));
    }
    

    /**
     * Pop N raw type records (eg. can contain {@link Any})
     * @param count
     * @return
     */
    public List<TypeRecord> popN(int count) {
        CurrentStack last = stacks.getLast();
        Deque<TypeRecord> currentStack = last.currentStack();
        int size = currentStack.size();

        List<TypeRecord> out = new ArrayList<>(size);
        for (int i = 0; i < count; ++i)
            out.add(null);

        for (int i = count - 1; i >= 0; --i) {
            out.set(i, currentStack.removeLast());
        }

        return out;
    }

    /**
     * Pop the java type (no {@link Any}) on the stack.
     * 
     * @return
     */
    public Class<?> pop() {
        CurrentStack currentStack = stacks.getLast();
        Deque<TypeRecord> typeRecords = currentStack.currentStack();
        ensureEx(! typeRecords.isEmpty(), MissingStackResultException::new,
                "Expected to have a stack argument, found none.");
        TypeRecord typeRecord = typeRecords.pollLast();
        return typeRecord.javaClass();        
    }

    /**
     * Pop raw type record.
     * @return
     */
    public TypeRecord popR() {
        CurrentStack currentStack = stacks.getLast();
        TypeRecord typeRecord = currentStack.currentStack().pollLast();
        return typeRecord;
    }

    public Class<?> peek() {
        CurrentStack currentStack = stacks.getLast();
        TypeRecord tr = currentStack.currentStack().peekLast();
        return tr.javaClass();        
    }
    
    public List<TypeRecord> peekN(int formCount) {
        List<TypeRecord> out = new ArrayList<>(formCount);
        Iterator<TypeRecord> desc = stacks.getLast().currentStack().descendingIterator();
        for (int i = 0; i < formCount; ++i) {
            out.add(desc.next());
        }
        Collections.reverse(out);
        return out;
    }

    public boolean isEmpty() {
        CurrentStack currentStack = stacks.getLast();
        return currentStack.currentStack().isEmpty();
    }

    @Override
    public String toString() {
        return stacks.stream()
                     .map(cs -> cs.currentStack().stream()
                                  .map(TypeRecord::clazz)
                                  .map(Object::toString)
                                  .collect(Collectors.joining(",", "[", "]")))
                     .collect(Collectors.joining(",\n"));
    }

    public boolean peekConstant() {
        CurrentStack currentStack = stacks.getLast();
        TypeRecord tr = currentStack.currentStack().peekLast();
        return tr.constant();
    }

    public int size() {
        return stacks.getLast().currentStack().size();
    }

}
