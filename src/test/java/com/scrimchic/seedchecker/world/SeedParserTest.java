package com.scrimchic.seedchecker.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SeedParserTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "-1, -1",
            "123456789, 123456789",
            "-7407337299659424542, -7407337299659424542",
            "9223372036854775807, 9223372036854775807",
            "-9223372036854775808, -9223372036854775808",
    })
    void acceptsTheWholeSignedLongRange(String text, long expected) {
        assertTrue(SeedParser.isValid(text), text);
        assertEquals(expected, SeedParser.parse(text));
    }

    @Test
    void ignoresSurroundingWhitespace() {
        assertTrue(SeedParser.isValid("  42  "));
        assertEquals(42L, SeedParser.parse("  42  "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            "-",
            "abc",
            "12a",
            "1.5",
            "1e9",
            "0x10",
            "1 2",
            "9223372036854775808",    // one past Long.MAX_VALUE
            "-9223372036854775809",   // one past Long.MIN_VALUE
            "99999999999999999999",
    })
    void rejectsAnythingThatIsNotASignedLong(String text) {
        assertFalse(SeedParser.isValid(text), text);
        assertThrows(NumberFormatException.class, () -> SeedParser.parse(text));
    }

    @Test
    void rejectsNull() {
        assertFalse(SeedParser.isValid(null));
        assertThrows(NumberFormatException.class, () -> SeedParser.parse(null));
    }
}
