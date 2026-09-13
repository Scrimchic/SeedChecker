package com.scrimchic.seedchecker.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class LazyInitTest {

    private final AtomicInteger closed = new AtomicInteger();

    private LazyInit<String> newInit() {
        return new LazyInit<String>(new LazyInit.Closer<String>() {
            @Override
            public void close(String value) {
                closed.incrementAndGet();
            }
        });
    }

    @Test
    void nothingIsLoadedUntilAsked() {
        LazyInit<String> init = newInit();
        assertEquals(LazyInit.State.UNINITIALIZED, init.state());
        assertNull(init.getIfReady());
    }

    @Test
    void theFirstCallerLoadsAndLaterCallersReuse() {
        LazyInit<String> init = newInit();
        final AtomicInteger loads = new AtomicInteger();
        LazyInit.Loader<String> loader = new LazyInit.Loader<String>() {
            @Override
            public String load() {
                loads.incrementAndGet();
                return "data";
            }
        };

        assertEquals(LazyInit.State.READY, init.initializeIfNeeded(loader));
        assertEquals(LazyInit.State.READY, init.initializeIfNeeded(loader));
        assertEquals("data", init.getIfReady());
        assertEquals(1, loads.get());
    }

    @Test
    void aConcurrentCallerNeitherWaitsNorLoadsAgain() throws Exception {
        final LazyInit<String> init = newInit();
        final CountDownLatch loading = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger loads = new AtomicInteger();
        final LazyInit.Loader<String> slow = new LazyInit.Loader<String>() {
            @Override
            public String load() throws Exception {
                loads.incrementAndGet();
                loading.countDown();
                release.await(10, TimeUnit.SECONDS);
                return "data";
            }
        };

        Thread first = new Thread(new Runnable() {
            @Override
            public void run() {
                init.initializeIfNeeded(slow);
            }
        });
        first.start();
        assertTrue(loading.await(10, TimeUnit.SECONDS));

        // The render thread's view, and a second worker's: an answer at once, no second load.
        long start = System.nanoTime();
        assertEquals(LazyInit.State.INITIALIZING, init.initializeIfNeeded(slow));
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1), "a caller waited");
        assertNull(init.getIfReady());

        release.countDown();
        first.join(10_000);
        assertEquals(LazyInit.State.READY, init.state());
        assertEquals(1, loads.get());
    }

    @Test
    void aFailureIsStickyAndNeverRetried() {
        LazyInit<String> init = newInit();
        final AtomicInteger loads = new AtomicInteger();
        LazyInit.Loader<String> broken = new LazyInit.Loader<String>() {
            @Override
            public String load() {
                loads.incrementAndGet();
                throw new IllegalStateException("no data pack");
            }
        };

        for (int frame = 0; frame < 100; frame++) {
            assertEquals(LazyInit.State.FAILED, init.initializeIfNeeded(broken));
        }
        assertEquals(1, loads.get());
        assertTrue(init.failure() instanceof IllegalStateException);
        assertNull(init.getIfReady());
    }

    @Test
    void closingReleasesTheValueOnceAndPreventsReloading() {
        LazyInit<String> init = newInit();
        final AtomicInteger loads = new AtomicInteger();
        LazyInit.Loader<String> loader = new LazyInit.Loader<String>() {
            @Override
            public String load() {
                loads.incrementAndGet();
                return "data";
            }
        };
        init.initializeIfNeeded(loader);

        init.close();
        init.close();

        assertEquals(1, closed.get());
        assertNull(init.getIfReady());
        assertEquals(LazyInit.State.FAILED, init.initializeIfNeeded(loader));
        assertEquals(1, loads.get());
    }

    @Test
    void closingDuringALoadReleasesTheValueWhenTheLoadFinishes() throws Exception {
        final LazyInit<String> init = newInit();
        final CountDownLatch loading = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicReference<LazyInit.State> result = new AtomicReference<LazyInit.State>();

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                result.set(init.initializeIfNeeded(new LazyInit.Loader<String>() {
                    @Override
                    public String load() throws Exception {
                        loading.countDown();
                        release.await(10, TimeUnit.SECONDS);
                        return "data";
                    }
                }));
            }
        });
        worker.start();
        assertTrue(loading.await(10, TimeUnit.SECONDS));

        init.close();
        assertEquals(0, closed.get(), "nothing to release until the load returns");
        release.countDown();
        worker.join(10_000);

        assertEquals(1, closed.get());
        assertSame(LazyInit.State.FAILED, result.get());
        assertNull(init.getIfReady());
    }
}
