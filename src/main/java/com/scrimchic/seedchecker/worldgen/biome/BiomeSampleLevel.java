package com.scrimchic.seedchecker.worldgen.biome;

/**
 * How coarsely a biome map is sampled - Seed Checker's level of detail.
 *
 * <p>Minecraft stores biomes on a four-block "quart" grid, so four blocks is the finest step that
 * carries any new information: sampling every block would return the same biome four times over.
 * The levels step by four from there, which bounds the visual error at one level while keeping the
 * number of distinct levels (and therefore cache churn while zooming) small.
 */
public enum BiomeSampleLevel {

    /** One sample per quart - the finest the biome grid actually holds. */
    NEAR(4),

    MEDIUM(16),

    FAR(64),

    /** For views spanning tens of thousands of blocks. */
    DISTANT(256);

    /**
     * How many screen pixels one sample should cover, at least. Four keeps biome edges legible
     * without sampling more finely than the eye can use, and it is what makes
     * {@link #forPixelsPerBlock} pick a step whose on-screen size stays in [4, 16) pixels.
     */
    private static final double TARGET_PIXELS_PER_SAMPLE = 4.0;

    private final int blockStep;

    BiomeSampleLevel(int blockStep) {
        this.blockStep = blockStep;
    }

    /** Distance in blocks between two neighbouring samples. */
    public int blockStep() {
        return blockStep;
    }

    /** The finest level whose samples still cover enough screen pixels to be worth generating. */
    public static BiomeSampleLevel forPixelsPerBlock(double pixelsPerBlock) {
        BiomeSampleLevel[] levels = values();
        for (BiomeSampleLevel level : levels) {
            if (level.blockStep * pixelsPerBlock >= TARGET_PIXELS_PER_SAMPLE) {
                return level;
            }
        }
        return levels[levels.length - 1];
    }
}
