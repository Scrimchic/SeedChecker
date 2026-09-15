package com.scrimchic.seedchecker.gui.map.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.scrimchic.seedchecker.exploration.MarkerType;
import com.scrimchic.seedchecker.exploration.StructureStatus;

class MarkerSymbolsTest {

    /** The shape alone: every drawn pixel the same, so only silhouettes are compared. */
    private static String silhouette(String[] mask) {
        StringBuilder shape = new StringBuilder();
        for (String row : mask) {
            shape.append(row.replace('w', '#').replace('o', '#')).append('\n');
        }
        return shape.toString();
    }

    private static int drawnPixels(String[] mask) {
        int count = 0;
        for (String row : mask) {
            for (int i = 0; i < row.length(); i++) {
                count += row.charAt(i) == '.' ? 0 : 1;
            }
        }
        return count;
    }

    private static int runPixels(int[] runs) {
        int count = 0;
        for (int i = 0; i < runs.length; i += 4) {
            count += runs[i + 2];
        }
        return count;
    }

    @Test
    void everyMarkerTypeHasItsOwnSilhouetteAndColour() {
        Set<String> shapes = new HashSet<String>();
        Set<Integer> colours = new HashSet<Integer>();
        for (MarkerType type : MarkerType.values()) {
            String[] mask = MarkerSymbols.symbolMask(type);
            assertEquals(MarkerSymbols.SYMBOL_SIZE, mask.length);
            assertTrue(shapes.add(silhouette(mask)), type + " has the silhouette of another type");
            assertTrue(colours.add(MarkerSymbols.colorOf(type)), type + " shares a colour");
            boolean outlined = false;
            for (String row : mask) {
                outlined |= row.indexOf('o') >= 0;
            }
            assertTrue(outlined, type + " has no dark outline to read against a bright biome");
            int[] runs = MarkerSymbols.runs(mask);
            assertEquals(drawnPixels(mask), runPixels(runs), type + ": runs must cover the mask exactly");
            assertEquals(runs.length / 4, MarkerSymbols.symbolFills(type));
        }
        assertEquals(MarkerType.values().length, shapes.size(), "silhouettes alone tell every type apart");
    }

    @Test
    void everyStatusBadgeIsADarkSquareWithItsOwnGlyph() {
        assertNull(MarkerSymbols.badgeMask(StructureStatus.UNVISITED));
        assertEquals(0, MarkerSymbols.badgeFills(StructureStatus.UNVISITED));
        Set<String> glyphs = new HashSet<String>();
        for (StructureStatus status : StructureStatus.values()) {
            if (status == StructureStatus.UNVISITED) {
                continue;
            }
            String[] mask = MarkerSymbols.badgeMask(status);
            assertNotNull(mask, status.toString());
            assertEquals(MarkerSymbols.BADGE_SIZE, mask.length);
            int glyph = 0;
            for (int y = 0; y < mask.length; y++) {
                for (int x = 0; x < mask[y].length(); x++) {
                    boolean edge = x == 0 || y == 0 || x == mask.length - 1 || y == mask.length - 1;
                    char c = mask[y].charAt(x);
                    assertFalse(c == '.', status + ": a badge has no transparent pixel");
                    if (edge) {
                        assertEquals('o', c, status + ": the dark ring keeps the glyph readable on any biome");
                    }
                    glyph += c == 'w' ? 1 : 0;
                }
            }
            assertTrue(glyph >= 3, status + " has almost no glyph");
            StringBuilder shape = new StringBuilder();
            for (String row : mask) {
                shape.append(row).append('\n');
            }
            assertTrue(glyphs.add(shape.toString()), status + ": shape, not only colour, must differ");
            assertEquals(MarkerSymbols.runs(mask).length / 4, MarkerSymbols.badgeFills(status));
        }
        assertEquals(4, glyphs.size());
    }
}
