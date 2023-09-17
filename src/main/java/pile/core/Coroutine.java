package pile.core;

import static java.util.Objects.*;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import pile.core.exception.PileExecutionException;
import pile.core.exception.PileInternalException;

import jdk.incubator.concurrent.ScopedValue;

public class Coroutine {

    public static final ScopedValue<CoroutineSync> SYNC_LOCAL = ScopedValue.newInstance();

    private final PCall fn;
    private final CoroutineSync sync;

    public Coroutine(CoroutineSync sync, PCall fn) {
        this.fn = fn;
        this.sync = sync;
    }

    /**
     * Starts the corountine on a virtual thread. Initially it will be parked until
     * the first call to {@link #resume()}.
     */
    public void run() {
        Thread.startVirtualThread(() -> {
            ScopedValue.where(SYNC_LOCAL, sync)
                .run(() -> {
                    try {
                        sync.awaitResume();
                        fn.invoke();
                    } catch (Throwable e) {
                        sync.putException(e);
                    } finally {
                        sync.signalEnd();
                    }
                });
        });
    }

    /**
     * Resume execution in a coroutine. Called from the thread holding the
     * coroutine.
     * 
     * @return
     * @throws InterruptedException
     */
    public Object resume() throws InterruptedException {
        return sync.resume();
    }

    /**
     * 
     * Waiter thread: Calls {@link #resume()} until nil is returned.<br>
     * <br>
     * 
     * Coroutine thread: Initially calls {@link #awaitResume()}. Then calls
     * {@link #putValueAndSleep(Object)}/{@link #putException(Throwable)} any number
     * of times. Eventually calls {@link #signalEnd()} when corountine is complete.
     * 
     *
     */
    public static final class CoroutineSync {

        private final ReentrantLock lock;
        private final Condition shouldResume;
        private final Condition awaitingValue;

        private Throwable exception;
        private Object resumedValue;
        private boolean isDone = false;
        
        /**
         * Seems redundant but there's a race condition. If the first resume beats the
         * virtual thread calling #awaitResume the first time then the virt thread will
         * miss the signal on shouldResume and will block forever. The first call should
         * only block if there's no waiters.
         */
        private boolean hasWaiter = false;

        public CoroutineSync() {
            this.lock = new ReentrantLock();
            this.awaitingValue = lock.newCondition();
            this.shouldResume = lock.newCondition();
        }

        public void awaitResume() throws InterruptedException {
            lock.lock();
            try {
                if (isDone) {
                    throw new IllegalStateException("Cannot await a completed couroutine");
                }
                if (! hasWaiter) {
                    this.shouldResume.await();
                }
            } finally {
                lock.unlock();
            }
        }

        public void putValueAndSleep(Object val) throws InterruptedException {
            requireNonNull(val, "Coroutine value cannot be null");
            lock.lock();
            try {
                this.resumedValue = val;
                this.awaitingValue.signal();
                this.shouldResume.await();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Put exception and set that coroutine is done.
         * 
         * @param t
         */
        public void putException(Throwable t) {
            lock.lock();
            try {
                isDone = true;
                this.exception = t;
                this.awaitingValue.signal();
            } finally {
                lock.unlock();
            }
        }

        public void signalEnd() {
            lock.lock();
            try {
                isDone = true;
                this.awaitingValue.signal();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Waiter thread method to retrieve values. May block until coroutine has
         * produced a value.
         * 
         * @return The next object that the coroutine yielded, or nil if the coroutine
         *         has no values left and has exited.
         * @throws InterruptedException   If interrupted while waiting.
         * @throws PileExecutionException If the coroutine threw an uncaught exception.
         */
        public Object resume() throws InterruptedException {
            lock.lock();
            try {
                this.shouldResume.signal();
                hasWaiter = true;
                try {
                    this.awaitingValue.await();
                } finally {
                    hasWaiter = false;
                }

                // Could be a variety of states at the end

                // resumedValue nonNull, isDone = false
                // coroutine thread put a value

                // resumedValue nonNull, isDone = true
                // coroutine thread put last value and exited

                // resumedValue null, isDone = true
                // coroutine exited with no more values

                // resumedValue null, exception nonNull, isDone = true
                // exception thrown

                if (resumedValue != null) {
                    var local = resumedValue;
                    resumedValue = null;
                    return local;
                }
                
                if (exception != null) {
                    throw new PileExecutionException("Error while executing coroutine", exception);
                }

                if (isDone) {
                    return null;
                }
                // shouldn't happen
                throw new PileInternalException("Error handling coroutine values.");
            } finally {
                lock.unlock();
            }
        }

    }

}
