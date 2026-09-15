package com.scrimchic.seedchecker.gui.map.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class WrappedTextTest {

    /** Six pixels a code point, like a monospace font. */
    private static final WrappedText.Measure SIX = new WrappedText.Measure() {
        @Override
        public int width(String text) {
            return text.codePointCount(0, text.length()) * 6;
        }
    };

    private static List<String> texts(String text, List<WrappedText.Line> lines) {
        List<String> out = new ArrayList<String>();
        for (WrappedText.Line line : lines) {
            out.add(text.substring(line.start(), line.end()));
        }
        return out;
    }

    @Test
    void emptyTextIsOneEmptyLine() {
        List<WrappedText.Line> lines = WrappedText.wrap("", 60, SIX);
        assertEquals(1, lines.size());
        assertEquals(0, WrappedText.lineOf(lines, 0));
    }

    @Test
    void lineBreaksAlwaysStartALineAndLongLinesBreakAtSpaces() {
        String text = "one two three four\n\nlast";
        List<WrappedText.Line> lines = WrappedText.wrap(text, 60, SIX);
        // 60 px is ten characters.
        assertEquals(java.util.Arrays.asList("one two ", "three four", "", "last"), texts(text, lines));
    }

    @Test
    void aWordLongerThanTheLineBreaksMidWordWithoutSplittingACodePoint() {
        String text = "🏰🏰🏰🏰🏰";
        List<WrappedText.Line> lines = WrappedText.wrap(text, 12, SIX);
        assertEquals(java.util.Arrays.asList("🏰🏰", "🏰🏰", "🏰"), texts(text, lines));
    }

    @Test
    void theCursorBelongsToTheLineItIsDrawnOn() {
        String text = "abcdefghij klm\nxy";
        List<WrappedText.Line> lines = WrappedText.wrap(text, 60, SIX);
        assertEquals(3, lines.size());
        assertEquals(0, WrappedText.lineOf(lines, 0));
        assertEquals(1, WrappedText.lineOf(lines, lines.get(1).start()), "a wrap boundary starts the next line");
        assertEquals(1, WrappedText.lineOf(lines, text.indexOf('\n')), "end of a paragraph stays on it");
        assertEquals(2, WrappedText.lineOf(lines, text.length()));
        for (int cursor = 0; cursor <= text.length(); cursor++) {
            int line = WrappedText.lineOf(lines, cursor);
            assertTrue(cursor >= lines.get(line).start() && cursor <= lines.get(line).end(), "cursor " + cursor);
        }
    }
}
