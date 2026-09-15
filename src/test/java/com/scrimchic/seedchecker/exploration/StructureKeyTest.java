package com.scrimchic.seedchecker.exploration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.world.PlayMode;
import com.scrimchic.seedchecker.world.WorldContext;
import com.scrimchic.seedchecker.world.WorldIdentity;
import com.scrimchic.seedchecker.world.WorldProfile;
import com.scrimchic.seedchecker.worldgen.GenerationPoint;
import com.scrimchic.seedchecker.worldgen.StructureBounds;
import com.scrimchic.seedchecker.worldgen.StructureGeometry;
import com.scrimchic.seedchecker.worldgen.StructureType;
import com.scrimchic.seedchecker.worldgen.StructureValidation;

class StructureKeyTest {

    private static final long SEED = -7407337299659424542L;
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String VERSION = "1.20.1";
    private static final WorldIdentity SERVER = WorldIdentity.multiplayer("mc.example.com", "Example");

    private static ActiveWorld serverWithManualSeed(Long seed, String dimension) {
        WorldProfile profile = WorldProfile.createNew(SERVER, VERSION, 1L);
        if (seed != null) {
            profile.setManualSeed(seed.longValue());
        }
        return new ActiveWorld(WorldContext.withUnknownSeed(VERSION, PlayMode.MULTIPLAYER, dimension), profile);
    }

