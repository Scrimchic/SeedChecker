package com.scrimchic.seedchecker.gui.map.layer;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.core.map.MapViewport;
import com.scrimchic.seedchecker.worldgen.StructurePlacementConfig;
import com.scrimchic.seedchecker.worldgen.StructurePlacementEngine;
import com.scrimchic.seedchecker.worldgen.StructurePlacements;
import com.scrimchic.seedchecker.worldgen.StructureType;

/**
 * What the structure layers put on screen, measured rather than guessed, for deciding whether a
 * minimum marker size or zoom rule is needed.
 *
 * <p>Counts raw candidates - every structure chunk before biome validation - so drawn markers are at
 * most this many; the validated map shows fewer. Frame rate is not measured here: that needs the
 * game, and only the region walk each frame pays for is timed.
 */
class MarkerDensityTest {

    private static final long SEED = -7407337299659424542L;

    /** The GUI sizes of the smallest window, and of 1080p at GUI scale 4. */
    private static final int[][] SCREENS = {{320, 240}, {480, 270}};

    private static final double[] SCALES = {1.0 / 64, 1.0 / 16, 1.0 / 4, 0.5, 1.0, 4.0};

    private static final String[] DIMENSIONS = {"minecraft:overworld", "minecraft:the_nether",
            "minecraft:the_end"};

    @Test
    void markerCountsOverlapAndScanCostAreReported() {
        StructurePlacements placements = StructurePlacements.forThisVersion();
        StructurePlacementEngine engine = new StructurePlacementEngine();
        int worstMarkers = 0;

        // Each dimension on its own: only its layers are drawn there, each on its own grid.
        for (String dimension : DIMENSIONS) {
        for (int[] screen : SCREENS) {
            for (double scale : SCALES) {
                final MapViewport viewport = new MapViewport();
                viewport.resize(screen[0], screen[1]);
                viewport.setScale(scale);
                viewport.setCenter(0.0, 0.0);
                ChunkRange visible = ChunkRange.visibleIn(viewport);
                int markerPixels = StructureLayer.markerPixels(scale);

                final List<double[]> all = new ArrayList<double[]>();
                int shown = 0;
                int zoomIn = 0;
                int overlapping = 0;
                long nanos = 0L;
                StringBuilder perType = new StringBuilder();
                for (StructureType type : placements.types()) {
                    if (!type.generatesIn(dimension)) {
                        continue;
                    }
                    StructurePlacementConfig config = placements.get(type, dimension);
                    if (!StructureLayer.isDrawableAt(config, visible, scale)) {
                        zoomIn++;
                        continue;
                    }
                    shown++;
                    final List<double[]> markers = new ArrayList<double[]>();
                    long start = System.nanoTime();
                    engine.forEachCandidate(SEED, config, visible, 1024, (chunkX, chunkZ) -> {
                        markers.add(new double[] {
                                viewport.blockToScreenX((chunkX << 4) + 8.5),
                                viewport.blockToScreenY((chunkZ << 4) + 8.5)});
                        return true;
                    });
                    nanos += System.nanoTime() - start;
                    overlapping += countOverlapping(markers, markerPixels);
                    all.addAll(markers);
                    if (markers.size() > 50) {
                        perType.append(' ').append(type).append('=').append(markers.size());
                    }
                }
                int ambiguous = countOverlapping(all, markerPixels);
                worstMarkers = Math.max(worstMarkers, all.size());
                System.out.printf("markers %s %dx%d at %.4f px/block: %2d px, %2d layers drawn, %2d say "
                                + "zoom in, %4d markers (%5d fills), %4d overlap within a layer, %4d "
                                + "within %d px of another layer's, walk %.2f ms%s%n",
                        dimension, screen[0], screen[1], scale, markerPixels, shown, zoomIn, all.size(),
                        all.size() * 5, overlapping, ambiguous, markerPixels, nanos / 1e6,
                        perType.length() == 0 ? "" : ";" + perType);
                assertTrue(overlapping == 0, "a drawn layer's own markers overlap at scale " + scale);
            }
        }
        }
        assertTrue(worstMarkers > 0, "no marker at any zoom");
    }

    /** How many markers have another one close enough that their squares touch. */
    private static int countOverlapping(List<double[]> markers, int size) {
        int count = 0;
        for (int i = 0; i < markers.size(); i++) {
            for (int j = 0; j < markers.size(); j++) {
                if (i != j && Math.abs(markers.get(i)[0] - markers.get(j)[0]) < size
                        && Math.abs(markers.get(i)[1] - markers.get(j)[1]) < size) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }
}
