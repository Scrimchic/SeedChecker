package com.scrimchic.seedchecker.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class FairWorkQueueTest {

    private static final int TILES = 0;
    private static final int CHECKS = 1;

    private static Runnable named(final String name, final StringBuilder log) {
        return new Runnable() {
            @Override
            public void run() {
                log.append(name);
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    @Test
    void busyLanesAreServedInTurn() throws Exception {
        FairWorkQueue queue = new FairWorkQueue(2);
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            queue.offer(CHECKS, named("c", log));
        }
        for (int i = 0; i < 3; i++) {
            queue.offer(TILES, named("t", log));
        }
        for (int i = 0; i < 6; i++) {
            queue.take().run();
        }
        assertEquals("tctctc", log.toString());
    }

    @Test
    void aLateTaskIsNotStuckBehindAWholeQueueOfTheOtherKind() throws Exception {
        // The property the pool needs: a biome tile arriving after 200 queued structure checks is
        // taken within two takes, not after all 200.
        FairWorkQueue queue = new FairWorkQueue(2);
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            queue.offer(CHECKS, named("c", log));
        }
        queue.take().run();
        Runnable tile = named("t", log);
        queue.offer(TILES, tile);

        int takes = 0;
        Runnable next;
        do {
            next = queue.take();
            takes++;
        } while (next != tile);
        assertTrue(takes <= 2, "the tile waited " + takes + " takes");
        assertEquals(199, queue.pending(CHECKS));
    }

    @Test
    void aLoneLaneIsDrainedInOrder() throws Exception {
        FairWorkQueue queue = new FairWorkQueue(2);
        StringBuilder log = new StringBuilder();
        queue.offer(CHECKS, named("1", log));
        queue.offer(CHECKS, named("2", log));
        queue.offer(CHECKS, named("3", log));
        for (int i = 0; i < 3; i++) {
            queue.take().run();
        }
        assertEquals("123", log.toString());
    }

    @Test
    void aWaitingWorkerWakesForNewWork() throws Exception {
        final FairWorkQueue queue = new FairWorkQueue(2);
        final AtomicReference<Runnable> taken = new AtomicReference<Runnable>();
        final CountDownLatch done = new CountDownLatch(1);
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    taken.set(queue.take());
                } catch (InterruptedException ignored) {
                    // Fails the assertion below.
                }
                done.countDown();
            }
        });
        worker.start();
        Thread.sleep(50);
        Runnable task = named("t", new StringBuilder());
        queue.offer(TILES, task);

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertSame(task, taken.get());
    }

    @Test
    void closingReleasesWorkersAndRefusesWork() throws Exception {
        final FairWorkQueue queue = new FairWorkQueue(2);
        queue.offer(CHECKS, named("dropped", new StringBuilder()));
        final CountDownLatch released = new CountDownLatch(1);
        final AtomicReference<Runnable> taken = new AtomicReference<Runnable>(named("x", new StringBuilder()));

        queue.close();
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    taken.set(queue.take());
                } catch (InterruptedException ignored) {
                    // Fails the assertion below.
                }
                released.countDown();
            }
        });
        worker.start();

        assertTrue(released.await(5, TimeUnit.SECONDS));
        assertNull(taken.get(), "a closed queue hands out nothing, queued work included");
        assertFalse(queue.offer(TILES, named("late", new StringBuilder())));
        assertEquals(0, queue.pending(CHECKS));
    }
}