    @Test
    void theSameInstanceIsTheSameKey() {
        StructureKey first = StructureKey.predicted(SEED, OVERWORLD, StructureType.VILLAGE, 12, -40);
        StructureKey second = StructureKey.predicted(SEED, OVERWORLD, StructureType.VILLAGE, 12, -40);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first, StructureKey.of(SEED, OVERWORLD, "village", 12, -40));
    }

    @Test
    void anyDifferenceIsAnotherStructure() {
        StructureKey key = StructureKey.predicted(SEED, OVERWORLD, StructureType.RUINED_PORTAL, 5, -9);
        assertNotEquals(key, StructureKey.predicted(SEED, NETHER, StructureType.RUINED_PORTAL, 5, -9));
        assertNotEquals(key, StructureKey.predicted(SEED, OVERWORLD, StructureType.VILLAGE, 5, -9));
        assertNotEquals(key, StructureKey.predicted(SEED, OVERWORLD, StructureType.RUINED_PORTAL, 6, -9));
        assertNotEquals(key, StructureKey.predicted(SEED, OVERWORLD, StructureType.RUINED_PORTAL, 5, -8));
        assertNotEquals(key, StructureKey.predicted(SEED + 1, OVERWORLD, StructureType.RUINED_PORTAL, 5, -9));
        assertNotEquals(key, StructureKey.of(null, OVERWORLD, "ruined_portal", 5, -9));
        // The fortress and the bastion share one grid, so one chunk can be asked about as both.
        assertNotEquals(StructureKey.predicted(SEED, NETHER, StructureType.NETHER_FORTRESS, 3, 3),
                StructureKey.predicted(SEED, NETHER, StructureType.BASTION_REMNANT, 3, 3));
    }

    @Test
    void negativeAndExtremeChunksAreExact() {
        int[][] chunks = {{-1, -1}, {0, -1}, {-1, 0}, {-1_875_000, 1_874_999},
                {Integer.MIN_VALUE, Integer.MAX_VALUE}};
        for (int[] chunk : chunks) {
            StructureKey key = StructureKey.predicted(Long.MIN_VALUE, OVERWORLD, StructureType.STRONGHOLD,
                    chunk[0], chunk[1]);
            assertEquals(chunk[0], key.chunkX());
            assertEquals(chunk[1], key.chunkZ());
            assertEquals(key, StructureKey.of(Long.MIN_VALUE, OVERWORLD, "stronghold", chunk[0], chunk[1]));
        }
        assertNotEquals(StructureKey.predicted(SEED, OVERWORLD, StructureType.VILLAGE, -1, 0),
                StructureKey.predicted(SEED, OVERWORLD, StructureType.VILLAGE, 0, -1));
    }

    @Test
    void whatValidationFoundIsNotPartOfTheKey() {
        // A legacy exact result carries no generation point; a modern one does; geometry may be
        // known or unavailable; a non-exact result has neither. The map builds the key from the
        // layer's type and the start chunk alone, so all of them name one structure.
        ActiveWorld world = serverWithManualSeed(SEED, OVERWORLD);
        StructureValidation legacy = StructureValidation.exactlyCompatible(null, "minecraft:plains");
        StructureValidation modern = StructureValidation.exactlyCompatible("village_plains",
                "minecraft:plains", new GenerationPoint(-5000, 70, 830));
        StructureValidation nonExact = StructureValidation.compatible(null, "minecraft:ocean");
        StructureGeometry withBounds = StructureGeometry.of(new GenerationPoint(-5000, 70, 830),
                new StructureBounds(-5040, 60, 790, -4960, 90, 870));
        StructureGeometry withoutBounds = StructureGeometry.unavailable("height fixed at placement");
        assertTrue(legacy.generationPoint() == null && modern.generationPoint() != null);
        assertTrue(withBounds.isAvailable() && !withoutBounds.isAvailable());
        assertFalse(nonExact.isExact());

        StructureKey key = StructureKey.predictedIn(world, StructureType.VILLAGE, -313, 51);
        assertEquals(StructureKey.predicted(SEED, OVERWORLD, StructureType.VILLAGE, -313, 51), key);
        // The generation point lies in the start chunk's neighbourhood, not the key: blocks -5000
        // and 830 are chunks -313 and 51, but a key is never rebuilt from them.
        assertEquals(-313, modern.generationPoint().x() >> 4);
        assertEquals(51, modern.generationPoint().z() >> 4);
    }

    @Test
    void theSeedDecidesWhichPredictionAKeyBelongsTo() {
        // Change Seed: the same chunk under another seed is another structure. Clear Seed: nothing
        // is predicted, so there is no key. Setting the first seed again finds the first key again.
        StructureKey underFirst = StructureKey.predictedIn(serverWithManualSeed(SEED, OVERWORLD),
                StructureType.DESERT_PYRAMID, 40, 40);
        StructureKey underSecond = StructureKey.predictedIn(serverWithManualSeed(42L, OVERWORLD),
                StructureType.DESERT_PYRAMID, 40, 40);
        assertNotEquals(underFirst, underSecond);
        assertTrue(underFirst.isPredictedFrom(SEED));
        assertFalse(underFirst.isPredictedFrom(42L));
        assertNull(StructureKey.predictedIn(serverWithManualSeed(null, OVERWORLD),
                StructureType.DESERT_PYRAMID, 40, 40));
        assertEquals(underFirst, StructureKey.predictedIn(serverWithManualSeed(SEED, OVERWORLD),
                StructureType.DESERT_PYRAMID, 40, 40));
    }

    @Test
    void theSeedsSourceIsNotPartOfTheKey() {
        // A seed typed in and the same seed reported by the integrated server are the same world.
        WorldIdentity save = WorldIdentity.singleplayer("New World", "New World");
        ActiveWorld runtime = new ActiveWorld(
                WorldContext.withKnownSeed(VERSION, PlayMode.SINGLEPLAYER, OVERWORLD, SEED),
                WorldProfile.createNew(save, VERSION, 1L));
        assertEquals(StructureKey.predictedIn(serverWithManualSeed(SEED, OVERWORLD), StructureType.IGLOO, 1, 2),
                StructureKey.predictedIn(runtime, StructureType.IGLOO, 1, 2));
    }

    @Test
    void outsideAWorldThereIsNoKey() {
        ActiveWorld outside = new ActiveWorld(WorldContext.outsideWorld(VERSION), null);
        assertNull(StructureKey.predictedIn(outside, StructureType.VILLAGE, 0, 0));
        assertNull(StructureKey.predictedIn(null, StructureType.VILLAGE, 0, 0));
    }

    @Test
    void aKeyWithoutASeedIsAllowedForLaterObservations() {
        StructureKey observed = StructureKey.of(null, OVERWORLD, "village", 1, 1);
        assertFalse(observed.hasSeed());
        assertFalse(observed.isPredictedFrom(0L));
        assertThrows(IllegalStateException.class, observed::seed);
        assertEquals(StructureType.VILLAGE, observed.structureType());
        assertNull(StructureKey.of(1L, OVERWORLD, "sky_castle", 1, 1).structureType());
    }

    @Test
    void aKeyNeedsADimensionAndAType() {
        assertThrows(IllegalArgumentException.class,
                () -> StructureKey.predicted(SEED, null, StructureType.VILLAGE, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> StructureKey.predicted(SEED, " ", StructureType.VILLAGE, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> StructureKey.predicted(SEED, OVERWORLD, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> StructureKey.of(SEED, OVERWORLD, "", 0, 0));
    }
}
