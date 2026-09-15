package com.scrimchic.seedchecker.worldgen;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.scrimchic.seedchecker.world.DimensionType;

/**
 * Which grid-placed structures the Minecraft version this jar was built for has, and with what
 * placement numbers.
 *
 * <h2>Why the numbers are a table here rather than read from Minecraft</h2>
 *
 * <p>They are not equally reachable across versions. On 1.16.5 the grid numbers are public
 * ({@code StructureSettings.getConfig(...)}), but the frequency reductions are code inside
 * {@code isFeatureChunk} overrides. From 1.18 onwards they live in the vanilla datapack, and while
 * {@code spacing()}, {@code separation()} and {@code spreadType()} are public on
 * {@code RandomSpreadStructurePlacement}, the salt, the frequency and the exclusion zone are
 * protected - so reading them means either building the whole vanilla registry set
 * ({@code VanillaRegistries.createLookup()}, which the worldgen spike measured at 400-900 ms and
 * several MB) or going through the codec, at client start, for numbers that never change within a
 * version.
 *
 * <p>The trade taken instead: keep an explicitly versioned table, and make every entry in it
 * <em>verified</em> rather than trusted. {@code VanillaStructurePlacementTest} runs on each target
 * and asserts each entry against that version's own vanilla placement - the numbers directly where
 * vanilla exposes them (by reflection where it hides them), the salt indirectly by comparing
 * thousands of candidate chunks against vanilla's own placement call, and the restrictions by
 * comparing whole chunk windows against vanilla's {@code isStructureChunk} (1.18+) or
 * {@code isFeatureChunk} (1.16.5). A wrong constant fails the build rather than quietly drawing a
 * wrong map.
 *
 * <p>The cost of the trade is real and worth naming: a future Minecraft version that changes a
 * spacing will not be caught until someone adds it as a target and the test fails.
 */
public final class StructurePlacements {

    private static final StructurePlacements CURRENT = build();

    private final Map<StructureType, StructurePlacementConfig> byType;

    /** Where the nether's generator uses a different grid for a type than the overworld's. */
    private final Map<StructureType, StructurePlacementConfig> netherGrids;

    private StructurePlacements(Map<StructureType, StructurePlacementConfig> byType,
                                Map<StructureType, StructurePlacementConfig> netherGrids) {
        this.byType = Collections.unmodifiableMap(byType);
        this.netherGrids = Collections.unmodifiableMap(netherGrids);
    }

    /** The placements for the Minecraft version this jar targets. */
    public static StructurePlacements forThisVersion() {
        return CURRENT;
    }

    private static StructurePlacements build() {
        Map<StructureType, StructurePlacementConfig> placements =
                new LinkedHashMap<StructureType, StructurePlacementConfig>();
        Map<StructureType, StructurePlacementConfig> netherGrids =
                new LinkedHashMap<StructureType, StructurePlacementConfig>();

        //? if >=26.1 {
        StructurePlacementConfig village = put(placements, StructureType.VILLAGE, 34, 8, 10387312);
        put(placements, StructureType.DESERT_PYRAMID, 32, 8, 14357617);
        put(placements, StructureType.SHIPWRECK, 24, 4, 165745295);
        put(placements, StructureType.ANCIENT_CITY, 24, 8, 20083232);
        put(placements, StructureType.TRIAL_CHAMBER, 34, 12, 94251327);
        putRemainingOverworld(placements, village);
        put(placements, StructureType.TRAIL_RUINS, 34, 8, 83469867);
        //?} else if >=1.18 {
        /*StructurePlacementConfig village = put(placements, StructureType.VILLAGE, 34, 8, 10387312);
        put(placements, StructureType.DESERT_PYRAMID, 32, 8, 14357617);
        put(placements, StructureType.SHIPWRECK, 24, 4, 165745295);
        put(placements, StructureType.ANCIENT_CITY, 24, 8, 20083232);
        putRemainingOverworld(placements, village);
        put(placements, StructureType.TRAIL_RUINS, 34, 8, 83469867);
        *///?} else {
        /*// Village spacing is 32 here and 34 from 1.20.1 on, both verified against vanilla;
        // 1.18 is where the worldgen rewrite landed, but only the three supported targets are
        // actually checked. Ancient cities, trial chambers and trail ruins do not exist yet.
        StructurePlacementConfig village = put(placements, StructureType.VILLAGE, 32, 8, 10387312);
        put(placements, StructureType.DESERT_PYRAMID, 32, 8, 14357617);
        put(placements, StructureType.SHIPWRECK, 24, 4, 165745295);
        putRemainingOverworld(placements, village);
        // Structure settings belong to the chunk generator on this version, and the nether's are
        // not the defaults for every feature: NoiseGeneratorSettings.nether replaces the ruined
        // portal's grid. From 1.18 one ruined_portals set places both dimensions' portals.
        netherGrids.put(StructureType.RUINED_PORTAL,
                new StructurePlacementConfig(25, 10, 34222645, SpreadType.LINEAR));
        *///?}

        return new StructurePlacements(placements, netherGrids);
    }

