package com.scrimchic.seedchecker.client.world;

import java.nio.file.Path;

import com.scrimchic.seedchecker.SeedChecker;
import com.scrimchic.seedchecker.platform.MinecraftBridge;
import com.scrimchic.seedchecker.storage.WorldProfileStorage;
import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.world.WorldContext;
import com.scrimchic.seedchecker.world.WorldIdentity;
import com.scrimchic.seedchecker.world.WorldProfile;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Keeps exactly one {@link WorldProfile} loaded for as long as the player stays in one world.
 *
 * <p>The filesystem is touched only when the world actually changes - on a join, and on a seed
 * edit - never while drawing. Identity is re-read on the client tick, which is cheap because it
 * only reads fields out of the running client; a profile is loaded solely when that identity turns
 * out to be a different world than the one already open.
 *
 * <p>Client side only, because it reaches the game through {@link MinecraftBridge}. It is created
 * from the client entrypoint, so nothing on a dedicated server can reach it.
 */
public final class WorldProfileManager {

    private static WorldProfileManager instance;

    private final WorldProfileStorage storage;

    private WorldIdentity activeIdentity;
    private WorldProfile activeProfile;

    private WorldProfileManager(WorldProfileStorage storage) {
        this.storage = storage;
    }

    /** @param configRoot the Seed Checker config directory that profiles live under */
    public static void initClient(Path configRoot) {
        final WorldProfileManager manager = new WorldProfileManager(new WorldProfileStorage(configRoot));
        instance = manager;
        ClientTickEvents.END_CLIENT_TICK.register(client -> manager.tick());
    }

    public static WorldProfileManager get() {
        if (instance == null) {
            throw new IllegalStateException("Seed Checker world profiles are client side only "
                    + "and are set up from the client entrypoint");
        }
        return instance;
    }

    /**
     * The world as the screen and the map layers should see it: this frame's runtime state paired
     * with the profile that is already in memory.
     */
    public ActiveWorld currentWorld() {
        return new ActiveWorld(MinecraftBridge.currentWorldContext(), activeProfile);
    }

    /**
     * Stores a seed the player typed in, and saves the profile.
     *
     * @return whether the seed was accepted; it is refused when there is no profile, or when
     *         Minecraft already provides the real seed
     */
    public boolean setManualSeed(long seed) {
        if (activeProfile == null || MinecraftBridge.currentWorldContext().hasSeed()) {
            return false;
        }
        if (!activeProfile.setManualSeed(seed)) {
            return false;
        }
        storage.save(activeProfile);
        return true;
    }

    /**
     * Forgets a manually entered seed, and saves the profile.
     *
     * @return whether anything was cleared
     */
    public boolean clearManualSeed() {
        if (activeProfile == null || !activeProfile.clearManualSeed()) {
            return false;
        }
        storage.save(activeProfile);
        return true;
    }

    private void tick() {
        WorldIdentity identity = MinecraftBridge.currentWorldIdentity();
        if (identity == null) {
            activeIdentity = null;
            activeProfile = null;
            return;
        }

        long now = System.currentTimeMillis();
        String minecraftVersion = MinecraftBridge.minecraftVersion();
        boolean dirty = false;

        if (!identity.equals(activeIdentity)) {
            activeIdentity = identity;
            activeProfile = storage.loadOrCreate(identity, minecraftVersion, now);
            activeProfile.markSeen(minecraftVersion, now);
            dirty = true;
            SeedChecker.LOGGER.info("Using world profile " + identity.storageKey()
                    + " for " + identity.displayName());
        }

        // A singleplayer world does not report its seed on the very first tick, so this is checked
        // every tick and saved the one time it actually changes something.
        WorldContext context = MinecraftBridge.currentWorldContext();
        if (context.hasSeed() && activeProfile.applyRuntimeSeed(context.seed())) {
            dirty = true;
        }

        if (dirty) {
            storage.save(activeProfile);
        }
    }
}
