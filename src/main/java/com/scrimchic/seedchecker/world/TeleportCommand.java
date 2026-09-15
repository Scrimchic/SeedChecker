package com.scrimchic.seedchecker.world;

/**
 * The text of a teleport command and of copied coordinates, without the leading slash.
 *
 * <p>Two forms and nothing else. A column keeps the player's own height with {@code ~}, which is
 * the only height known to be safe; an exact point names a height, and is only ever copied for the
 * player to use on purpose, never sent.
 */
public final class TeleportCommand {

    private TeleportCommand() {
    }

    /** {@code tp @s x ~ z}: to that column, at the height the player is already at. */
    public static String column(int x, int z) {
        return "tp @s " + x + " ~ " + z;
    }

    /** {@code tp @s x y z}: to that exact block. */
    public static String exact(int x, int y, int z) {
        return "tp @s " + x + " " + y + " " + z;
    }

    /** The exact form when the height is known, the column form when it is not. */
    public static String forPosition(int x, Integer y, int z) {
        return y == null ? column(x, z) : exact(x, y.intValue(), z);
    }

    /** {@code x y z}, or {@code x z} for a position without a known height. */
    public static String coordinates(int x, Integer y, int z) {
        return y == null ? x + " " + z : x + " " + y + " " + z;
    }
}
