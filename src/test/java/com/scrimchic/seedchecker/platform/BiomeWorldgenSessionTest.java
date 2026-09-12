package com.scrimchic.seedchecker.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Checks the vanilla-backed biome sampler against the figures recorded in
 * {@code docs/worldgen-engine-spike.md}, on whichever Minecraft version this target builds.
 *
 * <p>The expected biomes differ between 1.16.5 and 1.18+ because the worldgen rewrite genuinely
 * changed them for the same seed; that is the point of pinning both.
 *
 * <p>Canonical ids are compared, never display names, so translation changes cannot make this
 * test lie.
 */
class BiomeWorldgenSessionTest {

    private static final long SEED = -7407337299659424542L;

    private static final int[][] PROBES = {
            {0, 64, 0},
            {1000, 64, 1000},
            {-1000, 64, 500},
            {100000, 64, -100000},
    };

    //? if >=1.18 {
    private static final String[] EXPECTED = {
            "minecraft:meadow",
            "minecraft:ocean",
            "minecraft:forest",
            "minecraft:plains",
    };
    //?} else {
    /*private static final String[] EXPECTED = {
            "minecraft:desert",
            "minecraft:ocean",
            "minecraft:plains",
            "minecraft:forest",
    };
    *///?}

    private static BiomeWorldgenSession session;

    @BeforeAll
    static void openSession() {
        session = BiomeWorldgenSession.create(SEED, BiomeWorldgenSession.OVERWORLD);
        assertNotNull(session, "the overworld must always be generatable");
        assertEquals(SEED, session.seed());
    }

    @Test
    void theSpikeProbesStillSampleTheSameBiomes() {
        for (int i = 0; i < PROBES.length; i++) {
            int[] probe = PROBES[i];
            assertEquals(EXPECTED[i], session.sampleBiomeId(probe[0], probe[1], probe[2]),
                    "block " + probe[0] + "," + probe[1] + "," + probe[2]);
        }
    }

    @Test
    void idsAreCanonicalAndNeverNull() {
        for (int[] probe : PROBES) {
            String id = session.sampleBiomeId(probe[0], probe[1], probe[2]);
            assertNotNull(id);
            assertTrue(id.indexOf(':') > 0, "not a namespaced id: " + id);
            assertEquals(id.toLowerCase(java.util.Locale.ROOT), id);
        }
    }

    @Test
    void samplingIsRepeatable() {
        for (int[] probe : PROBES) {
            String first = session.sampleBiomeId(probe[0], probe[1], probe[2]);
            for (int repeat = 0; repeat < 5; repeat++) {
                assertEquals(first, session.sampleBiomeId(probe[0], probe[1], probe[2]));
            }
        }
    }

    @Test
    void idStringsAreReusedRatherThanRebuiltPerSample() {
        // A tile asks a thousand times; vanilla builds a fresh String on every stringify, so the
        // session caches one per distinct biome. Same instance, not merely equal.
        String first = session.sampleBiomeId(0, 64, 0);
        String again = session.sampleBiomeId(0, 64, 0);
        assertSame(first, again);
    }

    @Test
    void aSeparateSessionOnTheSameSeedAgrees() {
        BiomeWorldgenSession other =
                BiomeWorldgenSession.create(SEED, BiomeWorldgenSession.OVERWORLD);
        assertNotNull(other);
        for (int[] probe : PROBES) {
            assertEquals(session.sampleBiomeId(probe[0], probe[1], probe[2]),
                    other.sampleBiomeId(probe[0], probe[1], probe[2]));
        }
    }

    @Test
    void aDifferentSeedProducesADifferentMap() {
        BiomeWorldgenSession other = BiomeWorldgenSession.create(1234L, BiomeWorldgenSession.OVERWORLD);
        assertNotNull(other);

        int differences = 0;
        for (int block = 0; block < 4000; block += 64) {
            if (!session.sampleBiomeId(block, 64, block)
                    .equals(other.sampleBiomeId(block, 64, block))) {
                differences++;
            }
        }
        assertTrue(differences > 10, "seeds should disagree, differed " + differences + " times");
    }

    @Test
    void blockCoordinatesInsideOneQuartShareABiome() {
        // Biomes live on a four-block grid, which is why the sampler steps by four and why
        // sampling every block would be wasted work.
        String at0 = session.sampleBiomeId(0, 64, 0);
        assertEquals(at0, session.sampleBiomeId(1, 64, 1));
        assertEquals(at0, session.sampleBiomeId(3, 64, 3));
    }

    @Test
    void theCoarsestAffordableStepIsOneOfTheRealLevels() {
        int coarsest = BiomeWorldgenSession.coarsestBlockStep();
        boolean known = false;
        for (com.scrimchic.seedchecker.worldgen.biome.BiomeSampleLevel level
                : com.scrimchic.seedchecker.worldgen.biome.BiomeSampleLevel.values()) {
            known |= level.blockStep() == coarsest;
        }
        assertTrue(known, "coarsest step " + coarsest + " is not a level");
        assertTrue(coarsest >= com.scrimchic.seedchecker.worldgen.biome.BiomeSampleLevel.NEAR
                .blockStep(), "the finest level must always be affordable");
    }

    @Test
    void samplingStaysAffordableUpToTheDeclaredCap() {
        // Guards the measured cap: if a version ever regresses badly at its coarsest allowed step,
        // this catches it instead of a player noticing a stalled map.
        int coarsest = BiomeWorldgenSession.coarsestBlockStep();
        int samples = 1024;
        long start = System.nanoTime();
        for (int i = 0; i < samples; i++) {
            session.sampleBiomeId(i * coarsest, 64, (i % 32) * coarsest);
        }
        double msPerTile = (System.nanoTime() - start) / 1e6;
        assertTrue(msPerTile < 200.0,
                "a tile at step " + coarsest + " took " + msPerTile + " ms");
    }

    @Test
    void onlyTheOverworldIsSupportedSoFar() {
        assertTrue(BiomeWorldgenSession.supportsDimension(BiomeWorldgenSession.OVERWORLD));
        assertFalse(BiomeWorldgenSession.supportsDimension("minecraft:the_nether"));
        assertFalse(BiomeWorldgenSession.supportsDimension("minecraft:the_end"));
        assertFalse(BiomeWorldgenSession.supportsDimension(null));

        assertNull(BiomeWorldgenSession.create(SEED, "minecraft:the_nether"));
    }
}
