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

    private static String repeat(String text, int times) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < times; i++) {
            out.append(text);
        }
        return out.toString();
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
        assertEquals(9, buffer.codePointCount());
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
        assertEquals(1, buffer.codePointCount());

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
    void theLimitIsTheModelsInCodePoints() {
        TextBuffer buffer = new TextBuffer(ExplorationText.NOTE_MAX_CODE_POINTS, true);
        buffer.set(repeat("a", ExplorationText.NOTE_MAX_CODE_POINTS - 1));
        assertTrue(buffer.insert(0x1F3F0), "an emoji is one character, and one is left");
        assertEquals(ExplorationText.NOTE_MAX_CODE_POINTS, buffer.codePointCount());
        assertEquals(ExplorationText.NOTE_MAX_CODE_POINTS + 1, buffer.length(), "though it is two chars");
        assertFalse(buffer.insert('z'));
        assertFalse(buffer.insert(Character.toChars(0x1F3F0)[0]));
        assertFalse(buffer.newline());
        assertEquals(buffer.committedText(), ExplorationText.note(buffer.committedText()),
                "what the buffer holds, the model keeps unchanged");

        buffer.set(repeat("🏰", ExplorationText.NOTE_MAX_CODE_POINTS + 3));
        assertEquals(ExplorationText.NOTE_MAX_CODE_POINTS, buffer.codePointCount());
        assertFalse(Character.isHighSurrogate(buffer.text().charAt(buffer.length() - 1)));
    }

    @Test
    void aHighHalfWaitsForItsPartnerAtTheLimit() {
        TextBuffer buffer = new TextBuffer(3, true);
        buffer.set("ab");
        char[] castle = Character.toChars(0x1F3F0);
        assertTrue(buffer.insert(castle[0]), "the high half takes the last place");
        assertTrue(buffer.insert(castle[1]), "and its low half joins it without another");
        assertEquals("ab🏰", buffer.committedText());
        assertFalse(buffer.insert('c'));
    }

    @Test
    void pasteKeepsLinesInANoteAndFoldsThemInALabel() {
        TextBuffer note = new TextBuffer(100, true);
        type(note, "start end");
        for (int i = 0; i < 3; i++) {
            note.left();
        }
        note.left();
        assertEquals(ExplorationText.codePointLength("\nперший рядок\n🏰 second\n"),
                note.paste("\r\nперший\tрядок\r\n🏰 second\n"), "counted in code points, bell dropped");
        assertEquals("start\nперший рядок\n🏰 second\n end", note.text());

        TextBuffer label = new TextBuffer(100, false);
        label.paste("Main\r\nbase\n🏰");
        assertEquals("Main base 🏰", label.text());
        assertEquals(0, label.paste(null));
        assertEquals(0, label.paste(""));
    }

    @Test
    void pasteStopsAtTheLimitWithoutSplittingAPair() {
        TextBuffer buffer = new TextBuffer(5, true);
        type(buffer, "ab");
        assertEquals(3, buffer.paste("🏰🏰🏰🏰"));
        assertEquals("ab🏰🏰🏰", buffer.committedText());
        assertEquals(0, buffer.paste("more"));

        TextBuffer stray = new TextBuffer(10, true);
        stray.paste("a\uD83Cb\uDF30c");
        assertEquals("abc", stray.text(), "a surrogate half on the clipboard is dropped");
    }
}
