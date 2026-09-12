package com.scrimchic.seedchecker.worldgen.biome;

import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.world.WorldProfile;

/**
 * Everything that decides <em>which</em> biome map is being drawn, as opposed to which part of it.
 *
 * <p>This is the identity that must change whenever cached tiles stop being valid: a different
 * save or server, a seed that was just typed in or cleared, a different dimension, a different
 * Minecraft version (whose worldgen differs), or a different sample height. Because all of it is
 * part of the tile cache key, a tile generated for an old seed can never be shown for a new one -
 * it simply is not found under the new key.
 */
public final class BiomeMapKey {

    private final String worldKey;
    private final long seed;
    private final String dimensionId;
    private final String minecraftVersion;
    private final int sampleY;

    /**
     * @param worldKey         stable identity of the world or server, so two saves with the same
     *                         seed keep separate tiles
     * @param seed             the effective seed the map is drawn from
     * @param dimensionId      e.g. {@code minecraft:overworld}
     * @param minecraftVersion the version whose worldgen produced the samples
     * @param sampleY          the block height the biome slice was taken at
     */
    public BiomeMapKey(String worldKey, long seed, String dimensionId, String minecraftVersion,
                       int sampleY) {
        if (worldKey == null || dimensionId == null || minecraftVersion == null) {
            throw new IllegalArgumentException("worldKey, dimensionId and minecraftVersion are required");
        }
        this.worldKey = worldKey;
        this.seed = seed;
        this.dimensionId = dimensionId;
        this.minecraftVersion = minecraftVersion;
        this.sampleY = sampleY;
    }

    /**
     * Builds the key for the world Seed Checker is currently looking at.
     *
     * <p>Biomes depend only on seed, dimension and version, so a world with no stored profile can
     * safely share a placeholder key: two worlds with the same seed really do have the same biomes.
     *
     * @param sampleY the height this map is sampled at; pass the same value for every consumer
     *                that must share a cache
     */
    public static BiomeMapKey of(ActiveWorld world, int sampleY) {
        WorldProfile profile = world.profile();
        String worldKey = profile != null ? profile.identity().storageKey() : "no-profile";
        return new BiomeMapKey(worldKey, world.seed(), world.context().dimensionId(),
                world.context().minecraftVersion(), sampleY);
    }

    public String worldKey() {
        return worldKey;
    }

    public long seed() {
        return seed;
    }

    public String dimensionId() {
        return dimensionId;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public int sampleY() {
        return sampleY;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BiomeMapKey)) {
            return false;
        }
        BiomeMapKey that = (BiomeMapKey) other;
        return seed == that.seed
                && sampleY == that.sampleY
                && worldKey.equals(that.worldKey)
                && dimensionId.equals(that.dimensionId)
                && minecraftVersion.equals(that.minecraftVersion);
    }

    @Override
    public int hashCode() {
        int result = worldKey.hashCode();
        result = 31 * result + (int) (seed ^ (seed >>> 32));
        result = 31 * result + dimensionId.hashCode();
        result = 31 * result + minecraftVersion.hashCode();
        result = 31 * result + sampleY;
        return result;
    }

    @Override
    public String toString() {
        return "BiomeMapKey{" + worldKey + ", seed=" + seed + ", " + dimensionId
                + ", mc=" + minecraftVersion + ", y=" + sampleY + "}";
    }
}
