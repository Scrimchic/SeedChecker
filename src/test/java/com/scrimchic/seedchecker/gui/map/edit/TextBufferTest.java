package com.scrimchic.seedchecker.gui.map.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.scrimchic.seedchecker.exploration.ExplorationText;

class TextBufferTest {

    private static void type(TextBuffer buffer, String text) {
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            buffer.insert(codePoint);
            i += Character.charCount(codePoint);
        }
    }

    @Test
    void typingInsertsAtTheCursorAndKeysMoveIt() {
        TextBuffer buffer = new TextBuffer(100, true);
        type(buffer, "helo");
        buffer.left();
        type(buffer, "l");
        assertEquals("hello", buffer.text());
        buffer.home();
        type(buffer, ">");
        buffer.end();
        buffer.backspace();
        assertEquals(">hell", buffer.text());
        buffer.home();
        buffer.delete();
        assertEquals("hell", buffer.text());
        assertEquals(0, buffer.cursor());
    }

    @Test
    void multilineAndLineMovement() {
        TextBuffer buffer = new TextBuffer(100, true);
        type(buffer, "first line");
        assertTrue(buffer.newline());
        type(buffer, "2nd");
        buffer.up();
        assertEquals(3, buffer.cursor(), "same column on the line above");
        buffer.end();
        buffer.down();
        assertEquals(buffer.length(), buffer.cursor(), "clamped to the shorter line below");
        assertEquals("first line\n2nd", buffer.text());

        TextBuffer single = new TextBuffer(100, false);
        type(single, "a");
        assertFalse(single.newline());
        single.set("one\ntwo");
        assertEquals("one two", single.text(), "a single-line field folds line breaks");
    }

    @Test
    void unicodeCodePointsAreInsertedAndRemovedWhole() {
        TextBuffer buffer = new TextBuffer(100, true);
        type(buffer, "Скарб 🏰 ✓");
        assertEquals("Скарб 🏰 ✓", buffer.committedText());
        buffer.left();
        buffer.left();
        buffer.backspace();
        assertEquals("Скарб  ✓", buffer.text(), "the castle is one code point, two chars");
        buffer.set("a🏰b");
        buffer.left();
        buffer.left();
        assertEquals(1, buffer.cursor(), "never between the halves of a pair");
        buffer.delete();
        assertEquals("ab", buffer.text());
    }

    @Test
    void surrogateHalvesFromOlderVersionsJoinUpAndStrayOnesAreDropped() {
        TextBuffer buffer = new TextBuffer(100, true);
        char[] castle = Character.toChars(0x1F3F0);
        assertTrue(buffer.insert(castle[0]));
        assertTrue(buffer.insert(castle[1]));
        assertEquals("🏰", buffer.committedText());

        assertFalse(buffer.insert(castle[1]), "a low half without its high half");
        assertTrue(buffer.insert(castle[0]));
        type(buffer, "x");
        assertEquals("🏰x", buffer.committedText(), "a high half that never got its partner");
    }

    @Test
    void controlCharactersAreRefused() {
        TextBuffer buffer = new TextBuffer(100, true);
        assertFalse(buffer.insert('\t'));
        assertFalse(buffer.insert(0x7F));
        assertFalse(buffer.insert(0));
        assertTrue(buffer.insert('\n'));
        assertEquals("\n", buffer.text());
    }

    @Test
    void theModelsLimitIsNeverExceeded() {
        TextBuffer buffer = new TextBuffer(ExplorationText.NOTE_MAX_LENGTH, true);
        StringBuilder almost = new StringBuilder();
        while (almost.length() < ExplorationText.NOTE_MAX_LENGTH - 1) {
            almost.append('a');
        }
        buffer.set(almost.toString());
        assertFalse(buffer.insert(0x1F3F0), "a pair does not fit in one free char");
        assertFalse(buffer.insert(Character.toChars(0x1F3F0)[0]), "neither does its high half");
        assertTrue(buffer.insert('z'));
        assertFalse(buffer.insert('z'));
        assertFalse(buffer.newline());
        assertEquals(ExplorationText.NOTE_MAX_LENGTH, buffer.committedText().length());
        assertEquals(buffer.committedText(), ExplorationText.note(buffer.committedText()),
                "what the buffer holds, the model keeps unchanged");

        StringBuilder over = new StringBuilder(almost).append("🏰🏰");
        buffer.set(over.toString());
        assertTrue(buffer.length() <= ExplorationText.NOTE_MAX_LENGTH);
        assertFalse(Character.isHighSurrogate(buffer.text().charAt(buffer.length() - 1)));
    }
}
