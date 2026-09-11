package com.scrimchic.seedchecker.core.map;

/**
 * Pan/zoom state of a top-down world map.
 *
 * <p>Screen space is measured in pixels with {@code +x} to the right and {@code +y} downwards.
 * World space is measured in blocks with {@code +X} east and {@code +Z} south, so the world
 * {@code Z} axis maps onto the screen {@code y} axis.
 *
 * <p>This class is deliberately free of any Minecraft type so it can be reused by the map GUI,
 * by headless tests and later by the exploration/scanner code.
 */
public final class MapViewport {

    /** Smallest allowed zoom, in pixels per block. */
    public static final double MIN_SCALE = 1.0 / 64.0;

    /** Largest allowed zoom, in pixels per block. */
    public static final double MAX_SCALE = 16.0;

    /** Zoom factor applied per scroll step. */
    private static final double ZOOM_FACTOR = 1.25;

    /** Smallest on-screen distance between two grid lines, in pixels. */
    private static final double MIN_GRID_PIXELS = 24.0;

    private int width = 1;
    private int height = 1;

    private double centerBlockX;
    private double centerBlockZ;
    private double scale = 1.0;

    public void resize(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public double getCenterBlockX() {
        return centerBlockX;
    }

    public double getCenterBlockZ() {
        return centerBlockZ;
    }

    public void setCenter(double blockX, double blockZ) {
        this.centerBlockX = blockX;
        this.centerBlockZ = blockZ;
    }

    /** @return the current zoom in pixels per block. */
    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = clampScale(scale);
    }

    /** Moves the view by a screen-space delta, as produced by a mouse drag. */
    public void panByPixels(double deltaX, double deltaY) {
        centerBlockX -= deltaX / scale;
        centerBlockZ -= deltaY / scale;
    }

    /**
     * Zooms by {@code steps} scroll notches while keeping the world position currently under
     * ({@code anchorX}, {@code anchorY}) pinned to that screen position.
     */
    public void zoomAt(double steps, double anchorX, double anchorY) {
        double target = clampScale(scale * Math.pow(ZOOM_FACTOR, steps));
        if (target == scale) {
            return;
        }
        double anchorBlockX = screenToBlockX(anchorX);
        double anchorBlockZ = screenToBlockZ(anchorY);
        scale = target;
        centerBlockX = anchorBlockX - (anchorX - width / 2.0) / scale;
        centerBlockZ = anchorBlockZ - (anchorY - height / 2.0) / scale;
    }

    public void reset() {
        centerBlockX = 0.0;
        centerBlockZ = 0.0;
        scale = 1.0;
    }

    public double blockToScreenX(double blockX) {
        return width / 2.0 + (blockX - centerBlockX) * scale;
    }

    public double blockToScreenY(double blockZ) {
        return height / 2.0 + (blockZ - centerBlockZ) * scale;
    }

    public double screenToBlockX(double screenX) {
        return centerBlockX + (screenX - width / 2.0) / scale;
    }

    public double screenToBlockZ(double screenY) {
        return centerBlockZ + (screenY - height / 2.0) / scale;
    }

    /**
     * @return the spacing of the finest grid that still keeps grid lines at least
     *         {@link #MIN_GRID_PIXELS} apart, as a power of two number of blocks.
     */
    public int gridStepBlocks() {
        int step = 1;
        while (step * scale < MIN_GRID_PIXELS && step < (1 << 24)) {
            step <<= 1;
        }
        return step;
    }

    private static double clampScale(double value) {
        if (value < MIN_SCALE) {
            return MIN_SCALE;
        }
        if (value > MAX_SCALE) {
            return MAX_SCALE;
        }
        return value;
    }
}
