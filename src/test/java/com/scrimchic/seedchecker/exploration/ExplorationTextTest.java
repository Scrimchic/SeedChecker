package com.scrimchic.seedchecker.exploration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The note and label limits count what a player sees as characters: Unicode code points. */
class ExplorationTextTest {

    private static String repeat(String text, int times) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < times; i++) {
            out.append(text);
        }
        return out.toString();
    }

    @Test
    void anEmojiCountsOnceAgainstTheNoteLimit() {
        String fourThousandCastles = repeat("🏰", ExplorationText.NOTE_MAX_CODE_POINTS);
        assertEquals(8000, fourThousandCastles.length(), "two UTF-16 units each");
        assertEquals(fourThousandCastles, ExplorationText.note(fourThousandCastles), "exactly at the limit, kept whole");

        String note = ExplorationText.note(fourThousandCastles + "🏰x");
        assertEquals(ExplorationText.NOTE_MAX_CODE_POINTS, ExplorationText.codePointLength(note));
        assertFalse(Character.isHighSurrogate(note.charAt(note.length() - 1)), "never half a pair");
    }

    @Test
    void mixedTextIsCutAtTheCodePointLimit() {
        String almost = repeat("a", ExplorationText.NOTE_MAX_CODE_POINTS - 1);
        String note = ExplorationText.note(almost + "🏰🏰");
        assertEquals(almost + "🏰", note, "the first castle is the 4000th character and fits");
    }

    @Test
    void labelsAreLimitedTheSameWay() {
        String label = ExplorationText.label(repeat("✓🏰", 40));
        assertEquals(ExplorationText.LABEL_MAX_CODE_POINTS, ExplorationText.codePointLength(label));
        assertNull(ExplorationText.label(" \n "));
        assertEquals("one two", ExplorationText.label("one\ntwo"));
    }

    @Test
    void codePointHelpers() {
        assertEquals(3, ExplorationText.codePointLength("a🏰b"));
        assertEquals("a🏰", ExplorationText.limitCodePoints("a🏰b", 2));
        assertEquals("a", ExplorationText.limitCodePoints("a🏰b", 1));
        assertEquals("", ExplorationText.limitCodePoints("a🏰b", 0));
        assertEquals("a🏰b", ExplorationText.limitCodePoints("a🏰b", 10));
    }
}
