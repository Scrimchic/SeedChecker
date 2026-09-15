package com.scrimchic.seedchecker.gui.map;

import java.util.ArrayList;
import java.util.List;

/**
 * A small stack of text lines drawn over the map, some of which can be clicked.
 *
 * <p>Layout, drawing and hit testing live together here on purpose. The screen used to compute row
 * positions in one place and draw them in another, which is exactly the kind of duplicated
 * arithmetic that drifts apart. A panel is rebuilt each frame, remembers where it last drew
 * itself, and answers {@link #actionAt} for clicks that arrive between frames.
 *
 * <p>A panel may be given a maximum height. It then draws the rows that fit from a scroll offset,
 * with a thin bar showing where it is, so a long panel on a small window stays usable instead of
 * running off the screen.
 *
 * <p>A row is either one line - clickable as a whole or not at all - or a row of buttons, several
 * separately clickable labels side by side, so a choice between a few values takes one click.
 */
public final class TextPanel {

    /** Returned by {@link #actionAt} when nothing clickable is under the cursor. */
    public static final int NO_ACTION = -1;

    static final int PADDING = 4;

    /** Pixels between two buttons of one row. */
    static final int BUTTON_GAP = 8;

    private static final int SCROLLBAR_TRACK = 0x40FFFFFF;
    private static final int SCROLLBAR_THUMB = 0xB0FFFFFF;

    private final int backgroundColor;
    private final int hoverColor;
    private final List<Row> rows = new ArrayList<Row>();

    private int maxHeight = Integer.MAX_VALUE;
    private int requestedFirstRow;
    private int minWidth;

    private boolean drawn;
    private int drawnLeft;
    private int drawnTop;
    private int drawnRight;
    private int drawnBottom;
    private int drawnLineHeight;
    private int drawnFirstRow;
    private int drawnVisibleRows;

    public TextPanel(int backgroundColor, int hoverColor) {
        this.backgroundColor = backgroundColor;
        this.hoverColor = hoverColor;
    }

    public TextPanel line(String text, int color) {
        rows.add(new Row(text, color, NO_ACTION, null, null, null));
        return this;
    }

    public TextPanel blank() {
        return line("", 0);
    }

    /**
     * Adds a clickable row.
     *
     * @param actionId the value {@link #actionAt} returns for this row; must not be
     *                 {@link #NO_ACTION}
     */
    public TextPanel action(int actionId, String text, int color) {
        rows.add(new Row(text, color, actionId, null, null, null));
        return this;
    }

