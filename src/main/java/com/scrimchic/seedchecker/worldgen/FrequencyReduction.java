package com.scrimchic.seedchecker.worldgen;

/**
 * How a structure set thins out its placement chunks after grid placement has picked them.
 *
 * <p>Vanilla's {@code StructurePlacement.FrequencyReductionMethod}, transcribed from the bytecode of
 * 1.20.1 and 26.2, which agree: {@code isStructureChunk} asks the method only when the set's
 * {@code frequency} is below 1, and each method reseeds a fresh generator of its own, so the answer
 * depends on nothing but the world seed, the chunk and the set.
 *
 * <p>1.16.5 has no such field. The same three computations live in {@code isFeatureChunk}
 * overrides instead - {@code PillagerOutpostFeature} is {@link #LEGACY_TYPE_1} at one in five,
 * {@code BuriedTreasureFeature} {@link #LEGACY_TYPE_2} and {@code MineshaftFeature}
 * {@link #LEGACY_TYPE_3}, each at its configured probability - which is what the modern names
 * record. {@code VanillaStructurePlacementTest} checks the transcription against each version's
 * own vanilla.
 */
public enum FrequencyReduction {

    /**
     * {@code probabilityReducer}. Vanilla passes its arguments to {@code setLargeFeatureWithSalt} as
     * {@code (seed, salt, x, z)} against a {@code (seed, x, z, salt)} signature, and that is
     * reproduced as written. No vanilla structure set uses it below frequency 1.
     */
    DEFAULT {
        @Override
        boolean keeps(LegacyRandom random, long worldSeed, int salt, int chunkX, int chunkZ,
                      float frequency) {
            random.setLargeFeatureWithSalt(worldSeed, salt, chunkX, chunkZ);
            return random.nextFloat() < frequency;
        }
    },

    /** {@code legacyPillagerOutpostReducer}: decided per 16 by 16 chunk block, one in 1/frequency. */
    LEGACY_TYPE_1 {
        @Override
        boolean keeps(LegacyRandom random, long worldSeed, int salt, int chunkX, int chunkZ,
                      float frequency) {
            int blockX = chunkX >> 4;
            int blockZ = chunkZ >> 4;
            random.setSeed((long) (blockX ^ blockZ << 4) ^ worldSeed);
            random.nextInt();
            return random.nextInt((int) (1.0F / frequency)) == 0;
        }
    },

    /** {@code legacyArbitrarySaltProbabilityReducer}: a float draw under a fixed salt. */
    LEGACY_TYPE_2 {
        @Override
        boolean keeps(LegacyRandom random, long worldSeed, int salt, int chunkX, int chunkZ,
                      float frequency) {
            random.setLargeFeatureWithSalt(worldSeed, chunkX, chunkZ, LEGACY_TYPE_2_SALT);
            return random.nextFloat() < frequency;
        }
    },

    /** {@code legacyProbabilityReducerWithDouble}: a double draw under the large feature seed. */
    LEGACY_TYPE_3 {
        @Override
        boolean keeps(LegacyRandom random, long worldSeed, int salt, int chunkX, int chunkZ,
                      float frequency) {
            random.setLargeFeatureSeed(worldSeed, chunkX, chunkZ);
            return random.nextDouble() < (double) frequency;
        }
    };

    /** The salt {@link #LEGACY_TYPE_2} uses whatever the set's own salt is. */
    static final int LEGACY_TYPE_2_SALT = 10387320;

    /**
     * Whether vanilla keeps a placement chunk.
     *
     * @param random reseeded here; its previous state does not matter
     */
    abstract boolean keeps(LegacyRandom random, long worldSeed, int salt, int chunkX, int chunkZ,
                           float frequency);
}
