package com.scrimchic.seedchecker;

import net.fabricmc.api.ModInitializer;

import java.util.logging.Logger;

public final class SeedChecker implements ModInitializer {

    public static final String MOD_ID = "seedchecker";
    public static final String MOD_NAME = "Seed Checker";

    public static final Logger LOGGER = Logger.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info(MOD_NAME + " initialized.");
    }
}