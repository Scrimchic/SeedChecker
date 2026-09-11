package com.scrimchic.seedchecker.world;

/**
 * The three vanilla dimensions, plus a catch-all for datapack or modded ones.
 *
 * <p>Seed Checker identifies dimensions by their string id rather than by a Minecraft type, so
 * that world-generation code can stay independent of the running Minecraft version.
 */
public enum DimensionType {

    OVERWORLD("minecraft:overworld", "Overworld"),
    NETHER("minecraft:the_nether", "Nether"),
    THE_END("minecraft:the_end", "The End"),

    /** Any dimension Seed Checker does not know by name; its id is kept in the world context. */
    CUSTOM(null, "Custom");

    private final String id;
    private final String displayName;

    DimensionType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** @return the canonical dimension id, or {@code null} for {@link #CUSTOM}. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Maps a dimension id such as {@code minecraft:the_nether} onto a known dimension. */
    public static DimensionType fromId(String id) {
        if (id != null) {
            for (DimensionType candidate : values()) {
                if (id.equals(candidate.id)) {
                    return candidate;
                }
            }
        }
        return CUSTOM;
    }
}
