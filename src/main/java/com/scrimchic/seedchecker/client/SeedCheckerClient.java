package com.scrimchic.seedchecker.client;

import net.fabricmc.api.ClientModInitializer;

import com.scrimchic.seedchecker.SeedChecker;
import com.scrimchic.seedchecker.client.keybind.SeedCheckerKeyBindings;

public final class SeedCheckerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SeedCheckerKeyBindings.register();
        SeedChecker.LOGGER.info("Seed Checker client initialized.");
    }
}
