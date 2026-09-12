package com.scrimchic.seedchecker.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;

//? if >=1.18 {
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.resources.ResourceKey;
//?} else {
/*import net.minecraft.world.level.levelgen.StructureSettings;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.feature.configurations.StructureFeatureConfiguration;*/
//?}

/**
 * Checks {@link StructurePlacementEngine} and the {@link StructurePlacements} table against the
 * Minecraft version this target is built for.
 *
 * <p>This is the test that makes the hardcoded placement table safe. It does two things:
 *
 * <ol>
 *   <li>asserts our numbers against the numbers vanilla exposes, so a wrong spacing or separation
 *       cannot ship;</li>
 *   <li>compares tens of thousands of candidate chunks against vanilla's own placement call, which
 *       is what catches a wrong salt or spread type - those do not have public accessors on the
 *       modern versions, but they change every result.</li>
 * </ol>
 *
 * <p>Both halves go through a vanilla entry point that takes the seed directly and needs no world,
 * no chunk generator and no {@code ChunkGeneratorStructureState}:
 * {@code StructureFeature.getPotentialFeatureChunk} on 1.16.5 and
 * {@code RandomSpreadStructurePlacement.getPotentialStructureChunk} from 1.18 onwards. That is
 * what lets the comparison run on 26.2 as well, where the worldgen spike found the higher-level
 * structure-state API unusable without bound biome tags.
 *
 * <p>Limit of this verification, stated honestly: it covers <em>grid placement</em> only. It says
 * nothing about whether a structure passes vanilla's later biome and terrain checks.
 */
class VanillaStructurePlacementTest {

    private static final long[] SEEDS = {
            0L, 1L, -1L, 123456789L, -7407337299659424542L, Long.MAX_VALUE, Long.MIN_VALUE,
    };

    /** Chunk coordinates far enough out to exercise the long multiplications. */
    private static final int[] FAR_COORDINATES = {
            -1_875_000, -1_000_000, -46_341, 46_341, 1_000_000, 1_875_000,
    };

    private static StructurePlacements placements;

    @BeforeAll
    static void bootstrapMinecraft() {
        //? if >=1.18 {
        SharedConstants.tryDetectVersion();
        //?}
        Bootstrap.bootStrap();
        placements = StructurePlacements.forThisVersion();
    }

    @Test
    void thisVersionSupportsAtLeastTheOriginalThree() {
        assertTrue(placements.supports(StructureType.VILLAGE));
        assertTrue(placements.supports(StructureType.DESERT_PYRAMID));
        assertTrue(placements.supports(StructureType.SHIPWRECK));
    }

    @Test
    void everySupportedStructureHasVanillaBehindIt() {
        for (StructureType type : placements.types()) {
            assertTrue(hasVanillaPlacement(type),
                    type + " is in the table but this version has no vanilla placement for it");
        }
    }

    @Test
    void tableNumbersMatchVanilla() {
        for (StructureType type : placements.types()) {
            StructurePlacementConfig ours = placements.get(type);
            int[] vanilla = vanillaNumbers(type);

            assertEquals(vanilla[0], ours.spacing(), type + " spacing");
            assertEquals(vanilla[1], ours.separation(), type + " separation");
            if (vanilla[2] != UNKNOWN_SALT) {
                assertEquals(vanilla[2], ours.salt(), type + " salt");
            }
            if (vanilla[3] != UNKNOWN_SPREAD) {
                assertEquals(vanilla[3] == 1 ? SpreadType.LINEAR : SpreadType.TRIANGULAR,
                        ours.spreadType(), type + " spread type");
            }
        }
    }

    @Test
    void engineAgreesWithVanillaEverywhereItIsAsked() {
        StructurePlacementEngine engine = new StructurePlacementEngine();
        int comparisons = 0;

        for (StructureType type : placements.types()) {
            StructurePlacementConfig config = placements.get(type);
            List<Integer> coordinates = interestingCoordinates(config.spacing());

            for (long seed : SEEDS) {
                for (int chunkX : coordinates) {
                    for (int chunkZ : coordinates) {
                        long ours = engine.candidateChunk(seed, config,
                                Math.floorDiv(chunkX, config.spacing()),
                                Math.floorDiv(chunkZ, config.spacing()));
                        long vanilla = vanillaCandidate(type, seed, chunkX, chunkZ);

                        assertEquals(StructurePlacementEngine.chunkX(vanilla),
                                StructurePlacementEngine.chunkX(ours),
                                type + " seed " + seed + " chunk " + chunkX + "," + chunkZ + " x");
                        assertEquals(StructurePlacementEngine.chunkZ(vanilla),
                                StructurePlacementEngine.chunkZ(ours),
                                type + " seed " + seed + " chunk " + chunkX + "," + chunkZ + " z");
                        comparisons++;
                    }
                }
            }
        }
        // Exact rather than a threshold, so a structure quietly dropping out of the table cannot
        // make this test pass by comparing less.
        int coordinates = interestingCoordinates(16).size();
        assertEquals(placements.types().size() * SEEDS.length * coordinates * coordinates,
                comparisons, "every supported structure must have been compared");
    }

