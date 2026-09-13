package com.scrimchic.seedchecker.worldgen;

/**
 * {@code java.util.Random}'s linear congruential generator, reimplemented so worldgen maths can
 * run without allocating and without depending on Minecraft.
 *
 * <p>Minecraft reaches this same generator under three different names across the supported
 * versions - {@code java.util.Random} on 1.16.5, {@code LegacyRandomSource} on 1.20.1 and
 * {@code SingleThreadedRandomSource} on 26.2 - and all three were verified from bytecode to be
 * bit-identical to the JDK original, including the rejection loop in {@code nextInt(bound)}.
 *
 * <p><strong>Not thread safe, and deliberately so.</strong> It carries mutable state precisely so
 * that a whole region scan can reuse one instance instead of allocating per region. Each scanning
 * thread must own its own.
 *
 * <p>{@link SlimeChunkCalculator} intentionally does <em>not</em> use this class: it needs a single
 * draw per chunk in a loop that runs tens of thousands of times per frame, and keeps its generator
 * inlined so that path stays completely allocation free.
 */
public final class LegacyRandom {

    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final long MASK = (1L << 48) - 1L;

    private long state;

    /** Scrambles and installs a seed, exactly as {@code new java.util.Random(seed)} would. */
    public void setSeed(long seed) {
        this.state = (seed ^ MULTIPLIER) & MASK;
    }

    /**
     * {@code java.util.Random.nextInt(bound)}.
     *
     * <p>The power-of-two shortcut and the rejection loop are both part of the contract: they
     * consume different numbers of draws, so replacing either changes every value that follows.
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive, was " + bound);
        }
        if ((bound & (bound - 1)) == 0) {
            return (int) ((bound * (long) next(31)) >> 31);
        }
        while (true) {
            int bits = next(31);
            int value = bits % bound;
            if (bits - value + (bound - 1) >= 0) {
                return value;
            }
        }
    }

    /**
     * {@code java.util.Random.nextDouble()}: 53 bits from two draws, 26 and 27, scaled by 2^-53.
     * {@code LegacyRandomSource} inherits the identical expression from {@code BitRandomSource}.
     */
    public double nextDouble() {
        return (((long) next(26) << 27) + next(27)) * 0x1.0p-53;
    }

    /** {@code java.util.Random.nextLong()}: two 32-bit draws, the second sign extended. */
    public long nextLong() {
        return ((long) next(32) << 32) + next(32);
    }

    private int next(int bits) {
        state = (state * MULTIPLIER + ADDEND) & MASK;
        return (int) (state >>> (48 - bits));
    }
}
