package com.scrimchic.seedchecker.world;

/**
 * Where the player is, as a Seed Checker value rather than a Minecraft entity.
 *
 * <p>Only the three coordinates: no yaw, no pitch, no dimension. The map marker is a dot, not an
 * arrow, so a facing angle would be data carried for no reader. Dimension already lives in
 * {@link WorldContext}, where it belongs.
 */
public final class PlayerPosition {

    private static final int CHUNK_SHIFT = 4;

    private final double x;
    private final double y;
    private final double z;

    public PlayerPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    /**
     * The block the player is standing in.
     *
     * <p>Floored, not truncated. Standing at x = -0.5 is inside block -1, and casting to int would
     * report block 0 - a whole block of error, on the wrong side of the axis, for every negative
     * coordinate.
     */
    public int blockX() {
        return floor(x);
    }

    public int blockY() {
        return floor(y);
    }

    public int blockZ() {
        return floor(z);
    }

    /** Arithmetic shift, so chunk -1 covers blocks -16 to -1 rather than folding into chunk 0. */
    public int chunkX() {
        return blockX() >> CHUNK_SHIFT;
    }

    public int chunkZ() {
        return blockZ() >> CHUNK_SHIFT;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    @Override
    public String toString() {
        return "PlayerPosition{" + x + ", " + y + ", " + z + "}";
    }
}
