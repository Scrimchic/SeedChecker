package com.scrimchic.seedchecker.platform;

import net.minecraft.client.Minecraft;

/**
 * The system clipboard, through the running client. The same call on every supported version, so no
 * Stonecutter branch; kept here so nothing above {@code platform} touches the client singleton.
 */
public final class ClipboardBridge {

    private ClipboardBridge() {
    }

    /** @return the clipboard's text, or an empty string when there is none or the client is not up */
    public static String read() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.keyboardHandler == null) {
            return "";
        }
        String text = client.keyboardHandler.getClipboard();
        return text == null ? "" : text;
    }

    /** Puts text on the clipboard. Does nothing if the client is not up yet. */
    public static void write(String text) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.keyboardHandler != null) {
            client.keyboardHandler.setClipboard(text);
        }
    }
}
