package com.scrimchic.seedchecker.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorldIdentityTest {

    @Test
    void sameSaveGivesTheSameIdentityAndStorageKey() {
        WorldIdentity first = WorldIdentity.singleplayer("New World (1)", "New World");
        WorldIdentity second = WorldIdentity.singleplayer("New World (1)", "New World");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first.storageKey(), second.storageKey());
    }

    @Test
    void storageKeyIsStableAcrossRuns() {
        // Pinned on purpose: if this value ever changes, every stored profile silently orphans.
        assertEquals("new-world-70c38c7fa9f1bcac",
                WorldIdentity.singleplayer("New World", "New World").storageKey());
        assertEquals("mc-example-com-6408b550987bc7a8",
                WorldIdentity.multiplayer("mc.example.com", null).storageKey());
    }

    @Test
    void twoSavesWithTheSameDisplayNameStayApart() {
        WorldIdentity first = WorldIdentity.singleplayer("New World", "New World");
        WorldIdentity second = WorldIdentity.singleplayer("New World (1)", "New World");

        assertNotEquals(first, second);
        assertNotEquals(first.storageKey(), second.storageKey());
    }

    @Test
    void singleplayerAndMultiplayerNeverShareAnIdentity() {
        WorldIdentity single = WorldIdentity.singleplayer("shared", "shared");
        WorldIdentity multi = WorldIdentity.multiplayer("shared", "shared");

        assertNotEquals(single, multi);
        assertNotEquals(single.storageKey(), multi.storageKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "mc.example.com",
            "MC.Example.com",
            "  mc.example.com  ",
            "mc.example.com:25565",
            "MC.EXAMPLE.COM:25565",
    })
    void serverAddressSpellingsFoldIntoOneProfile(String address) {
        assertEquals(WorldIdentity.multiplayer("mc.example.com", null),
                WorldIdentity.multiplayer(address, null));
    }

    @Test
    void aNonDefaultPortIsPartOfTheIdentity() {
        assertNotEquals(WorldIdentity.multiplayer("mc.example.com", null),
                WorldIdentity.multiplayer("mc.example.com:25566", null));
    }

    @Test
    void identityIgnoresTheDisplayName() {
        // Renaming a server list entry must not orphan the profile behind it.
        assertEquals(WorldIdentity.multiplayer("mc.example.com", "Old name"),
                WorldIdentity.multiplayer("mc.example.com", "New name"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "New World",
            "../../etc/passwd",
            "con",
            "a name with spaces and !@#$%^&*() symbols",
            "..",
            "very long world name that goes on and on and on well past any sane limit",
    })
    void storageKeyIsAlwaysASafeSingleDirectoryName(String name) {
        String key = WorldIdentity.singleplayer(name, name).storageKey();

        assertTrue(key.matches("[a-z0-9-]+"), key);
        assertTrue(key.length() <= 32 + 1 + 16, key);
        assertNotEquals("..", key);
    }

    @Test
    void anUnusableNameStillProducesAKey() {
        String key = WorldIdentity.singleplayer("///", "///").storageKey();
        assertTrue(key.startsWith("world-"), key);
    }
}
