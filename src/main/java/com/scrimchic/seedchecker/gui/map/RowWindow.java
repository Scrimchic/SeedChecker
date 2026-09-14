package com.scrimchic.seedchecker.gui.map;

/**
 * Which rows of a text panel fit a height, and how far the panel may scroll.
 *
 * <p>Pure arithmetic, kept apart from {@link TextPanel} so the layout can be checked for the
 * smallest screen Minecraft allows without opening one.
 */
public final class RowWindow {

    private RowWindow() {
    }

    /**
     * @param availablePixels the height the panel may take, padding included
     * @return how many rows are drawn: every row when they fit, otherwise as many as the height
     *         holds, but never fewer than one
     */
    public static int visibleRows(int totalRows, int availablePixels, int lineHeight, int padding) {
        if (totalRows <= 0) {
            return 0;
        }
        long fit = ((long) availablePixels - padding * 2L) / Math.max(1, lineHeight);
        return (int) Math.max(1L, Math.min((long) totalRows, fit));
    }

    /** The first drawn row, kept inside the range that leaves no empty space below the last row. */
    public static int clampFirstRow(int firstRow, int totalRows, int visibleRows) {
        return Math.max(0, Math.min(firstRow, totalRows - visibleRows));
    }
}
