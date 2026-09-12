package com.scrimchic.seedchecker.worldgen;

import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;

/**
 * Identifies one validated candidate: which biome map it was validated against, which structure,
 * and which chunk.
 *
 * <p>The map key carries the world, seed, dimension, Minecraft version and sample height, so a
 * result computed before the player typed a seed in - or before they switched world or dimension -
 * can never be found under the new key.
 */
public final class StructureValidationKey {

    private final BiomeMapKey map;
    private final StructureType type;
    private final int chunkX;
    private final int chunkZ;

    /**
     * Validation samples its own band of heights, so the biome map's display height must not
     * partition this cache. A fixed value keeps every structure layer on one partition, whatever
     * height the biome layer happens to be drawing.
     */
    private static final int VALIDATION_SAMPLE_Y = 0;

    /** The map identity validation results are filed under for the world being looked at. */
    public static BiomeMapKey mapKeyFor(ActiveWorld world) {
        return BiomeMapKey.of(world, VALIDATION_SAMPLE_Y);
    }

    public StructureValidationKey(BiomeMapKey map, StructureType type, int chunkX, int chunkZ) {
        if (map == null || type == null) {
            throw new IllegalArgumentException("map and type are required");
        }
        this.map = map;
        this.type = type;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public BiomeMapKey map() {
        return map;
    }

    public StructureType type() {
        return type;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StructureValidationKey)) {
            return false;
        }
        StructureValidationKey that = (StructureValidationKey) other;
        return chunkX == that.chunkX && chunkZ == that.chunkZ
                && type == that.type && map.equals(that.map);
    }

    @Override
    public int hashCode() {
        int result = map.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + chunkX;
        result = 31 * result + chunkZ;
        return result;
    }

    @Override
    public String toString() {
        return "StructureValidationKey{" + type + " " + chunkX + "," + chunkZ + ", " + map + "}";
    }
}
