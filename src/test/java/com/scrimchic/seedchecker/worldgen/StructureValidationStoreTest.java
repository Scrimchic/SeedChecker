package com.scrimchic.seedchecker.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;

class StructureValidationStoreTest {

    private static final long SEED = -7407337299659424542L;

    private static BiomeMapKey mapWithSeed(long seed) {
        return new BiomeMapKey("world-a", seed, "minecraft:overworld", "1.20.1", 0);
    }

    private static StructureValidationKey keyIn(BiomeMapKey map, int chunkX, int chunkZ) {
        return new StructureValidationKey(map, StructureType.VILLAGE, chunkX, chunkZ);
    }

    @Test
    void aClaimedCandidateIsCheckedOnceAndThenRemembered() {
        StructureValidationStore store = new StructureValidationStore(16, 8);
        BiomeMapKey map = mapWithSeed(SEED);
        store.useMap(map);
        StructureValidationKey key = keyIn(map, 3, 7);

        int generation = store.claim(key);
        assertNotEquals(StructureValidationStore.NO_JOB, generation);
        assertEquals(StructureValidationStore.NO_JOB, store.claim(key), "already in flight");

        assertTrue(store.store(key, StructureValidation.compatible("village_plains",
                "minecraft:plains"), generation, 1_000_000L));
        store.release(key);

        assertNotNull(store.resultIfReady(key));
        assertEquals(StructureValidationStore.NO_JOB, store.claim(key), "already decided");
    }

    @Test
    void changingTheSeedThrowsEveryDecisionAway() {
        StructureValidationStore store = new StructureValidationStore(16, 8);
        BiomeMapKey before = mapWithSeed(SEED);
        store.useMap(before);
        StructureValidationKey key = keyIn(before, 0, 0);
        store.store(key, StructureValidation.incompatible("minecraft:ocean"),
                store.claim(key), 1L);
        store.release(key);
        assertNotNull(store.resultIfReady(key));

        assertTrue(store.useMap(mapWithSeed(SEED + 1)));

        assertNull(store.resultIfReady(key));
        assertEquals(0, store.metrics().cachedResults());
    }

    @Test
    void aDecisionThatArrivesAfterTheWorldChangedIsDropped() {
        StructureValidationStore store = new StructureValidationStore(16, 8);
        BiomeMapKey before = mapWithSeed(SEED);
        store.useMap(before);
        StructureValidationKey key = keyIn(before, 0, 0);

        int generation = store.claim(key);
        store.useMap(mapWithSeed(99L));

        assertFalse(store.store(key, StructureValidation.compatible(null, null), generation, 1L));
        store.release(key);

        assertNull(store.resultIfReady(key));
        assertEquals(1, store.metrics().discarded());
    }

    @Test
    void aDecisionFromAnOldSeedIsNotFoundUnderANewOne() {
        StructureValidationStore store = new StructureValidationStore(16, 8);
        BiomeMapKey oldMap = mapWithSeed(SEED);
        BiomeMapKey newMap = mapWithSeed(SEED + 1);
        store.useMap(oldMap);

        StructureValidationKey oldKey = keyIn(oldMap, 5, 5);
        store.store(oldKey, StructureValidation.compatible(null, null), store.claim(oldKey), 1L);

        assertNull(store.resultIfReady(keyIn(newMap, 5, 5)));
    }

    @Test
    void differentStructuresAtTheSameChunkAreSeparateDecisions() {
        BiomeMapKey map = mapWithSeed(SEED);
        StructureValidationKey village = new StructureValidationKey(map, StructureType.VILLAGE, 1, 2);
        StructureValidationKey pyramid =
                new StructureValidationKey(map, StructureType.DESERT_PYRAMID, 1, 2);

        assertNotEquals(village, pyramid);
        assertNotEquals(village.hashCode(), pyramid.hashCode());
    }

    @Test
    void swappingTheChunkAxesDoesNotCollide() {
        BiomeMapKey map = mapWithSeed(SEED);
        assertNotEquals(keyIn(map, 1, 2), keyIn(map, 2, 1));
    }

    @Test
    void theQueueIsBounded() {
        StructureValidationStore store = new StructureValidationStore(64, 2);
        BiomeMapKey map = mapWithSeed(SEED);
        store.useMap(map);

        assertNotEquals(StructureValidationStore.NO_JOB, store.claim(keyIn(map, 0, 0)));
        assertNotEquals(StructureValidationStore.NO_JOB, store.claim(keyIn(map, 1, 0)));
        assertEquals(StructureValidationStore.NO_JOB, store.claim(keyIn(map, 2, 0)));

        store.release(keyIn(map, 0, 0));
        assertNotEquals(StructureValidationStore.NO_JOB, store.claim(keyIn(map, 2, 0)));
    }

