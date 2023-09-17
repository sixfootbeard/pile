package pile.core;

import static java.util.Objects.*;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import pile.core.exception.PileExecutionException;
import pile.core.exception.PileInternalException;

public class Coroutine {

    public static final ThreadLocal<CoroutineSync> SYNC_LOCAL = new ThreadLocal<>();

    private final PCall fn;
    private final CoroutineSync sync;

    public Coroutine(CoroutineSync sync, PCall fn) {
        this.fn = fn;
        this.sync = sync;
    }

    public void run() {
        Thread.startVirtualThread(() -> {
            SYNC_LOCAL.set(sync);
            try {
                sync.awaitResume();
                fn.invoke();
            } catch (Throwable e) {
                e.printStackTrace();
                // TODO Better exceptions
            } finally {
                sync.signalEnd();
            }
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
                if (!hasWaiter) {
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

        public Object resume() throws InterruptedException {
            lock.lock();
            try {
                this.shouldResume.signal();
                hasWaiter = true;
                this.awaitingValue.await();
                // TODO maybe call in finally?
                hasWaiter = false;
                // Could be a variety of states now

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
                if (isDone) {
                    return null;
                }
                if (exception != null) {
                    throw new PileExecutionException("Error while executing coroutine", exception);
                }
                // shouldn't happen
                throw new PileInternalException("Error handling coroutine values.");
            } finally {
                lock.unlock();
            }
        }

    }

}
