package com.scrimchic.seedchecker.gui.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.scrimchic.seedchecker.gui.map.layer.MapLayer;
import com.scrimchic.seedchecker.gui.map.layer.MapLayers;

class RowWindowTest {

    /** Vanilla's font is 9 pixels tall, and a panel row adds one. */
    private static final int LINE_HEIGHT = 10;

    private static final int PADDING = 4;

    private static final int MARGIN = 6;

    /**
     * The smallest GUI height a player can get: the GUI scale is always chosen so the scaled window
     * is at least 320 by 240.
     */
    private static final int SMALLEST_GUI_HEIGHT = 240;

    @Test
    void everyRowIsShownWhenTheyFit() {
        assertEquals(12, RowWindow.visibleRows(12, 12 * LINE_HEIGHT + 2 * PADDING, LINE_HEIGHT,
                PADDING));
        assertEquals(12, RowWindow.visibleRows(12, 10_000, LINE_HEIGHT, PADDING));
    }

    @Test
    void aShortHeightShowsWhatFitsAndNeverNothing() {
        assertEquals(5, RowWindow.visibleRows(40, 5 * LINE_HEIGHT + 2 * PADDING + 9, LINE_HEIGHT,
                PADDING));
        assertEquals(1, RowWindow.visibleRows(40, 3, LINE_HEIGHT, PADDING));
        assertEquals(0, RowWindow.visibleRows(0, 100, LINE_HEIGHT, PADDING));
    }

    @Test
    void scrollingStopsAtTheEnds() {
        assertEquals(0, RowWindow.clampFirstRow(-3, 40, 10));
        assertEquals(30, RowWindow.clampFirstRow(99, 40, 10));
        assertEquals(7, RowWindow.clampFirstRow(7, 40, 10));
        assertEquals(0, RowWindow.clampFirstRow(5, 8, 10));
    }

    @Test
    void theContextPanelOverflowsTheSmallestGuiAndScrollingReachesEveryRow() {
        // The map screen's context panel, counted from MapScreen.buildContextPanel: title, world
        // (heading, four rows, at least two seed rows), player (heading, at least one row), layers
        // (heading, note, raw candidates toggle), four blank separators - and one toggle per layer
        // the dimension lists: biomes, slime chunks, the stronghold and every overworld structure.
        int layers = layerRows("minecraft:overworld");
        int rows = 1 + (1 + 4 + 2) + (1 + 1) + 3 + 4 + layers;
        int unlimited = rows * LINE_HEIGHT + 2 * PADDING;
        System.out.println("context panel: " + rows + " rows, " + unlimited + " px, against "
                + (SMALLEST_GUI_HEIGHT - 2 * MARGIN) + " px on the smallest GUI");
        assertTrue(unlimited > SMALLEST_GUI_HEIGHT - 2 * MARGIN,
                "the panel would fit anyway; the scroll would not be needed");

        // MapScreen: the debug readout takes at most a third, the context panel what remains.
        int debugCap = (SMALLEST_GUI_HEIGHT - 3 * MARGIN) / 3;
        int contextCap = SMALLEST_GUI_HEIGHT - 3 * MARGIN - debugCap;
        int visible = RowWindow.visibleRows(rows, contextCap, LINE_HEIGHT, PADDING);
        assertTrue(visible >= 12, "too few rows to use the panel: " + visible);
        assertTrue(visible * LINE_HEIGHT + 2 * PADDING <= contextCap);
        assertEquals(rows - visible, RowWindow.clampFirstRow(Integer.MAX_VALUE, rows, visible),
                "the last row must be reachable");
    }

    @Test
    void theNetherPanelListsOnlyItsOwnLayersAndStillReachesEveryRow() {
        // The same panel in the nether: biomes and the nether's structures, no slime chunks, no
        // stronghold, no overworld structure.
        int layers = layerRows("minecraft:the_nether");
        int rows = 1 + (1 + 4 + 2) + (1 + 1) + 3 + 4 + layers;
        int debugCap = (SMALLEST_GUI_HEIGHT - 3 * MARGIN) / 3;
        int contextCap = SMALLEST_GUI_HEIGHT - 3 * MARGIN - debugCap;
        int visible = RowWindow.visibleRows(rows, contextCap, LINE_HEIGHT, PADDING);
        System.out.println("nether context panel: " + rows + " rows, " + visible
                + " visible on the smallest GUI");
        assertTrue(layers < layerRows("minecraft:overworld"));
        assertTrue(visible >= 12, "too few rows to use the panel: " + visible);
        assertEquals(rows - visible, RowWindow.clampFirstRow(Integer.MAX_VALUE, rows, visible),
                "the last row must be reachable");
    }

    @Test
    void theEndPanelListsOnlyItsOwnLayers() {
        int layers = layerRows("minecraft:the_end");
        assertEquals(3, layers, "biomes, the end city and custom markers");
        assertTrue(layers < layerRows("minecraft:the_nether"));
    }

    /** The layer toggles the context panel lists in a dimension, as MapScreen filters them. */
    private static int layerRows(String dimensionId) {
        int count = 0;
        for (MapLayer layer : MapLayers.createDefault().all()) {
            if (layer.appliesTo(dimensionId)) {
                count++;
            }
        }
        return count;
    }
}
