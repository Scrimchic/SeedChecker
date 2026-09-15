package com.scrimchic.seedchecker.gui.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class NotePreviewTest {

    @Test
    void noNoteIsAnEmptyPreview() {
        assertTrue(NotePreview.of(null, 3, 20).isEmpty());
        assertTrue(NotePreview.of("", 3, 20).isEmpty());
    }

    @Test
    void onlyTheFirstLinesShowAndLongOnesAreShortened() {
        String note = "first\nsecond is a rather long line indeed\nthird\nfourth\nfifth";
        NotePreview preview = NotePreview.of(note, 3, 12);
        assertEquals(Arrays.asList("first", "second is...", "third"), preview.lines());
        assertEquals(2, preview.hiddenLines());
        assertEquals("first\nsecond is a rather long line indeed\nthird\nfourth\nfifth", note, "the note is untouched");
    }

    @Test
    void shorteningNeverSplitsACodePoint() {
        NotePreview preview = NotePreview.of("🏰🏰🏰🏰🏰🏰", 1, 5);
        assertEquals("🏰🏰...", preview.lines().get(0));
    }
}
