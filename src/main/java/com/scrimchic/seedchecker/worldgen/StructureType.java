package com.scrimchic.seedchecker.worldgen;

/**
 * The structures Seed Checker can place, independent of Minecraft version.
 *
 * <p>Grid ("random spread") structures, placed by {@link StructurePlacementEngine}, and the
 * stronghold, which sits on concentric rings instead and is placed by
 * {@link StrongholdPlacementEngine}. {@link StructurePlacements} lists only the grid ones.
 *
 * <p>Which of these actually exist in the running version is answered by
 * {@link StructurePlacements}, not by this enum.
 */
public enum StructureType {

    VILLAGE("Village"),
    DESERT_PYRAMID("Desert Pyramid"),
    SHIPWRECK("Shipwreck"),
    ANCIENT_CITY("Ancient City"),
    TRIAL_CHAMBER("Trial Chamber"),
    JUNGLE_TEMPLE("Jungle Temple"),
    SWAMP_HUT("Swamp Hut"),
    IGLOO("Igloo"),
    PILLAGER_OUTPOST("Pillager Outpost"),
    OCEAN_RUIN("Ocean Ruin"),
    BURIED_TREASURE("Buried Treasure"),
    MINESHAFT("Mineshaft"),
    TRAIL_RUINS("Trail Ruins"),
    OCEAN_MONUMENT("Ocean Monument"),
    WOODLAND_MANSION("Woodland Mansion"),

    /**
     * The overworld's portals. The vanilla structure set also holds the nether's entry; in the
     * overworld no biome ever accepts it, so it takes part in the weighted order and never starts.
     */
    RUINED_PORTAL("Ruined Portal"),

    /** Not grid placed; never in {@link StructurePlacements}. */
    STRONGHOLD("Stronghold");

    private final String displayName;

    StructureType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
