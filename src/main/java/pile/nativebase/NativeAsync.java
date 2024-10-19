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
package pile.nativebase;

import static pile.nativebase.NativeCore.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import pile.collection.PersistentList;
import pile.collection.PersistentVector;
import pile.core.Keyword;
import pile.core.PCall;
import pile.core.concurrency.BufferedChannel;
import pile.core.concurrency.Delay;
import pile.core.concurrency.PileChannel;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.nativebase.method.PileInvocationException;

public class NativeAsync {

    private static final Logger LOG = LoggerSupplier.getLogger(NativeAsync.class);

    private static class ScheduledHolder {
        private static final ScheduledExecutorService EXEC = new ScheduledThreadPoolExecutor(1);
    }

    private static final Keyword TIMEOUT_KW = Keyword.of("timeout");

    private NativeAsync() {
    }

    @PileDoc("Put a new element in a channel. This method may block.")
    public static void cput(PileChannel chan, Object o) throws InterruptedException {
        chan.put(o);
    }

    @PileDoc("Cancel a running async task. ")
    public static void cancel(Future<?> f) {
        f.cancel(true);
    }

    @RenamedMethod("compute*")
    public static CompletableFuture compute(PCall fn) {
        CompletableFuture cf = new CompletableFuture<>();
        Runnable r = createInvokingRunnable(fn, cf);
        ForkJoinTask<?> task = ForkJoinPool.commonPool().submit(r);
        // fj task can be cancelled directly
        return cf;
    }

    @RenamedMethod("async*")
    public static CompletableFuture<Object> async(PCall fn) {
        CompletableFuture<Object> cf = new CompletableFuture<>();
        Runnable r = createInvokingRunnable(fn, cf);
        Thread t = Thread.startVirtualThread(r);
        handleCancellation(cf, t);
        return cf;
    }

    @PileDoc("Creates a fixed size channel.")
    public static PileChannel channel() {
        return channel(16);
    }

    public static PileChannel channel(int max) {
        return new BufferedChannel(max);
    }

    public static CompletableFuture timeout(int ms) {
        return timeout(ms, TIMEOUT_KW);
    }

    @PileDoc("""
            Returns a task which completes after the provided number of millis. By default returns :timeout.
            """)
    public static CompletableFuture timeout(int ms, Object token) {
        // TODO Virtual thread?
        CompletableFuture r = new CompletableFuture<>();
        ScheduledHolder.EXEC.schedule(() -> r.complete(token), ms, TimeUnit.MILLISECONDS);
        return r;
    }

    @PileDoc("""
            Awaits the completion of 1 or many tasks. Tasks types may be either async calls, channel gets or
            channel puts. Respectively: (await (async (do-compute)) get-channel [put-channel val-to-enqueue]).
            This function returns either the result of the async call, the received value from the channel, or
            the value that was put into the channel. This process is atomic and only one task may succeed.
            Alternatively, this method can accept a single argument mapping from an arbitrary key type to any of the task types.
            The result will be (task-key task-result), which can aid in which task was selected. This method does
            not cancel any unselected results. If the selected task threw an exception then
            this method will also throw the same exception.
            """)
    public static Object await(Object... v) throws Exception {
        CompletableFuture<Object> last = awaitFuture(v);
        try {
            return last.get();
        } catch (ExecutionException e) {
            throw e;
        }
    }

    public static Object await_index(Object... vals) throws Exception {
        CompletableFuture<Object> last = new CompletableFuture<>();
        int i = 0;
        for (Object src : vals) {
            collect(src, i, last);
            ++i;
        }
        try {
            return last.get();
        } catch (ExecutionException e) {
            throw e;
        }
    }

    @PileDoc("""
            Awaits the completion of 1 or many tasks similar to await, however this variant will cancel
            all remaining async tasks. Channel operations are not affected.
            """)
    public static Object await_any(Object... fns) throws Exception {
        PersistentList typed = (PersistentList) await_index(fns);
        int resultIdx = (int) first(typed);
        for (int i = 0; i < fns.length; ++i) {
            if (resultIdx == i) {
                continue;
            }
            Object part = fns[i];
            if (part instanceof Future future) {
                cancel(future);
            }
        }
        return second(typed);


    }

