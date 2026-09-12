package com.scrimchic.seedchecker.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.scrimchic.seedchecker.core.map.ChunkRange;

/** Properties of the placement engine that hold regardless of Minecraft version. */
class StructurePlacementEngineTest {

    private static final long SEED = -7407337299659424542L;

    /** Village-shaped numbers, used wherever the exact values do not matter. */
    private static final StructurePlacementConfig VILLAGE =
            new StructurePlacementConfig(34, 8, 10387312, SpreadType.LINEAR);

    private static List<long[]> candidates(StructurePlacementConfig config, ChunkRange area) {
        final List<long[]> found = new ArrayList<long[]>();
        new StructurePlacementEngine().forEachCandidate(SEED, config, area, Integer.MAX_VALUE,
                new StructureCandidateVisitor() {
                    @Override
                    public boolean visit(int chunkX, int chunkZ) {
                        found.add(new long[] {chunkX, chunkZ});
                        return true;
                    }
                });
        return found;
    }

    @Test
    void packingRoundTripsIncludingNegatives() {
        int[] values = {0, 1, -1, 1000, -1000, Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (int x : values) {
            for (int z : values) {
                long packed = StructurePlacementEngine.pack(x, z);
                assertEquals(x, StructurePlacementEngine.chunkX(packed));
                assertEquals(z, StructurePlacementEngine.chunkZ(packed));
            }
        }
    }

    @Test
    void aCandidateAlwaysLandsInsideItsOwnRegion() {
        StructurePlacementEngine engine = new StructurePlacementEngine();
        for (int regionX = -40; regionX <= 40; regionX++) {
            for (int regionZ = -40; regionZ <= 40; regionZ++) {
                long candidate = engine.candidateChunk(SEED, VILLAGE, regionX, regionZ);
                int chunkX = StructurePlacementEngine.chunkX(candidate);
                int chunkZ = StructurePlacementEngine.chunkZ(candidate);

                int baseX = regionX * VILLAGE.spacing();
                int baseZ = regionZ * VILLAGE.spacing();
                assertTrue(chunkX >= baseX && chunkX < baseX + VILLAGE.offsetRange(),
                        "x " + chunkX + " outside region " + regionX);
                assertTrue(chunkZ >= baseZ && chunkZ < baseZ + VILLAGE.offsetRange(),
                        "z " + chunkZ + " outside region " + regionZ);
            }
        }
    }

    @Test
    void negativeChunksUseFloorDivisionNotTruncation() {
        StructurePlacementEngine engine = new StructurePlacementEngine();

        // Chunk -1 belongs to region -1, not to region 0. Truncating division would merge them,
        // and every structure west and north of the origin would shift by one region.
        long regionMinusOne = engine.candidateChunk(SEED, VILLAGE, -1, -1);
        long regionZero = engine.candidateChunk(SEED, VILLAGE, 0, 0);
        assertNotEquals(regionMinusOne, regionZero);

        assertTrue(StructurePlacementEngine.chunkX(regionMinusOne) < 0);
        assertTrue(StructurePlacementEngine.chunkX(regionZero) >= 0);
    }

    @Test
    void scanningARangeFindsExactlyTheCandidatesABruteForceScanWould() {
        ChunkRange area = ChunkRange.of(-70, -70, 70, 70);
        StructurePlacementEngine engine = new StructurePlacementEngine();

        // Reference: ask, for every single chunk in the area, what its region's candidate is, and
        // keep the ones that land inside the area. Slow, obviously correct.
        Set<String> expected = new HashSet<String>();
        for (int chunkX = area.minChunkX(); chunkX <= area.maxChunkX(); chunkX++) {
            for (int chunkZ = area.minChunkZ(); chunkZ <= area.maxChunkZ(); chunkZ++) {
                long candidate = engine.candidateChunk(SEED, VILLAGE,
                        Math.floorDiv(chunkX, VILLAGE.spacing()),
                        Math.floorDiv(chunkZ, VILLAGE.spacing()));
                int cx = StructurePlacementEngine.chunkX(candidate);
                int cz = StructurePlacementEngine.chunkZ(candidate);
                if (cx >= area.minChunkX() && cx <= area.maxChunkX()
                        && cz >= area.minChunkZ() && cz <= area.maxChunkZ()) {
                    expected.add(cx + "," + cz);
                }
            }
        }

        Set<String> actual = new HashSet<String>();
        for (long[] candidate : candidates(VILLAGE, area)) {
            actual.add(candidate[0] + "," + candidate[1]);
        }

        assertEquals(expected, actual);
        assertTrue(expected.size() > 10, "the reference scan should have found something");
    }

    @Test
    void everyCandidateReportedIsInsideTheRequestedArea() {
        ChunkRange area = ChunkRange.of(-13, 41, 57, 95);
        for (long[] candidate : candidates(VILLAGE, area)) {
            assertTrue(candidate[0] >= area.minChunkX() && candidate[0] <= area.maxChunkX());
            assertTrue(candidate[1] >= area.minChunkZ() && candidate[1] <= area.maxChunkZ());
        }
    }

    @Test
    void aOneChunkAreaReportsAtMostThatChunk() {
        for (long[] candidate : candidates(VILLAGE, ChunkRange.of(0, 0, 0, 0))) {
            assertEquals(0L, candidate[0]);
            assertEquals(0L, candidate[1]);
        }
    }

    @Test
    void theCandidateBudgetIsHonoured() {
        ChunkRange area = ChunkRange.of(-200, -200, 200, 200);
        final int[] seen = {0};
        int reported = new StructurePlacementEngine().forEachCandidate(SEED, VILLAGE, area, 5,
                new StructureCandidateVisitor() {
                    @Override
                    public boolean visit(int chunkX, int chunkZ) {
                        seen[0]++;
                        return true;
                    }
                });
        assertEquals(5, reported);
        assertEquals(5, seen[0]);
    }

    @Test
    void aVisitorCanStopTheScan() {
        int reported = new StructurePlacementEngine().forEachCandidate(SEED, VILLAGE,
                ChunkRange.of(-200, -200, 200, 200), Integer.MAX_VALUE,
                new StructureCandidateVisitor() {
                    @Override
                    public boolean visit(int chunkX, int chunkZ) {
                        return false;
                    }
                });
        assertEquals(1, reported);
    }

    @Test
    void regionCountScalesWithRegionsNotChunks() {
        ChunkRange area = ChunkRange.of(0, 0, 33, 33);
        assertEquals(1L, StructurePlacementEngine.regionCount(VILLAGE, area));

        // 34 chunks per region, so chunks 0..67 span exactly two regions on each axis.
        assertEquals(4L, StructurePlacementEngine.regionCount(VILLAGE, ChunkRange.of(0, 0, 67, 67)));
        assertEquals(4L, StructurePlacementEngine.regionCount(VILLAGE, ChunkRange.of(-1, -1, 0, 0)));
    }

    @Test
    void regionCountSaturatesInsteadOfOverflowing() {
        long count = StructurePlacementEngine.regionCount(VILLAGE,
                ChunkRange.of(Integer.MIN_VALUE, Integer.MIN_VALUE,
                        Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertTrue(count > 0L, "saturating count must stay positive, was " + count);
    }

    @Test
    void reusingOneEngineDoesNotLeakStateBetweenRegions() {
        StructurePlacementEngine shared = new StructurePlacementEngine();
        long first = shared.candidateChunk(SEED, VILLAGE, 3, 7);

        // Drive the generator hard, then ask again: the seed is reinstalled per region, so the
        // answer must not depend on what was computed before it.
        for (int i = 0; i < 500; i++) {
            shared.candidateChunk(SEED + i, VILLAGE, i, -i);
        }
        assertEquals(first, shared.candidateChunk(SEED, VILLAGE, 3, 7));
        assertEquals(first, new StructurePlacementEngine().candidateChunk(SEED, VILLAGE, 3, 7));
    }

    @Test
    void triangularSpreadIsNotTheSameAsLinear() {
        StructurePlacementConfig linear =
                new StructurePlacementConfig(34, 8, 10387312, SpreadType.LINEAR);
        StructurePlacementConfig triangular =
                new StructurePlacementConfig(34, 8, 10387312, SpreadType.TRIANGULAR);
        StructurePlacementEngine engine = new StructurePlacementEngine();

        int differences = 0;
        for (int region = -50; region <= 50; region++) {
            if (engine.candidateChunk(SEED, linear, region, region)
                    != engine.candidateChunk(SEED, triangular, region, region)) {
                differences++;
            }
        }
        assertTrue(differences > 50, "spread type must change placement, differed " + differences);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, -1L, 123456789L, -7407337299659424542L,
            Long.MAX_VALUE, Long.MIN_VALUE})
    void anySeedProducesRegionLocalCandidates(long seed) {
        StructurePlacementEngine engine = new StructurePlacementEngine();
        for (int region = -20; region <= 20; region++) {
            long candidate = engine.candidateChunk(seed, VILLAGE, region, -region);
            int baseX = region * VILLAGE.spacing();
            assertTrue(StructurePlacementEngine.chunkX(candidate) >= baseX);
            assertTrue(StructurePlacementEngine.chunkX(candidate) < baseX + VILLAGE.offsetRange());
        }
    }

    @Test
    void configRejectsImpossibleNumbers() {
        assertThrows(IllegalArgumentException.class,
                () -> new StructurePlacementConfig(0, 0, 1, SpreadType.LINEAR));
        assertThrows(IllegalArgumentException.class,
                () -> new StructurePlacementConfig(8, 8, 1, SpreadType.LINEAR));
        assertThrows(IllegalArgumentException.class,
                () -> new StructurePlacementConfig(8, -1, 1, SpreadType.LINEAR));
        assertThrows(IllegalArgumentException.class,
                () -> new StructurePlacementConfig(8, 1, 1, null));
    }

    @Test
    void legacyRandomMatchesJavaUtilRandom() {
        // The engine's generator must be java.util.Random exactly, including nextInt's rejection
        // loop, because vanilla's own placement is built on that generator.
        long[] seeds = {0L, 1L, -1L, SEED, Long.MIN_VALUE, Long.MAX_VALUE};
        int[] bounds = {1, 2, 16, 26, 20, 30, 34};
        for (long seed : seeds) {
            for (int bound : bounds) {
                java.util.Random reference = new java.util.Random(seed);
                LegacyRandom ours = new LegacyRandom();
                ours.setSeed(seed);
                for (int draw = 0; draw < 32; draw++) {
                    assertEquals(reference.nextInt(bound), ours.nextInt(bound),
                            "seed " + seed + " bound " + bound + " draw " + draw);
                }
            }
        }
    }

    @Test
    void legacyRandomRejectsNonPositiveBounds() {
        LegacyRandom random = new LegacyRandom();
        random.setSeed(1L);
        assertThrows(IllegalArgumentException.class, () -> random.nextInt(0));
        assertThrows(IllegalArgumentException.class, () -> random.nextInt(-4));
    }
}
