package com.scrimchic.seedchecker.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Cache-key identity: every field that changes the pixels must change the key. */
class BiomeTileKeyTest {

    private static final String WORLD = "new-world-70c38c7fa9f1bcac";
    private static final long SEED = -7407337299659424542L;
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String VERSION = "1.20.1";

    private static BiomeMapKey map() {
        return new BiomeMapKey(WORLD, SEED, OVERWORLD, VERSION, 64);
    }

    private static BiomeTileKey tile() {
        return new BiomeTileKey(map(), 4, 3, -7);
    }

    @Test
    void identicalKeysAreEqualAndHashAlike() {
        assertEquals(map(), map());
        assertEquals(map().hashCode(), map().hashCode());
        assertEquals(tile(), tile());
        assertEquals(tile().hashCode(), tile().hashCode());
    }

    @Test
    void aDifferentSeedIsADifferentMap() {
        assertNotEquals(map(), new BiomeMapKey(WORLD, SEED + 1, OVERWORLD, VERSION, 64));
    }

    @Test
    void aDifferentWorldIsADifferentMap() {
        assertNotEquals(map(), new BiomeMapKey("other-world", SEED, OVERWORLD, VERSION, 64));
    }

    @Test
    void aDifferentDimensionIsADifferentMap() {
        assertNotEquals(map(),
                new BiomeMapKey(WORLD, SEED, "minecraft:the_nether", VERSION, 64));
    }

    @Test
    void aDifferentMinecraftVersionIsADifferentMap() {
        // 1.16.5 and 1.20.1 generate different biomes for the same seed, so their tiles must not
        // be interchangeable.
        assertNotEquals(map(), new BiomeMapKey(WORLD, SEED, OVERWORLD, "1.16.5", 64));
    }

    @Test
    void aDifferentSampleHeightIsADifferentMap() {
        assertNotEquals(map(), new BiomeMapKey(WORLD, SEED, OVERWORLD, VERSION, 128));
    }

    @Test
    void levelOfDetailIsPartOfTheTileKey() {
        assertNotEquals(tile(), new BiomeTileKey(map(), 16, 3, -7));
    }

    @Test
    void tilePositionIsPartOfTheTileKey() {
        assertNotEquals(tile(), new BiomeTileKey(map(), 4, 4, -7));
        assertNotEquals(tile(), new BiomeTileKey(map(), 4, 3, -8));
        // Swapping the axes must not collide.
        assertNotEquals(new BiomeTileKey(map(), 4, 1, 2), new BiomeTileKey(map(), 4, 2, 1));
    }

    @Test
    void originsFollowTheGrid() {
        BiomeTileKey key = new BiomeTileKey(map(), 4, 3, -7);
        assertEquals(BiomeTileGrid.tileOriginBlock(3, 4), key.originBlockX());
        assertEquals(BiomeTileGrid.tileOriginBlock(-7, 4), key.originBlockZ());
    }

    @Test
    void requiredFieldsAreChecked() {
        assertThrows(IllegalArgumentException.class,
                () -> new BiomeMapKey(null, SEED, OVERWORLD, VERSION, 64));
        assertThrows(IllegalArgumentException.class,
                () -> new BiomeMapKey(WORLD, SEED, null, VERSION, 64));
        assertThrows(IllegalArgumentException.class,
                () -> new BiomeMapKey(WORLD, SEED, OVERWORLD, null, 64));
        assertThrows(IllegalArgumentException.class, () -> new BiomeTileKey(null, 4, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BiomeTileKey(map(), 0, 0, 0));
    }
}
