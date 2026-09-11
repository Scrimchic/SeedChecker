package com.scrimchic.seedchecker.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The seed priority rule: runtime beats remembered, remembered beats nothing. */
class ActiveWorldTest {

    private static final String VERSION = "1.20.1";
    private static final String OVERWORLD = "minecraft:overworld";

    private static final WorldIdentity SERVER = WorldIdentity.multiplayer("mc.example.com", null);
    private static final WorldIdentity SAVE = WorldIdentity.singleplayer("New World", "New World");

    private static WorldContext singleplayerWith(long seed) {
        return WorldContext.withKnownSeed(VERSION, PlayMode.SINGLEPLAYER, OVERWORLD, seed);
    }

    private static WorldContext multiplayer() {
        return WorldContext.withUnknownSeed(VERSION, PlayMode.MULTIPLAYER, OVERWORLD);
    }

    @Test
    void singleplayerUsesTheRuntimeSeed() {
        ActiveWorld world = new ActiveWorld(singleplayerWith(99L),
                WorldProfile.createNew(SAVE, VERSION, 1L));

        assertTrue(world.hasSeed());
        assertEquals(99L, world.seed());
        assertEquals(SeedSource.RUNTIME, world.seedSource());
        assertEquals(SeedState.KNOWN, world.seedState());
    }

    @Test
    void aRuntimeSeedWinsOverAStaleRememberedOne() {
        WorldProfile profile = WorldProfile.createNew(SAVE, VERSION, 1L);
        profile.setManualSeed(42L);

        ActiveWorld world = new ActiveWorld(singleplayerWith(99L), profile);

        assertEquals(99L, world.seed());
        assertEquals(SeedSource.RUNTIME, world.seedSource());
    }

    @Test
    void multiplayerWithoutAProfileKnowsNothing() {
        ActiveWorld world = new ActiveWorld(multiplayer(), null);

        assertFalse(world.hasSeed());
        assertEquals(SeedState.UNKNOWN, world.seedState());
        assertEquals(SeedSource.UNKNOWN, world.seedSource());
        assertThrows(IllegalStateException.class, world::seed);
        assertFalse(world.acceptsManualSeed());
    }

    @Test
    void multiplayerWithAnEmptyProfileKnowsNothingButAcceptsASeed() {
        ActiveWorld world = new ActiveWorld(multiplayer(), WorldProfile.createNew(SERVER, VERSION, 1L));

        assertFalse(world.hasSeed());
        assertTrue(world.acceptsManualSeed());
        assertFalse(world.hasManualSeed());
    }

    @Test
    void multiplayerFallsBackToTheRememberedSeed() {
        WorldProfile profile = WorldProfile.createNew(SERVER, VERSION, 1L);
        profile.setManualSeed(123456789L);

        ActiveWorld world = new ActiveWorld(multiplayer(), profile);

        assertTrue(world.hasSeed());
        assertEquals(123456789L, world.seed());
        assertEquals(SeedSource.MANUAL, world.seedSource());
        assertTrue(world.hasManualSeed());
    }

    @Test
    void singleplayerNeverOffersManualEditing() {
        ActiveWorld world = new ActiveWorld(singleplayerWith(99L),
                WorldProfile.createNew(SAVE, VERSION, 1L));

        assertFalse(world.acceptsManualSeed());
        assertFalse(world.hasManualSeed());
    }

    @Test
    void outsideAWorldNothingIsKnown() {
        ActiveWorld world = new ActiveWorld(WorldContext.outsideWorld(VERSION), null);

        assertFalse(world.hasSeed());
        assertFalse(world.hasProfile());
        assertFalse(world.acceptsManualSeed());
    }
}
