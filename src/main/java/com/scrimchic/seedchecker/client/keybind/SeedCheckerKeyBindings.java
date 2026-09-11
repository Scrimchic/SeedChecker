package com.scrimchic.seedchecker.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import com.scrimchic.seedchecker.gui.map.MapScreen;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;*/
//?}
import net.minecraft.client.KeyMapping;

import org.lwjgl.glfw.GLFW;

/** Registers and services the Seed Checker key bindings. */
public final class SeedCheckerKeyBindings {

    private static final String OPEN_MAP_KEY = "key.seedchecker.open_map";

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
        //? if >=26.1 {
        return new KeyMapping(OPEN_MAP_KEY, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, KeyMapping.Category.MISC);
        //?} else {
        /*return new KeyMapping(OPEN_MAP_KEY, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.misc");*/
        //?}
    }
}
