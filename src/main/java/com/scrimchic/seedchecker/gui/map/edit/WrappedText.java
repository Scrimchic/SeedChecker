package com.scrimchic.seedchecker.gui.map.edit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Breaks text into the lines a panel of some width can show, and finds the line holding a cursor.
 *
 * <p>Line breaks in the text always start a new line; a line that is too wide breaks after its last
 * space that fits, or mid-word when there is none. Code points are never split. The width of a
 * string is asked of a {@link Measure}, which on screen is the font and in a test is anything.
 */
public final class WrappedText {

    /** The pixel width of a string. */
    public interface Measure {
        int width(String text);
    }

    /** One visual line: the characters from {@code start} up to, not including, {@code end}. */
    public static final class Line {

        private final int start;
        private final int end;

        Line(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public int start() {
            return start;
        }

        public int end() {
            return end;
        }
    }

    private WrappedText() {
    }

    /** Never empty: empty text is one empty line. */
    public static List<Line> wrap(String text, int maxWidth, Measure measure) {
        List<Line> lines = new ArrayList<Line>();
        int position = 0;
        while (true) {
            int newline = text.indexOf('\n', position);
            int lineEnd = newline < 0 ? text.length() : newline;
            wrapLogicalLine(text, position, lineEnd, Math.max(1, maxWidth), measure, lines);
            if (newline < 0) {
                break;
            }
            position = newline + 1;
        }
        return Collections.unmodifiableList(lines);
    }

    private static void wrapLogicalLine(String text, int start, int lineEnd, int maxWidth, Measure measure,
                                        List<Line> into) {
        if (start == lineEnd) {
            into.add(new Line(start, start));
            return;
        }
        int lineStart = start;
        while (lineStart < lineEnd) {
            int end = lineStart;
            while (end < lineEnd) {
                int next = end + Character.charCount(text.codePointAt(end));
                if (end > lineStart && measure.width(text.substring(lineStart, next)) > maxWidth) {
                    break;
                }
                end = next;
            }
            if (end < lineEnd) {
                int space = text.lastIndexOf(' ', end - 1);
                if (space >= lineStart && space + 1 > lineStart && space + 1 < end) {
                    end = space + 1;
                }
            }
            into.add(new Line(lineStart, end));
            lineStart = end;
        }
    }

    /**
     * The visual line a cursor belongs to. A cursor at the break between two wrapped lines of one
     * paragraph is at the start of the second; at the end of a paragraph it stays on its last line.
     */
    public static int lineOf(List<Line> lines, int cursor) {
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (cursor >= line.start && cursor < line.end) {
                return i;
            }
            if (cursor == line.end) {
                boolean last = i == lines.size() - 1;
                if (last || lines.get(i + 1).start > line.end) {
                    return i;
                }
            }
        }
        return lines.size() - 1;
    }
}
