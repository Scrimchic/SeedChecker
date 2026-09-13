package com.scrimchic.seedchecker.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The pure half of stronghold placement: the ring arithmetic and the biome search, with a fake
 * biome filter. Whether it matches vanilla is {@code StrongholdPlacementTest}'s job; this checks
 * the properties a transcription slip would break.
 */
class StrongholdPlacementEngineTest {

    private static final ConcentricRingConfig VANILLA = new ConcentricRingConfig(32, 3, 128);

    private static final StrongholdPlacementEngine.BiomeFilter NOWHERE = (x, y, z) -> false;
    private static final StrongholdPlacementEngine.BiomeFilter EVERYWHERE = (x, y, z) -> true;

    @Test
    void theRngMatchesJavaUtilRandomDrawForDraw() {
        LegacyRandom ours = new LegacyRandom();
        for (long seed : new long[] {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE,
                -7407337299659424542L}) {
            ours.setSeed(seed);
            Random jdk = new Random(seed);
            for (int i = 0; i < 200; i++) {
                assertEquals(jdk.nextDouble(), ours.nextDouble(), 0.0);
                assertEquals(jdk.nextLong(), ours.nextLong());
                assertEquals(jdk.nextInt(3249), ours.nextInt(3249));
            }
        }
    }

    @Test
    void ringsGrowAsVanillaDescribesAndEveryStrongholdIsPlaced() {
        // With no biome ever accepted nothing moves, so the ring structure itself is visible:
        // 3, then spread += 2 * spread / (ring + 1) - 6, 10, 15, 21, 28, 36 - capped by what is left.
        List<StrongholdPosition> positions = StrongholdPlacementEngine.place(1L, VANILLA,
                StrongholdPlacementEngine.RandomModel.FORKED, NOWHERE);
        assertEquals(128, positions.size());
        int[] perRing = new int[8];
        for (int i = 0; i < positions.size(); i++) {
            StrongholdPosition position = positions.get(i);
            assertEquals(i, position.index(), "placement order is the list order");
            assertFalse(position.isBiomeAdjusted());
            perRing[position.ring()]++;
        }
        assertEquals("3,6,10,15,21,28,36,9", perRing[0] + "," + perRing[1] + "," + perRing[2] + ","
                + perRing[3] + "," + perRing[4] + "," + perRing[5] + "," + perRing[6] + ","
                + perRing[7]);
    }

    @Test
    void ringDistancesStayInsideTheirBands() {
        // dist = (4 + 6 * ring) * 32 +- 40 chunks.
        for (StrongholdPosition position : StrongholdPlacementEngine.place(
                -7407337299659424542L, VANILLA, StrongholdPlacementEngine.RandomModel.FORKED,
                NOWHERE)) {
            double centre = (4 + 6 * position.ring()) * 32.0;
            double actual = Math.hypot(position.ringChunkX(), position.ringChunkZ());
            assertTrue(Math.abs(actual - centre) <= 40.0 + 1.0, position + " at " + actual);
        }
    }

    @Test
    void theSameSeedGivesTheSameListAndAnotherSeedDoesNot() {
        for (StrongholdPlacementEngine.RandomModel model
                : StrongholdPlacementEngine.RandomModel.values()) {
            List<StrongholdPosition> first = StrongholdPlacementEngine.place(42L, VANILLA, model,
                    checkerboard());
            List<StrongholdPosition> again = StrongholdPlacementEngine.place(42L, VANILLA, model,
                    checkerboard());
            List<StrongholdPosition> other = StrongholdPlacementEngine.place(43L, VANILLA, model,
                    checkerboard());
            assertEquals(first, again, model.name());
            assertNotEquals(first, other, model.name());
        }
    }

