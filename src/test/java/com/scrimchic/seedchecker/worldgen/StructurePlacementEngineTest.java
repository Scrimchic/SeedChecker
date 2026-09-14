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

    // ------------------------------------------------ Phase 3H-1: placement restrictions

    private static final StructurePlacementConfig OUTPOST = new StructurePlacementConfig(32, 8,
            165745296, SpreadType.LINEAR, 0.2F, FrequencyReduction.LEGACY_TYPE_1,
            new ExclusionZone(StructureType.VILLAGE, VILLAGE, 10));

    private static final StructurePlacementConfig TREASURE = new StructurePlacementConfig(1, 0, 0,
            SpreadType.LINEAR, 0.01F, FrequencyReduction.LEGACY_TYPE_2, null);

    private static final StructurePlacementConfig MINESHAFT = new StructurePlacementConfig(1, 0, 0,
            SpreadType.LINEAR, 0.004F, FrequencyReduction.LEGACY_TYPE_3, null);

    private static final long[] EDGE_SEEDS = {0L, 1L, -1L, SEED, Long.MIN_VALUE, Long.MAX_VALUE};

    @Test
    void legacyRandomFloatsIntsAndLongsMatchJavaUtilRandom() {
        for (long seed : EDGE_SEEDS) {
            java.util.Random reference = new java.util.Random(seed);
            LegacyRandom ours = new LegacyRandom();
            ours.setSeed(seed);
            for (int draw = 0; draw < 64; draw++) {
                assertEquals(reference.nextInt(), ours.nextInt(), "nextInt seed " + seed);
                assertEquals(reference.nextFloat(), ours.nextFloat(), "nextFloat seed " + seed);
                assertEquals(reference.nextDouble(), ours.nextDouble(), "nextDouble seed " + seed);
                assertEquals(reference.nextLong(), ours.nextLong(), "nextLong seed " + seed);
            }
        }
    }

    @Test
    void largeFeatureSeedsAreVanillasDefinitions() {
        // WorldgenRandom's two reseeding helpers, re-derived over java.util.Random.
        int[] coordinates = {0, 1, -1, 31, -32, 1_000_000, -1_875_000, Integer.MAX_VALUE};
        LegacyRandom ours = new LegacyRandom();
        for (long seed : EDGE_SEEDS) {
            for (int x : coordinates) {
                for (int z : coordinates) {
                    java.util.Random first = new java.util.Random(seed);
                    long a = first.nextLong();
                    long b = first.nextLong();
                    java.util.Random large = new java.util.Random((long) x * a ^ (long) z * b ^ seed);
                    ours.setLargeFeatureSeed(seed, x, z);
                    assertEquals(large.nextInt(), ours.nextInt(), "large feature seed");

                    java.util.Random salted = new java.util.Random(
                            (long) x * 341873128712L + (long) z * 132897987541L + seed + 10387320L);
                    ours.setLargeFeatureWithSalt(seed, x, z, 10387320);
                    assertEquals(salted.nextInt(), ours.nextInt(), "salted large feature seed");
                }
            }
        }
    }

    @Test
    void spacingOneSetsKeepRoughlyTheirFrequency() {
        // Not the exactness check - that is against vanilla, per version - but a transcription that
        // compares the wrong way or draws from the wrong generator shows up here at once.
        StructurePlacementEngine engine = new StructurePlacementEngine();
        int treasures = 0;
        int mineshafts = 0;
        int chunks = 0;
        for (int x = -200; x < 200; x++) {
            for (int z = -200; z < 200; z++) {
                chunks++;
                if (engine.isStructureChunk(SEED, TREASURE, x, z)) {
                    treasures++;
                }
                if (engine.isStructureChunk(SEED, MINESHAFT, x, z)) {
                    mineshafts++;
                }
            }
        }
        assertTrue(treasures > chunks * 0.008 && treasures < chunks * 0.012,
                "buried treasure kept " + treasures + " of " + chunks);
        assertTrue(mineshafts > chunks * 0.003 && mineshafts < chunks * 0.005,
                "mineshaft kept " + mineshafts + " of " + chunks);
    }

    @Test
    void theOutpostReducerDecidesWholeSixteenChunkBlocks() {
        // legacy_type_1 is seeded from chunk >> 4 alone, so every chunk of a 16 by 16 block gets the
        // same frequency answer - and roughly one block in five says yes.
        LegacyRandom random = new LegacyRandom();
        int kept = 0;
        int blocks = 0;
        for (int blockX = -30; blockX < 30; blockX++) {
            for (int blockZ = -30; blockZ < 30; blockZ++) {
                boolean first = FrequencyReduction.LEGACY_TYPE_1.keeps(random, SEED, 0,
                        blockX << 4, blockZ << 4, 0.2F);
                assertEquals(first, FrequencyReduction.LEGACY_TYPE_1.keeps(random, SEED, 0,
                        (blockX << 4) + 15, (blockZ << 4) + 7, 0.2F));
                blocks++;
                if (first) {
                    kept++;
                }
            }
        }
        assertTrue(kept > blocks * 0.15 && kept < blocks * 0.25, "kept " + kept + " of " + blocks);
    }

    @Test
    void exclusionIsVanillasChunkByChunkWalk() {
        // ChunkGeneratorStructureState.hasStructureChunkInRange asks isStructureChunk of every chunk
        // in the square; the engine walks regions instead. Same answer everywhere, including across
        // region boundaries, at negative and at far coordinates.
        StructurePlacementEngine engine = new StructurePlacementEngine();
        int[][] origins = {{0, 0}, {-34, -34}, {-1000, 700}, {1_000_000, -1_000_000}};
        int inRange = 0;
        for (long seed : EDGE_SEEDS) {
            for (int[] origin : origins) {
                for (int x = origin[0] - 40; x < origin[0] + 40; x += 3) {
                    for (int z = origin[1] - 40; z < origin[1] + 40; z += 3) {
                        boolean walked = false;
                        for (int dx = -10; dx <= 10 && !walked; dx++) {
                            for (int dz = -10; dz <= 10 && !walked; dz++) {
                                walked = engine.isStructureChunk(seed, VILLAGE, x + dx, z + dz);
                            }
                        }
                        assertEquals(walked, engine.hasStructureChunkInRange(seed, VILLAGE, x, z, 10),
                                "seed " + seed + " chunk " + x + "," + z);
                        if (walked) {
                            inRange++;
                        }
                    }
                }
            }
        }
        assertTrue(inRange > 100, "the positive side was hardly exercised: " + inRange);
    }

    @Test
    void theScanReportsExactlyTheStructureChunks() {
        StructurePlacementEngine engine = new StructurePlacementEngine();
        Object[][] cases = {{OUTPOST, ChunkRange.of(-400, -400, 400, 400)},
                {TREASURE, ChunkRange.of(-90, -60, 70, 110)},
                {MINESHAFT, ChunkRange.of(-120, -120, 120, 120)}};
        for (Object[] testCase : cases) {
            StructurePlacementConfig config = (StructurePlacementConfig) testCase[0];
            ChunkRange area = (ChunkRange) testCase[1];
            Set<String> expected = new HashSet<String>();
            for (int x = area.minChunkX(); x <= area.maxChunkX(); x++) {
                for (int z = area.minChunkZ(); z <= area.maxChunkZ(); z++) {
                    if (engine.isStructureChunk(SEED, config, x, z)) {
                        expected.add(x + "," + z);
                    }
                }
            }
            Set<String> actual = new HashSet<String>();
            for (long[] candidate : candidates(config, area)) {
                actual.add(candidate[0] + "," + candidate[1]);
            }
            assertEquals(expected, actual, config.toString());
            assertTrue(expected.size() > 5, config + " found almost nothing: " + expected.size());
        }
    }

    @Test
    void anOutpostIsNeverWithinTenChunksOfAVillageCandidateAndTheZoneRefusesSome() {
        StructurePlacementEngine engine = new StructurePlacementEngine();
        int outposts = 0;
        int refusedByZone = 0;
        for (int regionX = -40; regionX < 40; regionX++) {
            for (int regionZ = -40; regionZ < 40; regionZ++) {
                long packed = engine.candidateChunk(SEED, OUTPOST, regionX, regionZ);
                int x = StructurePlacementEngine.chunkX(packed);
                int z = StructurePlacementEngine.chunkZ(packed);
                boolean frequency = FrequencyReduction.LEGACY_TYPE_1.keeps(new LegacyRandom(), SEED,
                        OUTPOST.salt(), x, z, OUTPOST.frequency());
                boolean nearVillage = engine.hasStructureChunkInRange(SEED, VILLAGE, x, z, 10);
                assertEquals(frequency && !nearVillage, engine.isStructureChunk(SEED, OUTPOST, x, z));
                if (frequency && !nearVillage) {
                    outposts++;
                }
                if (frequency && nearVillage) {
                    refusedByZone++;
                }
            }
        }
        assertTrue(outposts > 0 && refusedByZone > 0, outposts + " outposts, " + refusedByZone
                + " refused by the exclusion zone");
    }

    @Test
    void configRejectsImpossibleRestrictions() {
        assertThrows(IllegalArgumentException.class, () -> new StructurePlacementConfig(1, 0, 0,
                SpreadType.LINEAR, -0.1F, FrequencyReduction.DEFAULT, null));
        assertThrows(IllegalArgumentException.class, () -> new StructurePlacementConfig(1, 0, 0,
                SpreadType.LINEAR, 1.5F, FrequencyReduction.DEFAULT, null));
        assertThrows(IllegalArgumentException.class, () -> new StructurePlacementConfig(1, 0, 0,
                SpreadType.LINEAR, Float.NaN, FrequencyReduction.DEFAULT, null));
        assertThrows(IllegalArgumentException.class, () -> new StructurePlacementConfig(1, 0, 0,
                SpreadType.LINEAR, 0.5F, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ExclusionZone(StructureType.VILLAGE, VILLAGE, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ExclusionZone(StructureType.VILLAGE, VILLAGE, 17));
        assertThrows(IllegalArgumentException.class,
                () -> new ExclusionZone(StructureType.VILLAGE, null, 4));
        assertTrue(OUTPOST.hasRestrictions() && TREASURE.hasRestrictions());
        assertTrue(!VILLAGE.hasRestrictions());
    }

    @Test
    void scanCostIsReported() {
        // A measurement, not a guarantee: what one frame's region walk costs per structure type.
        Object[][] cases = {{"village", VILLAGE}, {"outpost", OUTPOST}, {"buried treasure", TREASURE},
                {"mineshaft", MINESHAFT}};
        StructurePlacementEngine engine = new StructurePlacementEngine();
        for (Object[] testCase : cases) {
            StructurePlacementConfig config = (StructurePlacementConfig) testCase[1];
            int side = config.spacing() == 1 ? 128 : 128 * config.spacing();
            ChunkRange area = ChunkRange.of(0, 0, side - 1, side - 1);
            long regions = StructurePlacementEngine.regionCount(config, area);
            engine.forEachCandidate(SEED, config, area, Integer.MAX_VALUE, (x, z) -> true);
            long start = System.nanoTime();
            int found = 0;
            for (int round = 0; round < 5; round++) {
                found = engine.forEachCandidate(SEED + round, config, area, Integer.MAX_VALUE,
                        (x, z) -> true);
            }
            double nanosPerRegion = (System.nanoTime() - start) / 5.0 / regions;
            System.out.printf("scan cost %-16s %6.1f ns per region, %d candidates in %d regions%n",
                    testCase[0], nanosPerRegion, found, regions);
            assertTrue(found > 0);
        }
    }
}
