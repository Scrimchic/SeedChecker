package com.scrimchic.seedchecker.gui.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class MapHitTestTest {

    private static MapHitTest.Candidate<String> marker(String id, double x, double y) {
        return new MapHitTest.Candidate<String>("marker " + id, x, y, 8, MapHitTest.PRIORITY_CUSTOM_MARKER, id);
    }

    private static MapHitTest.Candidate<String> structure(String key, double x, double y, double radius) {
        return new MapHitTest.Candidate<String>("structure " + key, x, y, radius, MapHitTest.PRIORITY_STRUCTURE, key);
    }

    private static String pick(double x, double y, List<MapHitTest.Candidate<String>> candidates) {
        MapHitTest.Candidate<String> picked = MapHitTest.pick(candidates, x, y);
        return picked == null ? null : picked.target();
    }

    @Test
    void aMarkerOnTopOfAStructureWinsTheTie() {
        List<MapHitTest.Candidate<String>> candidates = Arrays.asList(
                structure("000:3:4", 100, 100, 12), marker("m", 100, 100));
        assertEquals("marker m", pick(102, 101, candidates));
    }

    @Test
    void theNearestWinsWhateverItIs() {
        List<MapHitTest.Candidate<String>> candidates = Arrays.asList(
                structure("000:0:0", 100, 100, 20), marker("m", 110, 100));
        assertEquals("structure 000:0:0", pick(103, 100, candidates));
        assertEquals("marker m", pick(108, 100, candidates));
    }

    @Test
    void anExactTieIsStableWhateverTheOrder() {
        // All four exactly 6 pixels from the click, inside every radius.
        List<MapHitTest.Candidate<String>> candidates = new ArrayList<MapHitTest.Candidate<String>>(Arrays.asList(
                marker("b-id", 94, 100), marker("a-id", 106, 100), structure("001:0:0", 100, 94, 12),
                structure("000:5:5", 100, 106, 12)));
        assertEquals("marker a-id", pick(100, 100, candidates));
        Collections.reverse(candidates);
        assertEquals("marker a-id", pick(100, 100, candidates));

        List<MapHitTest.Candidate<String>> structures = Arrays.asList(
                structure("001:0:0", 100, 94, 12), structure("000:5:5", 100, 106, 12));
        assertEquals("structure 000:5:5", pick(100, 100, structures));
    }

    @Test
    void aClickOutsideEveryRadiusPicksNothing() {
        List<MapHitTest.Candidate<String>> candidates = Arrays.asList(marker("m", 0, 0), structure("s", 50, 0, 3));
        assertNull(pick(9, 0, candidates));
        assertEquals("structure s", pick(57, 0, candidates), "a radius is never below the minimum");
        assertNull(pick(59, 0, candidates));
        assertNull(MapHitTest.pick(Collections.<MapHitTest.Candidate<String>>emptyList(), 0, 0));
    }

    @Test
    void negativeScreenPositionsAreJustNumbers() {
        List<MapHitTest.Candidate<String>> candidates = Arrays.asList(marker("left", -4, -4), marker("right", 4, 4));
        assertEquals("marker left", pick(-1, -1, candidates));
        assertEquals("marker right", pick(1, 1, candidates));
    }
}
