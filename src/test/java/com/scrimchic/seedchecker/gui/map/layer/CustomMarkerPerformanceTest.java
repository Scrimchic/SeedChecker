package com.scrimchic.seedchecker.gui.map.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.scrimchic.seedchecker.client.exploration.ExplorationManager;
import com.scrimchic.seedchecker.core.map.MapViewport;
import com.scrimchic.seedchecker.exploration.CustomMarker;
import com.scrimchic.seedchecker.exploration.MarkerType;
import com.scrimchic.seedchecker.gui.map.MapHitTest;
import com.scrimchic.seedchecker.world.ActiveWorld;

/**
 * What custom markers cost per frame and per click at 100, 1,000 and 10,000 in one dimension, for
 * the phase report: the dimension lookup, culling to a 480 by 270 GUI viewport, a click's hit test,
 * and the fills the visible symbols take. The bound asserted only catches something pathological.
 */
class CustomMarkerPerformanceTest {

    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void markerCostIsReported(@TempDir Path root) {
        for (int count : new int[] {100, 1_000, 10_000}) {
            ExplorationManager exploration = CustomMarkerLayerTest.manager(root.resolve("n" + count));
            MarkerType[] types = MarkerType.values();
            for (int i = 0; i < count; i++) {
                // Spread over 20,000 by 20,000 blocks, plus as many again in another dimension.
                exploration.createMarker(OVERWORLD, (int) ((i * 7919L) % 20_000) - 10_000, null,
                        (int) ((i * 104_729L) % 20_000) - 10_000, types[i % types.length], "marker " + i, null);
                exploration.createMarker("minecraft:the_nether", i, null, i, MarkerType.PORTAL, null, null);
            }
            CustomMarkerLayer layer = new CustomMarkerLayer(exploration);
            ActiveWorld world = CustomMarkerLayerTest.unknownSeedWorld(OVERWORLD);

            for (double scale : new double[] {MapViewport.MIN_SCALE, 1.0 / 4, 1.0}) {
                MapViewport view = new MapViewport();
                view.resize(480, 270);
                view.setScale(scale);
                view.setCenter(0, 0);

                // Warm-up, then measured rounds.
                for (int i = 0; i < 50; i++) {
                    layer.visibleMarkers(world, view);
                }
                int rounds = 200;
                long start = System.nanoTime();
                for (int i = 0; i < rounds; i++) {
                    exploration.markersIn(OVERWORLD);
                }
                double lookupNs = (System.nanoTime() - start) / (double) rounds;

                List<CustomMarker> visible = null;
                start = System.nanoTime();
                for (int i = 0; i < rounds; i++) {
                    visible = layer.visibleMarkers(world, view);
                }
                double cullMs = (System.nanoTime() - start) / 1e6 / rounds;

                start = System.nanoTime();
                for (int i = 0; i < rounds; i++) {
                    MapHitTest.pick(layer.hitCandidates(world, view), 240, 135);
                }
                double hitMs = (System.nanoTime() - start) / 1e6 / rounds;

                int fills = 0;
                for (CustomMarker marker : visible) {
                    fills += MarkerSymbols.symbolFills(marker.type());
                }
                int labels = visible.size() <= CustomMarkerLayer.LABEL_LIMIT ? visible.size() : 0;
                System.out.printf("custom markers %5d in dimension, scale 1/%-3d: %5d on screen, markersIn %4.0f ns, "
                                + "cull %6.3f ms, click hit test %6.3f ms, %6d fills + %2d labels%n",
                        count, Math.round(1 / scale), visible.size(), lookupNs, cullMs, hitMs, fills, labels);
                assertTrue(cullMs < 50 && hitMs < 50, "a frame's marker work must stay far below a frame");
            }
            assertEquals(count, exploration.markersIn(OVERWORLD).size());
        }
    }
}