    @Test
    void theSharedModelLetsBiomeMatchesMoveLaterStrongholdsAndTheForkedModelDoesNot() {
        List<StrongholdPosition> sharedNone = StrongholdPlacementEngine.place(7L, VANILLA,
                StrongholdPlacementEngine.RandomModel.SHARED, NOWHERE);
        List<StrongholdPosition> sharedAll = StrongholdPlacementEngine.place(7L, VANILLA,
                StrongholdPlacementEngine.RandomModel.SHARED, EVERYWHERE);
        assertNotEquals(sharedNone.get(5).ringChunkX(), sharedAll.get(5).ringChunkX(),
                "on 1.16.5 search draws shift the ring maths");

        List<StrongholdPosition> forkedNone = StrongholdPlacementEngine.place(7L, VANILLA,
                StrongholdPlacementEngine.RandomModel.FORKED, NOWHERE);
        List<StrongholdPosition> forkedAll = StrongholdPlacementEngine.place(7L, VANILLA,
                StrongholdPlacementEngine.RandomModel.FORKED, EVERYWHERE);
        for (int i = 0; i < 128; i++) {
            assertEquals(forkedNone.get(i).ringChunkX(), forkedAll.get(i).ringChunkX());
            assertEquals(forkedNone.get(i).ringChunkZ(), forkedAll.get(i).ringChunkZ());
        }
    }

    @Test
    void noTwoStrongholdsShareAChunk() {
        for (long seed : new long[] {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            Set<Long> chunks = new HashSet<Long>();
            for (StrongholdPosition position : StrongholdPlacementEngine.place(seed, VANILLA,
                    StrongholdPlacementEngine.RandomModel.FORKED, checkerboard())) {
                assertTrue(chunks.add(((long) position.chunkX() << 32)
                        ^ (position.chunkZ() & 0xFFFFFFFFL)), "duplicate " + position);
            }
        }
    }

    @Test
    void theSearchPicksTheOnlyMatchAndFindsNothingWithoutOne() {
        LegacyRandom random = new LegacyRandom();
        random.setSeed(5L);
        long found = StrongholdPlacementEngine.findBiome(-4000, 0, 1200, 112,
                (x, y, z) -> x == (-4000 >> 2) + 3 && z == (1200 >> 2) - 28, random);
        assertEquals(((-4000 >> 2) + 3) << 2, StrongholdPlacementEngine.blockXOf(found));
        assertEquals(((1200 >> 2) - 28) << 2, StrongholdPlacementEngine.blockZOf(found));

        assertEquals(Long.MIN_VALUE,
                StrongholdPlacementEngine.findBiome(0, 0, 0, 112, NOWHERE, random));
    }

    @Test
    void theSearchDrawsOnceForEveryMatchAfterTheFirst() {
        // 57 x 57 columns all match: 3248 draws. Measured on a copy seeded the same way.
        LegacyRandom searched = new LegacyRandom();
        searched.setSeed(99L);
        StrongholdPlacementEngine.findBiome(8, 0, 8, 112, EVERYWHERE, searched);

        LegacyRandom counted = new LegacyRandom();
        counted.setSeed(99L);
        for (int n = 1; n < 57 * 57; n++) {
            counted.nextInt(n + 1);
        }
        assertEquals(counted.nextLong(), searched.nextLong());
    }

    @Test
    void theNearestStrongholdIsByDistanceNotByIndex() {
        List<StrongholdPosition> positions = java.util.Arrays.asList(
                new StrongholdPosition(0, 0, 100, 0, 100, 0),
                new StrongholdPosition(1, 0, -3, 2, -3, 2));
        assertSame(positions.get(1), StrongholdPlacementEngine.nearest(positions, 0, 0));
        assertSame(positions.get(0), StrongholdPlacementEngine.nearest(positions, 1590, 0));
        assertNull(StrongholdPlacementEngine.nearest(java.util.Collections.<StrongholdPosition>emptyList(), 0, 0));
    }

    private static StrongholdPlacementEngine.BiomeFilter checkerboard() {
        return (x, y, z) -> ((x ^ z) & 7) == 0;
    }
}
