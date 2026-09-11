package com.scrimchic.seedchecker.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import com.scrimchic.seedchecker.SeedChecker;
import com.scrimchic.seedchecker.gui.map.MapScreen;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;*/
//?}
import net.minecraft.client.KeyMapping;

import org.lwjgl.glfw.GLFW;

/** Registers and services the Seed Checker key bindings. */
public final class SeedCheckerKeyBindings {

    private static final String OPEN_MAP_KEY = "key.seedchecker.open_map";

    /**
     * Seed Checker's own section in the Controls screen.
     *
     * <p>Before 26.x a category is just a translation key; 26.x replaced that with a registered
     * {@code KeyMapping.Category} whose label comes from {@code key.category.<namespace>.<path>}.
     * Both translation keys live in the shared {@code en_us.json}, so only the declaration below
     * differs between versions - everything that uses {@code CATEGORY} is shared.
     */
    //? if >=26.1 {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SeedChecker.MOD_ID, "main"));
    //?} else {
    /*private static final String CATEGORY = "key.categories." + SeedChecker.MOD_ID;*/
    //?}

    private static KeyMapping openMap;

    private SeedCheckerKeyBindings() {
    }

    public static void register() {
        //? if >=26.1 {
        openMap = KeyMappingHelper.registerKeyMapping(createOpenMapBinding());
        //?} else {
        /*openMap = KeyBindingHelper.registerKeyBinding(createOpenMapBinding());*/
        //?}

        // consumeClick() only reports presses made while no screen was open, so opening the
        // map here cannot fight with the screen the player is already looking at.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMap.consumeClick()) {
                //? if >=26.1 {
                client.setScreenAndShow(new MapScreen());
                //?} else {
                /*client.setScreen(new MapScreen());*/
                //?}
            }
        });
    }

    private static KeyMapping createOpenMapBinding() {
        return new KeyMapping(OPEN_MAP_KEY, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);
    }
}
