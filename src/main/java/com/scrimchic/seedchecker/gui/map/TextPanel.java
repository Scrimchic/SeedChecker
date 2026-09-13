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
 */
public final class TextPanel {

    /** Returned by {@link #actionAt} when nothing clickable is under the cursor. */
    public static final int NO_ACTION = -1;

    private static final int PADDING = 4;

    private final int backgroundColor;
    private final int hoverColor;
    private final List<Row> rows = new ArrayList<Row>();

    private boolean drawn;
    private int drawnLeft;
    private int drawnTop;
    private int drawnRight;
    private int drawnLineHeight;

    public TextPanel(int backgroundColor, int hoverColor) {
        this.backgroundColor = backgroundColor;
        this.hoverColor = hoverColor;
    }

    public TextPanel line(String text, int color) {
        rows.add(new Row(text, color, NO_ACTION));
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
        rows.add(new Row(text, color, actionId));
        return this;
    }

    public int height(MapCanvas canvas) {
        return rows.size() * lineHeight(canvas) + PADDING * 2;
    }

    /** The width the panel will take, so a caller can right-align it before drawing. */
    public int width(MapCanvas canvas) {
        int textWidth = 0;
        for (int i = 0; i < rows.size(); i++) {
            textWidth = Math.max(textWidth, canvas.textWidth(rows.get(i).text));
        }
        return textWidth + PADDING * 2;
    }

    /**
     * Draws the panel with its top-left corner at ({@code left}, {@code top}) and highlights
     * whichever clickable row the cursor is over.
     */
    public void draw(MapCanvas canvas, int left, int top, double mouseX, double mouseY) {
        int lineHeight = lineHeight(canvas);

        // Recorded before anything is drawn so that hover highlighting below, and the click that
        // may follow, both read this frame's geometry.
        drawn = true;
        drawnLeft = left;
        drawnTop = top;
        drawnRight = left + width(canvas);
        drawnLineHeight = lineHeight;

        canvas.fill(left, top, drawnRight, top + height(canvas), backgroundColor);

        int hovered = actionAt(mouseX, mouseY);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int color = row.actionId != NO_ACTION && row.actionId == hovered ? hoverColor : row.color;
            canvas.text(row.text, left + PADDING, rowTop(i), color);
        }
    }

    /** @return the action id of the clickable row at that point, or {@link #NO_ACTION}. */
    public int actionAt(double x, double y) {
        if (!drawn || x < drawnLeft || x >= drawnRight || drawnLineHeight <= 0) {
            return NO_ACTION;
        }
        double offset = y - (drawnTop + PADDING);
        if (offset < 0.0) {
            return NO_ACTION;
        }
        int index = (int) (offset / drawnLineHeight);
        return index < rows.size() ? rows.get(index).actionId : NO_ACTION;
    }

    private int rowTop(int index) {
        return drawnTop + PADDING + index * drawnLineHeight;
    }

    private static int lineHeight(MapCanvas canvas) {
        return canvas.lineHeight() + 1;
    }

    private static final class Row {

        final String text;
        final int color;
        final int actionId;

        Row(String text, int color, int actionId) {
            this.text = text;
            this.color = color;
            this.actionId = actionId;
        }
    }
}
