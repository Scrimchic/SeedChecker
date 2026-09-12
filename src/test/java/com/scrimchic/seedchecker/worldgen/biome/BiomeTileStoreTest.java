package com.scrimchic.seedchecker.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BiomeTileStoreTest {

    private static final String WORLD = "world-a";
    private static final long SEED = -7407337299659424542L;
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String VERSION = "1.20.1";

    private static BiomeMapKey mapWithSeed(long seed) {
        return new BiomeMapKey(WORLD, seed, OVERWORLD, VERSION, 64);
    }

    private static BiomeTile tileFor(BiomeTileKey key, String biomeId) {
        BiomeTile.Builder builder = BiomeTile.builder(key);
        for (int z = 0; z < BiomeTileGrid.SAMPLES_PER_SIDE; z++) {
            for (int x = 0; x < BiomeTileGrid.SAMPLES_PER_SIDE; x++) {
                builder.set(x, z, biomeId);
            }
        }
        return builder.build();
    }

    @Test
    void aClaimedTileIsGeneratedOnceAndThenCached() {
        BiomeTileStore store = new BiomeTileStore(16, 8);
        BiomeMapKey map = mapWithSeed(SEED);
        store.useMap(map);
        BiomeTileKey key = new BiomeTileKey(map, 4, 0, 0);

        int generation = store.claim(key);
        assertNotEquals(BiomeTileStore.NO_JOB, generation);

        // Already in flight, so a second request must not start another job.
        assertEquals(BiomeTileStore.NO_JOB, store.claim(key));

        assertTrue(store.store(key, tileFor(key, "minecraft:plains"), generation, 1_000_000L));
        store.release(key);

        assertNotNull(store.tileIfReady(key));
        // Cached now, so still no new job.
        assertEquals(BiomeTileStore.NO_JOB, store.claim(key));
    }

    @Test
    void theQueueIsBounded() {
        BiomeTileStore store = new BiomeTileStore(64, 3);
        BiomeMapKey map = mapWithSeed(SEED);
        store.useMap(map);

        assertNotEquals(BiomeTileStore.NO_JOB, store.claim(new BiomeTileKey(map, 4, 0, 0)));
        assertNotEquals(BiomeTileStore.NO_JOB, store.claim(new BiomeTileKey(map, 4, 1, 0)));
        assertNotEquals(BiomeTileStore.NO_JOB, store.claim(new BiomeTileKey(map, 4, 2, 0)));
        assertEquals(BiomeTileStore.NO_JOB, store.claim(new BiomeTileKey(map, 4, 3, 0)));

        store.release(new BiomeTileKey(map, 4, 0, 0));
        assertNotEquals(BiomeTileStore.NO_JOB, store.claim(new BiomeTileKey(map, 4, 3, 0)));
    }

    @Test
    void changingTheSeedInvalidatesEverything() {
        BiomeTileStore store = new BiomeTileStore(16, 8);
        BiomeMapKey before = mapWithSeed(SEED);
        store.useMap(before);
        BiomeTileKey key = new BiomeTileKey(before, 4, 0, 0);
        int generation = store.claim(key);
        store.store(key, tileFor(key, "minecraft:plains"), generation, 1L);
        store.release(key);
        assertNotNull(store.tileIfReady(key));

        // Set Seed / Clear Seed / another server / another dimension all look like this.
        assertTrue(store.useMap(mapWithSeed(SEED + 1)));

        assertNull(store.tileIfReady(key));
        assertEquals(0, store.metrics().cachedTiles());
        assertTrue(store.isStale(generation));
    }

    @Test
    void aResultThatArrivesAfterTheWorldChangedIsThrownAway() {
        BiomeTileStore store = new BiomeTileStore(16, 8);
        BiomeMapKey before = mapWithSeed(SEED);
        store.useMap(before);
        BiomeTileKey key = new BiomeTileKey(before, 4, 0, 0);

        int generation = store.claim(key);
        // The player clears the seed while the worker is still sampling.
        store.useMap(mapWithSeed(99L));

        assertFalse(store.store(key, tileFor(key, "minecraft:plains"), generation, 1L));
        store.release(key);

        assertNull(store.tileIfReady(key));
        assertEquals(0, store.metrics().completedTiles());
        assertEquals(1, store.metrics().rejectedTiles());
    }

    @Test
    void aTileFromAnOldSeedCannotBeFoundUnderANewOne() {
        // Belt and braces next to the generation counter: even if a stale tile somehow reached the
        // cache, its key carries the old seed, so a lookup for the new one misses.
        BiomeTileStore store = new BiomeTileStore(16, 8);
        BiomeMapKey oldMap = mapWithSeed(SEED);
        BiomeMapKey newMap = mapWithSeed(SEED + 1);
        store.useMap(oldMap);

        BiomeTileKey oldKey = new BiomeTileKey(oldMap, 4, 0, 0);
        store.store(oldKey, tileFor(oldKey, "minecraft:plains"), store.claim(oldKey), 1L);

        assertNull(store.tileIfReady(new BiomeTileKey(newMap, 4, 0, 0)));
    }

    @Test
    void reusingTheSameMapDoesNotThrowAwayTheCache() {
        BiomeTileStore store = new BiomeTileStore(16, 8);
        BiomeMapKey map = mapWithSeed(SEED);
        assertTrue(store.useMap(map));

        BiomeTileKey key = new BiomeTileKey(map, 4, 0, 0);
        store.store(key, tileFor(key, "minecraft:plains"), store.claim(key), 1L);
        store.release(key);

        // Called every frame with an equal key; must be a no-op.
        assertFalse(store.useMap(mapWithSeed(SEED)));
        assertNotNull(store.tileIfReady(key));
    }

    @Test
    void theCacheEvictsRatherThanGrowing() {
        BiomeTileStore store = new BiomeTileStore(4, 64);
        BiomeMapKey map = mapWithSeed(SEED);
        store.useMap(map);

        for (int i = 0; i < 40; i++) {
            BiomeTileKey key = new BiomeTileKey(map, 4, i, 0);
            store.store(key, tileFor(key, "minecraft:plains"), store.claim(key), 1L);
            store.release(key);
        }
        assertEquals(4, store.metrics().cachedTiles());
        assertEquals(40, store.metrics().completedTiles());
    }

    @Test
    void metricsTrackTiming() {
        BiomeTileStore store = new BiomeTileStore(16, 8);
        BiomeMapKey map = mapWithSeed(SEED);
        store.useMap(map);

        BiomeTileKey first = new BiomeTileKey(map, 4, 0, 0);
        BiomeTileKey second = new BiomeTileKey(map, 4, 1, 0);
        store.store(first, tileFor(first, "minecraft:plains"), store.claim(first), 10_000_000L);
        store.store(second, tileFor(second, "minecraft:ocean"), store.claim(second), 20_000_000L);

        BiomeTileStore.Metrics metrics = store.metrics();
        assertEquals(2, metrics.completedTiles());
        assertEquals(20.0, metrics.lastMillis(), 0.001);
        assertEquals(15.0, metrics.averageMillis(), 0.001);
        assertEquals(0, metrics.failedTiles());

        store.recordFailure();
        assertEquals(1, store.metrics().failedTiles());
    }

    @Test
    void metricsStartAtZeroWithoutDividingByZero() {
        BiomeTileStore.Metrics metrics = new BiomeTileStore(4, 4).metrics();
        assertEquals(0, metrics.completedTiles());
        assertEquals(0.0, metrics.averageMillis(), 0.001);
    }
}
