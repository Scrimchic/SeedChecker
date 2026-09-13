package com.scrimchic.seedchecker.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;

class StructureGeometryStoreTest {

    private static final long SEED = -7407337299659424542L;

    private static BiomeMapKey map(long seed) {
        return new BiomeMapKey("world-a", seed, "minecraft:overworld", "26.2", 0);
    }

    private static StructureValidationKey village(BiomeMapKey map, int chunkX, int chunkZ) {
        return new StructureValidationKey(map, StructureType.VILLAGE, chunkX, chunkZ);
    }

    private static StructureGeometry someGeometry() {
        return StructureGeometry.of(new GenerationPoint(8, 70, 8),
                new StructureBounds(-40, 60, -30, 50, 90, 44));
    }

    @Test
    void geometryIsAssembledOnceAndThenRemembered() {
        StructureGeometryStore store = new StructureGeometryStore(8, 2);
        BiomeMapKey map = map(SEED);
        store.useMap(map);
        StructureValidationKey key = village(map, 3, -4);

        int generation = store.claim(key);
        assertNotEquals(StructureGeometryStore.NO_JOB, generation);
        assertEquals(StructureGeometryStore.NO_JOB, store.claim(key), "already being assembled");

        StructureGeometry geometry = someGeometry();
        assertTrue(store.store(key, geometry, generation));
        store.release(key);

        assertSame(geometry, store.resultIfReady(key));
        assertEquals(StructureGeometryStore.NO_JOB, store.claim(key), "already known");
    }

    @Test
    void oldBoundsNeverSurviveASeedOrWorldChange() {
        // Change seed, clear seed, switch world and switch dimension all arrive here as a new map.
        StructureGeometryStore store = new StructureGeometryStore(8, 2);
        BiomeMapKey before = map(SEED);
        store.useMap(before);
        StructureValidationKey key = village(before, 0, 0);
        int generation = store.claim(key);

        assertTrue(store.useMap(map(SEED + 1)));
        assertFalse(store.store(key, someGeometry(), generation),
                "a job that finishes after the change must be dropped");
        store.release(key);

        assertNull(store.resultIfReady(key));
        assertEquals(0, store.cachedResults());
        assertEquals(1, store.discarded());
    }

    @Test
    void nothingIsAssembledForAMapThatIsNotBeingDrawn() {
        StructureGeometryStore store = new StructureGeometryStore(8, 2);
        store.useMap(map(SEED));
        BiomeMapKey otherDimension =
                new BiomeMapKey("world-a", SEED, "minecraft:the_nether", "26.2", 0);
        assertEquals(StructureGeometryStore.NO_JOB, store.claim(village(otherDimension, 1, 1)));
    }

    @Test
    void theQueueIsTiny() {
        StructureGeometryStore store = new StructureGeometryStore(8, 2);
        BiomeMapKey map = map(SEED);
        store.useMap(map);

        assertNotEquals(StructureGeometryStore.NO_JOB, store.claim(village(map, 0, 0)));
        assertNotEquals(StructureGeometryStore.NO_JOB, store.claim(village(map, 1, 0)));
        assertEquals(StructureGeometryStore.NO_JOB, store.claim(village(map, 2, 0)));
        store.release(village(map, 0, 0));
        assertNotEquals(StructureGeometryStore.NO_JOB, store.claim(village(map, 2, 0)));
    }

    @Test
    void unavailableGeometryIsAnAnswerAndIsCached() {
        StructureGeometryStore store = new StructureGeometryStore(8, 2);
        BiomeMapKey map = map(SEED);
        store.useMap(map);
        StructureValidationKey pyramid =
                new StructureValidationKey(map, StructureType.DESERT_PYRAMID, 5, 5);

        StructureGeometry none = StructureGeometry.unavailable("height fixed at placement");
        store.store(pyramid, none, store.claim(pyramid));
        store.release(pyramid);

        assertFalse(store.resultIfReady(pyramid).isAvailable());
        assertNull(store.resultIfReady(pyramid).bounds());
        assertEquals("height fixed at placement", store.resultIfReady(pyramid).unavailableReason());
        assertEquals(StructureGeometryStore.NO_JOB, store.claim(pyramid), "not retried per frame");
    }

    @Test
    void boundsAreInclusiveAndTheirCentreIsNotTheGenerationPoint() {
        StructureBounds bounds = new StructureBounds(-41, 60, 10, 50, 90, 13);
        assertTrue(bounds.contains(-41, 60, 10));
        assertTrue(bounds.contains(50, 90, 13));
        assertFalse(bounds.contains(51, 90, 13));
        assertEquals(4, bounds.centerX(), "(-41 + 50) / 2 rounded down");
        assertEquals(75, bounds.centerY());
        assertEquals(11, bounds.centerZ());
        assertEquals(new StructureBounds(-41, 60, 10, 50, 90, 13), bounds);

        assertThrows(IllegalArgumentException.class, () -> new StructureBounds(5, 0, 0, 4, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> StructureGeometry.of(null, null));
    }
}
