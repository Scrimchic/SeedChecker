package com.scrimchic.seedchecker.worldgen;

import java.util.HashSet;
import java.util.Set;

import com.scrimchic.seedchecker.core.util.BoundedLruCache;
import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;

/**
 * The bookkeeping behind asynchronous structure validation: what has been decided, what is being
 * decided, and which answers are still wanted.
 *
 * <p>Same shape as {@code BiomeTileStore} and deliberately a separate class rather than a shared
 * generic one: the two are about 120 lines each and obvious on their own, where a store
 * parameterised over session key, item key and value would be harder to read than both put
 * together.
 *
 * <p>Two guards against a stale answer, covering different things: {@link StructureValidationKey}
 * carries the world, seed, dimension and version, so a result for an old seed is never found under
 * a new one; and a generation counter marks work already in flight as unwanted.
 *
 * <p>All methods are synchronised, and every critical section is a handful of map operations -
 * validation itself never runs while the lock is held.
 */
public final class StructureValidationStore {

    /** Returned by {@link #claim} when a job should not be started. */
    public static final int NO_JOB = -1;

    private final BoundedLruCache<StructureValidationKey, StructureValidation> cache;
    private final Set<StructureValidationKey> pending = new HashSet<StructureValidationKey>();

    /**
     * Structure types whose check found the data it needs still loading.
     *
     * <p>Held back from {@link #claim} until {@link #resumeWaiting}, so that nearly a second of
     * loading does not turn into a queue full of jobs that each immediately give up.
     */
    private final Set<StructureType> waitingTypes = new HashSet<StructureType>();

    private final int maxPending;

    private int generation;
    private BiomeMapKey activeMap;

    private long totalNanos;
    private int accepted;
    private int rejected;
    private int undecided;
    private int failed;
    private int discarded;
    private int deferred;

    public StructureValidationStore(int maxCachedResults, int maxPending) {
        if (maxPending <= 0) {
            throw new IllegalArgumentException("maxPending must be positive, was " + maxPending);
        }
        this.cache = new BoundedLruCache<StructureValidationKey, StructureValidation>(maxCachedResults);
        this.maxPending = maxPending;
    }

    /**
     * Declares which biome map validation is running against.
     *
     * @return whether this is a different map, in which case everything decided so far was dropped
     */
    public synchronized boolean useMap(BiomeMapKey map) {
        if (map == null || map.equals(activeMap)) {
            return false;
        }
        activeMap = map;
        generation++;
        cache.clear();
        pending.clear();
        waitingTypes.clear();
        return true;
    }

    /** @return the decision, or {@code null} while it is unknown. Never blocks. */
    public synchronized StructureValidation resultIfReady(StructureValidationKey key) {
        return cache.get(key);
    }

    /**
     * Reserves a candidate for validation.
     *
     * @return the generation the job must run under, or {@link #NO_JOB} when it is already decided,
     *         already queued, or the queue is full
     */
    public synchronized int claim(StructureValidationKey key) {
        if (cache.containsKey(key) || pending.contains(key) || pending.size() >= maxPending
                || waitingTypes.contains(key.type())) {
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
     * Files a decision, unless the world has changed since the job started.
     *
     * @return whether the decision was accepted into the cache
     */
    public synchronized boolean store(StructureValidationKey key, StructureValidation validation,
                                      int jobGeneration, long elapsedNanos) {
        if (jobGeneration != generation) {
            discarded++;
            return false;
        }
        cache.put(key, validation);
        totalNanos += elapsedNanos;
        if (validation.isRejected()) {
            rejected++;
        } else if (validation.isCompatible()) {
            accepted++;
        } else {
            undecided++;
        }
        return true;
    }

    public synchronized void recordFailure() {
        failed++;
    }

    /**
     * Records that a job gave up because the data its check needs is still loading. Nothing is
     * filed, and the type is held back from {@link #claim} until {@link #resumeWaiting}.
     *
     * @return whether the job still belonged to the current map
     */
    public synchronized boolean markWaiting(StructureType type, int jobGeneration) {
        if (jobGeneration != generation) {
            return false;
        }
        deferred++;
        waitingTypes.add(type);
        return true;
    }

    /** @return whether candidates of this type are being held back until data finishes loading. */
    public synchronized boolean isWaiting(StructureType type) {
        return waitingTypes.contains(type);
    }

    /** Lets every held-back type be claimed again. */
    public synchronized void resumeWaiting() {
        waitingTypes.clear();
    }

    public synchronized Metrics metrics() {
        int answered = accepted + rejected + undecided;
        double average = answered == 0 ? 0.0 : totalNanos / 1e6 / answered;
        return new Metrics(cache.size(), pending.size(), accepted, rejected, undecided, failed,
                discarded, deferred, waitingTypes.size(), average);
    }

    /** A snapshot for the prototype debug panel. */
    public static final class Metrics {

        private final int cachedResults;
        private final int pendingResults;
        private final int accepted;
        private final int rejected;
        private final int undecided;
        private final int failed;
        private final int discarded;
        private final int deferred;
        private final int waitingTypes;
        private final double averageMillis;

        Metrics(int cachedResults, int pendingResults, int accepted, int rejected, int undecided,
                int failed, int discarded, int deferred, int waitingTypes, double averageMillis) {
            this.cachedResults = cachedResults;
            this.pendingResults = pendingResults;
            this.accepted = accepted;
            this.rejected = rejected;
            this.undecided = undecided;
            this.failed = failed;
            this.discarded = discarded;
            this.deferred = deferred;
            this.waitingTypes = waitingTypes;
            this.averageMillis = averageMillis;
        }

        public int cachedResults() {
            return cachedResults;
        }

        public int pendingResults() {
            return pendingResults;
        }

        public int accepted() {
            return accepted;
        }

        public int rejected() {
            return rejected;
        }

        /** Candidates the check ran on and could not decide, so they stay on the map. */
        public int undecided() {
            return undecided;
        }

        public int failed() {
            return failed;
        }

        /** Decisions that finished after the world changed and were thrown away. */
        public int discarded() {
            return discarded;
        }

        /** Jobs that found the data their check needs still loading, and gave up to retry later. */
        public int deferred() {
            return deferred;
        }

        /** Structure types currently held back until that data has loaded. */
        public int waitingTypes() {
            return waitingTypes;
        }

        public double averageMillis() {
            return averageMillis;
        }
    }
}
