package pile.core;

import static org.junit.Assert.*;
import static pile.nativebase.NativeCore.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import pile.core.Coroutine.CoroutineSync;
import pile.core.exception.PileExecutionException;
import pile.nativebase.NativeCore;

public class CoroutineTest {

    @Rule
    public Timeout maxTime = new Timeout(5000);

    private CoroutineSync sync;
    private AtomicInteger calledCount;

    @Test
    public void test() throws InterruptedException {
        var c = newCoroutine();
        sleep(1000);
        assertEquals(0, calledCount.get());
        assertEquals(1, resume(c));
        assertEquals(null, resume(c));
    }
    
    @Test
    public void testThrows() throws InterruptedException {
        var c = newCoroutine(() -> { throw new RuntimeException();});
        try {
            resume(c);
            fail("Should throw");
        } catch (PileExecutionException e) {
            assertEquals(RuntimeException.class, e.getCause().getClass());
        }        
    }

    @Test
    public void testRaceResume() throws InterruptedException {
        // Testing to make sure race condition which resume beats a call to the first
        // coroutine sleep (within run).
        var c = newStoppedCoroutine();
        Thread.startVirtualThread(() -> {
            try {
                sleep(1000);
                c.run();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals(1, resume(c));
        assertEquals(null, resume(c));
    }

    public Coroutine newStoppedCoroutine() {
        this.sync = new CoroutineSync();
        this.calledCount = new AtomicInteger();

        Coroutine c = new Coroutine(sync, new PCall() {
            @Override
            public Object invoke(Object... args) throws Throwable {
                NativeCore.yield(calledCount.incrementAndGet());
                return null;
            }
        });
        return c;
    }

    public Coroutine newCoroutine() {
        this.sync = new CoroutineSync();
        this.calledCount = new AtomicInteger();

        Coroutine c = new Coroutine(sync, new PCall() {
            @Override
            public Object invoke(Object... args) throws Throwable {
                NativeCore.yield(calledCount.incrementAndGet());
                return null;
            }
        });
        c.run();
        return c;
    }
    
    public Coroutine newCoroutine(Runnable r) {
        this.sync = new CoroutineSync();
        this.calledCount = new AtomicInteger();

        Coroutine c = new Coroutine(sync, new PCall() {
            @Override
            public Object invoke(Object... args) throws Throwable {
                r.run();
                return null;
            }
        });
        c.run();
        return c;
    }

}
