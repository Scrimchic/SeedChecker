package com.scrimchic.seedchecker.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TeleportCommandTest {

    @Test
    void aColumnKeepsThePlayersHeight() {
        assertEquals("tp @s -120 ~ 4000", TeleportCommand.column(-120, 4000));
        assertEquals("tp @s -120 ~ 4000", TeleportCommand.forPosition(-120, null, 4000));
        assertEquals("-120 4000", TeleportCommand.coordinates(-120, null, 4000));
    }

    @Test
    void aKnownHeightIsCopiedExactly() {
        assertEquals("tp @s 7 -59 -8", TeleportCommand.exact(7, -59, -8));
        assertEquals("tp @s 7 -59 -8", TeleportCommand.forPosition(7, -59, -8));
        assertEquals("7 -59 -8", TeleportCommand.coordinates(7, -59, -8));
    }
}
