package com.scrimchic.seedchecker.platform;

import java.nio.file.Path;

import com.scrimchic.seedchecker.world.PlayMode;
import com.scrimchic.seedchecker.world.WorldContext;
import com.scrimchic.seedchecker.world.WorldIdentity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The single place where Seed Checker reads live state out of the running Minecraft client.
 *
 * <p>Everything above this class works on {@link WorldContext} and the other Seed Checker models,
 * so a Minecraft API change only has to be absorbed here.
 *
 * <p>Client side only: it reaches for {@code Minecraft.getInstance()}, so nothing reachable from
 * the mod's main entrypoint may call it. This is deliberately the only place in Seed Checker that
 * touches the client singleton - classes that need world state take a {@link WorldContext}.
 */
public final class MinecraftBridge {

    /** Swapped by Stonecutter for the Minecraft version this jar is built against. */
    private static final String MINECRAFT_VERSION = /*$ minecraft*/ "26.2";

    private MinecraftBridge() {
    }

    public static String minecraftVersion() {
        return MINECRAFT_VERSION;
    }

    /**
     * Reads the current world state.
     *
     * <p>The seed is only reported as {@link com.scrimchic.seedchecker.world.SeedState#KNOWN} when
     * an integrated server is running locally, which is the only case where the client genuinely
     * holds the real world seed. A remote server does not send it, so multiplayer always yields
     * {@code UNKNOWN} until seed recovery exists.
     */
    public static WorldContext currentWorldContext() {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) {
            return WorldContext.outsideWorld(MINECRAFT_VERSION);
        }

        String dimensionId = dimensionId(level);

        if (!client.hasSingleplayerServer()) {
            return WorldContext.withUnknownSeed(MINECRAFT_VERSION, PlayMode.MULTIPLAYER, dimensionId);
        }

        ServerLevel serverLevel = singleplayerLevel(client, level);
        if (serverLevel == null) {
            // The integrated server is still starting up, or already shutting down.
            return WorldContext.withUnknownSeed(MINECRAFT_VERSION, PlayMode.SINGLEPLAYER, dimensionId);
        }
        return WorldContext.withKnownSeed(
                MINECRAFT_VERSION, PlayMode.SINGLEPLAYER, dimensionId, serverLevel.getSeed());
    }

    /**
     * Identifies the world the player is in, so Seed Checker can find its stored profile.
     *
     * <p>A singleplayer world is identified by its save <em>directory name</em>, which is unique
     * inside {@code saves/}, stays the same across restarts, and - unlike the absolute path -
     * survives the game directory being moved. A server is identified by the address the player
     * connected to.
     *
     * <p>Both Overworld and Nether return the same identity: a dimension is runtime context, not
     * a separate world.
     *
     * @return the identity, or {@code null} when there is no world, or when the connection is one
     *         Seed Checker cannot name stably enough to key a profile on
     */
    public static WorldIdentity currentWorldIdentity() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return null;
        }

        if (client.hasSingleplayerServer()) {
            IntegratedServer server = client.getSingleplayerServer();
            if (server == null) {
                return null;
            }
            Path saveDirectory = server.getWorldPath(LevelResource.ROOT).getFileName();
            if (saveDirectory == null) {
                return null;
            }
            return WorldIdentity.singleplayer(
                    saveDirectory.toString(), server.getWorldData().getLevelName());
        }

        ServerData server = client.getCurrentServer();
        if (server == null) {
            // Realms and other joins that carry no server entry. The resolved socket address would
            // be a poor substitute: DNS round-robin would hand out a new profile every join.
            return null;
        }
        return WorldIdentity.multiplayer(server.ip, server.name);
    }

    /** @return the server-side counterpart of the level the player is in, or {@code null}. */
    private static ServerLevel singleplayerLevel(Minecraft client, ClientLevel level) {
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null) {
            return null;
        }
        ServerLevel matching = server.getLevel(level.dimension());
        // All dimensions of a save share the world seed, so the overworld is a safe fallback
        // for a dimension the integrated server has not loaded yet.
        return matching != null ? matching : server.overworld();
    }

    private static String dimensionId(ClientLevel level) {
        // ResourceKey.location() was renamed to identifier() in 26.x.
        //? if >=26.1 {
        return level.dimension().identifier().toString();
        //?} else {
        /*return level.dimension().location().toString();*/
        //?}
    }
}
