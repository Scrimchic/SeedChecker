package com.scrimchic.seedchecker.gui.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The first few lines of a note, short enough for a side panel. Only for showing: the stored note
 * is never touched, and the whole text is only ever in the editor.
 */
public final class NotePreview {

    private final List<String> lines;
    private final int hiddenLines;

    private NotePreview(List<String> lines, int hiddenLines) {
        this.lines = lines;
        this.hiddenLines = hiddenLines;
    }

    /**
     * @param maxLines how many of the note's lines to show at most
     * @param maxChars how long one shown line may be before it ends in "..."
     */
    public static NotePreview of(String note, int maxLines, int maxChars) {
        if (note == null || note.isEmpty()) {
            return new NotePreview(Collections.<String>emptyList(), 0);
        }
        String[] all = note.split("\n", -1);
        List<String> shown = new ArrayList<String>();
        for (int i = 0; i < all.length && i < maxLines; i++) {
            shown.add(shorten(all[i], maxChars));
        }
        return new NotePreview(Collections.unmodifiableList(shown), Math.max(0, all.length - maxLines));
    }

    private static String shorten(String line, int maxChars) {
        if (line.codePointCount(0, line.length()) <= maxChars) {
            return line;
        }
        return line.substring(0, line.offsetByCodePoints(0, Math.max(1, maxChars - 3))) + "...";
    }

    public List<String> lines() {
        return lines;
    }

    /** How many further lines of the note are not shown. */
    public int hiddenLines() {
        return hiddenLines;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
