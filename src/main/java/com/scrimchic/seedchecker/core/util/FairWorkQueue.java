package com.scrimchic.seedchecker.core.util;

import java.util.ArrayDeque;

/**
 * A work queue with several lanes that workers take from in turn.
 *
 * <p>Made for the one worker pool that both biome tiles and structure checks run on. With a single
 * first-in-first-out queue, the slower kind of job decides how long the other waits: measured on a
 * screenful at 16 blocks per pixel, a biome tile queued behind a full queue of exact village checks
 * waited around 350 ms, against about 85 ms before those checks existed. Lanes served in rotation
 * bound that wait by one job of the other kind per worker instead of a whole queue of them, in both
 * directions, so neither kind can starve the other.
 *
 * <p>Within a lane, first in first out. All methods are synchronised; a critical section is a
 * handful of deque operations.
 */
public final class FairWorkQueue {

    private final ArrayDeque<Runnable>[] lanes;

    /** The lane the next take looks at first. */
    private int nextLane;

    private boolean closed;

    @SuppressWarnings("unchecked")
    public FairWorkQueue(int laneCount) {
        if (laneCount <= 0) {
            throw new IllegalArgumentException("laneCount must be positive, was " + laneCount);
        }
        lanes = new ArrayDeque[laneCount];
        for (int i = 0; i < laneCount; i++) {
            lanes[i] = new ArrayDeque<Runnable>();
        }
    }

    /**
     * Queues a task.
     *
     * @return {@code false} once the queue is closed, in which case nothing was queued
     */
    public synchronized boolean offer(int lane, Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task is required");
        }
        if (closed) {
            return false;
        }
        lanes[lane].addLast(task);
        notify();
        return true;
    }

    /**
     * Waits for a task, taking from the lanes in rotation and skipping empty ones.
     *
     * @return the task, or {@code null} once the queue is closed
     * @throws InterruptedException when the waiting worker is interrupted
     */
    public synchronized Runnable take() throws InterruptedException {
        while (!closed) {
            for (int step = 0; step < lanes.length; step++) {
                int lane = (nextLane + step) % lanes.length;
                Runnable task = lanes[lane].pollFirst();
                if (task != null) {
                    nextLane = (lane + 1) % lanes.length;
                    return task;
                }
            }
            wait();
        }
        return null;
    }

    /** @return how many tasks are waiting in that lane. */
    public synchronized int pending(int lane) {
        return lanes[lane].size();
    }

    /** Refuses new tasks, drops queued ones, and releases every waiting worker. */
    public synchronized void close() {
        closed = true;
        for (ArrayDeque<Runnable> lane : lanes) {
            lane.clear();
        }
        notifyAll();
    }
}