    /** Region boundaries on both sides of the origin, plus deliberately far-out coordinates. */
    private static List<Integer> interestingCoordinates(int spacing) {
        List<Integer> coordinates = new ArrayList<Integer>();
        for (int region = -2; region <= 2; region++) {
            coordinates.add(Integer.valueOf(region * spacing - 1));
            coordinates.add(Integer.valueOf(region * spacing));
            coordinates.add(Integer.valueOf(region * spacing + 1));
        }
        for (int far : FAR_COORDINATES) {
            coordinates.add(Integer.valueOf(far));
        }
        return coordinates;
    }

    private static final int UNKNOWN_SALT = Integer.MIN_VALUE;
    private static final int UNKNOWN_SPREAD = -1;

    //? if >=26.1 {
    private static int posX(ChunkPos pos) {
        return pos.x();
    }

    private static int posZ(ChunkPos pos) {
        return pos.z();
    }
    //?}
    //? if <26.1 {
    /*private static int posX(ChunkPos pos) {
        return pos.x;
    }

    private static int posZ(ChunkPos pos) {
        return pos.z;
    }
    *///?}

    // ------------------------------------------------- version-specific vanilla access

    //? if >=1.18 {
    private static HolderLookup.RegistryLookup<StructureSet> structureSets;

    private static HolderLookup.RegistryLookup<StructureSet> structureSets() {
        if (structureSets == null) {
            // Registry contents only. No ChunkGeneratorStructureState, so no biome tags are
            // dereferenced and this works on 26.2 too.
            structureSets = VanillaRegistries.createLookup().lookupOrThrow(Registries.STRUCTURE_SET);
        }
        return structureSets;
    }

    private static ResourceKey<StructureSet> setKeyOf(StructureType type) {
        switch (type) {
            case VILLAGE:
                return BuiltinStructureSets.VILLAGES;
            case DESERT_PYRAMID:
                return BuiltinStructureSets.DESERT_PYRAMIDS;
            case SHIPWRECK:
                return BuiltinStructureSets.SHIPWRECKS;
            case ANCIENT_CITY:
                return BuiltinStructureSets.ANCIENT_CITIES;
            //? if >=1.21 {
            case TRIAL_CHAMBER:
                return BuiltinStructureSets.TRIAL_CHAMBERS;
            //?}
            default:
                return null;
        }
    }

    private static RandomSpreadStructurePlacement vanillaPlacement(StructureType type) {
        ResourceKey<StructureSet> key = setKeyOf(type);
        if (key == null) {
            return null;
        }
        StructurePlacement placement = structureSets().getOrThrow(key).value().placement();
        assertTrue(placement instanceof RandomSpreadStructurePlacement,
                type + " is not grid placed in this version: " + placement.getClass().getName());
        return (RandomSpreadStructurePlacement) placement;
    }

    private static boolean hasVanillaPlacement(StructureType type) {
        return vanillaPlacement(type) != null;
    }

    private static int[] vanillaNumbers(StructureType type) {
        RandomSpreadStructurePlacement placement = vanillaPlacement(type);
        // salt() is protected on StructurePlacement, so it is verified through the candidate
        // comparison instead of read here.
        int spread = "LINEAR".equals(placement.spreadType().name()) ? 1 : 0;
        return new int[] {placement.spacing(), placement.separation(), UNKNOWN_SALT, spread};
    }

    private static long vanillaCandidate(StructureType type, long seed, int chunkX, int chunkZ) {
        ChunkPos pos = vanillaPlacement(type).getPotentialStructureChunk(seed, chunkX, chunkZ);
        return StructurePlacementEngine.pack(posX(pos), posZ(pos));
    }
    //?} else {
    /*// Built lazily rather than in a static initialiser: on 1.16.5, touching StructureSettings
    // before Bootstrap.bootStrap() has run throws from NoiseGeneratorSettings' class init.
    private static WorldgenRandom legacyRandom;
    private static StructureSettings legacySettings;

    private static WorldgenRandom legacyRandom() {
        if (legacyRandom == null) {
            legacyRandom = new WorldgenRandom();
        }
        return legacyRandom;
    }

    private static StructureSettings legacySettings() {
        if (legacySettings == null) {
            legacySettings = new StructureSettings(true);
        }
        return legacySettings;
    }

    private static StructureFeature<?> vanillaFeature(StructureType type) {
        switch (type) {
            case VILLAGE:
                return StructureFeature.VILLAGE;
            case DESERT_PYRAMID:
                return StructureFeature.DESERT_PYRAMID;
            case SHIPWRECK:
                return StructureFeature.SHIPWRECK;
            default:
                return null;
        }
    }

    private static boolean hasVanillaPlacement(StructureType type) {
        return vanillaFeature(type) != null;
    }

    private static int[] vanillaNumbers(StructureType type) {
        StructureFeatureConfiguration config = legacySettings().getConfig(vanillaFeature(type));
        // 1.16.5 exposes all three numbers publicly. linearSeparation() is protected, so the
        // spread type is verified through the candidate comparison instead.
        return new int[] {config.spacing(), config.separation(), config.salt(), UNKNOWN_SPREAD};
    }

    private static long vanillaCandidate(StructureType type, long seed, int chunkX, int chunkZ) {
        StructureFeature<?> feature = vanillaFeature(type);
        ChunkPos pos = feature.getPotentialFeatureChunk(
                legacySettings().getConfig(feature), seed, legacyRandom(), chunkX, chunkZ);
        return StructurePlacementEngine.pack(posX(pos), posZ(pos));
    }
    *///?}
}
