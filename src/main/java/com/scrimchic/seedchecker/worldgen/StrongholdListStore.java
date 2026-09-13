package com.scrimchic.seedchecker.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;

/**
 * The stronghold list of the map being looked at, and whether it is being computed.
 *
 * <p>One list per map - world, seed, dimension and Minecraft version, which is what a
 * {@link BiomeMapKey} names - computed at most once and held in memory. A different map drops it,
 * and a job that finishes for the old map is thrown away, so a stronghold of the previous seed can
 * never be drawn.
 *
 * <p>Unlike the other stores this one holds one answer rather than many: vanilla places all the
 * strongholds of a world in a single pass, so they are computed as one job.
 */
public final class StrongholdListStore {

    /** Returned by {@link #claim} when no job should be started. */
    public static final int NO_JOB = -1;

    private BiomeMapKey activeMap;
    private int generation;
    private boolean pending;
    private List<StrongholdPosition> positions;
    private Map<Long, StrongholdPosition> byChunk;
    private String failure;
    private long millis = -1L;

    /**
     * Declares which map is being drawn.
     *
     * @return whether it is a different map, in which case the old list was dropped
     */
    public synchronized boolean useMap(BiomeMapKey map) {
        if (map == null || map.equals(activeMap)) {
            return false;
        }
        activeMap = map;
        generation++;
        pending = false;
        positions = null;
        byChunk = null;
        failure = null;
        millis = -1L;
        return true;
    }

    /**
     * Reserves the computation of that map's list.
     *
     * @return the generation the job runs under, or {@link #NO_JOB} when the map is not the one being
     *         drawn, or the list is already known, being computed, or failed
     */
    public synchronized int claim(BiomeMapKey map) {
        if (map == null || !map.equals(activeMap) || pending || positions != null
                || failure != null) {
            return NO_JOB;
        }
        pending = true;
        return generation;
    }

    /** Ends a job's reservation; a job from an older map changes nothing. */
    public synchronized void release(int jobGeneration) {
        if (jobGeneration == generation) {
            pending = false;
        }
    }

    public synchronized boolean isStale(int jobGeneration) {
        return jobGeneration != generation;
    }

    /** @return whether the list was accepted, which it is not after a map change. */
    public synchronized boolean store(int jobGeneration, List<StrongholdPosition> computed,
                                     long elapsedMillis) {
        if (jobGeneration != generation) {
            return false;
        }
        positions = Collections.unmodifiableList(new ArrayList<StrongholdPosition>(computed));
        byChunk = new HashMap<Long, StrongholdPosition>(computed.size() * 2);
        for (StrongholdPosition position : computed) {
            byChunk.put(chunkKey(position.chunkX(), position.chunkZ()), position);
        }
        millis = elapsedMillis;
        return true;
    }

    /** Remembers that the list could not be computed, so it is not retried every frame. */
    public synchronized boolean storeFailure(int jobGeneration, String reason) {
        if (jobGeneration != generation) {
            return false;
        }
        failure = reason;
        return true;
    }

    /** @return the list for that map in vanilla's order, or {@code null} while it is not known. */
    public synchronized List<StrongholdPosition> positionsIfReady(BiomeMapKey map) {
        return map != null && map.equals(activeMap) ? positions : null;
    }

    /** @return the stronghold starting in that chunk of that map, or {@code null}. */
    public synchronized StrongholdPosition positionAt(BiomeMapKey map, int chunkX, int chunkZ) {
        if (byChunk == null || map == null || !map.equals(activeMap)) {
            return null;
        }
        return byChunk.get(chunkKey(chunkX, chunkZ));
    }

    public synchronized boolean isPending() {
        return pending;
    }

    public synchronized String failure() {
        return failure;
    }

    /** How long the list took, or {@code -1} before it is known. */
    public synchronized long millis() {
        return millis;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }
}
