package com.scrimchic.seedchecker.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import com.scrimchic.seedchecker.SeedChecker;
import com.scrimchic.seedchecker.client.biome.BiomeTileManager;
import com.scrimchic.seedchecker.client.keybind.SeedCheckerKeyBindings;
import com.scrimchic.seedchecker.client.world.WorldProfileManager;

public final class SeedCheckerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // World profiles reach the game through MinecraftBridge, so they are set up here and
        // never from the common entrypoint - a dedicated server must not load client classes.
        WorldProfileManager.initClient(
                FabricLoader.getInstance().getConfigDir().resolve(SeedChecker.MOD_ID));
        BiomeTileManager.initClient();
        SeedCheckerKeyBindings.register();
        SeedChecker.LOGGER.info("Seed Checker client initialized.");
    }
}
