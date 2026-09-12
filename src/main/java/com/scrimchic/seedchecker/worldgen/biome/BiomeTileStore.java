package com.scrimchic.seedchecker.worldgen.biome;

import java.util.HashSet;
import java.util.Set;

import com.scrimchic.seedchecker.core.util.BoundedLruCache;

/**
 * The bookkeeping behind asynchronous biome tiles: what is cached, what is in flight, and which
 * results are still wanted.
 *
 * <p>Separate from the executor on purpose. Everything that decides correctness - deduplicating
 * requests, bounding the queue, evicting old tiles, and above all rejecting results for a world
 * the player has since left - lives here, with no Minecraft and no threads, so it can be tested
 * directly.
 *
 * <p>Two mechanisms guard against showing a stale tile, and they cover different things:
 *
 * <ul>
 *   <li>{@link BiomeTileKey} carries the seed, world, dimension, version and sample height, so a
 *       tile generated for an old seed is simply never found under the new key. This is what makes
 *       a wrong tile impossible to display.</li>
 *   <li>A generation counter, bumped by {@link #useMap}, marks work in flight as unwanted. This is
 *       what keeps a job that was already running from filling the cache with tiles nobody asked
 *       for any more.</li>
 * </ul>
 *
 * <p>All methods are synchronised; the render thread and the workers share one instance.
 */
public final class BiomeTileStore {

    /** Returned by {@link #claim} when a job should not be started. */
    public static final int NO_JOB = -1;

    private final BoundedLruCache<BiomeTileKey, BiomeTile> cache;
    private final Set<BiomeTileKey> pending = new HashSet<BiomeTileKey>();
    private final int maxPending;

    private int generation;
    private BiomeMapKey activeMap;

    private long lastGenerationNanos;
    private long totalGenerationNanos;
    private int completedTiles;
    private int failedTiles;
    private int rejectedTiles;

    public BiomeTileStore(int maxCachedTiles, int maxPending) {
        if (maxPending <= 0) {
            throw new IllegalArgumentException("maxPending must be positive, was " + maxPending);
        }
        this.cache = new BoundedLruCache<BiomeTileKey, BiomeTile>(maxCachedTiles);
        this.maxPending = maxPending;
    }

    /**
     * Declares which biome map is being drawn now.
     *
     * @return whether this is a different map than before, in which case the cache was dropped
     *         and everything in flight was invalidated
     */
    public synchronized boolean useMap(BiomeMapKey map) {
        if (map == null || map.equals(activeMap)) {
            return false;
        }
        activeMap = map;
        generation++;
        cache.clear();
        pending.clear();
        return true;
    }

    public synchronized BiomeMapKey activeMap() {
        return activeMap;
    }

    public synchronized int generation() {
        return generation;
    }

    /** @return the finished tile, or {@code null}. Counts as a use for eviction purposes. */
    public synchronized BiomeTile tileIfReady(BiomeTileKey key) {
        return cache.get(key);
    }

    /**
     * Reserves a tile for generation.
     *
     * @return the generation the job must run under, or {@link #NO_JOB} when the tile is already
     *         cached, already queued, or the queue is full
     */
    public synchronized int claim(BiomeTileKey key) {
        if (cache.containsKey(key) || pending.contains(key) || pending.size() >= maxPending) {
            return NO_JOB;
        }
        pending.add(key);
        return generation;
    }

    /** Releases a reservation. Must be called for every successful {@link #claim}. */
    public synchronized void release(BiomeTileKey key) {
        pending.remove(key);
    }

    public synchronized boolean isStale(int jobGeneration) {
        return jobGeneration != generation;
    }

    /**
     * Files a finished tile, unless the world has changed since the job started.
     *
     * @return whether the tile was accepted
     */
    public synchronized boolean store(BiomeTileKey key, BiomeTile tile, int jobGeneration,
                                      long elapsedNanos) {
        if (jobGeneration != generation) {
            rejectedTiles++;
            return false;
        }
        cache.put(key, tile);
        lastGenerationNanos = elapsedNanos;
        totalGenerationNanos += elapsedNanos;
        completedTiles++;
        return true;
    }

    public synchronized void recordFailure() {
        failedTiles++;
    }

    public synchronized Metrics metrics() {
        double average = completedTiles == 0 ? 0.0 : totalGenerationNanos / 1e6 / completedTiles;
        return new Metrics(cache.size(), pending.size(), completedTiles, failedTiles,
                rejectedTiles, lastGenerationNanos / 1e6, average);
    }

    /** A snapshot of how the tile engine is doing, for the prototype debug panel. */
    public static final class Metrics {

        private final int cachedTiles;
        private final int pendingTiles;
        private final int completedTiles;
        private final int failedTiles;
        private final int rejectedTiles;
        private final double lastMillis;
        private final double averageMillis;

        Metrics(int cachedTiles, int pendingTiles, int completedTiles, int failedTiles,
                int rejectedTiles, double lastMillis, double averageMillis) {
            this.cachedTiles = cachedTiles;
            this.pendingTiles = pendingTiles;
            this.completedTiles = completedTiles;
            this.failedTiles = failedTiles;
            this.rejectedTiles = rejectedTiles;
            this.lastMillis = lastMillis;
            this.averageMillis = averageMillis;
        }

        public int cachedTiles() {
            return cachedTiles;
        }

        public int pendingTiles() {
            return pendingTiles;
        }

        public int completedTiles() {
            return completedTiles;
        }

        public int failedTiles() {
            return failedTiles;
        }

        /** Tiles that finished after the world changed and were thrown away. */
        public int rejectedTiles() {
            return rejectedTiles;
        }

        public double lastMillis() {
            return lastMillis;
        }

        public double averageMillis() {
            return averageMillis;
        }
    }
}
