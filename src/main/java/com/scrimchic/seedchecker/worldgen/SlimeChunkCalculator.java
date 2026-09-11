package com.scrimchic.seedchecker.worldgen;

/**
 * Decides whether a chunk is a slime chunk, reproducing vanilla bit for bit.
 *
 * <p>Vanilla evaluates, in {@code Slime.checkSlimeSpawnRules}:
 *
 * <pre>
 * WorldgenRandom.seedSlimeChunk(chunkX, chunkZ, worldSeed, 987234911L).nextInt(10) == 0
 * </pre>
 *
 * where {@code seedSlimeChunk} builds the RNG seed as
 *
 * <pre>
 * worldSeed + (long) (x * x * 4987142)
 *           + (long) (x * 5947611)
 *           + (long) (z * z) * 4392871L
 *           + (long) (z * 389711)
 *           ^ 987234911L
 * </pre>
 *
 * <p>Two details of that expression are load bearing and must not be "cleaned up": the
 * multiplications inside each cast are <em>int</em> arithmetic and are expected to overflow and
 * wrap, and only afterwards is the wrapped int sign extended to a long. Widening any of them to
 * long arithmetic produces different results for large coordinates.
 *
 * <p>The RNG itself is {@code java.util.Random}'s LCG. Minecraft reached it through
 * {@code java.util.Random} on 1.16.5, {@code LegacyRandomSource} on 1.20.1 and
 * {@code SingleThreadedRandomSource} on 26.2; all three are the same generator with the same
 * {@code nextInt(bound)} rejection loop, so this class needs no per-version branches. It is
 * reimplemented here rather than calling {@code java.util.Random} so that scanning a viewport
 * full of chunks allocates nothing.
 */
public final class SlimeChunkCalculator {

    private static final long SLIME_SALT = 987234911L;

    /** One in ten chunks is a slime chunk. */
    private static final int BOUND = 10;

    private static final long LCG_MULTIPLIER = 0x5DEECE66DL;
    private static final long LCG_ADDEND = 0xBL;
    private static final long LCG_MASK = (1L << 48) - 1L;

    private SlimeChunkCalculator() {
    }

    /**
     * @param worldSeed the world seed
     * @param chunkX    chunk X coordinate, i.e. block X divided by 16, rounding towards negative
     * @param chunkZ    chunk Z coordinate
     * @return whether slimes may spawn in this chunk below y=40
     */
    public static boolean isSlimeChunk(long worldSeed, int chunkX, int chunkZ) {
        return nextIntBoundTen(chunkSeed(worldSeed, chunkX, chunkZ)) == 0;
    }

    /** The RNG seed vanilla's {@code WorldgenRandom.seedSlimeChunk} produces. */
    private static long chunkSeed(long worldSeed, int chunkX, int chunkZ) {
        long mixed = worldSeed
                + (long) (chunkX * chunkX * 4987142)
                + (long) (chunkX * 5947611)
                + (long) (chunkZ * chunkZ) * 4392871L
                + (long) (chunkZ * 389711);
        return mixed ^ SLIME_SALT;
    }

    /**
     * {@code new Random(seed).nextInt(10)}, inlined.
     *
     * <p>Ten is not a power of two, so this is the rejection-loop branch of
     * {@code java.util.Random.nextInt(int)}; the loop discards the few values at the top of the
     * 31-bit range that would otherwise bias the result.
     */
    private static int nextIntBoundTen(long seed) {
        long state = (seed ^ LCG_MULTIPLIER) & LCG_MASK;
        while (true) {
            state = (state * LCG_MULTIPLIER + LCG_ADDEND) & LCG_MASK;
            int bits = (int) (state >>> (48 - 31));
            int value = bits % BOUND;
            if (bits - value + (BOUND - 1) >= 0) {
                return value;
            }
        }
    }
}
