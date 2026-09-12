package com.scrimchic.seedchecker.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.scrimchic.seedchecker.core.map.MapViewport;

class BiomeSampleLevelTest {

    @Test
    void theFinestLevelIsOneQuart() {
        // Four blocks is the resolution the biome grid actually has; anything finer would repeat
        // the same value.
        assertEquals(4, BiomeSampleLevel.NEAR.blockStep());
    }

    @Test
    void levelsGetCoarserInOrder() {
        BiomeSampleLevel[] levels = BiomeSampleLevel.values();
        for (int i = 1; i < levels.length; i++) {
            assertTrue(levels[i].blockStep() > levels[i - 1].blockStep(),
                    levels[i] + " must be coarser than " + levels[i - 1]);
        }
    }

    @Test
    void zoomedInUsesTheFinestLevel() {
        assertEquals(BiomeSampleLevel.NEAR, BiomeSampleLevel.forPixelsPerBlock(MapViewport.MAX_SCALE));
        assertEquals(BiomeSampleLevel.NEAR, BiomeSampleLevel.forPixelsPerBlock(1.0));
    }

    @Test
    void zoomingOutStepsThroughTheLevels() {
        assertEquals(BiomeSampleLevel.MEDIUM, BiomeSampleLevel.forPixelsPerBlock(0.5));
        assertEquals(BiomeSampleLevel.FAR, BiomeSampleLevel.forPixelsPerBlock(0.1));
        assertEquals(BiomeSampleLevel.DISTANT,
                BiomeSampleLevel.forPixelsPerBlock(MapViewport.MIN_SCALE));
    }

    @Test
    void samplesStayLargeEnoughToSeeAcrossTheWholeZoomRange() {
        // The point of the level selection: across every zoom the viewport allows, one sample
        // covers at least a few pixels, so the map is never generated finer than it can be shown.
        for (double scale = MapViewport.MIN_SCALE; scale <= MapViewport.MAX_SCALE; scale *= 1.05) {
            BiomeSampleLevel level = BiomeSampleLevel.forPixelsPerBlock(scale);
            double pixelsPerSample = level.blockStep() * scale;
            assertTrue(pixelsPerSample >= 3.9,
                    "scale " + scale + " gave " + pixelsPerSample + " px per sample");
        }
    }

    @Test
    void anAbsurdlySmallScaleStillReturnsTheCoarsestLevel() {
        assertEquals(BiomeSampleLevel.DISTANT, BiomeSampleLevel.forPixelsPerBlock(1e-9));
        assertEquals(BiomeSampleLevel.DISTANT, BiomeSampleLevel.forPixelsPerBlock(0.0));
    }
}
