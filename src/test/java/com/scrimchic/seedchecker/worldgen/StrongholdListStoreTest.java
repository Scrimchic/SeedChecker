package com.scrimchic.seedchecker.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;

class StrongholdListStoreTest {

    private static BiomeMapKey map(long seed) {
        return new BiomeMapKey("world-a", seed, "minecraft:overworld", "26.2", 0);
    }

    private static final List<StrongholdPosition> LIST = Arrays.asList(
            new StrongholdPosition(0, 0, -90, 40, -91, 38),
            new StrongholdPosition(1, 0, 60, 70, 60, 70));

    @Test
    void theListIsComputedOnceAndFoundByChunk() {
        StrongholdListStore store = new StrongholdListStore();
        BiomeMapKey map = map(1L);
        store.useMap(map);

        int generation = store.claim(map);
        assertNotEquals(StrongholdListStore.NO_JOB, generation);
        assertEquals(StrongholdListStore.NO_JOB, store.claim(map), "already being computed");
        assertTrue(store.isPending());

        assertTrue(store.store(generation, LIST, 5000L));
        store.release(generation);

        assertEquals(LIST, store.positionsIfReady(map));
        assertSame(LIST.get(0).getClass(), store.positionAt(map, -90, 40).getClass());
        assertEquals(0, store.positionAt(map, -90, 40).index());
        assertNull(store.positionAt(map, -91, 38), "a raw ring chunk is not a stronghold");
        assertEquals(StrongholdListStore.NO_JOB, store.claim(map), "already known");
        assertEquals(5000L, store.millis());
    }

    @Test
    void aSeedOrWorldChangeDropsTheListAndALateJobCannotBringItBack() {
        StrongholdListStore store = new StrongholdListStore();
        BiomeMapKey before = map(1L);
        store.useMap(before);
        int generation = store.claim(before);

        BiomeMapKey after = map(2L);
        assertTrue(store.useMap(after));
        assertFalse(store.store(generation, LIST, 1L));
        store.release(generation);

        assertNull(store.positionsIfReady(after));
        assertNull(store.positionsIfReady(before));
        assertNull(store.positionAt(after, -90, 40));
        assertNotEquals(StrongholdListStore.NO_JOB, store.claim(after),
                "the old job must not have released or blocked the new map");
    }

    @Test
    void aFailureIsNotRetriedUntilTheMapChanges() {
        StrongholdListStore store = new StrongholdListStore();
        BiomeMapKey map = map(1L);
        store.useMap(map);
        int generation = store.claim(map);
        assertTrue(store.storeFailure(generation, "IllegalStateException"));
        store.release(generation);

        assertEquals("IllegalStateException", store.failure());
        assertEquals(StrongholdListStore.NO_JOB, store.claim(map));

        store.useMap(map(3L));
        assertNull(store.failure());
        assertNotEquals(StrongholdListStore.NO_JOB, store.claim(map(3L)));
    }

    @Test
    void nothingIsComputedForAMapThatIsNotDrawn() {
        StrongholdListStore store = new StrongholdListStore();
        assertEquals(StrongholdListStore.NO_JOB, store.claim(map(1L)));
        store.useMap(map(1L));
        assertEquals(StrongholdListStore.NO_JOB, store.claim(map(9L)));
    }
}
