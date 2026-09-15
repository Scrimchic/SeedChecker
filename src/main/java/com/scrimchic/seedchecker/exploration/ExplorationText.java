package com.scrimchic.seedchecker.exploration;

/**
 * The plain-text rules shared by structure notes and marker labels.
 *
 * <p>Plain UTF-8 text, no markup. Line breaks are folded to {@code \n}, text that is empty once
 * trimmed means "none", and anything over the limit is cut at a code point boundary rather than
 * refused - a hand-edited file must still load.
 */
public final class ExplorationText {

    /** Enough for a paragraph or a short list; a note is not a document. */
    public static final int NOTE_MAX_LENGTH = 4000;

    /** A label is one line on the map. */
    public static final int LABEL_MAX_LENGTH = 64;

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
        return truncate(normalized, NOTE_MAX_LENGTH);
    }

    /** @return the label as stored - one line, trimmed - or {@code null} for no label */
    public static String label(String text) {
        if (text == null) {
            return null;
        }
        String line = text.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').trim();
        return line.isEmpty() ? null : truncate(line, LABEL_MAX_LENGTH);
    }

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        int end = maxLength;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }
}
