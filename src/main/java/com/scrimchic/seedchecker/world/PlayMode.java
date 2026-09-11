package com.scrimchic.seedchecker.world;

/** Where the world the player is currently in is being simulated. */
public enum PlayMode {

    /** A local world served by the integrated server, so its seed is directly readable. */
    SINGLEPLAYER("Singleplayer"),

    /** A remote server, which normally does not hand the seed to clients. */
    MULTIPLAYER("Multiplayer");

    private final String displayName;

    PlayMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
