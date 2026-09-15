package com.scrimchic.seedchecker.exploration;

/**
 * The plain-text rules shared by structure notes and marker labels, and the one place their limits
 * are counted.
 *
 * <p>Plain UTF-8 text, no markup. Line breaks are folded to {@code \n}, text that is empty once
 * trimmed means "none", and anything over the limit is cut rather than refused - a hand-edited file
 * must still load.
 *
 * <p><strong>Limits are in Unicode code points</strong>, the characters a player sees: an emoji
 * counts once, as a letter does, not as the two UTF-16 units Java stores it in. A cut never splits
 * a surrogate pair. The editor counts through {@link #codePointLength} so it enforces exactly the
 * limit the model keeps.
 */
public final class ExplorationText {

    /** Enough for a paragraph or a short list; a note is not a document. In code points. */
    public static final int NOTE_MAX_CODE_POINTS = 4000;

    /** A label is one line on the map. In code points. */
    public static final int LABEL_MAX_CODE_POINTS = 64;

    private ExplorationText() {
    }

    /** @return the note as stored, or {@code null} for no note */
    public static String note(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.trim().isEmpty()) {
            return null;
        }
        return limitCodePoints(normalized, NOTE_MAX_CODE_POINTS);
    }

    /** @return the label as stored - one line, trimmed - or {@code null} for no label */
    public static String label(String text) {
        if (text == null) {
            return null;
        }
        String line = text.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').trim();
        return line.isEmpty() ? null : limitCodePoints(line, LABEL_MAX_CODE_POINTS);
    }

    /** How many code points a text has; a lone surrogate counts as one. */
    public static int codePointLength(CharSequence text) {
        return Character.codePointCount(text, 0, text.length());
    }

    /** The text cut to at most that many code points, never inside a surrogate pair. */
    public static String limitCodePoints(String text, int maxCodePoints) {
        if (text.length() <= maxCodePoints || codePointLength(text) <= maxCodePoints) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxCodePoints));
    }
}
