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
package pile.core.concurrency;

import static java.util.Objects.*;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class BufferedChannel implements PileChannel {

    private record PendingPut(Supplier<Boolean> acceptor, Object o) {}

    // TODO Fix these waiters to be fair
    private final LinkedList<Function<Object, Boolean>> waiters = new LinkedList<>();
    private final Deque<PendingPut> pendingPuts = new ArrayDeque<>();
    
    private final Deque<Object> pendingValues = new ArrayDeque<>();
    
    private final int max;
    // guarded by this
    private boolean closed = false;

    public BufferedChannel(int max) {
        this.max = max;
    }

    @Override
    public void put(Object val) throws InterruptedException {
        if (val == null) {
            throw new IllegalArgumentException("Cannot put nil into a channel");
        }
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("Cannot put to a closed channel");
            }
            Iterator<Function<Object, Boolean>> it = waiters.iterator();
            while (it.hasNext()) {
                Function<Object, Boolean> acc = it.next();
                boolean accepted = acc.apply(val);
                // If the value was accepted then we can remove the waiter, but if it was
                // rejected then the waiter is already completed, so we also need to remove it.
                it.remove();
                if (accepted) {
                    return;
                }
            }
            for (;;) {
                if (max == pendingValues.size()) {
                    this.wait();
                } else {
                    pendingValues.add(val);
                    break;
                }
            }    
        }
    }
    
    @Override
    public void put(Supplier<Boolean> acceptor, Object o) {
        requireNonNull(acceptor, "acceptor cannot be null");
        requireNonNull(o, "value cannot be null");
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("Cannot put to a closed channel");
            }
            // can never have waiters AND pending values
            if (atCapacity()) {
                pendingPuts.add(new PendingPut(acceptor, o));
                return;
            } else {
                // 'take' the object knowing we can always add it to pending.
                Boolean didTake = acceptor.get();
                if (! didTake) {
                    return;
                    // someone else took it, bail.
                }
                // Object is 'ours' now.
                Iterator<Function<Object, Boolean>> it = waiters.iterator();
                while (it.hasNext()) {
                    Function<Object, Boolean> waiter = it.next();
                    Boolean waiterAccepted = waiter.apply(o);
                    it.remove();
                    if (waiterAccepted) {
                        return;
                    }
                }
                // no waiters took
                pendingValues.add(o);
            }
        }
    }

    @Override
    public void get(Function<Object, Boolean> acceptor) {
        synchronized (this) {
            // even if closed there might be pending values
            while (!pendingValues.isEmpty()) {
                Object first = pendingValues.getFirst();
                Boolean accepted = acceptor.apply(first);
                if (accepted) {
                    pendingValues.removeFirst();
                    acceptPendingPut();
                    this.notify();
                    return;
                }
            }
            
       
            if (closed) {
                acceptor.apply(null);
            } else {
                waiters.add(acceptor);
            }
        }
    }
    
    @Override
    public void close() {
        synchronized (this) {
            this.closed = true;
            for (var wait : waiters) {
                // ignore return value
                wait.apply(null); 
            }
        }
    }

    private void acceptPendingPut() {
        Iterator<PendingPut> it = pendingPuts.iterator();
        while (it.hasNext()) {
            PendingPut pp = it.next();
            if (pp.acceptor().get()) {
                pendingValues.add(pp.o());
            }
        }        
    }

    private boolean atCapacity() {
        return pendingValues.size() == max;
    }

}
