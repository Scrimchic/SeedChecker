package com.scrimchic.seedchecker.worldgen;

import java.util.HashSet;
import java.util.Set;

import com.scrimchic.seedchecker.core.util.BoundedLruCache;
import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;

/**
 * The bookkeeping behind on-demand structure geometry: what has been assembled, what is being
 * assembled, and which answers are still wanted.
 *
 * <p>The same shape as {@link StructureValidationStore}, and keyed the same way. A
 * {@link StructureValidationKey} already names the world, seed, dimension, Minecraft version,
 * structure type and candidate chunk, so geometry for an old seed or another world is never found
 * under a new one; and the generation counter drops work that finishes after the map changed.
 *
 * <p>Small on purpose. Geometry is asked for one selected structure at a time, and a village can
 * take seconds, so the queue holds two jobs and the cache a few dozen answers.
 */
public final class StructureGeometryStore {

    /** Returned by {@link #claim} when a job should not be started. */
    public static final int NO_JOB = -1;

    private final BoundedLruCache<StructureValidationKey, StructureGeometry> cache;
    private final Set<StructureValidationKey> pending = new HashSet<StructureValidationKey>();
    private final int maxPending;

    private int generation;
    private BiomeMapKey activeMap;
    private int discarded;

    public StructureGeometryStore(int maxCachedResults, int maxPending) {
        if (maxPending <= 0) {
            throw new IllegalArgumentException("maxPending must be positive, was " + maxPending);
        }
        this.cache = new BoundedLruCache<StructureValidationKey, StructureGeometry>(maxCachedResults);
        this.maxPending = maxPending;
    }

    /**
     * Declares which map geometry is wanted for.
     *
     * @return whether it is a different map, in which case everything known so far was dropped
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

    /** @return the geometry, or {@code null} while it is not known. Never blocks. */
    public synchronized StructureGeometry resultIfReady(StructureValidationKey key) {
        return cache.get(key);
    }

    /**
     * Reserves a structure for assembly.
     *
     * @return the generation the job must run under, or {@link #NO_JOB} when it is already known,
     *         already queued, belongs to another map, or the queue is full
     */
    public synchronized int claim(StructureValidationKey key) {
        if (!key.map().equals(activeMap) || cache.containsKey(key) || pending.contains(key)
                || pending.size() >= maxPending) {
            return NO_JOB;
        }
        pending.add(key);
        return generation;
    }

    /** Releases a reservation. Must be called for every successful {@link #claim}. */
    public synchronized void release(StructureValidationKey key) {
        pending.remove(key);
    }

    public synchronized boolean isStale(int jobGeneration) {
        return jobGeneration != generation;
    }

    /**
     * Files geometry, unless the map has changed since the job started.
     *
     * @return whether it was accepted
     */
    public synchronized boolean store(StructureValidationKey key, StructureGeometry geometry,
                                      int jobGeneration) {
        if (jobGeneration != generation) {
            discarded++;
            return false;
        }
        cache.put(key, geometry);
        return true;
    }

    public synchronized int cachedResults() {
        return cache.size();
    }

    public synchronized int pendingResults() {
        return pending.size();
    }

    /** Results that finished after the map changed and were thrown away. */
    public synchronized int discarded() {
        return discarded;
    }
}
