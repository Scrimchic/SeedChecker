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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.resources.ResourceKey;
//?} else {
/*import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.data.BuiltinRegistries;
import net.minecraft.data.worldgen.StructureFeatures;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.OverworldBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.StructureSettings;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.MineshaftConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.ProbabilityFeatureConfiguration;
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
 * <p>Phase 3H-1 adds the restrictions: the frequency reductions and the exclusion zone, compared
 * chunk by chunk against vanilla's own {@code StructurePlacement.isStructureChunk} from 1.18 and
 * against {@code getPotentialFeatureChunk} followed by the {@code isFeatureChunk} override on 1.16.5.
 *
 * <p>Limit of this verification, stated honestly: it covers <em>placement</em> only. It says
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

    /** Windows of chunks, as lower corners: the origin, region boundaries, negative and far out. */
    private static final int[][] WINDOWS = {
            {-24, -24}, {-10_000, 7_000}, {999_980, -1_000_020}, {-1_875_000, 1_874_960},
    };

    private static final int WINDOW_SIZE = 48;

    @Test
    void restrictionsAgreeWithVanillaChunkByChunk() throws Exception {
        // For every set with restrictions: every chunk of several windows, and every grid chunk of a
        // wide square of regions, asked of vanilla's own full placement decision and of the engine.
        StructurePlacementEngine engine = new StructurePlacementEngine();
        int restrictedTypes = 0;

        for (StructureType type : placements.types()) {
            StructurePlacementConfig config = placements.get(type);
            if (!config.hasRestrictions()) {
                continue;
            }
            restrictedTypes++;
            int compared = 0;
            int kept = 0;
            int refused = 0;
            for (long seed : SEEDS) {
                List<int[]> chunks = new ArrayList<int[]>();
                for (int[] window : WINDOWS) {
                    for (int dx = 0; dx < WINDOW_SIZE; dx++) {
                        for (int dz = 0; dz < WINDOW_SIZE; dz++) {
                            chunks.add(new int[] {window[0] + dx, window[1] + dz});
                        }
                    }
                }
                if (config.spacing() > 1) {
                    for (int regionX = -40; regionX < 40; regionX++) {
                        for (int regionZ = -40; regionZ < 40; regionZ++) {
                            long packed = engine.candidateChunk(seed, config, regionX, regionZ);
                            chunks.add(new int[] {StructurePlacementEngine.chunkX(packed),
                                    StructurePlacementEngine.chunkZ(packed)});
                        }
                    }
                }
                for (int[] chunk : chunks) {
                    boolean vanilla = vanillaIsStructureChunk(type, seed, chunk[0], chunk[1]);
                    boolean ours = engine.isStructureChunk(seed, config, chunk[0], chunk[1]);
                    assertEquals(vanilla, ours, type + " seed " + seed + " chunk "
                            + chunk[0] + "," + chunk[1]);
                    compared++;
                    if (ours) {
                        kept++;
                    } else if (isGridChunk(engine, seed, config, chunk[0], chunk[1])) {
                        refused++;
                    }
                }
            }
            System.out.println("restriction oracle " + type + ": " + compared + " chunks, "
                    + kept + " structure chunks, " + refused + " grid chunks refused");
            assertTrue(kept > 0, type + ": no chunk was kept, so acceptance was never compared");
            assertTrue(refused > 0, type + ": no grid chunk was refused, so nothing was compared");
        }
        assertTrue(restrictedTypes >= 3, "expected the outpost, treasure and mineshaft, saw "
                + restrictedTypes);
    }

    private static boolean isGridChunk(StructurePlacementEngine engine, long seed,
                                       StructurePlacementConfig config, int chunkX, int chunkZ) {
        long packed = engine.candidateChunk(seed, config, Math.floorDiv(chunkX, config.spacing()),
                Math.floorDiv(chunkZ, config.spacing()));
        return StructurePlacementEngine.chunkX(packed) == chunkX
                && StructurePlacementEngine.chunkZ(packed) == chunkZ;
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
            case JUNGLE_TEMPLE:
                return BuiltinStructureSets.JUNGLE_TEMPLES;
            case SWAMP_HUT:
                return BuiltinStructureSets.SWAMP_HUTS;
            case IGLOO:
                return BuiltinStructureSets.IGLOOS;
            case PILLAGER_OUTPOST:
                return BuiltinStructureSets.PILLAGER_OUTPOSTS;
            case OCEAN_RUIN:
                return BuiltinStructureSets.OCEAN_RUINS;
            case BURIED_TREASURE:
                return BuiltinStructureSets.BURIED_TREASURES;
            case MINESHAFT:
                return BuiltinStructureSets.MINESHAFTS;
            case TRAIL_RUINS:
                return BuiltinStructureSets.TRAIL_RUINS;
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

    @Test
    void restrictionNumbersMatchVanilla() throws Exception {
        // frequency, frequencyReductionMethod and exclusionZone are protected, so read by
        // reflection: the same values the chunk-by-chunk comparison exercises, named.
        for (StructureType type : placements.types()) {
            StructurePlacementConfig ours = placements.get(type);
            StructurePlacement placement = vanillaPlacement(type);
            float frequency = (Float) field(StructurePlacement.class, "frequency").get(placement);
            Object method = field(StructurePlacement.class, "frequencyReductionMethod").get(placement);
            java.util.Optional<?> zone = (java.util.Optional<?>) field(StructurePlacement.class,
                    "exclusionZone").get(placement);

            assertEquals(frequency, ours.frequency(), type + " frequency");
            if (frequency < 1.0F) {
                assertEquals(((Enum<?>) method).name(), ours.frequencyReduction().name(),
                        type + " frequency reduction");
            }
            assertEquals(zone.isPresent(), ours.exclusionZone() != null, type + " exclusion zone");
            if (zone.isPresent()) {
                Object vanillaZone = zone.get();
                Holder<?> other = (Holder<?>) field(vanillaZone.getClass(), "otherSet").get(vanillaZone);
                int chunkCount = (Integer) field(vanillaZone.getClass(), "chunkCount").get(vanillaZone);
                assertEquals(setKeyOf(ours.exclusionZone().otherType()), other.unwrapKey().get(),
                        type + " exclusion zone set");
                assertEquals(chunkCount, ours.exclusionZone().chunkCount(), type + " exclusion reach");
                assertEquals(placements.get(ours.exclusionZone().otherType()).toString(),
                        ours.exclusionZone().other().toString(),
                        type + " exclusion zone must use this version's own placement of the set");
            }
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static ChunkGeneratorStructureState structureState;
    private static long structureStateSeed;

    /**
     * Vanilla's own isStructureChunk. The state is built through its private constructor: the
     * placement decision reads nothing from it but the level seed, so no biome source or noise is
     * needed - and none is given, so a decision that tried to read one would fail loudly.
     */
    private static boolean vanillaIsStructureChunk(StructureType type, long seed, int chunkX,
                                                   int chunkZ) throws Exception {
        if (structureState == null || structureStateSeed != seed) {
            Constructor<ChunkGeneratorStructureState> constructor =
                    ChunkGeneratorStructureState.class.getDeclaredConstructor(
                            net.minecraft.world.level.levelgen.RandomState.class,
                            net.minecraft.world.level.biome.BiomeSource.class, long.class, long.class,
                            List.class);
            constructor.setAccessible(true);
            structureState = constructor.newInstance(null, null, seed, seed,
                    new ArrayList<Object>());
            structureStateSeed = seed;
        }
        return vanillaPlacement(type).isStructureChunk(structureState, chunkX, chunkZ);
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
            case JUNGLE_TEMPLE:
                return StructureFeature.JUNGLE_TEMPLE;
            case SWAMP_HUT:
                return StructureFeature.SWAMP_HUT;
            case IGLOO:
                return StructureFeature.IGLOO;
            case PILLAGER_OUTPOST:
                return StructureFeature.PILLAGER_OUTPOST;
            case OCEAN_RUIN:
                return StructureFeature.OCEAN_RUIN;
            case BURIED_TREASURE:
                return StructureFeature.BURIED_TREASURE;
            case MINESHAFT:
                return StructureFeature.MINESHAFT;
            default:
                return null;
        }
    }

    private static boolean hasVanillaPlacement(StructureType type) {
        return vanillaFeature(type) != null;
    }

    /^*
     * The configuration isFeatureChunk reads its probability from. Both mineshaft configurations
     * must carry the same one, or a single frequency could not describe the set.
     ^/
    private static FeatureConfiguration configuredConfig(StructureType type) {
        switch (type) {
            case PILLAGER_OUTPOST:
                return StructureFeatures.PILLAGER_OUTPOST.config;
            case BURIED_TREASURE:
                return StructureFeatures.BURIED_TREASURE.config;
            case MINESHAFT:
                assertEquals(((MineshaftConfiguration) StructureFeatures.MINESHAFT.config).probability,
                        ((MineshaftConfiguration) StructureFeatures.MINESHAFT_MESA.config).probability);
                return StructureFeatures.MINESHAFT.config;
            default:
                return null;
        }
    }

    @Test
    void restrictionProbabilitiesMatchVanilla() {
        assertEquals(((ProbabilityFeatureConfiguration) configuredConfig(StructureType.BURIED_TREASURE))
                .probability, placements.get(StructureType.BURIED_TREASURE).frequency());
        assertEquals(((MineshaftConfiguration) configuredConfig(StructureType.MINESHAFT)).probability,
                placements.get(StructureType.MINESHAFT).frequency());
    }

    private static final Map<Long, ChunkGenerator> GENERATORS = new HashMap<Long, ChunkGenerator>();

    private static Method isFeatureChunk;

    /^*
     * Vanilla's own decision before any biome is consulted: getPotentialFeatureChunk, then the
     * feature's isFeatureChunk override, exactly as StructureFeature.generate calls them. The
     * outpost's reads the generator's village settings, so a real generator is given; the biome and
     * the random are not read by any of the three.
     ^/
    private static boolean vanillaIsStructureChunk(StructureType type, long seed, int chunkX,
                                                   int chunkZ) throws Exception {
        StructureFeature<?> feature = vanillaFeature(type);
        StructureFeatureConfiguration config = legacySettings().getConfig(feature);
        ChunkPos potential = feature.getPotentialFeatureChunk(config, seed, legacyRandom(), chunkX,
                chunkZ);
        if (posX(potential) != chunkX || posZ(potential) != chunkZ) {
            return false;
        }
        ChunkGenerator generator = GENERATORS.get(seed);
        if (generator == null) {
            final NoiseGeneratorSettings settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS
                    .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            generator = new NoiseBasedChunkGenerator(
                    new OverworldBiomeSource(seed, false, false, BuiltinRegistries.BIOME), seed,
                    () -> settings);
            GENERATORS.put(seed, generator);
        }
        if (isFeatureChunk == null) {
            isFeatureChunk = StructureFeature.class.getDeclaredMethod("isFeatureChunk",
                    ChunkGenerator.class, BiomeSource.class, long.class, WorldgenRandom.class,
                    int.class, int.class, Biome.class, ChunkPos.class, FeatureConfiguration.class);
            isFeatureChunk.setAccessible(true);
        }
        FeatureConfiguration configured = configuredConfig(type);
        if (configured == null) {
            // No override to call: the base implementation is "return true", which the validator
            // test asserts of every unrestricted structure.
            return true;
        }
        return (Boolean) isFeatureChunk.invoke(feature, generator, generator.getBiomeSource(), seed,
                new WorldgenRandom(), chunkX, chunkZ, null, potential, configured);
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
