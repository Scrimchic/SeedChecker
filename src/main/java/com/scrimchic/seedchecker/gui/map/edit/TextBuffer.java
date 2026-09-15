package com.scrimchic.seedchecker.gui.map.edit;

/**
 * The text of one editable field and where the cursor is in it.
 *
 * <p>Plain text only. The limit is the model's own - {@code ExplorationText}'s, in UTF-16 units - so
 * a buffer never holds more than the model would keep, and input past it is refused rather than
 * cut later. The cursor never stands between the two halves of a surrogate pair, and a code point
 * is inserted or removed whole.
 *
 * <p>Older Minecraft versions deliver a character outside the Basic Multilingual Plane as two
 * separate {@code char} events; a lone high surrogate is accepted only with room for its partner,
 * and a stray one is dropped from {@link #committedText()}.
 */
public final class TextBuffer {

    private final int maxLength;
    private final boolean multiline;
    private final StringBuilder text = new StringBuilder();
    private int cursor;

    public TextBuffer(int maxLength, boolean multiline) {
        this.maxLength = maxLength;
        this.multiline = multiline;
    }

    /** Replaces the whole text and puts the cursor at its end. */
    public void set(String value) {
        text.setLength(0);
        if (value != null) {
            String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
            if (!multiline) {
                normalized = normalized.replace('\n', ' ');
            }
            int end = Math.min(normalized.length(), maxLength);
            if (end > 0 && end < normalized.length() && Character.isHighSurrogate(normalized.charAt(end - 1))) {
                end--;
            }
            text.append(normalized, 0, end);
        }
        cursor = text.length();
    }

    public String text() {
        return text.toString();
    }

    /** The text to store: as typed, minus any surrogate half without its partner. */
    public String committedText() {
        StringBuilder clean = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    clean.append(c).append(text.charAt(++i));
                }
            } else if (!Character.isLowSurrogate(c)) {
                clean.append(c);
            }
        }
        return clean.toString();
    }

    public int cursor() {
        return cursor;
    }

    public int length() {
        return text.length();
    }

    public int maxLength() {
        return maxLength;
    }

    public boolean isMultiline() {
        return multiline;
    }

    /**
     * Inserts one typed code point, or one half of a surrogate pair, at the cursor.
     *
     * @return whether it was inserted; control characters and input past the limit are not
     */
    public boolean insert(int codePoint) {
        if (codePoint == '\n') {
            return newline();
        }
        if (!Character.isValidCodePoint(codePoint) || Character.isISOControl(codePoint)) {
            return false;
        }
        if (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
            char half = (char) codePoint;
            if (Character.isHighSurrogate(half)) {
                if (text.length() + 2 > maxLength) {
                    return false;
                }
            } else if (cursor == 0 || !Character.isHighSurrogate(text.charAt(cursor - 1))
                    || text.length() + 1 > maxLength) {
                return false;
            }
            text.insert(cursor, half);
            cursor++;
            return true;
        }
        char[] chars = Character.toChars(codePoint);
        if (text.length() + chars.length > maxLength) {
            return false;
        }
        text.insert(cursor, chars);
        cursor += chars.length;
        return true;
    }

    /** @return whether a line break was inserted; never in a single-line field */
    public boolean newline() {
        if (!multiline || text.length() + 1 > maxLength) {
            return false;
        }
        text.insert(cursor, '\n');
        cursor++;
        return true;
    }

    /** Removes the code point before the cursor. */
    public boolean backspace() {
        if (cursor == 0) {
            return false;
        }
        int width = isPairEndingAt(cursor) ? 2 : 1;
        text.delete(cursor - width, cursor);
        cursor -= width;
        return true;
    }

    /** Removes the code point after the cursor. */
    public boolean delete() {
        if (cursor >= text.length()) {
            return false;
        }
        int width = isPairStartingAt(cursor) ? 2 : 1;
        text.delete(cursor, cursor + width);
        return true;
    }

    public void left() {
        if (cursor > 0) {
            cursor -= isPairEndingAt(cursor) ? 2 : 1;
        }
    }

    public void right() {
        if (cursor < text.length()) {
            cursor += isPairStartingAt(cursor) ? 2 : 1;
        }
    }

    /** To the start of the current line. */
    public void home() {
        cursor = lineStart(cursor);
    }

    /** To the end of the current line. */
    public void end() {
        cursor = lineEnd(cursor);
    }

    /** One line up, at the same column or the end of a shorter line. */
    public void up() {
        int start = lineStart(cursor);
        if (start == 0) {
            cursor = 0;
            return;
        }
        int column = cursor - start;
        int previousStart = lineStart(start - 1);
        cursor = clampToPair(Math.min(previousStart + column, start - 1));
    }

    /** One line down, at the same column or the end of a shorter line. */
    public void down() {
        int end = lineEnd(cursor);
        if (end >= text.length()) {
            cursor = text.length();
            return;
        }
        int column = cursor - lineStart(cursor);
        int nextStart = end + 1;
        cursor = clampToPair(Math.min(nextStart + column, lineEnd(nextStart)));
    }

    private int lineStart(int index) {
        int newline = text.lastIndexOf("\n", index - 1);
        return newline < 0 ? 0 : newline + 1;
    }

    private int lineEnd(int index) {
        int newline = text.indexOf("\n", index);
        return newline < 0 ? text.length() : newline;
    }

    private boolean isPairEndingAt(int index) {
        return index >= 2 && Character.isLowSurrogate(text.charAt(index - 1))
                && Character.isHighSurrogate(text.charAt(index - 2));
    }

    private boolean isPairStartingAt(int index) {
        return index + 1 < text.length() && Character.isHighSurrogate(text.charAt(index))
                && Character.isLowSurrogate(text.charAt(index + 1));
    }

    /** Moves an index that landed inside a surrogate pair back to its start. */
    private int clampToPair(int index) {
        return index > 0 && index < text.length() && Character.isLowSurrogate(text.charAt(index))
                && Character.isHighSurrogate(text.charAt(index - 1)) ? index - 1 : index;
    }
}
