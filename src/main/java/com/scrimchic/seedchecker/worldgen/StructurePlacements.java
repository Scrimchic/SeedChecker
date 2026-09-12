package com.scrimchic.seedchecker.worldgen;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which grid-placed structures the Minecraft version this jar was built for has, and with what
 * placement numbers.
 *
 * <h2>Why the numbers are a table here rather than read from Minecraft</h2>
 *
 * <p>They are not equally reachable across versions. On 1.16.5 all three are public
 * ({@code StructureSettings.getConfig(...)}). From 1.18 onwards they live in the vanilla datapack,
 * and while {@code spacing()}, {@code separation()} and {@code spreadType()} are public on
 * {@code RandomSpreadStructurePlacement}, {@code salt()} is protected - so reading them means
 * either building the whole vanilla registry set ({@code VanillaRegistries.createLookup()}, which
 * the worldgen spike measured at 400-900 ms and several MB) or going through the codec, at client
 * start, for five integers that never change within a version.
 *
 * <p>The trade taken instead: keep an explicitly versioned table, and make every entry in it
 * <em>verified</em> rather than trusted. {@code VanillaStructurePlacementTest} runs on each target
 * and asserts each entry against that version's own vanilla placement - the numbers directly where
 * vanilla exposes them, and the salt indirectly by comparing thousands of candidate chunks against
 * vanilla's own placement call. A wrong constant fails the build rather than quietly drawing a
 * wrong map.
 *
 * <p>The cost of the trade is real and worth naming: a future Minecraft version that changes a
 * spacing will not be caught until someone adds it as a target and the test fails.
 */
public final class StructurePlacements {

    private static final StructurePlacements CURRENT = build();

    private final Map<StructureType, StructurePlacementConfig> byType;

    private StructurePlacements(Map<StructureType, StructurePlacementConfig> byType) {
        this.byType = Collections.unmodifiableMap(byType);
    }

    /** The placements for the Minecraft version this jar targets. */
    public static StructurePlacements forThisVersion() {
        return CURRENT;
    }

    private static StructurePlacements build() {
        Map<StructureType, StructurePlacementConfig> placements =
                new LinkedHashMap<StructureType, StructurePlacementConfig>();

        //? if >=26.1 {
        put(placements, StructureType.VILLAGE, 34, 8, 10387312);
        put(placements, StructureType.DESERT_PYRAMID, 32, 8, 14357617);
        put(placements, StructureType.SHIPWRECK, 24, 4, 165745295);
        put(placements, StructureType.ANCIENT_CITY, 24, 8, 20083232);
        put(placements, StructureType.TRIAL_CHAMBER, 34, 12, 94251327);
        //?} else if >=1.18 {
        /*put(placements, StructureType.VILLAGE, 34, 8, 10387312);
        put(placements, StructureType.DESERT_PYRAMID, 32, 8, 14357617);
        put(placements, StructureType.SHIPWRECK, 24, 4, 165745295);
        put(placements, StructureType.ANCIENT_CITY, 24, 8, 20083232);
        *///?} else {
        /*// Village spacing is 32 here and 34 from 1.20.1 on, both verified against vanilla;
        // 1.18 is where the worldgen rewrite landed, but only the three supported targets are
        // actually checked. Ancient cities and trial chambers do not exist yet.
        put(placements, StructureType.VILLAGE, 32, 8, 10387312);
        put(placements, StructureType.DESERT_PYRAMID, 32, 8, 14357617);
        put(placements, StructureType.SHIPWRECK, 24, 4, 165745295);
        *///?}

        return new StructurePlacements(placements);
    }

    /** Every vanilla set Seed Checker supports so far uses the codec's default spread. */
    private static void put(Map<StructureType, StructurePlacementConfig> placements,
                            StructureType type, int spacing, int separation, int salt) {
        placements.put(type, new StructurePlacementConfig(spacing, separation, salt, SpreadType.LINEAR));
    }

    /** The supported structures, in a stable order. */
    public Set<StructureType> types() {
        return byType.keySet();
    }

    public boolean supports(StructureType type) {
        return byType.containsKey(type);
    }

    /** @return the placement, or {@code null} when this version has no such structure. */
    public StructurePlacementConfig get(StructureType type) {
        return byType.get(type);
    }
}