    @PileDoc("""
            Attaches a new callback to a running (async) function which is called with the result of computation.
            If completed exceptionally, this attached task is not run.
            """)
    public static void and_then(CompletableFuture<Object> source, PCall fn) {
        source.thenAcceptAsync(o -> {
            try {
                fn.invoke(o);
            } catch (Throwable e) {
                LOG.warn("Error while running attached stage", e);
            }
        }, Executors.newVirtualThreadPerTaskExecutor());

    }
    
    @RenamedMethod("delay*")
    public static Delay delay_star(PCall fn) {
        return new Delay(fn);
    }

    private static void handleCancellation(CompletableFuture<Object> cf, Thread t) {
        // Intentionally dangling stage here. If the returned future is cancelled (say
        // via await-any) then we want to interrupt the associated virtual thread.
        cf.handle((ignored, ex) -> {
            if (ex instanceof CancellationException cex) {
                t.interrupt();
            }
            return null;
        });
    }

    private static Runnable createInvokingRunnable(PCall fn, CompletableFuture cf) {
        Runnable r = () -> {
            try {
                Object result = fn.invoke();
                cf.complete(result);
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            }
        };
        return r;
    }

    private static CompletableFuture<Object> awaitFuture(Object... vals) {
        CompletableFuture<Object> last = new CompletableFuture<>();
        if (vals.length == 1 && vals[0] instanceof Map map) {
            // (await {:async (async (dosomething)) :chan (cget channel) :timeout (timeout
            // 500)} ... )
            // return a pair (list) of [map-key result]
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) map).entrySet()) {
                collect(entry.getValue(), entry.getKey(), last);
            }
        } else {
            for (var v : vals) {
                // (await (async (docomp)))
                // returns a singular result
                collect(v, last);
            }
        }

        return last;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void collect(Object source, CompletableFuture<Object> stage) {
        switch (source) {
            case null -> throw new PileInvocationException("Cannot await a null source");
            case CompletableFuture f -> {
                ((CompletableFuture<Object>) f).whenComplete((result, ex) -> {
                    if (ex != null) {
                        stage.completeExceptionally(ex);
                    } else {
                        stage.complete(result);
                    }
                });
            }
            case Future f -> {
                Thread.startVirtualThread(() -> {
                    try {
                        Object result = f.get();
                        stage.complete(result);
                    } catch (Throwable t) {
                        stage.completeExceptionally(t);
                    }
                });
            }
            case PileChannel chan -> {
                // channel take
                chan.get(stage::complete);
            }
            case PersistentVector pv -> {
                // channel put
                var chan = (PileChannel) pv.get(0);
                var val = pv.get(1);
                chan.put(() -> stage.complete(val), val);

            }
            default -> throw new PileInvocationException(
                    "Don't know how to use a " + source.getClass() + " as an await source.");
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void collect(Object source, Object key, CompletableFuture<Object> stage) {
        switch (source) {
            case null -> throw new PileInvocationException("Cannot await a null source");
            case CompletableFuture f -> {
                ((CompletableFuture<Object>) f).whenComplete((result, ex) -> {
                    if (result != null) {
                        stage.complete(PersistentList.reversed(key, result));
                    } else if (ex != null) {
                        stage.completeExceptionally(ex);
                    }
                });
            }
            case Future f -> {
                Thread.startVirtualThread(() -> {
                    try {
                        Object result = f.get();
                        stage.complete(PersistentList.reversed(key, result));
                    } catch (Throwable t) {
                        stage.completeExceptionally(t);
                    }
                });
            }
            case PileChannel chan -> {
                chan.get(result -> stage.complete(PersistentList.reversed(key, result)));
            }
            case PersistentVector pv -> {
                // channel put
                var chan = (PileChannel) pv.get(0);
                var val = pv.get(1);
                chan.put(() -> stage.complete(PersistentList.reversed(key, val)), val);
            }
            default -> throw new PileInvocationException(
                    "Don't know how to use a " + source.getClass() + " as an await source.");
        }
    }

}
