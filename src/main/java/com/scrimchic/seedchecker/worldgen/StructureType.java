package com.scrimchic.seedchecker.worldgen;

/**
 * The structures Seed Checker can place, independent of Minecraft version.
 *
 * <p>Only structures that use vanilla's grid ("random spread") placement belong here. Strongholds
 * deliberately do not: they sit on concentric rings, which is a different algorithm and needs
 * biome data to solve.
 *
 * <p>Which of these actually exist in the running version is answered by
 * {@link StructurePlacements}, not by this enum.
 */
public enum StructureType {

    VILLAGE("Village"),
    DESERT_PYRAMID("Desert Pyramid"),
    SHIPWRECK("Shipwreck"),
    ANCIENT_CITY("Ancient City"),
    TRIAL_CHAMBER("Trial Chamber");

    private final String displayName;

    StructureType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
