package com.scrimchic.seedchecker.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BiomeTileGridTest {

    @ParameterizedTest
    @ValueSource(ints = {4, 16, 64, 256})
    void tileSizeIsTheStepTimesTheSampleCount(int step) {
        assertEquals(step * BiomeTileGrid.SAMPLES_PER_SIDE, BiomeTileGrid.tileSizeInBlocks(step));
    }

    @Test
    void blocksMapToTheTileThatContainsThem() {
        int step = 4;
        int size = BiomeTileGrid.tileSizeInBlocks(step); // 128

        assertEquals(0, BiomeTileGrid.tileOf(0, step));
        assertEquals(0, BiomeTileGrid.tileOf(size - 1, step));
        assertEquals(1, BiomeTileGrid.tileOf(size, step));
        assertEquals(2, BiomeTileGrid.tileOf(size * 2 + 5, step));
    }

    @Test
    void negativeBlocksFloorRatherThanTruncate() {
        int step = 4;
        int size = BiomeTileGrid.tileSizeInBlocks(step); // 128

        // Block -1 belongs to tile -1. Truncating division would put it in tile 0 and every tile
        // west or north of the origin would be offset by one.
        assertEquals(-1, BiomeTileGrid.tileOf(-1, step));
        assertEquals(-1, BiomeTileGrid.tileOf(-size, step));
        assertEquals(-2, BiomeTileGrid.tileOf(-size - 1, step));
    }

    @Test
    void tileOriginAndSamplesLineUp() {
        int step = 16;
        assertEquals(0, BiomeTileGrid.tileOriginBlock(0, step));
        assertEquals(BiomeTileGrid.tileSizeInBlocks(step), BiomeTileGrid.tileOriginBlock(1, step));
        assertEquals(-BiomeTileGrid.tileSizeInBlocks(step), BiomeTileGrid.tileOriginBlock(-1, step));

        assertEquals(0, BiomeTileGrid.sampleBlock(0, 0, step));
        assertEquals(step, BiomeTileGrid.sampleBlock(0, 1, step));
        assertEquals(BiomeTileGrid.tileSizeInBlocks(step) - step,
                BiomeTileGrid.sampleBlock(0, BiomeTileGrid.SAMPLES_PER_SIDE - 1, step));
    }

    @Test
    void everySampleOfATileFallsBackIntoThatTile() {
        for (int step : new int[] {4, 16, 64, 256}) {
            for (int tile : new int[] {-1000, -1, 0, 1, 1000}) {
                for (int sample = 0; sample < BiomeTileGrid.SAMPLES_PER_SIDE; sample++) {
                    int block = BiomeTileGrid.sampleBlock(tile, sample, step);
                    assertEquals(tile, BiomeTileGrid.tileOf(block, step),
                            "step " + step + " tile " + tile + " sample " + sample);
                }
            }
        }
    }

    @Test
    void saturatingLookupStaysInsideTheIntRange() {
        assertEquals(Integer.MAX_VALUE, BiomeTileGrid.tileOfSaturating(Long.MAX_VALUE, 4));
        assertEquals(Integer.MIN_VALUE, BiomeTileGrid.tileOfSaturating(Long.MIN_VALUE, 4));
        assertEquals(0, BiomeTileGrid.tileOfSaturating(7L, 4));
        assertEquals(-1, BiomeTileGrid.tileOfSaturating(-1L, 4));
    }

    @Test
    void tilesAreSmallEnoughToArriveProgressively() {
        // A sanity bound on the constant: 1024 samples at roughly 10 us each is about 10 ms of
        // work, which is one short job rather than a stall.
        int samples = BiomeTileGrid.SAMPLES_PER_SIDE * BiomeTileGrid.SAMPLES_PER_SIDE;
        assertTrue(samples >= 256 && samples <= 4096, "samples per tile " + samples);
    }
}
