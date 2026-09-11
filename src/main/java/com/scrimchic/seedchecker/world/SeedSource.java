package com.scrimchic.seedchecker.world;

/** Where a known seed came from. */
public enum SeedSource {

    /** Read straight out of a running integrated server. Always trustworthy, always wins. */
    RUNTIME("Runtime"),

    /** Typed in by the player for a world whose seed Minecraft does not expose. */
    MANUAL("Manual"),

    /** Recovered from observed world generation. Not produced yet; the seed cracker will. */
    RECOVERED("Recovered"),

    /** No seed is known. */
    UNKNOWN("Unknown");

    private final String displayName;

    SeedSource(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
