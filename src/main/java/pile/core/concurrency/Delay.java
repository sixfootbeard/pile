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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import pile.core.PCall;
import pile.core.Realizable;
import pile.core.Ref;

// FIXME Cancellable? If cancelled while one thread is directly waiting then interrupt? 
public class Delay implements Ref, Realizable {

    private static final Object UNINITIALIZED = new Object();

    private PCall fn;
    private Object val = UNINITIALIZED;

    public Delay(PCall fn) {
        this.fn = fn;
    }

    @Override
    public Object deref() throws Throwable {
        if (val == UNINITIALIZED) {
            synchronized (this) {
                if (val == UNINITIALIZED) {
                    // FIXME What to do about exceptions thrown during call?
                    val = fn.invoke();
                    fn = null;
                }
            }
        }

        return val;
    }

    @Override
    public Object deref(long time, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (val == UNINITIALIZED) {
            synchronized (this) {
                if (val == UNINITIALIZED) {
                    // Weirdly, the common forkjoin pool was used here initially but it appears that
                    // the forkjointask get(long, timeunit) won't actually return by the deadline if
                    // the thread is asleep. Although, it will hit the deadline while debugging.
                    CompletableFuture f = new CompletableFuture<>();
                    Thread.startVirtualThread(() -> {
                        try {
                            var result = fn.invoke();
                            f.complete(result);
                        } catch (Throwable e) {
                            f.completeExceptionally(e);
                        }
                    });
                    val = f.get(time, unit);
                    fn = null;
                }
            }
        }
        return val;

    }

    @Override
    public boolean isRealized() {
        return val != UNINITIALIZED;
    }

}
