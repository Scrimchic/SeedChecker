package com.scrimchic.seedchecker.platform;

import com.scrimchic.seedchecker.world.TeleportCommand;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

//? if >=26.1 {
import net.minecraft.server.permissions.Permissions;
//?}

/**
 * Moving the player to a place on the map, and putting coordinates on the clipboard.
 *
 * <p>A developer convenience for checking by eye whether a marker corresponds to anything in the
 * world, not a gameplay feature. It exists here rather than in the screen because both halves are
 * version-specific, and because it is the second place - after {@link MinecraftBridge} - that has
 * any business touching the client singleton.
 *
 * <h2>Why a command rather than moving the player</h2>
 *
 * <p>Setting the position client side would desynchronise immediately and, on a server, would be
 * indistinguishable from a movement cheat. So Seed Checker asks the game to run the same
 * {@code /tp} the player could type, and if the player is not allowed to type it, the teleport is
 * simply unavailable and the coordinates can still be copied.
 *
 * <h2>Why the Y is relative</h2>
 *
 * <p>A non-exact marker carries no height at all, and an exact one carries the structure's own
 * generation height, which is rarely a safe place to stand - an ancient city starts at y=-27, inside
 * solid rock. {@code ~} keeps whatever height the player is already at, which is the only height
 * known to be safe; the exact height is shown and copied, not teleported to.
 */
public final class PlayerNavigator {

    /** What happened when a teleport was asked for. */
    public enum TeleportResult {

        SENT,

        /** No player to move - in a menu, or between worlds. */
        NO_PLAYER,

        /** The player may not run commands here, so nothing was sent. */
        NO_PERMISSION
    }

    private PlayerNavigator() {
    }

    /**
     * Whether a teleport would be accepted right now.
     *
     * <p>False in singleplayer without cheats just as on a server without operator rights, which is
     * correct: in both cases the command would bounce.
     */
    public static boolean canTeleport() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && mayRunCommands(player);
    }

    /**
     * Teleports the player to a column, keeping their current height.
     *
     * @param blockX the column's X
     * @param blockZ the column's Z
     */
    public static TeleportResult teleportToColumn(int blockX, int blockZ) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return TeleportResult.NO_PLAYER;
        }
        if (!mayRunCommands(player)) {
            return TeleportResult.NO_PERMISSION;
        }
        sendCommand(player, teleportArguments(blockX, blockZ));
        return TeleportResult.SENT;
    }

    /** The column command as the player would type it, for the clipboard. */
    public static String teleportCommand(int blockX, int blockZ) {
        return "/" + teleportArguments(blockX, blockZ);
    }

    /**
     * For the clipboard only: the exact command when the height is known, the column one when it is
     * not. Never sent - a remembered height is not known to be safe to stand at any more.
     */
    public static String teleportCommand(int blockX, Integer blockY, int blockZ) {
        return "/" + TeleportCommand.forPosition(blockX, blockY, blockZ);
    }

    private static String teleportArguments(int blockX, int blockZ) {
        return TeleportCommand.column(blockX, blockZ);
    }

    /** Puts text on the system clipboard. Does nothing if the client is not up yet. */
    public static void copyToClipboard(String text) {
        ClipboardBridge.write(text);
    }

    // ----------------------------------------------------- version-specific client calls

    //? if >=26.1 {
    /**
     * 26.x replaced the numeric permission level with a permission set.
     * {@code Commands.LEVEL_GAMEMASTERS}, which {@code TeleportCommand} requires, is defined as
     * {@code PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)}, so that is the one to ask
     * for.
     */
    private static boolean mayRunCommands(LocalPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
    //?} else {
    /*/^* The permission level vanilla's own TeleportCommand requires. ^/
    private static final int COMMAND_PERMISSION_LEVEL = 2;

    private static boolean mayRunCommands(LocalPlayer player) {
        return player.hasPermissions(COMMAND_PERMISSION_LEVEL);
    }*/
    //?}

    //? if >=1.19 {
    /** From 1.19 the client sends commands down their own path, without the leading slash. */
    private static void sendCommand(LocalPlayer player, String commandWithoutSlash) {
        player.connection.sendCommand(commandWithoutSlash);
    }
    //?} else {
    /*private static void sendCommand(LocalPlayer player, String commandWithoutSlash) {
        // 1.16.5 has no separate command channel: a command is a chat message that starts with a
        // slash, and the server splits them apart.
        player.chat("/" + commandWithoutSlash);
    }*/
    //?}
}