    /** Adds a row of buttons, each its own action; the arrays are parallel. */
    public TextPanel buttons(int[] actionIds, String[] labels, int[] colors) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < labels.length; i++) {
            if (i > 0) {
                text.append("  ");
            }
            text.append(labels[i]);
        }
        rows.add(new Row(text.toString(), 0, NO_ACTION, actionIds.clone(), labels.clone(), colors.clone()));
        return this;
    }

    /** How many rows have been added; the index the next row will get. */
    public int rowCount() {
        return rows.size();
    }

    /** Makes the panel at least this wide, padding included, however short its rows. */
    public TextPanel minWidth(int pixels) {
        this.minWidth = pixels;
        return this;
    }

    /**
     * Caps the panel's height, scrolled to a row; both are clamped when the panel is drawn.
     *
     * @param maxHeightPixels the tallest the panel may be, padding included
     * @param firstRow        the row to start from, as remembered from {@link #firstRow()}
     */
    public TextPanel limitHeight(int maxHeightPixels, int firstRow) {
        this.maxHeight = maxHeightPixels;
        this.requestedFirstRow = firstRow;
        return this;
    }

    public int height(MapCanvas canvas) {
        return visibleRows(lineHeight(canvas)) * lineHeight(canvas) + PADDING * 2;
    }

    /** How many rows the panel shows at its height limit. */
    public int visibleRowCount(MapCanvas canvas) {
        return visibleRows(lineHeight(canvas));
    }

    /** The width the panel will take, so a caller can right-align it before drawing. */
    public int width(MapCanvas canvas) {
        int textWidth = 0;
        for (int i = 0; i < rows.size(); i++) {
            textWidth = Math.max(textWidth, rowWidth(canvas, rows.get(i)));
        }
        return Math.max(minWidth, textWidth + PADDING * 2);
    }

    private static int rowWidth(MapCanvas canvas, Row row) {
        if (row.buttonLabels == null) {
            return canvas.textWidth(row.text);
        }
        int width = 0;
        for (int i = 0; i < row.buttonLabels.length; i++) {
            width += canvas.textWidth(row.buttonLabels[i]) + (i > 0 ? BUTTON_GAP : 0);
        }
        return width;
    }

    /**
     * Draws the panel with its top-left corner at ({@code left}, {@code top}) and highlights
     * whichever clickable row or button the cursor is over.
     */
    public void draw(MapCanvas canvas, int left, int top, double mouseX, double mouseY) {
        int lineHeight = lineHeight(canvas);
        int visible = visibleRows(lineHeight);

        // Recorded before anything is drawn so that hover highlighting below, and the click that
        // may follow, both read this frame's geometry.
        drawn = true;
        drawnLeft = left;
        drawnTop = top;
        drawnRight = left + width(canvas);
        drawnBottom = top + height(canvas);
        drawnLineHeight = lineHeight;
        drawnVisibleRows = visible;
        drawnFirstRow = RowWindow.clampFirstRow(requestedFirstRow, rows.size(), visible);

        canvas.fill(left, top, drawnRight, drawnBottom, backgroundColor);

        // Button spans first, so hover testing below can use them.
        for (int i = 0; i < visible; i++) {
            Row row = rows.get(drawnFirstRow + i);
            if (row.buttonLabels != null) {
                row.layoutButtons(canvas, left + PADDING);
            }
        }
        int hovered = actionAt(mouseX, mouseY);
        for (int i = 0; i < visible; i++) {
            Row row = rows.get(drawnFirstRow + i);
            int y = drawnTop + PADDING + i * lineHeight;
            if (row.buttonLabels == null) {
                int color = row.actionId != NO_ACTION && row.actionId == hovered ? hoverColor : row.color;
                canvas.text(row.text, left + PADDING, y, color);
                continue;
            }
            for (int b = 0; b < row.buttonLabels.length; b++) {
                int color = row.buttonActions[b] == hovered ? hoverColor : row.buttonColors[b];
                canvas.text(row.buttonLabels[b], row.buttonStarts[b], y, color);
            }
        }

        if (isScrollable()) {
            int trackTop = top + PADDING;
            int trackHeight = visible * lineHeight;
            int thumbHeight = Math.max(4, trackHeight * visible / rows.size());
            int thumbTop = trackTop
                    + (trackHeight - thumbHeight) * drawnFirstRow / (rows.size() - visible);
            canvas.fill(drawnRight - 2, trackTop, drawnRight - 1, trackTop + trackHeight,
                    SCROLLBAR_TRACK);
            canvas.fill(drawnRight - 2, thumbTop, drawnRight - 1, thumbTop + thumbHeight,
                    SCROLLBAR_THUMB);
        }
    }

    /** @return the action id of the clickable row or button at that point, or {@link #NO_ACTION}. */
    public int actionAt(double x, double y) {
        if (!drawn || x < drawnLeft || x >= drawnRight || drawnLineHeight <= 0) {
            return NO_ACTION;
        }
        double offset = y - (drawnTop + PADDING);
        if (offset < 0.0) {
            return NO_ACTION;
        }
        int index = (int) (offset / drawnLineHeight);
        if (index >= drawnVisibleRows) {
            return NO_ACTION;
        }
        int rowIndex = drawnFirstRow + index;
        if (rowIndex >= rows.size()) {
            return NO_ACTION;
        }
        Row row = rows.get(rowIndex);
        if (row.buttonLabels == null) {
            return row.actionId;
        }
        if (row.buttonStarts == null) {
            return NO_ACTION;
        }
        for (int b = 0; b < row.buttonLabels.length; b++) {
            // Half the gap on either side belongs to the button, so there is no dead pixel between.
            if (x >= row.buttonStarts[b] - BUTTON_GAP / 2 && x < row.buttonEnds[b] + BUTTON_GAP / 2) {
                return row.buttonActions[b];
            }
        }
        return NO_ACTION;
    }

    /** Whether the point is over the panel as last drawn. */
    public boolean contains(double x, double y) {
        return drawn && x >= drawnLeft && x < drawnRight && y >= drawnTop && y < drawnBottom;
    }

    /** The first row drawn last frame, clamped, for the caller to remember. */
    public int firstRow() {
        return drawnFirstRow;
    }

    /**
     * Where a row's text was drawn from, for drawing over it - a text cursor.
     *
     * @return the top pixel of the row, or {@link Integer#MIN_VALUE} when it was scrolled out of view
     */
    public int rowTop(int rowIndex) {
        if (!drawn || rowIndex < drawnFirstRow || rowIndex >= drawnFirstRow + drawnVisibleRows) {
            return Integer.MIN_VALUE;
        }
        return drawnTop + PADDING + (rowIndex - drawnFirstRow) * drawnLineHeight;
    }

    /** The left pixel every row's text starts at. */
    public int textLeft() {
        return drawnLeft + PADDING;
    }

    /** Whether the last draw had more rows than it could show. */
    public boolean isScrollable() {
        return drawn && drawnVisibleRows < rows.size();
    }

    private int visibleRows(int lineHeight) {
        return maxHeight == Integer.MAX_VALUE
                ? rows.size()
                : RowWindow.visibleRows(rows.size(), maxHeight, lineHeight, PADDING);
    }

    private static int lineHeight(MapCanvas canvas) {
        return canvas.lineHeight() + 1;
    }

    private static final class Row {

        final String text;
        final int color;
        final int actionId;

        final int[] buttonActions;
        final String[] buttonLabels;
        final int[] buttonColors;
        int[] buttonStarts;
        int[] buttonEnds;

        Row(String text, int color, int actionId, int[] buttonActions, String[] buttonLabels,
            int[] buttonColors) {
            this.text = text;
            this.color = color;
            this.actionId = actionId;
            this.buttonActions = buttonActions;
            this.buttonLabels = buttonLabels;
            this.buttonColors = buttonColors;
        }

        void layoutButtons(MapCanvas canvas, int left) {
            buttonStarts = new int[buttonLabels.length];
            buttonEnds = new int[buttonLabels.length];
            int x = left;
            for (int b = 0; b < buttonLabels.length; b++) {
                buttonStarts[b] = x;
                x += canvas.textWidth(buttonLabels[b]);
                buttonEnds[b] = x;
                x += BUTTON_GAP;
            }
        }
    }
}
