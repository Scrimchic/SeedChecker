package com.scrimchic.seedchecker.worldgen;

/**
 * The block box a generated structure occupies: the extent of every one of its pieces, inclusive
 * on all six faces, as vanilla assembles them before anything is placed in the world.
 *
 * <p>Not the same box vanilla's {@code StructureStart} reports on 1.18 and later for a structure
 * with terrain adaptation - that one is this box inflated by 12 blocks on every side, which is the
 * reach of the terrain beard rather than of the structure.
 *
 * <p>Deliberately says nothing about where the structure is "centred" in any meaningful sense:
 * {@link #centerX()} is the middle of the box, and it is neither the generation point nor the start
 * piece.
 */
public final class StructureBounds {

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public StructureBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            throw new IllegalArgumentException("inverted bounds " + minX + "," + minY + "," + minZ
                    + " .. " + maxX + "," + maxY + "," + maxZ);
        }
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    /** The middle block of the box on each axis, rounded down. */
    public int centerX() {
        return Math.floorDiv(minX + maxX, 2);
    }

    /** @see #centerX() */
    public int centerY() {
        return Math.floorDiv(minY + maxY, 2);
    }

    /** @see #centerX() */
    public int centerZ() {
        return Math.floorDiv(minZ + maxZ, 2);
    }

    /** @return whether the box contains that block. */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StructureBounds)) {
            return false;
        }
        StructureBounds that = (StructureBounds) other;
        return minX == that.minX && minY == that.minY && minZ == that.minZ
                && maxX == that.maxX && maxY == that.maxY && maxZ == that.maxZ;
    }

    @Override
    public int hashCode() {
        int result = minX;
        result = 31 * result + minY;
        result = 31 * result + minZ;
        result = 31 * result + maxX;
        result = 31 * result + maxY;
        return 31 * result + maxZ;
    }

    @Override
    public String toString() {
        return "x " + minX + ".." + maxX + ", y " + minY + ".." + maxY + ", z " + minZ + ".." + maxZ;
    }
}
