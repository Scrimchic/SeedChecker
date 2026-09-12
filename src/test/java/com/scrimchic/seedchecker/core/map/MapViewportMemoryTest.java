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