    @Test
    void theCacheEvictsRatherThanGrowing() {
        StructureValidationStore store = new StructureValidationStore(4, 64);
        BiomeMapKey map = mapWithSeed(SEED);
        store.useMap(map);

        for (int i = 0; i < 40; i++) {
            StructureValidationKey key = keyIn(map, i, 0);
            store.store(key, StructureValidation.compatible(null, null), store.claim(key), 1L);
            store.release(key);
        }
        assertEquals(4, store.metrics().cachedResults());
        assertEquals(40, store.metrics().accepted());
    }

    @Test
    void metricsSeparateKeptFromRejected() {
        StructureValidationStore store = new StructureValidationStore(16, 8);
        BiomeMapKey map = mapWithSeed(SEED);
        store.useMap(map);

        StructureValidationKey kept = keyIn(map, 0, 0);
        StructureValidationKey thrown = keyIn(map, 1, 0);
        store.store(kept, StructureValidation.compatible("village_plains", "minecraft:plains"),
                store.claim(kept), 10_000_000L);
        store.store(thrown, StructureValidation.incompatible("minecraft:ocean"),
                store.claim(thrown), 20_000_000L);

        StructureValidationStore.Metrics metrics = store.metrics();
        assertEquals(1, metrics.accepted());
        assertEquals(1, metrics.rejected());
        assertEquals(15.0, metrics.averageMillis(), 0.001);
        assertEquals(0, metrics.failed());

        store.recordFailure();
        assertEquals(1, store.metrics().failed());
    }

    @Test
    void aValidationCarriesItsVariantAndBiome() {
        StructureValidation compatible =
                StructureValidation.compatible("village_desert", "minecraft:desert");
        assertTrue(compatible.isCompatible());
        assertEquals(StructureBiomeStatus.COMPATIBLE, compatible.status());
        assertEquals("village_desert", compatible.variant());
        assertEquals("minecraft:desert", compatible.sampledBiomeId());

        StructureValidation rejected = StructureValidation.incompatible("minecraft:jungle");
        assertFalse(rejected.isCompatible());
        assertTrue(rejected.isRejected());
        assertEquals(StructureBiomeStatus.INCOMPATIBLE, rejected.status());
        assertNull(rejected.variant(), "a rejected candidate has no variant");
        assertEquals("minecraft:jungle", rejected.sampledBiomeId());
    }

    @Test
    void anUndecidedCandidateIsNeitherKeptNorRejected() {
        // The distinction the map depends on: only isRejected() may hide a marker, so an
        // undecided candidate must answer false to it even though it is not compatible either.
        StructureValidation unknown = StructureValidation.unknown("no known sample position");
        assertEquals(StructureBiomeStatus.UNKNOWN, unknown.status());
        assertFalse(unknown.isRejected(), "an undecided candidate must never be hidden");
        assertFalse(unknown.isCompatible());
        assertNull(unknown.variant());
        assertNull(unknown.sampledBiomeId());
        assertEquals("no known sample position", unknown.reason());
    }

    @Test
    void metricsCountUndecidedApartFromBothOthers() {
        StructureValidationStore store = new StructureValidationStore(16, 8);
        store.useMap(mapWithSeed(SEED));

        StructureValidationKey kept = keyIn(mapWithSeed(SEED), 1, 1);
        StructureValidationKey thrown = keyIn(mapWithSeed(SEED), 2, 2);
        StructureValidationKey undecided = keyIn(mapWithSeed(SEED), 3, 3);
        store.store(kept, StructureValidation.compatible("village_plains", "minecraft:plains"),
                store.claim(kept), 1L);
        store.store(thrown, StructureValidation.incompatible("minecraft:ocean"),
                store.claim(thrown), 1L);
        store.store(undecided, StructureValidation.unknown("start piece geometry"),
                store.claim(undecided), 1L);

        StructureValidationStore.Metrics metrics = store.metrics();
        assertEquals(1, metrics.accepted());
        assertEquals(1, metrics.rejected());
        assertEquals(1, metrics.undecided());
        assertEquals(3, metrics.cachedResults());
    }
}
