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
 *
 * <p>Every type has a stable {@link #id()} that saved data refers to it by. It is written out
 * explicitly rather than derived from the constant name, so renaming a constant cannot orphan what
 * a player has recorded; {@code StructureTypeIdTest} pins the list.
 */
public enum StructureType {

    VILLAGE("village", "Village", Dimensions.OVERWORLD),
    DESERT_PYRAMID("desert_pyramid", "Desert Pyramid", Dimensions.OVERWORLD),
    SHIPWRECK("shipwreck", "Shipwreck", Dimensions.OVERWORLD),
    ANCIENT_CITY("ancient_city", "Ancient City", Dimensions.OVERWORLD),
    TRIAL_CHAMBER("trial_chamber", "Trial Chamber", Dimensions.OVERWORLD),
    JUNGLE_TEMPLE("jungle_temple", "Jungle Temple", Dimensions.OVERWORLD),
    SWAMP_HUT("swamp_hut", "Swamp Hut", Dimensions.OVERWORLD),
    IGLOO("igloo", "Igloo", Dimensions.OVERWORLD),
    PILLAGER_OUTPOST("pillager_outpost", "Pillager Outpost", Dimensions.OVERWORLD),
    OCEAN_RUIN("ocean_ruin", "Ocean Ruin", Dimensions.OVERWORLD),
    BURIED_TREASURE("buried_treasure", "Buried Treasure", Dimensions.OVERWORLD),
    MINESHAFT("mineshaft", "Mineshaft", Dimensions.OVERWORLD),
    TRAIL_RUINS("trail_ruins", "Trail Ruins", Dimensions.OVERWORLD),
    OCEAN_MONUMENT("ocean_monument", "Ocean Monument", Dimensions.OVERWORLD),
    WOODLAND_MANSION("woodland_mansion", "Woodland Mansion", Dimensions.OVERWORLD),

    /**
     * One structure set for both dimensions: six overworld entries and one nether entry share the
     * grid, and which of them can start is decided by the biome source of the dimension.
     */
    RUINED_PORTAL("ruined_portal", "Ruined Portal", Dimensions.OVERWORLD_AND_NETHER),

    /**
     * Shares its grid with {@link #BASTION_REMNANT}: a grid chunk of that set builds at most one of
     * the two, and validation decides which.
     */
    NETHER_FORTRESS("nether_fortress", "Nether Fortress", Dimensions.NETHER),
    BASTION_REMNANT("bastion_remnant", "Bastion Remnant", Dimensions.NETHER),
    NETHER_FOSSIL("nether_fossil", "Nether Fossil", Dimensions.NETHER),

    /** Phase 3H-3: the only structure the End's biome source lets start, on every target. */
    END_CITY("end_city", "End City", Dimensions.END),

    /** Not grid placed; never in {@link StructurePlacements}. */
    STRONGHOLD("stronghold", "Stronghold", Dimensions.OVERWORLD);

    private final String id;
    private final String displayName;
    private final Dimensions dimensions;

    StructureType(String id, String displayName, Dimensions dimensions) {
        this.id = id;
        this.displayName = displayName;
        this.dimensions = dimensions;
    }

    /** The stable id saved data uses, e.g. {@code end_city}. Never change an existing one. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** @return the type with that stable id, or {@code null} for an id this version does not know */
    public static StructureType fromId(String id) {
        if (id != null) {
            for (StructureType type : values()) {
                if (type.id.equals(id)) {
                    return type;
                }
            }
        }
        return null;
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
