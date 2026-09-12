package com.scrimchic.seedchecker.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BiomeTileTest {

    private static final BiomeMapKey MAP =
            new BiomeMapKey("world", 42L, "minecraft:overworld", "1.20.1", 64);
    private static final BiomeTileKey KEY = new BiomeTileKey(MAP, 4, 1, -2);

    @Test
    void samplesReadBackWhereTheyWereWritten() {
        BiomeTile.Builder builder = BiomeTile.builder(KEY);
        int side = BiomeTileGrid.SAMPLES_PER_SIDE;
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                builder.set(x, z, x == z ? "minecraft:ocean" : "minecraft:plains");
            }
        }
        BiomeTile tile = builder.build();

        assertEquals(KEY, tile.key());
        assertEquals(side, tile.samplesPerSide());
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                assertEquals(x == z ? "minecraft:ocean" : "minecraft:plains", tile.biomeIdAt(x, z));
            }
        }
    }

    @Test
    void repeatedBiomesShareOnePaletteEntry() {
        BiomeTile.Builder builder = BiomeTile.builder(KEY);
        int side = BiomeTileGrid.SAMPLES_PER_SIDE;
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                builder.set(x, z, "minecraft:plains");
            }
        }
        BiomeTile tile = builder.build();

        assertEquals(1, tile.paletteSize());
        assertEquals("minecraft:plains", tile.paletteBiomeId(0));
        assertEquals(0, tile.paletteIndexAt(5, 7));
    }

    @Test
    void coloursComeFromThePaletteAndAreOpaque() {
        BiomeTile.Builder builder = BiomeTile.builder(KEY);
        builder.set(0, 0, "minecraft:ocean");
        builder.set(1, 0, "minecraft:desert");
        BiomeTile tile = builder.build();

        assertEquals(BiomePalette.colorOf("minecraft:ocean"), tile.colorAt(0, 0));
        assertEquals(BiomePalette.colorOf("minecraft:desert"), tile.colorAt(1, 0));
        for (int i = 0; i < tile.paletteSize(); i++) {
            assertEquals(0xFF000000, tile.paletteColor(i) & 0xFF000000,
                    "palette entry " + i + " must be opaque");
        }
    }

    @Test
    void unwrittenSamplesFallIntoThePaletteRatherThanCrashing() {
        BiomeTile tile = BiomeTile.builder(KEY).set(0, 0, "minecraft:plains").build();

        // Byte array starts zeroed, so untouched samples read as palette entry 0.
        assertEquals("minecraft:plains", tile.biomeIdAt(31, 31));
    }

    @Test
    void aNullBiomeIdDoesNotBreakTheTile() {
        BiomeTile tile = BiomeTile.builder(KEY).set(0, 0, null).build();
        assertEquals("", tile.biomeIdAt(0, 0));
        assertEquals(0xFF000000, tile.paletteColor(0) & 0xFF000000);
    }

    @Test
    void aTileRefusesMoreBiomesThanAByteIndexCanHold() {
        BiomeTile.Builder builder = BiomeTile.builder(KEY);
        int side = BiomeTileGrid.SAMPLES_PER_SIDE;
        assertThrows(IllegalStateException.class, () -> {
            int written = 0;
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++) {
                    builder.set(x, z, "test:biome_" + written++);
                }
            }
        });
    }

    @Test
    void mergedRectanglesCoverEverySampleExactlyOnce() {
        // The render representation replaces per-sample drawing, so it has to be an exact cover:
        // no gap would leave a hole in the map, no overlap would waste a draw call.
        BiomeTile.Builder builder = BiomeTile.builder(KEY);
        int side = BiomeTileGrid.SAMPLES_PER_SIDE;
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                builder.set(x, z, "minecraft:biome_" + ((x / 5) + (z / 7) * 3));
            }
        }
        BiomeTile tile = builder.build();

        int[] covered = new int[side * side];
        for (int i = 0; i < tile.rectCount(); i++) {
            int rect = tile.rect(i);
            int x0 = BiomeTile.rectSampleX(rect);
            int z0 = BiomeTile.rectSampleZ(rect);
            int width = BiomeTile.rectWidth(rect);
            int height = BiomeTile.rectHeight(rect);
            int index = BiomeTile.rectPaletteIndex(rect);

            assertTrue(width >= 1 && height >= 1, "degenerate rectangle " + width + "x" + height);
            assertTrue(x0 + width <= side && z0 + height <= side, "rectangle leaves the tile");

            for (int dz = 0; dz < height; dz++) {
                for (int dx = 0; dx < width; dx++) {
                    covered[(z0 + dz) * side + x0 + dx]++;
                    assertEquals(tile.paletteIndexAt(x0 + dx, z0 + dz), index,
                            "rectangle colour disagrees with the sample it covers");
                }
            }
        }
        for (int i = 0; i < covered.length; i++) {
            assertEquals(1, covered[i], "sample " + i + " covered " + covered[i] + " times");
        }
    }

    @Test
    void aUniformTileCollapsesToOneRectangle() {
        BiomeTile.Builder builder = BiomeTile.builder(KEY);
        int side = BiomeTileGrid.SAMPLES_PER_SIDE;
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                builder.set(x, z, "minecraft:ocean");
            }
        }
        assertEquals(1, builder.build().rectCount());
    }

    @Test
    void aFullyMixedTileCannotMergeAndThatIsStillBounded() {
        BiomeTile.Builder builder = BiomeTile.builder(KEY);
        int side = BiomeTileGrid.SAMPLES_PER_SIDE;
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                builder.set(x, z, "minecraft:biome_" + ((x + z) % 2));
            }
        }
        BiomeTile tile = builder.build();

        assertTrue(tile.rectCount() <= side * side,
                "never more rectangles than samples, was " + tile.rectCount());
        assertTrue(tile.rectCount() > 1, "a checkerboard cannot merge into one");
    }

    @Test
    void rectanglePackingSurvivesTheExtremesOfItsFields() {
        BiomeTile.Builder builder = BiomeTile.builder(KEY);
        int side = BiomeTileGrid.SAMPLES_PER_SIDE;
        // One biome everywhere except the very last sample, forcing a rectangle at the far corner
        // and one spanning the full width and almost the full height.
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                builder.set(x, z, "minecraft:plains");
            }
        }
        builder.set(side - 1, side - 1, "minecraft:ocean");
        BiomeTile tile = builder.build();

        boolean sawCorner = false;
        boolean sawFullWidth = false;
        for (int i = 0; i < tile.rectCount(); i++) {
            int rect = tile.rect(i);
            if (BiomeTile.rectSampleX(rect) == side - 1 && BiomeTile.rectSampleZ(rect) == side - 1) {
                sawCorner = true;
                assertEquals("minecraft:ocean",
                        tile.paletteBiomeId(BiomeTile.rectPaletteIndex(rect)));
            }
            if (BiomeTile.rectWidth(rect) == side) {
                sawFullWidth = true;
            }
        }
        assertTrue(sawCorner, "the last sample must be its own rectangle");
        assertTrue(sawFullWidth, "a full-width rectangle must pack and unpack correctly");
    }

    @Test
    void aTileStaysSmall() {
        BiomeTile.Builder builder = BiomeTile.builder(KEY);
        int side = BiomeTileGrid.SAMPLES_PER_SIDE;
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                builder.set(x, z, "minecraft:biome_" + (x % 12));
            }
        }
        BiomeTile tile = builder.build();

        // The palette-plus-index layout is what keeps a cached screenful in the hundreds of KB.
        assertTrue(tile.approximateBytes() < 2048, "tile was " + tile.approximateBytes() + " bytes");
    }
}
