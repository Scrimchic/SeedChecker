package com.scrimchic.seedchecker.worldgen;

/**
 * The block vanilla builds a structure from: the position of its {@code GenerationStub}.
 *
 * <p>Only ever produced by a path that reproduces vanilla's own generation point, never estimated.
 * Where that position is not known, a result carries no point at all rather than a plausible one,
 * so anything holding a {@code GenerationPoint} can treat it as exact.
 */
public final class GenerationPoint {

    private final int x;
    private final int y;
    private final int z;

    public GenerationPoint(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GenerationPoint)) {
            return false;
        }
        GenerationPoint that = (GenerationPoint) other;
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * x + y) + z;
    }

    @Override
    public String toString() {
        return x + ", " + y + ", " + z;
    }
}
