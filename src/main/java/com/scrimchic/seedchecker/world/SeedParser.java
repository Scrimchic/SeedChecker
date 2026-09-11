package com.scrimchic.seedchecker.world;

/**
 * The one definition of what counts as valid seed text.
 *
 * <p>A Minecraft seed is a signed 64-bit integer, so the whole {@code long} range including
 * {@link Long#MIN_VALUE} must be accepted, and anything that is not such a number must be
 * rejected rather than silently coerced.
 */
public final class SeedParser {

    private SeedParser() {
    }

    /** @return whether {@link #parse(String)} would succeed. */
    public static boolean isValid(String text) {
        if (text == null) {
            return false;
        }
        try {
            Long.parseLong(text.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * @return the seed encoded by {@code text}, ignoring surrounding whitespace
     * @throws NumberFormatException if the text is not a signed 64-bit integer
     */
    public static long parse(String text) {
        if (text == null) {
            throw new NumberFormatException("null");
        }
        return Long.parseLong(text.trim());
    }
}
