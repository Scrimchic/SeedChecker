package com.scrimchic.seedchecker.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldProfileTest {

    private static final WorldIdentity SERVER = WorldIdentity.multiplayer("mc.example.com", null);
    private static final WorldIdentity SAVE = WorldIdentity.singleplayer("New World", "New World");

    private static WorldProfile serverProfile() {
        return WorldProfile.createNew(SERVER, "1.20.1", 1000L);
    }

    @Test
    void aNewProfileKnowsNothing() {
        WorldProfile profile = serverProfile();

        assertFalse(profile.hasSeed());
        assertEquals(SeedState.UNKNOWN, profile.seedState());
        assertEquals(SeedSource.UNKNOWN, profile.seedSource());
        assertThrows(IllegalStateException.class, profile::seed);
    }

    @Test
    void manualSeedIsRemembered() {
        WorldProfile profile = serverProfile();

        assertTrue(profile.setManualSeed(-7407337299659424542L));
        assertTrue(profile.hasSeed());
        assertEquals(-7407337299659424542L, profile.seed());
        assertEquals(SeedSource.MANUAL, profile.seedSource());
    }

    @Test
    void settingTheSameManualSeedTwiceIsNotAChange() {
        WorldProfile profile = serverProfile();

        assertTrue(profile.setManualSeed(42L));
        assertFalse(profile.setManualSeed(42L));
        assertTrue(profile.setManualSeed(43L));
    }

    @Test
    void runtimeSeedOverridesAManualOne() {
        WorldProfile profile = WorldProfile.createNew(SAVE, "1.20.1", 1000L);
        profile.setManualSeed(42L);

        assertTrue(profile.applyRuntimeSeed(99L));
        assertEquals(99L, profile.seed());
        assertEquals(SeedSource.RUNTIME, profile.seedSource());
    }

    @Test
    void manualSeedCannotShadowARuntimeSeed() {
        WorldProfile profile = WorldProfile.createNew(SAVE, "1.20.1", 1000L);
        profile.applyRuntimeSeed(99L);

        assertFalse(profile.setManualSeed(42L));
        assertEquals(99L, profile.seed());
        assertEquals(SeedSource.RUNTIME, profile.seedSource());
    }

    @Test
    void clearingRemovesOnlyManualSeeds() {
        WorldProfile profile = serverProfile();
        profile.setManualSeed(42L);

        assertTrue(profile.clearManualSeed());
        assertFalse(profile.hasSeed());
        assertEquals(SeedState.UNKNOWN, profile.seedState());
        assertEquals(SeedSource.UNKNOWN, profile.seedSource());
        assertFalse(profile.clearManualSeed());
    }

    @Test
    void clearingCannotHideARuntimeSeed() {
        WorldProfile profile = WorldProfile.createNew(SAVE, "1.20.1", 1000L);
        profile.applyRuntimeSeed(99L);

        assertFalse(profile.clearManualSeed());
        assertEquals(99L, profile.seed());
    }

    @Test
    void reapplyingTheSameRuntimeSeedIsNotAChange() {
        WorldProfile profile = WorldProfile.createNew(SAVE, "1.20.1", 1000L);

        assertTrue(profile.applyRuntimeSeed(99L));
        assertFalse(profile.applyRuntimeSeed(99L));
        assertTrue(profile.applyRuntimeSeed(100L));
    }

    @Test
    void markSeenTracksTheVersionAndTimestamp() {
        WorldProfile profile = serverProfile();

        assertFalse(profile.markSeen("1.20.1", 1000L));
        assertTrue(profile.markSeen("1.20.1", 2000L));
        assertEquals(2000L, profile.lastSeenAt());
        assertEquals(1000L, profile.createdAt());
        assertTrue(profile.markSeen("26.2", 2000L));
        assertEquals("26.2", profile.minecraftVersion());
    }

    @Test
    void aHandEditedFileClaimingASeedWithoutASourceIsNotTrusted() {
        WorldProfile profile = WorldProfile.restore(
                SERVER, "1.20.1", SeedState.KNOWN, 42L, SeedSource.UNKNOWN, 1L, 2L);

        assertFalse(profile.hasSeed());
        assertEquals(SeedSource.UNKNOWN, profile.seedSource());
    }

    @Test
    void aSourceWithoutAKnownSeedIsNotTrustedEither() {
        WorldProfile profile = WorldProfile.restore(
                SERVER, "1.20.1", SeedState.UNKNOWN, 42L, SeedSource.MANUAL, 1L, 2L);

        assertFalse(profile.hasSeed());
        assertEquals(SeedSource.UNKNOWN, profile.seedSource());
    }
}
