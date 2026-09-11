package com.scrimchic.seedchecker.core.map;

/**
 * The inclusive rectangle of chunks a {@link MapViewport} currently shows.
 *
 * <p>Map layers work chunk by chunk, so this is the unit of work handed to them: it bounds every
 * loop to what is actually on screen instead of to the world.
 */
public final class ChunkRange {

    /** Blocks per chunk along one axis. */
    public static final int CHUNK_SIZE = 16;

    private final int minChunkX;
    private final int minChunkZ;
    private final int maxChunkX;
    private final int maxChunkZ;

    private ChunkRange(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        this.minChunkX = minChunkX;
        this.minChunkZ = minChunkZ;
        this.maxChunkX = maxChunkX;
        this.maxChunkZ = maxChunkZ;
    }

    /** The chunks covered by the viewport, from its top-left corner to its bottom-right one. */
    public static ChunkRange visibleIn(MapViewport viewport) {
        return new ChunkRange(
                chunkOf(viewport.screenToBlockX(0.0)),
                chunkOf(viewport.screenToBlockZ(0.0)),
                chunkOf(viewport.screenToBlockX(viewport.getWidth())),
                chunkOf(viewport.screenToBlockZ(viewport.getHeight())));
    }

    /**
     * Floor-divides a block coordinate into a chunk coordinate, saturating at the int range so a
     * viewport panned absurdly far away still produces a usable, finite range.
     */
    private static int chunkOf(double blockCoordinate) {
        double chunk = Math.floor(blockCoordinate / CHUNK_SIZE);
        if (!(chunk > Integer.MIN_VALUE)) {
            return Integer.MIN_VALUE;
        }
        if (chunk > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) chunk;
    }

    public int minChunkX() {
        return minChunkX;
    }

    public int minChunkZ() {
        return minChunkZ;
    }

    public int maxChunkX() {
        return maxChunkX;
    }

    public int maxChunkZ() {
        return maxChunkZ;
    }

    /** @return how many chunks this range covers, saturating instead of overflowing. */
    public long chunkCount() {
        long wide = (long) maxChunkX - minChunkX + 1L;
        long tall = (long) maxChunkZ - minChunkZ + 1L;
        if (wide <= 0L || tall <= 0L) {
            return 0L;
        }
        return wide > Long.MAX_VALUE / tall ? Long.MAX_VALUE : wide * tall;
    }
}
