package com.scrimchic.seedchecker.worldgen;

/**
 * How a structure's offset inside its region is drawn.
 *
 * <p>Vanilla calls this {@code RandomSpreadType}. The two variants consume a different number of
 * random draws, so they are not interchangeable: getting this wrong shifts every structure of that
 * type. Every vanilla structure set Seed Checker currently supports uses {@link #LINEAR}, which is
 * also the codec's default when the field is absent from the datapack.
 */
public enum SpreadType {

    /** A flat distribution across the region. */
    LINEAR {
        @Override
        int offset(LegacyRandom random, int range) {
            return random.nextInt(range);
        }
    },

    /** Biased towards the middle of the region, and costs two draws instead of one. */
    TRIANGULAR {
        @Override
        int offset(LegacyRandom random, int range) {
            return (random.nextInt(range) + random.nextInt(range)) / 2;
        }
    };

    abstract int offset(LegacyRandom random, int range);
}
