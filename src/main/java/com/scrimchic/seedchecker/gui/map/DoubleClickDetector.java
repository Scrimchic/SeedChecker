package com.scrimchic.seedchecker.gui.map;

/**
 * Tells a second click on the same object, soon and close enough, from two separate clicks.
 *
 * <p>Measured by the screen itself on every version - one clock, one threshold - rather than through
 * the double-click flag only 26.x hands a screen. A double click needs the same target key, at most
 * {@link #MAX_INTERVAL_MILLIS} apart and at most {@link #MAX_DISTANCE_PIXELS} away; the click that
 * completes one does not also start the next.
 */
public final class DoubleClickDetector {

    static final long MAX_INTERVAL_MILLIS = 400L;

    static final double MAX_DISTANCE_PIXELS = 4.0;

    private String lastTarget;
    private long lastMillis;
    private double lastX;
    private double lastY;

    /**
     * @param target a key naming what the click selected, or {@code null} when it selected nothing
     * @return whether this click completes a double click on that target
     */
    public boolean click(long nowMillis, double x, double y, String target) {
        boolean isDouble = target != null && target.equals(lastTarget)
                && nowMillis >= lastMillis && nowMillis - lastMillis <= MAX_INTERVAL_MILLIS
                && Math.abs(x - lastX) <= MAX_DISTANCE_PIXELS && Math.abs(y - lastY) <= MAX_DISTANCE_PIXELS;
        if (isDouble) {
            lastTarget = null;
        } else {
            lastTarget = target;
            lastMillis = nowMillis;
            lastX = x;
            lastY = y;
        }
        return isDouble;
    }

    public void reset() {
        lastTarget = null;
    }
}
