package com.scrimchic.seedchecker.core.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MapViewportMemoryTest {

    private static MapViewport viewportAt(double centerX, double centerZ, double scale) {
        MapViewport viewport = new MapViewport();
        viewport.resize(1920, 1080);
        viewport.setCenter(centerX, centerZ);
        viewport.setScale(scale);
        return viewport;
    }

    @Test
    void theFirstOpenInAWorldHasNothingToRestore() {
        MapViewportMemory memory = new MapViewportMemory();

        assertFalse(memory.remembers("world-a"));
        assertFalse(memory.restore("world-a", viewportAt(0.0, 0.0, 1.0)),
                "with nothing remembered the caller must pick a start position itself");
    }

    @Test
    void reopeningTheSameWorldComesBackToWhereItWasLeft() {
        MapViewportMemory memory = new MapViewportMemory();
        memory.remember("world-a", viewportAt(1234.0, -567.0, 2.0), false);

        MapViewport reopened = viewportAt(0.0, 0.0, 1.0);
        assertTrue(memory.restore("world-a", reopened));
        assertEquals(1234.0, reopened.getCenterBlockX(), 0.0001);
        assertEquals(-567.0, reopened.getCenterBlockZ(), 0.0001);
        assertEquals(2.0, reopened.getScale(), 0.0001);
    }

    @Test
    void anotherWorldStartsFresh() {
        MapViewportMemory memory = new MapViewportMemory();
        memory.remember("world-a", viewportAt(1234.0, -567.0, 2.0), true);

        MapViewport viewport = viewportAt(0.0, 0.0, 1.0);
        assertFalse(memory.remembers("world-b"));
        assertFalse(memory.restore("world-b", viewport),
                "a different save, server or dimension must not inherit the previous view");
        assertEquals(0.0, viewport.getCenterBlockX(), 0.0001);
    }

    @Test
    void eachDimensionKeepsItsOwnView() {
        // Through a portal and back: each side comes back to where the map was left there, and
        // neither side ever shows the other's coordinates.
        MapViewportMemory memory = new MapViewportMemory();
        memory.remember("world-a|minecraft:overworld", viewportAt(8000.0, -400.0, 1.0), false);
        memory.remember("world-a|minecraft:the_nether", viewportAt(1000.0, -50.0, 0.25), true);

        MapViewport overworld = viewportAt(0.0, 0.0, 4.0);
        assertTrue(memory.restore("world-a|minecraft:overworld", overworld));
        assertEquals(8000.0, overworld.getCenterBlockX(), 0.0001);
        assertEquals(1.0, overworld.getScale(), 0.0001);
        assertFalse(memory.followPlayer());

        MapViewport nether = viewportAt(0.0, 0.0, 4.0);
        assertTrue(memory.restore("world-a|minecraft:the_nether", nether));
        assertEquals(1000.0, nether.getCenterBlockX(), 0.0001);
        assertEquals(0.25, nether.getScale(), 0.0001);
        assertTrue(memory.followPlayer());

        assertFalse(memory.remembers("world-a|minecraft:the_end"));
    }

    @Test
    void theEndKeepsItsOwnViewAcrossAllThreeDimensions() {
        // Overworld, then the nether, then the End, then back through both: each restores its own
        // centre, zoom and follow state, whatever was remembered in between.
        MapViewportMemory memory = new MapViewportMemory();
        memory.remember("world-a|minecraft:overworld", viewportAt(8000.0, -400.0, 1.0), false);
        memory.remember("world-a|minecraft:the_nether", viewportAt(1000.0, -50.0, 0.25), true);
        memory.remember("world-a|minecraft:the_end", viewportAt(-1200.0, 900.0, 0.5), false);

        MapViewport end = viewportAt(0.0, 0.0, 4.0);
        assertTrue(memory.restore("world-a|minecraft:the_end", end));
        assertEquals(-1200.0, end.getCenterBlockX(), 0.0001);
        assertEquals(900.0, end.getCenterBlockZ(), 0.0001);
        assertEquals(0.5, end.getScale(), 0.0001);
        assertFalse(memory.followPlayer());

        MapViewport nether = viewportAt(0.0, 0.0, 4.0);
        assertTrue(memory.restore("world-a|minecraft:the_nether", nether));
        assertEquals(1000.0, nether.getCenterBlockX(), 0.0001);
        assertEquals(0.25, nether.getScale(), 0.0001);
        assertTrue(memory.followPlayer());

        MapViewport overworld = viewportAt(0.0, 0.0, 4.0);
        assertTrue(memory.restore("world-a|minecraft:overworld", overworld));
        assertEquals(8000.0, overworld.getCenterBlockX(), 0.0001);
        assertEquals(-400.0, overworld.getCenterBlockZ(), 0.0001);
        assertEquals(1.0, overworld.getScale(), 0.0001);
        assertFalse(memory.followPlayer());

        MapViewport endAgain = viewportAt(0.0, 0.0, 4.0);
        assertTrue(memory.restore("world-a|minecraft:the_end", endAgain));
        assertEquals(-1200.0, endAgain.getCenterBlockX(), 0.0001);
        assertEquals(0.5, endAgain.getScale(), 0.0001);
        assertFalse(memory.remembers("world-b|minecraft:the_end"));
    }

    @Test
    void followModeSurvivesReopening() {
        MapViewportMemory memory = new MapViewportMemory();
        assertFalse(memory.followPlayer());

        memory.remember("world-a", viewportAt(0.0, 0.0, 1.0), true);
        assertTrue(memory.followPlayer());

        memory.remember("world-a", viewportAt(0.0, 0.0, 1.0), false);
        assertFalse(memory.followPlayer());
    }

    @Test
    void rememberingAgainOverwritesRatherThanAccumulating() {
        MapViewportMemory memory = new MapViewportMemory();
        memory.remember("world-a", viewportAt(10.0, 10.0, 1.0), false);
        memory.remember("world-a", viewportAt(20.0, 30.0, 4.0), false);

        MapViewport viewport = viewportAt(0.0, 0.0, 1.0);
        assertTrue(memory.restore("world-a", viewport));
        assertEquals(20.0, viewport.getCenterBlockX(), 0.0001);
        assertEquals(30.0, viewport.getCenterBlockZ(), 0.0001);
        assertEquals(4.0, viewport.getScale(), 0.0001);
    }

    @Test
    void forgettingSendsTheNextOpenBackToTheDefault() {
        MapViewportMemory memory = new MapViewportMemory();
        memory.remember("world-a", viewportAt(10.0, 10.0, 1.0), true);
        memory.forget();

        assertFalse(memory.remembers("world-a"));
        assertFalse(memory.followPlayer());
        assertFalse(memory.restore("world-a", viewportAt(0.0, 0.0, 1.0)));
    }

    @Test
    void aNullWorldKeyIsNeverRemembered() {
        MapViewportMemory memory = new MapViewportMemory();
        memory.remember("world-a", viewportAt(10.0, 10.0, 1.0), false);

        assertFalse(memory.remembers(null));
        assertFalse(memory.restore(null, viewportAt(0.0, 0.0, 1.0)));
    }
}