    /**
     * Phase 3H-1's overworld structures, whose numbers are the same on all three targets.
     *
     * <p>1.16.5's {@code StructureSettings.DEFAULTS} and the modern structure sets agree field for
     * field on these, and 1.16.5's {@code isFeatureChunk} overrides for the outpost, the buried
     * treasure and the mineshaft are the computations the modern data pack calls legacy types 1, 2
     * and 3. The outpost's exclusion zone is 1.16.5's {@code isNearVillage} - any village grid chunk
     * within ten chunks - which is why it takes the version's own village placement.
     */
    private static void putRemainingOverworld(Map<StructureType, StructurePlacementConfig> placements,
                                              StructurePlacementConfig village) {
        put(placements, StructureType.JUNGLE_TEMPLE, 32, 8, 14357619);
        put(placements, StructureType.SWAMP_HUT, 32, 8, 14357620);
        put(placements, StructureType.IGLOO, 32, 8, 14357618);
        placements.put(StructureType.PILLAGER_OUTPOST, new StructurePlacementConfig(32, 8, 165745296,
                SpreadType.LINEAR, 0.2F, FrequencyReduction.LEGACY_TYPE_1,
                new ExclusionZone(StructureType.VILLAGE, village, 10)));
        put(placements, StructureType.OCEAN_RUIN, 20, 8, 14357621);
        placements.put(StructureType.BURIED_TREASURE, new StructurePlacementConfig(1, 0, 0,
                SpreadType.LINEAR, 0.01F, FrequencyReduction.LEGACY_TYPE_2, null));
        placements.put(StructureType.MINESHAFT, new StructurePlacementConfig(1, 0, 0,
                SpreadType.LINEAR, 0.004F, FrequencyReduction.LEGACY_TYPE_3, null));
        // The last three. 1.16.5 marks the monument and the mansion triangular by overriding
        // linearSeparation to false; the modern sets say spread_type triangular.
        put(placements, StructureType.OCEAN_MONUMENT, 32, 5, 10387313, SpreadType.TRIANGULAR);
        put(placements, StructureType.WOODLAND_MANSION, 80, 20, 10387319, SpreadType.TRIANGULAR);
        put(placements, StructureType.RUINED_PORTAL, 40, 15, 34222645);
        putNether(placements);
    }

    /**
     * Phase 3H-2, the same numbers on all three targets.
     *
     * <p>The fortress and the bastion remnant are one grid. From 1.18 that is literally one structure
     * set, {@code nether_complexes}, holding both as weighted entries; 1.16.5 gives
     * {@code NETHER_BRIDGE} and {@code BASTION_REMNANT} two {@code StructureSettings.DEFAULTS}
     * entries that agree field for field. Which of the two a grid chunk builds is not placement - the
     * modern weighted draw with its fallback, or 1.16.5's two {@code isFeatureChunk} overrides - so
     * both layers are handed the same grid and validation decides. The nether fossil's spacing of 2
     * and separation of 1 put exactly one candidate at the minimum chunk of every 2 by 2 region.
     */
    private static void putNether(Map<StructureType, StructurePlacementConfig> placements) {
        put(placements, StructureType.NETHER_FORTRESS, 27, 4, 30084232);
        put(placements, StructureType.BASTION_REMNANT, 27, 4, 30084232);
        put(placements, StructureType.NETHER_FOSSIL, 2, 1, 14357921);
    }

    /** A set with nothing but its grid, in the codec's default spread. */
    private static StructurePlacementConfig put(Map<StructureType, StructurePlacementConfig> placements,
                                                StructureType type, int spacing, int separation,
                                                int salt) {
        return put(placements, type, spacing, separation, salt, SpreadType.LINEAR);
    }

    private static StructurePlacementConfig put(Map<StructureType, StructurePlacementConfig> placements,
                                                StructureType type, int spacing, int separation,
                                                int salt, SpreadType spread) {
        StructurePlacementConfig config =
                new StructurePlacementConfig(spacing, separation, salt, spread);
        placements.put(type, config);
        return config;
    }

    /** The supported structures, in a stable order. */
    public Set<StructureType> types() {
        return byType.keySet();
    }

    public boolean supports(StructureType type) {
        return byType.containsKey(type);
    }

    /**
     * @return the overworld placement, or {@code null} when this version has no such structure. The
     *         nether's may differ; see {@link #get(StructureType, String)}.
     */
    public StructurePlacementConfig get(StructureType type) {
        return byType.get(type);
    }

    /**
     * The placement a type uses in one dimension: the grid that dimension's vanilla generator
     * places it on. Differs from {@link #get(StructureType)} only where the version gives the
     * nether a grid of its own - 1.16.5's ruined portal - which {@code NetherStructureTest} holds
     * to the nether generator's settings.
     *
     * @return the placement, or {@code null} when this version has no such structure
     */
    public StructurePlacementConfig get(StructureType type, String dimensionId) {
        if (DimensionType.fromId(dimensionId) == DimensionType.NETHER) {
            StructurePlacementConfig nether = netherGrids.get(type);
            if (nether != null) {
                return nether;
            }
        }
        return byType.get(type);
    }
}
