package com.scrimchic.seedchecker.worldgen;

import com.scrimchic.seedchecker.world.DimensionType;

/**
 * The structures Seed Checker can place, independent of Minecraft version.
 *
 * <p>Grid ("random spread") structures, placed by {@link StructurePlacementEngine}, and the
 * stronghold, which sits on concentric rings instead and is placed by
 * {@link StrongholdPlacementEngine}. {@link StructurePlacements} lists only the grid ones.
 *
 * <p>Which of these actually exist in the running version is answered by
 * {@link StructurePlacements}, not by this enum. Which dimension each one can start in is answered
 * here, and held to every version's biome data by {@code NetherStructureTest} and
 * {@code EndStructureTest}.
 */
public enum StructureType {

    VILLAGE("Village", Dimensions.OVERWORLD),
    DESERT_PYRAMID("Desert Pyramid", Dimensions.OVERWORLD),
    SHIPWRECK("Shipwreck", Dimensions.OVERWORLD),
    ANCIENT_CITY("Ancient City", Dimensions.OVERWORLD),
    TRIAL_CHAMBER("Trial Chamber", Dimensions.OVERWORLD),
    JUNGLE_TEMPLE("Jungle Temple", Dimensions.OVERWORLD),
    SWAMP_HUT("Swamp Hut", Dimensions.OVERWORLD),
    IGLOO("Igloo", Dimensions.OVERWORLD),
    PILLAGER_OUTPOST("Pillager Outpost", Dimensions.OVERWORLD),
    OCEAN_RUIN("Ocean Ruin", Dimensions.OVERWORLD),
    BURIED_TREASURE("Buried Treasure", Dimensions.OVERWORLD),
    MINESHAFT("Mineshaft", Dimensions.OVERWORLD),
    TRAIL_RUINS("Trail Ruins", Dimensions.OVERWORLD),
    OCEAN_MONUMENT("Ocean Monument", Dimensions.OVERWORLD),
    WOODLAND_MANSION("Woodland Mansion", Dimensions.OVERWORLD),

    /**
     * One structure set for both dimensions: six overworld entries and one nether entry share the
     * grid, and which of them can start is decided by the biome source of the dimension.
     */
    RUINED_PORTAL("Ruined Portal", Dimensions.OVERWORLD_AND_NETHER),

    /**
     * Shares its grid with {@link #BASTION_REMNANT}: a grid chunk of that set builds at most one of
     * the two, and validation decides which.
     */
    NETHER_FORTRESS("Nether Fortress", Dimensions.NETHER),
    BASTION_REMNANT("Bastion Remnant", Dimensions.NETHER),
    NETHER_FOSSIL("Nether Fossil", Dimensions.NETHER),

    /** Phase 3H-3: the only structure the End's biome source lets start, on every target. */
    END_CITY("End City", Dimensions.END),

    /** Not grid placed; never in {@link StructurePlacements}. */
    STRONGHOLD("Stronghold", Dimensions.OVERWORLD);

    private final String displayName;
    private final Dimensions dimensions;

    StructureType(String displayName, Dimensions dimensions) {
        this.displayName = displayName;
        this.dimensions = dimensions;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Whether vanilla can start this structure in that dimension at all - whether the dimension's
     * biome source can return a biome the structure accepts. {@code false} for any dimension Seed
     * Checker does not know by name.
     */
    public boolean generatesIn(String dimensionId) {
        DimensionType dimension = DimensionType.fromId(dimensionId);
        switch (dimension) {
            case OVERWORLD:
                return dimensions.overworld;
            case NETHER:
                return dimensions.nether;
            case THE_END:
                return dimensions.end;
            default:
                return false;
        }
    }

    private enum Dimensions {
        OVERWORLD(true, false, false),
        NETHER(false, true, false),
        OVERWORLD_AND_NETHER(true, true, false),
        END(false, false, true);

        private final boolean overworld;
        private final boolean nether;
        private final boolean end;

        Dimensions(boolean overworld, boolean nether, boolean end) {
            this.overworld = overworld;
            this.nether = nether;
            this.end = end;
        }
    }
}
