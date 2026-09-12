package com.scrimchic.seedchecker.worldgen.biome;

/** One tile of one biome map: a {@link BiomeMapKey} plus a level of detail and a tile position. */
public final class BiomeTileKey {

    private final BiomeMapKey map;
    private final int blockStep;
    private final int tileX;
    private final int tileZ;

    public BiomeTileKey(BiomeMapKey map, int blockStep, int tileX, int tileZ) {
        if (map == null) {
            throw new IllegalArgumentException("map is required");
        }
        if (blockStep <= 0) {
            throw new IllegalArgumentException("blockStep must be positive, was " + blockStep);
        }
        this.map = map;
        this.blockStep = blockStep;
        this.tileX = tileX;
        this.tileZ = tileZ;
    }

    public BiomeMapKey map() {
        return map;
    }

    public int blockStep() {
        return blockStep;
    }

    public int tileX() {
        return tileX;
    }

    public int tileZ() {
        return tileZ;
    }

    /** Block coordinate of this tile's first sample along X. */
    public int originBlockX() {
        return BiomeTileGrid.tileOriginBlock(tileX, blockStep);
    }

    /** Block coordinate of this tile's first sample along Z. */
    public int originBlockZ() {
        return BiomeTileGrid.tileOriginBlock(tileZ, blockStep);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BiomeTileKey)) {
            return false;
        }
        BiomeTileKey that = (BiomeTileKey) other;
        return blockStep == that.blockStep
                && tileX == that.tileX
                && tileZ == that.tileZ
                && map.equals(that.map);
    }

    @Override
    public int hashCode() {
        int result = map.hashCode();
        result = 31 * result + blockStep;
        result = 31 * result + tileX;
        result = 31 * result + tileZ;
        return result;
    }

    @Override
    public String toString() {
        return "BiomeTileKey{" + tileX + "," + tileZ + " step=" + blockStep + ", " + map + "}";
    }
}
