package com.scrimchic.seedchecker.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlayerPositionTest {

    @Test
    void keepsTheExactCoordinates() {
        PlayerPosition player = new PlayerPosition(123.5, 64.25, -56.75);
        assertEquals(123.5, player.x(), 0.0);
        assertEquals(64.25, player.y(), 0.0);
        assertEquals(-56.75, player.z(), 0.0);
    }

    @ParameterizedTest
    @CsvSource({
            // x, expected block, expected chunk
            "0.0,      0,   0",
            "0.9,      0,   0",
            "15.99,   15,   0",
            "16.0,    16,   1",
            "-0.0001, -1,  -1",
            "-0.5,    -1,  -1",
            "-1.0,    -1,  -1",
            "-1.5,    -2,  -1",
            "-16.0,  -16,  -1",
            "-16.5,  -17,  -2",
            "-17.0,  -17,  -2",
    })
    void blockAndChunkFloorRatherThanTruncate(double x, int expectedBlock, int expectedChunk) {
        PlayerPosition player = new PlayerPosition(x, 64.0, x);

        // Casting to int would round towards zero: a player at -0.5 would be reported in block 0
        // and chunk 0, a whole block and chunk off, for every negative coordinate.
        assertEquals(expectedBlock, player.blockX(), "blockX for " + x);
        assertEquals(expectedBlock, player.blockZ(), "blockZ for " + x);
        assertEquals(expectedChunk, player.chunkX(), "chunkX for " + x);
        assertEquals(expectedChunk, player.chunkZ(), "chunkZ for " + x);
    }

    @Test
    void chunkBoundariesLineUpWithSixteenBlockChunks() {
        for (int block = -80; block < 80; block++) {
            PlayerPosition player = new PlayerPosition(block + 0.5, 64.0, block + 0.5);
            assertEquals(Math.floorDiv(block, 16), player.chunkX(), "block " + block);
            assertEquals(Math.floorDiv(block, 16), player.chunkZ(), "block " + block);
        }
    }

    @Test
    void heightIsFlooredTheSameWay() {
        assertEquals(63, new PlayerPosition(0.0, 63.9, 0.0).blockY());
        assertEquals(-1, new PlayerPosition(0.0, -0.5, 0.0).blockY());
        assertEquals(-64, new PlayerPosition(0.0, -64.0, 0.0).blockY());
    }
}
