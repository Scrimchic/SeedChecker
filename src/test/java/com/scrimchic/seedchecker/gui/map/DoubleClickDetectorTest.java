package com.scrimchic.seedchecker.gui.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DoubleClickDetectorTest {

    @Test
    void aQuickSecondClickOnTheSameTargetIsADoubleClick() {
        DoubleClickDetector clicks = new DoubleClickDetector();
        assertFalse(clicks.click(1000, 50, 50, "marker:a"), "a single click only selects");
        assertTrue(clicks.click(1300, 52, 49, "marker:a"));
        assertFalse(clicks.click(1400, 52, 49, "marker:a"), "the completing click does not start another");
    }

    @Test
    void tooSlowTooFarOrAnotherTargetIsNot() {
        DoubleClickDetector clicks = new DoubleClickDetector();
        clicks.click(0, 10, 10, "marker:a");
        assertFalse(clicks.click(DoubleClickDetector.MAX_INTERVAL_MILLIS + 1, 10, 10, "marker:a"));

        clicks.click(5000, 10, 10, "marker:a");
        assertFalse(clicks.click(5100, 20, 10, "marker:a"), "moved too far");

        clicks.click(9000, 10, 10, "marker:a");
        assertFalse(clicks.click(9100, 10, 10, "structure:village:0:0"));

        clicks.click(12000, 10, 10, null);
        assertFalse(clicks.click(12100, 10, 10, null), "clicking nothing twice opens nothing");

        clicks.click(15000, 10, 10, "marker:a");
        clicks.reset();
        assertFalse(clicks.click(15100, 10, 10, "marker:a"));
    }
}
