package com.scrimchic.seedchecker.worldgen.biome;

/**
 * The world-space grid biome tiles are cut along.
 *
 * <p>Tiles are anchored to world coordinates, not to the screen, which is the property that makes
 * them reusable: panning slides the viewport across the same tiles, and zooming only changes which
 * {@link BiomeSampleLevel} is in use. A tile always holds {@link #SAMPLES_PER_SIDE} samples per
 * side, so its size in blocks depends on the level - 128 blocks at the finest, 8192 at the
 * coarsest.
 */
public final class BiomeTileGrid {

    /**
     * Samples along one edge of a tile, so 1024 samples per tile.
     *
     * <p>Chosen against the existing {@link com.scrimchic.seedchecker.core.map.MapViewport} range:
     * at one pixel per block a 1080p viewport covers roughly 15 by 9 of these, which is small
     * enough that tiles appear progressively rather than in one long stall, and large enough that
     * per-tile overhead and edge waste stay negligible.
     */
    public static final int SAMPLES_PER_SIDE = 32;

    private BiomeTileGrid() {
    }

    public static int tileSizeInBlocks(int blockStep) {
        return blockStep * SAMPLES_PER_SIDE;
    }

    /** The tile a block coordinate falls in, flooring so that negatives work. */
    public static int tileOf(int blockCoordinate, int blockStep) {
        return Math.floorDiv(blockCoordinate, tileSizeInBlocks(blockStep));
    }

    /** Same, saturating at the int range so an absurdly panned viewport stays finite. */
    public static int tileOfSaturating(long blockCoordinate, int blockStep) {
        long tile = Math.floorDiv(blockCoordinate, (long) tileSizeInBlocks(blockStep));
        if (tile < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        if (tile > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) tile;
    }

    /** Block coordinate of a tile's first sample. */
    public static int tileOriginBlock(int tile, int blockStep) {
        return tile * tileSizeInBlocks(blockStep);
    }

    /**
     * Block coordinate of one sample inside a tile.
     *
     * @param sampleIndex 0 .. {@link #SAMPLES_PER_SIDE} - 1
     */
    public static int sampleBlock(int tile, int sampleIndex, int blockStep) {
        return tileOriginBlock(tile, blockStep) + sampleIndex * blockStep;
    }
}
