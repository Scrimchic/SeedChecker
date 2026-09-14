package com.scrimchic.seedchecker.platform;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.worldgen.StructurePlacementConfig;
import com.scrimchic.seedchecker.worldgen.StructurePlacementEngine;
import com.scrimchic.seedchecker.worldgen.StructurePlacements;
import com.scrimchic.seedchecker.worldgen.StructureType;
import com.scrimchic.seedchecker.worldgen.StructureValidation;

//? if >=1.18 {
import java.util.Optional;
import java.util.function.Predicate;

import com.scrimchic.seedchecker.core.util.LazyInit;
import com.scrimchic.seedchecker.worldgen.GenerationPoint;
import com.scrimchic.seedchecker.worldgen.StructureBiomeStatus;
import com.scrimchic.seedchecker.worldgen.StructureBounds;
import com.scrimchic.seedchecker.worldgen.StructureGeometry;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
//?}
//? if >=26.1 {
import net.minecraft.world.level.Level;
//?} else if >=1.18 {
//?} else {
/*import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.SimpleReloadableResourceManager;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.OverworldBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.StructureSettings;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.feature.configurations.StructureFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.levelgen.structure.BeardedStructureStart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import com.scrimchic.seedchecker.worldgen.StructureBounds;
import com.scrimchic.seedchecker.worldgen.StructureGeometry;
import org.junit.jupiter.api.AfterAll;*/
//?}

import net.minecraft.server.Bootstrap;

/**
 * Proves the biome filter agrees with the Minecraft version it is built against.
 *
 * <p>Where vanilla can be used as an oracle, it is. On 1.16.5 the whole check is reproducible
 * exactly, so the test compares candidate by candidate against vanilla's own answer.
 *
 * <p>From 1.18 onwards vanilla's answer depends on where a structure's generation point lands, and
 * each way of reproducing that has its own oracle: the desert pyramid against vanilla's
 * {@code findValidGenerationPoint}, position included; the jigsaws against the same method run in a
 * generation context this test builds over the loaded data pack, on several seeds, plus vanilla's
 * full {@code Structure.generate} on a few accepted and rejected candidates; and the shipwreck,
 * which stays a superset, against a block-by-block re-derivation of its column enumeration. The
 * biome sets underneath all of them are checked against vanilla's own tag names.
 */
class StructureBiomeValidatorTest {

    private static final long SEED = -7407337299659424542L;

    private static StructurePlacements placements;
    private static BiomeWorldgenSession session;

    @BeforeAll
    static void openSession() {
        //? if >=1.18 {
        SharedConstants.tryDetectVersion();
        //?}
        Bootstrap.bootStrap();
        //? if <1.18 {
        /*// Populates the template pool registry that JigsawPlacement reads while building a
        // village start. Must follow Bootstrap.bootStrap().
        StructureFeature.bootstrap();*/
        //?}
        placements = StructurePlacements.forThisVersion();
        session = BiomeWorldgenSession.create(SEED, BiomeWorldgenSession.OVERWORLD);
        assertNotNull(session);
    }

    @Test
    void everyPlacedStructureHasBiomeRules() {
        for (StructureType type : placements.types()) {
            assertTrue(StructureBiomeValidator.supports(type),
                    type + " is placed but has no biome rules, so nothing would be filtered");
            assertFalse(StructureBiomeValidator.variantNames(type).isEmpty(),
                    type + " has no structure entries");
        }
    }

    @Test
    void everyAcceptedBiomeIsARealBiome() {
        // Catches a mis-parsed or misspelled id, which would silently make a structure
        // un-matchable and hide all of its markers.
        for (StructureType type : placements.types()) {
            for (String variant : StructureBiomeValidator.variantNames(type)) {
                Set<String> biomes = StructureBiomeValidator.acceptedBiomes(type, variant);
                assertFalse(biomes.isEmpty(), type + "/" + variant + " accepts no biome");
                for (String biomeId : biomes) {
                    assertTrue(knownBiomeIds().contains(biomeId),
                            type + "/" + variant + " accepts unknown biome " + biomeId);
                }
            }
        }
    }

    @Test
    void desertPyramidsAcceptDesertAndNothingGreen() {
        Set<String> biomes = allAcceptedBiomes(StructureType.DESERT_PYRAMID);
        assertTrue(biomes.contains("minecraft:desert"), "desert must be accepted: " + biomes);
        assertFalse(biomes.contains("minecraft:jungle"));
        assertFalse(biomes.contains("minecraft:plains"));
        assertFalse(biomes.contains("minecraft:ocean"));
    }

    @Test
    void shipwrecksAcceptWaterAndNotDryLand() {
        Set<String> biomes = allAcceptedBiomes(StructureType.SHIPWRECK);
        assertTrue(biomes.contains("minecraft:ocean"), "ocean must be accepted: " + biomes);
        assertFalse(biomes.contains("minecraft:desert"));
        assertFalse(biomes.contains("minecraft:plains"));
    }

    @Test
    void villagesAcceptTheirOwnBiomesAndNotWater() {
        Set<String> biomes = allAcceptedBiomes(StructureType.VILLAGE);
        assertTrue(biomes.contains("minecraft:plains"), "plains must be accepted: " + biomes);
        assertTrue(biomes.contains("minecraft:desert"), "desert villages exist: " + biomes);
        assertFalse(biomes.contains("minecraft:ocean"));
        assertFalse(biomes.contains("minecraft:jungle"));
    }

    @Test
    void aDesertPyramidIsKeptInADesertAndRejectedOutsideOne() {
        // The concrete false positive this phase exists to remove. Scanned over a wide area,
        // because a small patch of world may contain no desert at all.
        Set<String> accepted = allAcceptedBiomes(StructureType.DESERT_PYRAMID);
        int kept = 0;
        int rejected = 0;

        for (int[] candidate : candidates(placements.get(StructureType.DESERT_PYRAMID), 400)) {
            StructureValidation result = StructureBiomeValidator.validate(
                    session, StructureType.DESERT_PYRAMID, candidate[0], candidate[1]);
            if (result.isRejected()) {
                rejected++;
            } else {
                kept++;
                assertTrue(accepted.contains(result.sampledBiomeId()),
                        "kept on a biome that is not accepted: " + result.sampledBiomeId());
            }
        }
        assertTrue(rejected > 0, "no desert pyramid candidate was rejected, so nothing is filtered");
        assertTrue(kept > 0, "no desert pyramid candidate was kept anywhere, which cannot be right");
        // Deserts are a small share of the overworld, so most candidates must go.
        assertTrue(rejected > kept, "expected most candidates rejected, kept " + kept
                + " of " + (kept + rejected));
    }

    @Test
    void theFilterKeepsSomeAndRejectsSomeOverall() {
        int totalShown = 0;
        int totalRejected = 0;

        for (StructureType type : placements.types()) {
            int shown = 0;
            int total = 0;
            for (int[] candidate : candidates(placements.get(type), 200)) {
                total++;
                if (!StructureBiomeValidator.validate(session, type, candidate[0], candidate[1])
                        .isRejected()) {
                    shown++;
                }
            }
            assertTrue(total > 0, "no candidates found for " + type);
            // Every type must survive somewhere: a type that rejects everything would mean its
            // biome set was read wrongly and all its markers would vanish.
            assertTrue(shown > 0, type + " kept nothing out of " + total);
            totalShown += shown;
            totalRejected += total - shown;
        }
        // And the filter as a whole must actually filter. Only the types whose sample position is
        // known can reject anything at all, so this is asserted across all types rather than per
        // type.
        assertTrue(totalRejected > 0, "nothing at all was filtered out of "
                + (totalShown + totalRejected));
    }

    @Test
    void decisionsAreRepeatable() {
        StructurePlacementConfig config = placements.get(StructureType.VILLAGE);
        for (int[] candidate : candidates(config, 8)) {
            StructureValidation first = StructureBiomeValidator.validate(
                    session, StructureType.VILLAGE, candidate[0], candidate[1]);
            StructureValidation again = StructureBiomeValidator.validate(
                    session, StructureType.VILLAGE, candidate[0], candidate[1]);
            assertEquals(first.status(), again.status());
            assertEquals(first.variant(), again.variant());
            assertEquals(first.reason(), again.reason());
        }
    }

    private static Set<String> allAcceptedBiomes(StructureType type) {
        Set<String> all = new java.util.HashSet<String>();
        for (String variant : StructureBiomeValidator.variantNames(type)) {
            all.addAll(StructureBiomeValidator.acceptedBiomes(type, variant));
        }
        return all;
    }

    /**
     * Grid candidates from a square of regions around the origin, wide enough to contain a decent
     * spread of biomes rather than whatever happens to be next to spawn.
     */
    private static List<int[]> candidates(StructurePlacementConfig config, int wanted) {
        return candidates(config, wanted, SEED);
    }

    private static List<int[]> candidates(StructurePlacementConfig config, int wanted, long seed) {
        return candidatesAround(config, wanted, seed, 0, 0);
    }

    /**
     * Structure chunks - grid placement with the set's restrictions applied, so exactly the chunks
     * vanilla lets the set try - from a square of regions around a chunk, widened until enough are
     * found. For a set with no restrictions this is the first square, as it always was.
     */
    private static List<int[]> candidatesAround(StructurePlacementConfig config, int wanted, long seed,
                                                int originChunkX, int originChunkZ) {
        StructurePlacementEngine engine = new StructurePlacementEngine();
        int originRegionX = Math.floorDiv(originChunkX, config.spacing());
        int originRegionZ = Math.floorDiv(originChunkZ, config.spacing());
        int side = (int) Math.ceil(Math.sqrt(wanted));
        while (true) {
            List<int[]> found = new ArrayList<int[]>();
            for (int dz = -side / 2; dz <= side / 2 && found.size() < wanted; dz++) {
                for (int dx = -side / 2; dx <= side / 2 && found.size() < wanted; dx++) {
                    long packed = engine.candidateChunk(seed, config, originRegionX + dx,
                            originRegionZ + dz);
                    int chunkX = StructurePlacementEngine.chunkX(packed);
                    int chunkZ = StructurePlacementEngine.chunkZ(packed);
                    if (engine.passesRestrictions(seed, config, chunkX, chunkZ)) {
                        found.add(new int[] {chunkX, chunkZ});
                    }
                }
            }
            if (found.size() >= wanted || side > 8192) {
                return found;
            }
            side *= 2;
        }
    }

    // ----------------------------------------------- Phase 3H-1, shared by both oracles

    /** The seeds every Phase 3H-1 structure is compared on: the edges of the seed space. */
    private static final long[] EDGE_SEEDS = {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, SEED};

    /** Far enough out for the long multiplications and for noise a long way from spawn. */
    private static final int FAR_CHUNK = 1_000_000;

    private static boolean isPhase3h1(StructureType type) {
        switch (type) {
            case JUNGLE_TEMPLE:
            case SWAMP_HUT:
            case IGLOO:
            case PILLAGER_OUTPOST:
            case OCEAN_RUIN:
            case BURIED_TREASURE:
            case MINESHAFT:
            case TRAIL_RUINS:
                return true;
            default:
                return false;
        }
    }

    /** Structure chunks around the origin, then around a far chunk with negative Z. */
    private static List<int[]> oracleCandidates(StructurePlacementConfig config, int near, int far,
                                                long seed) {
        List<int[]> all = new ArrayList<int[]>(candidatesAround(config, near, seed, 0, 0));
        all.addAll(candidatesAround(config, far, seed, FAR_CHUNK, -FAR_CHUNK));
        return all;
    }

    @Test
    void phase3h1CostAndDensityAreReported() {
        // A measurement, printed for the phase report: how dense each new structure's candidates are,
        // and what one validation costs on this version. Nothing is asserted beyond "found some".
        StructurePlacementEngine engine = new StructurePlacementEngine();
        ChunkRange area = ChunkRange.of(-256, -256, 255, 255);
        for (StructureType type : placements.types()) {
            if (!isPhase3h1(type)) {
                continue;
            }
            StructurePlacementConfig config = placements.get(type);
            final int[] found = {0};
            engine.forEachCandidate(SEED, config, area, Integer.MAX_VALUE, (x, z) -> {
                found[0]++;
                return true;
            });
            List<int[]> sample = candidates(config, 40);
            int accepted = 0;
            long start = System.nanoTime();
            for (int[] candidate : sample) {
                StructureValidation result =
                        StructureBiomeValidator.validate(session, type, candidate[0], candidate[1]);
                if (result != null && result.isCompatible()) {
                    accepted++;
                }
            }
            double millis = (System.nanoTime() - start) / 1e6 / sample.size();
            System.out.printf("phase 3H-1 %-16s %6.2f candidates per 10k chunks, %6.2f ms per "
                            + "validation, %d of %d accepted%n", type, found[0] * 10000.0 / (512 * 512),
                    millis, accepted, sample.size());
            assertTrue(found[0] > 0, type + " has no candidate in 512 by 512 chunks");
        }
    }

    // ------------------------------------------------- version-specific oracle

    //? if >=1.18 {
    private static HolderLookup.Provider registries;

    private static HolderLookup.Provider registries() {
        if (registries == null) {
            registries = VanillaRegistries.createLookup();
        }
        return registries;
    }

    private static Set<String> knownBiomeIds() {
        Set<String> ids = new java.util.HashSet<String>();
        registries().lookupOrThrow(Registries.BIOME).listElementIds()
                .forEach(key -> ids.add(keyName(key)));
        return ids;
    }

    private static String keyName(ResourceKey<?> key) {
        //? if >=26.1 {
        return key.identifier().toString();
        //?} else {
        /*return key.location().toString();*/
        //?}
    }

    /**
     * The lowest block height vanilla's terrain-derived structure position can take, derived here
     * from the noise settings directly rather than from the session, so the two must agree.
     *
     * <p>{@code NoiseBasedChunkGenerator.getBaseHeight} walks a column clamped to these settings
     * and falls back to the dimension's minimum, and {@code getFirstOccupiedHeight} subtracts one.
     */
    private static int lowestSampledBlockY() {
        return noiseSettingsMinY() - 1;
    }

    /** The highest, from {@code getFirstFreeHeight}, which is {@code getBaseHeight} unshifted. */
    private static int highestSampledBlockY() {
        return noiseSettingsMinY() + noiseSettingsHeight();
    }

    private static int noiseSettingsMinY() {
        return registries().lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value().noiseSettings().minY();
    }

    private static int noiseSettingsHeight() {
        return registries().lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value().noiseSettings().height();
    }

    @Test
    void theSessionColumnSpansEveryHeightVanillaCouldPick() {
        // Everything below rests on the enumerated column covering the whole dimension. If the
        // session's range ever stopped short, rejections would quietly become guesses.
        assertTrue(session.lowestQuartY() <= QuartPos.fromBlock(lowestSampledBlockY()),
                "column starts above the lowest height vanilla can produce");
        assertTrue(session.highestQuartY() >= QuartPos.fromBlock(highestSampledBlockY()),
                "column stops below the highest height vanilla can produce");
    }

    @Test
    void everyStructureCanBeDecidedAndOnlyTheOceanFloorColumnsAreNonExact() {
        // The production semantics after Phase 3H-1, pinned: every placed structure may be
        // rejected, every structure but the three bounded on their ocean-floor column (shipwreck,
        // ocean ruins, buried treasure) is vanilla's exact answer, and the jigsaw ones are all on
        // the exact path.
        HolderLookup.RegistryLookup<Structure> structures =
                registries().lookupOrThrow(Registries.STRUCTURE);
        int jigsaws = 0;

        for (StructureType type : placements.types()) {
            assertTrue(StructureBiomeValidator.canDecide(type), type + " must be decidable");
            boolean bounded = type == StructureType.SHIPWRECK || type == StructureType.OCEAN_RUIN
                    || type == StructureType.BURIED_TREASURE;
            assertEquals(!bounded, StructureBiomeValidator.isExact(type),
                    type + " exactness changed without its oracle being extended");
            for (String variant : StructureBiomeValidator.variantNames(type)) {
                if (vanillaStructure(structures, variant) instanceof JigsawStructure) {
                    jigsaws++;
                    assertTrue(StructureBiomeValidator.isExact(type),
                            variant + " is a jigsaw and must go through the exact jigsaw path");
                }
            }
        }
        assertTrue(jigsaws >= 6, "expected every village variant and the ancient city, saw "
                + jigsaws);
    }

    @Test
    void aRejectionMeansNoHeightWouldHaveWorked() {
        // The claim every hidden marker rests on for the structures whose height is bounded rather
        // than computed, re-derived independently: walk the candidate's centre column one block at
        // a time over the whole dimension, and the validator must reject exactly when not one of
        // those heights carries an accepted biome. Block granularity here against quart granularity
        // in the validator, so a gap in the enumeration shows up.
        //
        // Exactly reproduced structures are deliberately out of scope: they are allowed to reject a
        // candidate some height would have accepted, because vanilla only ever looks at one height
        // and may refuse on grounds of its own. Their guarantee is the stronger one asserted by
        // anExactStructureMatchesVanillaGenerationPoint.
        int rejections = 0;
        int acceptances = 0;

        for (StructureType type : placements.types()) {
            if (!StructureBiomeValidator.canDecide(type)
                    || StructureBiomeValidator.isExact(type)) {
                continue;
            }
            Set<String> accepted = allAcceptedBiomes(type);
            for (int[] candidate : candidates(placements.get(type), 60)) {
                boolean someHeightWorks = false;
                for (int blockY = lowestSampledBlockY(); blockY <= highestSampledBlockY();
                        blockY++) {
                    if (accepted.contains(session.sampleBiomeIdAtQuart(
                            (candidate[0] << 2) + 2, QuartPos.fromBlock(blockY),
                            (candidate[1] << 2) + 2))) {
                        someHeightWorks = true;
                        break;
                    }
                }
                StructureValidation result =
                        StructureBiomeValidator.validate(session, type, candidate[0], candidate[1]);
                assertEquals(!someHeightWorks, result.isRejected(),
                        type + " at chunk " + candidate[0] + "," + candidate[1]);
                if (result.isRejected()) {
                    rejections++;
                } else {
                    acceptances++;
                }
            }
        }
        assertTrue(rejections > 0, "no rejection was checked at all");
        assertTrue(acceptances > 0, "no acceptance was checked at all");
    }

    // ----------------------------------------- Phase 3E-2a: the exactly reproduced structures

    @Test
    void anExactStructureMatchesVanillaGenerationPoint() {
        // The guarantee that lets an exact structure reject a candidate a height would have
        // accepted: vanilla's own findValidGenerationPoint is run for every candidate and must
        // agree in both directions.
        //
        // Reaching it needs no template manager and no registry access - onTopOfChunkCenter and
        // isValidBiome touch neither - so those two slots of the GenerationContext are null. The
        // biome predicate is ours rather than structure.biomes()::contains, because the tags are
        // unbound on this path; that half is already checked against vanilla's tags by
        // everyBiomeTagMatchesTheOneVanillaDeclares, so what is under test here is the position.
        int agreed = 0;
        int generated = 0;

        // The terrain-exact single piece structure. The jigsaws have their own oracle below, against
        // the loaded data pack they are generated from.
        for (StructureType type : new StructureType[] {StructureType.DESERT_PYRAMID,
                StructureType.JUNGLE_TEMPLE, StructureType.IGLOO, StructureType.SWAMP_HUT}) {
            for (String variant : StructureBiomeValidator.variantNames(type)) {
                Structure structure = vanillaStructure(
                        registries().lookupOrThrow(Registries.STRUCTURE), variant);
                Set<String> accepted = StructureBiomeValidator.acceptedBiomes(type, variant);

                for (int[] candidate : candidates(placements.get(type), 300)) {
                    Optional<Structure.GenerationStub> stub = structure.findValidGenerationPoint(
                            generationContext(accepted, candidate[0], candidate[1]));
                    boolean vanilla = stub.isPresent();
                    StructureValidation result = StructureBiomeValidator
                            .validate(session, type, candidate[0], candidate[1]);
                    boolean ours = result.isCompatible();

                    assertEquals(vanilla, ours, type + "/" + variant + " at chunk "
                            + candidate[0] + "," + candidate[1]);
                    if (vanilla) {
                        assertEquals(pointOf(stub.get().position()), result.generationPoint(),
                                "exact generation position at chunk "
                                        + candidate[0] + "," + candidate[1]);
                    }
                    if (vanilla) {
                        generated++;
                    }
                    agreed++;
                }
            }
        }
        assertTrue(agreed > 0, "no exact structure was compared at all");
        // A run where vanilla generated nothing anywhere would pass without testing acceptance.
        assertTrue(generated > 0, "vanilla generated nothing, so only rejections were compared");
    }

    @Test
    void theSeaLevelConditionIsWhatMakesTheExactPathWorthIt() {
        // The concrete thing exactness buys, and the reason the pyramid was singled out: a
        // candidate can sit in a desert, pass every biome test, and still never generate because
        // its lowest corner is under water. Nothing in a biome column can see that.
        int rejectedOnBiome = 0;
        int rejectedBelowSeaLevel = 0;

        for (int[] candidate : candidates(placements.get(StructureType.DESERT_PYRAMID), 600)) {
            StructureValidation result = StructureBiomeValidator.validate(
                    session, StructureType.DESERT_PYRAMID, candidate[0], candidate[1]);
            if (!result.isRejected()) {
                continue;
            }
            if (result.reason() == null) {
                rejectedOnBiome++;
            } else {
                rejectedBelowSeaLevel++;
                assertNotNull(result.sampledBiomeId(),
                        "a sea-level rejection still sampled a biome and should report it");
            }
        }
        assertTrue(rejectedOnBiome > 0, "nothing was rejected on biome, which cannot be right");
        assertTrue(rejectedBelowSeaLevel > 0,
                "no candidate was rejected below sea level, so the exact path proves nothing here");
    }

    private static Structure.GenerationContext generationContext(final Set<String> accepted,
                                                                 int chunkX, int chunkZ) {
        return new Structure.GenerationContext(
                null, oracleChunkGenerator(), oracleBiomeSource(), oracleRandomState(), null, SEED,
                new ChunkPos(chunkX, chunkZ), oracleHeightAccessor(),
                holder -> {
                    ResourceKey<Biome> key = holder.unwrapKey().orElse(null);
                    return key != null && accepted.contains(keyName(key));
                });
    }

    // The oracle's own worldgen: built here rather than read off the session, so the two are
    // independent reconstructions of the same vanilla setup.

    private static BiomeSource oracleBiomeSource;
    private static ChunkGenerator oracleChunkGenerator;
    private static RandomState oracleRandomState;
    private static LevelHeightAccessor oracleHeightAccessor;

    private static void buildOracleWorldgen() {
        if (oracleChunkGenerator != null) {
            return;
        }
        Holder<NoiseGeneratorSettings> settings = registries()
                .lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        oracleRandomState = RandomState.create(settings.value(),
                registries().lookupOrThrow(Registries.NOISE), SEED);
        oracleBiomeSource = MultiNoiseBiomeSource.createFromPreset(
                registries().lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        oracleChunkGenerator = new NoiseBasedChunkGenerator(oracleBiomeSource, settings);
        oracleHeightAccessor = LevelHeightAccessor.create(
                settings.value().noiseSettings().minY(), settings.value().noiseSettings().height());
    }

    private static BiomeSource oracleBiomeSource() {
        buildOracleWorldgen();
        return oracleBiomeSource;
    }

    private static ChunkGenerator oracleChunkGenerator() {
        buildOracleWorldgen();
        return oracleChunkGenerator;
    }

    private static RandomState oracleRandomState() {
        buildOracleWorldgen();
        return oracleRandomState;
    }

    private static LevelHeightAccessor oracleHeightAccessor() {
        buildOracleWorldgen();
        return oracleHeightAccessor;
    }

    private static Structure vanillaStructure(HolderLookup.RegistryLookup<Structure> structures,
                                             String variant) {
        return structures.listElements()
                .filter(holder -> holder.unwrapKey().isPresent()
                        && keyName(holder.unwrapKey().get()).endsWith(":" + variant))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no vanilla structure named " + variant))
                .value();
    }

    @Test
    void everyBiomeTagMatchesTheOneVanillaDeclares() {
        // The real oracle for the modern path: vanilla's own Structure object says which tag it
        // uses, and that must be the tag the datapack reader picked up. unwrapKey() reads the tag
        // name without dereferencing its contents, so it works even though the tags are unbound.
        HolderLookup.RegistryLookup<Structure> structures =
                registries().lookupOrThrow(Registries.STRUCTURE);
        int compared = 0;

        for (StructureType type : placements.types()) {
            for (String variant : StructureBiomeValidator.variantNames(type)) {
                String ours = StructureBiomeValidator.declaredBiomeTag(type, variant);
                assertNotNull(ours, type + "/" + variant + " read no biome tag");

                TagKey<Biome> vanillaTag = vanillaStructure(structures, variant).biomes().unwrapKey()
                        .orElseThrow(() -> new AssertionError(variant + " has an inline biome set"));
                // TagKey.location() is spelled the same on both modern targets, and only the
                // returned type differs - which Stonecutter already renames.
                assertEquals(vanillaTag.location().toString(), ours,
                        type + "/" + variant + " biome tag");
                compared++;
            }
        }
        assertTrue(compared >= 8, "expected a broad comparison, compared " + compared);
    }

    // --------------------------------------- Phase 3E-2: exact jigsaw generation through vanilla

    /** Several seeds, so a coincidence of one world cannot pass for a guarantee. */
    private static final long[] JIGSAW_SEEDS = {SEED, 0L, 20260913L};

    /** Every seed a vanilla-point oracle runs on: the Phase 3E ones and the Phase 3H-1 edges. */
    private static final long[] ORACLE_SEEDS = {SEED, 0L, 20260913L, 1L, -1L, Long.MIN_VALUE,
            Long.MAX_VALUE};

    /** Phase 3H-1 structures run on the edge seeds, the earlier ones on the seeds they always had. */
    private static boolean runsOn(StructureType type, long seed) {
        long[] seeds = isPhase3h1(type) ? EDGE_SEEDS : JIGSAW_SEEDS;
        for (long candidate : seeds) {
            if (candidate == seed) {
                return true;
            }
        }
        return false;
    }

    private static List<int[]> vanillaPointCandidates(StructureType type, int wanted, long seed) {
        return isPhase3h1(type)
                ? oracleCandidates(placements.get(type), wanted, wanted / 3, seed)
                : candidates(placements.get(type), wanted, seed);
    }

    @Test
    void theEntryListIsVanillasOwnInOrderAndWeight() {
        // The weighted draw is reproduced over our parsed entry list, so that list must be the one
        // vanilla draws over - same entries, same order, same weights.
        HolderLookup.RegistryLookup<StructureSet> sets =
                structureData().registries().lookupOrThrow(Registries.STRUCTURE_SET);
        for (StructureType type : placements.types()) {
            final String path = setPathOf(type);
            StructureSet set = sets.listElements()
                    .filter(holder -> keyName(holder.key()).endsWith(":" + path))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no structure set " + path))
                    .value();

            List<String> vanillaNames = new ArrayList<String>();
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                String id = keyName(entry.structure().unwrapKey().get());
                String name = id.substring(id.indexOf(':') + 1);
                vanillaNames.add(name);
                assertEquals(entry.weight(), StructureBiomeValidator.variantWeight(type, name),
                        type + "/" + name + " weight");
            }
            assertEquals(vanillaNames, StructureBiomeValidator.variantNames(type), type + " order");
        }
    }

    @Test
    void theSelectionOrderIsAWeightedPermutation() {
        // Not an oracle - vanilla's draw runs inside createStructures on a real chunk - but the
        // properties a transcription error would break: a permutation, deterministic per chunk,
        // and a first pick that follows the weights.
        int[] even = {1, 1, 1, 1, 1};
        int[] firstPicks = new int[even.length];
        for (int chunkZ = -40; chunkZ < 40; chunkZ++) {
            for (int chunkX = -40; chunkX < 40; chunkX++) {
                int[] order = session.structureSelectionOrder(even, chunkX, chunkZ);
                assertArrayEquals(order, session.structureSelectionOrder(even, chunkX, chunkZ));
                boolean[] seen = new boolean[even.length];
                for (int index : order) {
                    assertFalse(seen[index], "an entry was tried twice");
                    seen[index] = true;
                }
                firstPicks[order[0]]++;
            }
        }
        for (int picks : firstPicks) {
            assertTrue(picks > 980 && picks < 1580, "uneven first picks " + java.util.Arrays.toString(firstPicks));
        }

        int[] skewed = {3, 1};
        int heavyFirst = 0;
        for (int i = 0; i < 4000; i++) {
            if (session.structureSelectionOrder(skewed, i, -i)[0] == 0) {
                heavyFirst++;
            }
        }
        assertTrue(heavyFirst > 2700 && heavyFirst < 3300, "weight 3 of 4 won " + heavyFirst);
    }

    @Test
    void jigsawDecisionsAndPositionsMatchVanilla() throws Exception {
        // The production guarantee for the jigsaws. For every entry of the set, vanilla's own
        // findValidGenerationPoint is run with that entry's biome predicate in a generation context
        // this test builds itself - its own template manager, noise and chunk generator - and the
        // production decision must agree: compatible exactly when some entry is valid, and then
        // naming a valid entry and that entry's exact stub position.
        int compared = 0;
        int generated = 0;
        int rejected = 0;
        int severalValid = 0;

        for (long seed : ORACLE_SEEDS) {
            BiomeWorldgenSession seedSession =
                    BiomeWorldgenSession.create(seed, BiomeWorldgenSession.OVERWORLD);
            LoadedWorld world = new LoadedWorld(seed);

            for (StructureType type : placements.types()) {
                if (!isJigsawType(type) || !runsOn(type, seed)) {
                    continue;
                }
                int wanted = type == StructureType.VILLAGE ? 40
                        : type == StructureType.ANCIENT_CITY ? 120 : 60;
                for (int[] candidate : vanillaPointCandidates(type, wanted, seed)) {
                    StructureValidation ours = StructureBiomeValidator
                            .validate(seedSession, type, candidate[0], candidate[1]);
                    String where = type + " seed " + seed + " chunk "
                            + candidate[0] + "," + candidate[1];
                    assertNotNull(ours, where + ": the data is loaded, so nothing may be pending");
                    assertNotEquals(StructureBiomeStatus.UNKNOWN, ours.status(), where);

                    List<String> valid = new ArrayList<String>();
                    java.util.Map<String, BlockPos> stubs = new java.util.HashMap<String, BlockPos>();
                    for (String variant : StructureBiomeValidator.variantNames(type)) {
                        Optional<Structure.GenerationStub> stub = world.structure(variant).value()
                                .findValidGenerationPoint(world.context(candidate[0], candidate[1],
                                        predicateFor(type, variant)));
                        if (stub.isPresent()) {
                            valid.add(variant);
                            stubs.put(variant, stub.get().position());
                        }
                    }

                    assertEquals(!valid.isEmpty(), ours.isCompatible(), where);
                    if (ours.isCompatible()) {
                        assertTrue(ours.isExact(), where);
                        assertTrue(valid.contains(ours.variant()),
                                where + ": " + ours.variant() + " is not one vanilla accepts " + valid);
                        assertEquals(pointOf(stubs.get(ours.variant())), ours.generationPoint(),
                                where + " exact generation position");
                        generated++;
                        if (valid.size() > 1) {
                            severalValid++;
                        }
                    } else {
                        rejected++;
                    }
                    compared++;
                }
            }
        }
        System.out.println("jigsaw oracle: " + compared + " candidates, " + generated
                + " generated, " + rejected + " rejected, " + severalValid
                + " with more than one valid entry");
        assertTrue(generated > 0, "nothing generated, so acceptance was never compared");
        assertTrue(rejected > 0, "nothing was rejected, so rejection was never compared");
    }

    @Test
    void anAcceptedJigsawReallyBuildsAStructureStart() throws Exception {
        // findValidGenerationPoint is not yet "the structure exists": Structure.generate then
        // assembles pieces and asks StructureStart.isValid(). Production relies on that step always
        // keeping the start piece for a jigsaw of positive size. Checked here with vanilla's full
        // generate() - expensive, since it assembles every piece, so on a handful of candidates.
        LoadedWorld world = new LoadedWorld(SEED);
        int acceptedChecked = 0;
        int rejectedChecked = 0;

        for (StructureType type : placements.types()) {
            if (!isJigsawType(type)) {
                continue;
            }
            int accepted = 0;
            int refused = 0;
            for (int[] candidate : candidates(placements.get(type), 400)) {
                if (accepted >= 3 && refused >= 3) {
                    break;
                }
                StructureValidation ours = StructureBiomeValidator
                        .validate(session, type, candidate[0], candidate[1]);
                if (ours.isCompatible() && accepted < 3) {
                    assertTrue(world.generates(ours.variant(), candidate[0], candidate[1],
                            predicateFor(type, ours.variant())),
                            type + " accepted at " + candidate[0] + "," + candidate[1]
                                    + " but vanilla builds no structure start");
                    accepted++;
                } else if (ours.isRejected() && refused < 3) {
                    for (String variant : StructureBiomeValidator.variantNames(type)) {
                        assertFalse(world.generates(variant, candidate[0], candidate[1],
                                predicateFor(type, variant)),
                                type + "/" + variant + " rejected at " + candidate[0] + ","
                                        + candidate[1] + " but vanilla builds it");
                    }
                    refused++;
                }
            }
            acceptedChecked += accepted;
            rejectedChecked += refused;
        }
        assertTrue(acceptedChecked > 0, "no accepted jigsaw candidate was built");
        assertTrue(rejectedChecked > 0, "no rejected jigsaw candidate was checked");
    }

    @Test
    void theStructureDataUniverseAgreesWithTheBiomeTileUniverse() throws Exception {
        // Two sets of vanilla objects exist side by side - VanillaRegistries for biome tiles and
        // the desert pyramid, the loaded data pack for the jigsaws. No holder crosses between them,
        // but their answers must still be the same world, or a jigsaw would be judged on a
        // different map from the one drawn under it.
        LoadedWorld world = new LoadedWorld(SEED);
        int[] heights = {-50, 0, 64, 150};
        for (int i = 0; i < 400; i++) {
            int x = (int) ((i * 7919L) % 40000) - 20000;
            int z = (int) ((i * 104729L) % 40000) - 20000;
            for (int y : heights) {
                assertEquals(session.sampleBiomeId(x, y, z), world.biomeIdAt(x, y, z),
                        "biome at " + x + "," + y + "," + z);
            }
            if (i % 5 == 0) {
                assertEquals(session.surfaceOccupiedHeight(x, z), world.surfaceOccupiedHeight(x, z),
                        "surface at " + x + "," + z);
            }
        }
    }

    // ------------------------------------------- Phase 3F: exact structure geometry

    @Test
    void jigsawGeometryIsVanillasOwnStructureStart() throws Exception {
        // The oracle is vanilla's StructureStart, built by Structure.generate in a context this test
        // owns: its bounding box must be exactly our bounds put through vanilla's own
        // adjustBoundingBox, every piece it generated must lie inside our bounds, and each of our six
        // faces must be reached by one of its pieces - so the bounds are neither short nor loose.
        int compared = 0;
        for (long seed : ORACLE_SEEDS) {
            BiomeWorldgenSession seedSession =
                    BiomeWorldgenSession.create(seed, BiomeWorldgenSession.OVERWORLD);
            LoadedWorld world = new LoadedWorld(seed);

            for (StructureType type : placements.types()) {
                if (!isJigsawType(type) || !runsOn(type, seed)) {
                    continue;
                }
                // A village's pieces take a second or more to assemble, and this assembles each
                // structure twice. The Phase 3H-1 ones run on twice the seeds, one each.
                int wanted = type == StructureType.VILLAGE || isPhase3h1(type) ? 1 : 2;
                int found = 0;
                for (int[] candidate : candidates(placements.get(type), 600, seed)) {
                    if (found >= wanted) {
                        break;
                    }
                    StructureValidation validation = StructureBiomeValidator
                            .validate(seedSession, type, candidate[0], candidate[1]);
                    if (!validation.isCompatible()) {
                        continue;
                    }
                    String where = type + " seed " + seed + " chunk "
                            + candidate[0] + "," + candidate[1];

                    StructureGeometry ours = StructureGeometryGenerator.generate(seedSession, type,
                            validation.variant(), candidate[0], candidate[1]);
                    assertNotNull(ours, where);
                    assertTrue(ours.isAvailable(), where + ": " + ours);
                    assertEquals(validation.generationPoint(), ours.generationPoint(),
                            where + ": geometry was assembled from a different start");

                    StructureStart start = world.start(validation.variant(), candidate[0],
                            candidate[1], predicateFor(type, validation.variant()));
                    assertTrue(start.isValid(), where);

                    StructureBounds bounds = ours.bounds();
                    BoundingBox adjusted = world.structure(validation.variant()).value()
                            .adjustBoundingBox(new BoundingBox(bounds.minX(), bounds.minY(),
                                    bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()));
                    assertSameBox(start.getBoundingBox(), adjusted, where + " StructureStart box");
                    assertPiecesSpan(bounds, start.getPieces(), where);
                    found++;
                    compared++;
                }
                assertEquals(wanted, found, type + " seed " + seed + ": too few structures found");
            }
        }
        System.out.println("geometry oracle: " + compared + " jigsaw structures");
    }

    @Test
    void thePyramidsStartHeightIsAPlaceholderSoItClaimsNoBounds() throws Exception {
        // Why the desert pyramid has no bounds: vanilla builds its piece at y 64 whatever the
        // terrain, and moves it only while placing it into generated chunks. The shipwreck here is
        // not exact at all.
        LoadedWorld world = new LoadedWorld(SEED);
        int pyramids = 0;
        boolean placeholderDiffersFromTerrain = false;
        for (int[] candidate : candidates(placements.get(StructureType.DESERT_PYRAMID), 3000)) {
            if (pyramids >= 3) {
                break;
            }
            StructureValidation validation = StructureBiomeValidator
                    .validate(session, StructureType.DESERT_PYRAMID, candidate[0], candidate[1]);
            if (!validation.isCompatible()) {
                continue;
            }
            StructureGeometry geometry = StructureGeometryGenerator.generate(session,
                    StructureType.DESERT_PYRAMID, validation.variant(), candidate[0], candidate[1]);
            assertFalse(geometry.isAvailable());
            assertNotNull(geometry.unavailableReason());

            StructureStart start = world.start(validation.variant(), candidate[0], candidate[1],
                    predicateFor(StructureType.DESERT_PYRAMID, validation.variant()));
            assertEquals(64, start.getBoundingBox().minY(), "vanilla's start-time pyramid height");
            if (validation.generationPoint().y() != 64) {
                placeholderDiffersFromTerrain = true;
            }
            pyramids++;
        }
        assertTrue(pyramids > 0, "no desert pyramid found");
        assertTrue(placeholderDiffersFromTerrain,
                "every pyramid happened to stand at y 64, so nothing was shown");

        for (int[] candidate : candidates(placements.get(StructureType.SHIPWRECK), 60)) {
            StructureValidation validation = StructureBiomeValidator
                    .validate(session, StructureType.SHIPWRECK, candidate[0], candidate[1]);
            if (validation.isCompatible()) {
                assertFalse(StructureGeometryGenerator.generate(session, StructureType.SHIPWRECK,
                        validation.variant(), candidate[0], candidate[1]).isAvailable());
                return;
            }
        }
        throw new AssertionError("no compatible shipwreck found");
    }

    // ----------------------------------------- Phase 3H-1: surface and ocean-floor structures

    @Test
    void phase3h1SurfaceAndOceanFloorStructuresMatchVanillaOnEverySeed() throws Exception {
        // For the new structures this version either reproduces itself (the surface ones) or
        // bounds (the ocean-floor ones): vanilla's own findValidGenerationPoint per entry, in the
        // test's own loaded world, on the edge seeds, near the origin and a million chunks out.
        // Exact ones agree both ways and on the point, and an accepted one really builds a
        // StructureStart; bounded ones never hide a candidate vanilla accepts, and claim no bounds.
        StructureType[] types = {StructureType.JUNGLE_TEMPLE, StructureType.SWAMP_HUT,
                StructureType.IGLOO, StructureType.OCEAN_RUIN, StructureType.BURIED_TREASURE};
        java.util.Map<StructureType, int[]> tally = new java.util.LinkedHashMap<StructureType, int[]>();

        for (long seed : EDGE_SEEDS) {
            BiomeWorldgenSession seedSession =
                    BiomeWorldgenSession.create(seed, BiomeWorldgenSession.OVERWORLD);
            LoadedWorld world = new LoadedWorld(seed);
            for (StructureType type : types) {
                boolean exact = StructureBiomeValidator.isExact(type);
                int[] counts = tally.computeIfAbsent(type, key -> new int[5]);
                int built = 0;
                // Swamps are a sliver of the modern overworld: 360 candidates saw none at all.
                boolean rare = type == StructureType.SWAMP_HUT;
                for (int[] candidate : oracleCandidates(placements.get(type), rare ? 400 : 40,
                        rare ? 100 : 20, seed)) {
                    String where = type + " seed " + seed + " chunk "
                            + candidate[0] + "," + candidate[1];
                    StructureValidation ours = StructureBiomeValidator
                            .validate(seedSession, type, candidate[0], candidate[1]);
                    assertNotNull(ours, where);

                    List<String> valid = new ArrayList<String>();
                    java.util.Map<String, BlockPos> stubs = new java.util.HashMap<String, BlockPos>();
                    for (String variant : StructureBiomeValidator.variantNames(type)) {
                        Optional<Structure.GenerationStub> stub = world.structure(variant).value()
                                .findValidGenerationPoint(world.context(candidate[0], candidate[1],
                                        predicateFor(type, variant)));
                        if (stub.isPresent()) {
                            valid.add(variant);
                            stubs.put(variant, stub.get().position());
                        }
                    }
                    counts[0]++;
                    if (!valid.isEmpty()) {
                        counts[1]++;
                    }
                    if (ours.isCompatible()) {
                        counts[2]++;
                    }

                    if (exact) {
                        assertEquals(!valid.isEmpty(), ours.isCompatible(), where);
                        if (ours.isCompatible()) {
                            assertTrue(ours.isExact(), where);
                            assertTrue(valid.contains(ours.variant()), where + ": " + ours);
                            assertEquals(pointOf(stubs.get(ours.variant())), ours.generationPoint(),
                                    where + " exact generation position");
                            if (built < 2) {
                                assertTrue(world.generates(ours.variant(), candidate[0],
                                        candidate[1], predicateFor(type, ours.variant())),
                                        where + ": accepted but vanilla builds no structure start");
                                built++;
                                counts[3]++;
                            }
                        }
                    } else {
                        assertFalse(ours.isExact(), where);
                        if (!valid.isEmpty()) {
                            assertTrue(ours.isCompatible(),
                                    where + ": vanilla generates " + valid + " but it was hidden");
                        } else if (ours.isCompatible()) {
                            counts[4]++;
                        }
                        if (ours.isCompatible() && counts[3] == 0) {
                            assertFalse(StructureGeometryGenerator.generate(seedSession, type,
                                    ours.variant(), candidate[0], candidate[1]).isAvailable(),
                                    where + ": a bounded structure must claim no bounds");
                            counts[3]++;
                        }
                    }
                }
            }
        }
        for (java.util.Map.Entry<StructureType, int[]> entry : tally.entrySet()) {
            int[] counts = entry.getValue();
            System.out.println("phase 3H-1 oracle " + entry.getKey() + ": " + counts[0]
                    + " candidates, vanilla generates " + counts[1] + ", ours accepts " + counts[2]
                    + (StructureBiomeValidator.isExact(entry.getKey())
                            ? ", " + counts[3] + " built by Structure.generate"
                            : ", " + counts[4] + " accepted that vanilla does not generate"));
            assertTrue(counts[1] > 0, entry.getKey() + ": vanilla generated nothing, so acceptance "
                    + "was never compared");
            assertTrue(counts[1] < counts[0], entry.getKey() + ": nothing was rejected");
        }
    }

    @Test
    void phase3h1SurfaceStructuresBuildAtAPlaceholderSoClaimNoBounds() throws Exception {
        // Why the jungle temple, the swamp hut and the igloo have no bounds: vanilla builds the
        // first two at y 64 whatever the terrain and moves them while placing them into chunks, and
        // the igloo's pieces are likewise moved to the heightmap in postProcess.
        LoadedWorld world = new LoadedWorld(SEED);
        StructureType[] types = {StructureType.JUNGLE_TEMPLE, StructureType.SWAMP_HUT,
                StructureType.IGLOO};
        int[] placeholders = {64, 64, Integer.MIN_VALUE};
        for (int i = 0; i < types.length; i++) {
            StructureType type = types[i];
            int found = 0;
            for (int[] candidate : candidates(placements.get(type), 6000)) {
                if (found >= 2) {
                    break;
                }
                StructureValidation validation = StructureBiomeValidator
                        .validate(session, type, candidate[0], candidate[1]);
                if (!validation.isCompatible()) {
                    continue;
                }
                StructureGeometry geometry = StructureGeometryGenerator.generate(session, type,
                        validation.variant(), candidate[0], candidate[1]);
                assertFalse(geometry.isAvailable(), type + " must not claim bounds");
                assertNotNull(geometry.unavailableReason());
                StructureStart start = world.start(validation.variant(), candidate[0], candidate[1],
                        predicateFor(type, validation.variant()));
                assertTrue(start.isValid(), type + " accepted but not built");
                if (placeholders[i] != Integer.MIN_VALUE) {
                    assertEquals(placeholders[i], start.getBoundingBox().minY(),
                            type + " start-time height is vanilla's placeholder");
                }
                found++;
            }
            assertEquals(2, found, "too few " + type + " found");
        }
    }

    private static void assertSameBox(BoundingBox expected, BoundingBox actual, String what) {
        assertEquals(expected.minX() + "," + expected.minY() + "," + expected.minZ() + " .. "
                        + expected.maxX() + "," + expected.maxY() + "," + expected.maxZ(),
                actual.minX() + "," + actual.minY() + "," + actual.minZ() + " .. "
                        + actual.maxX() + "," + actual.maxY() + "," + actual.maxZ(), what);
    }

    private static void assertPiecesSpan(StructureBounds bounds, List<StructurePiece> pieces,
                                         String where) {
        assertFalse(pieces.isEmpty(), where + ": vanilla generated no pieces");
        boolean[] touched = new boolean[6];
        for (StructurePiece piece : pieces) {
            BoundingBox box = piece.getBoundingBox();
            assertTrue(bounds.contains(box.minX(), box.minY(), box.minZ())
                            && bounds.contains(box.maxX(), box.maxY(), box.maxZ()),
                    where + ": a piece lies outside the bounds: " + box);
            touched[0] |= box.minX() == bounds.minX();
            touched[1] |= box.minY() == bounds.minY();
            touched[2] |= box.minZ() == bounds.minZ();
            touched[3] |= box.maxX() == bounds.maxX();
            touched[4] |= box.maxY() == bounds.maxY();
            touched[5] |= box.maxZ() == bounds.maxZ();
        }
        for (int face = 0; face < 6; face++) {
            assertTrue(touched[face], where + ": no piece reaches face " + face + " of " + bounds);
        }
    }

    /** Every structure whose generation point production asks of vanilla: jigsaws and mineshaft. */
    private static boolean isJigsawType(StructureType type) {
        return type == StructureType.VILLAGE || type == StructureType.ANCIENT_CITY
                || type == StructureType.TRIAL_CHAMBER || type == StructureType.PILLAGER_OUTPOST
                || type == StructureType.TRAIL_RUINS || type == StructureType.MINESHAFT;
    }

    private static String setPathOf(StructureType type) {
        switch (type) {
            case VILLAGE:
                return "villages";
            case DESERT_PYRAMID:
                return "desert_pyramids";
            case SHIPWRECK:
                return "shipwrecks";
            case ANCIENT_CITY:
                return "ancient_cities";
            case TRIAL_CHAMBER:
                return "trial_chambers";
            case JUNGLE_TEMPLE:
                return "jungle_temples";
            case SWAMP_HUT:
                return "swamp_huts";
            case IGLOO:
                return "igloos";
            case PILLAGER_OUTPOST:
                return "pillager_outposts";
            case OCEAN_RUIN:
                return "ocean_ruins";
            case BURIED_TREASURE:
                return "buried_treasures";
            case MINESHAFT:
                return "mineshafts";
            case TRAIL_RUINS:
                return "trail_ruins";
            default:
                throw new AssertionError(type);
        }
    }

    private static GenerationPoint pointOf(BlockPos position) {
        return new GenerationPoint(position.getX(), position.getY(), position.getZ());
    }

    private static Predicate<Holder<Biome>> predicateFor(StructureType type, String variant) {
        final Set<String> accepted = StructureBiomeValidator.acceptedBiomes(type, variant);
        return holder -> {
            ResourceKey<Biome> key = holder.unwrapKey().orElse(null);
            return key != null && accepted.contains(keyName(key));
        };
    }

    private static VanillaStructureData structureData() {
        assertEquals(LazyInit.State.READY, VanillaStructureData.loadIfNeeded(),
                "the vanilla data pack must load on the test classpath");
        return VanillaStructureData.get();
    }

    /**
     * A generation context of the test's own over the loaded data pack: separate template manager,
     * noise and chunk generator from anything production built, sharing only the frozen registries.
     */
    private static final class LoadedWorld {

        private final VanillaStructureData data;
        private final long seed;
        private final StructureTemplateManager templates;
        private final RandomState randomState;
        private final BiomeSource biomeSource;
        private final ChunkGenerator chunkGenerator;
        private final LevelHeightAccessor heightAccessor;

        LoadedWorld(long seed) throws Exception {
            this.data = structureData();
            this.seed = seed;
            this.templates = data.newTemplateManager();
            Holder<NoiseGeneratorSettings> settings = data.registries()
                    .lookupOrThrow(Registries.NOISE_SETTINGS)
                    .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            this.randomState = RandomState.create(settings.value(),
                    data.registries().lookupOrThrow(Registries.NOISE), seed);
            this.biomeSource = MultiNoiseBiomeSource.createFromPreset(
                    data.registries().lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                            .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
            this.chunkGenerator = new NoiseBasedChunkGenerator(biomeSource, settings);
            this.heightAccessor = LevelHeightAccessor.create(
                    settings.value().noiseSettings().minY(), settings.value().noiseSettings().height());
        }

        Holder<Structure> structure(final String variant) {
            return data.registries().lookupOrThrow(Registries.STRUCTURE).listElements()
                    .filter(holder -> keyName(holder.key()).endsWith(":" + variant))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no loaded structure " + variant));
        }

        Structure.GenerationContext context(int chunkX, int chunkZ,
                                            Predicate<Holder<Biome>> biomes) {
            return new Structure.GenerationContext(data.registries(), chunkGenerator, biomeSource,
                    randomState, templates, seed, new ChunkPos(chunkX, chunkZ), heightAccessor,
                    biomes);
        }

        /** Vanilla's full Structure.generate, pieces and all. */
        boolean generates(String variant, int chunkX, int chunkZ, Predicate<Holder<Biome>> biomes) {
            return start(variant, chunkX, chunkZ, biomes).isValid();
        }

        /** The StructureStart vanilla's Structure.generate builds - the geometry oracle. */
        StructureStart start(String variant, int chunkX, int chunkZ,
                             Predicate<Holder<Biome>> biomes) {
            Holder<Structure> holder = structure(variant);
            //? if >=26.1 {
            return holder.value().generate(holder, Level.OVERWORLD, data.registries(),
                    chunkGenerator, biomeSource, randomState, templates, seed,
                    new ChunkPos(chunkX, chunkZ), 0, heightAccessor, biomes);
            //?} else {
            /*return holder.value().generate(data.registries(), chunkGenerator, biomeSource,
                    randomState, templates, seed, new ChunkPos(chunkX, chunkZ), 0, heightAccessor,
                    biomes);*/
            //?}
        }

        String biomeIdAt(int x, int y, int z) {
            ResourceKey<Biome> key = biomeSource.getNoiseBiome(QuartPos.fromBlock(x),
                    QuartPos.fromBlock(y), QuartPos.fromBlock(z), randomState.sampler())
                    .unwrapKey().orElse(null);
            return key == null ? "seedchecker:unnamed" : keyName(key);
        }

        int surfaceOccupiedHeight(int x, int z) {
            return chunkGenerator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                    heightAccessor, randomState);
        }
    }
    //?} else {
    /*private static Set<String> knownBiomeIds() {
        Set<String> ids = new java.util.HashSet<String>();
        Registry<Biome> registry = BuiltinRegistries.BIOME;
        for (Biome biome : registry) {
            if (registry.getKey(biome) != null) {
                ids.add(registry.getKey(biome).toString());
            }
        }
        return ids;
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

    @Test
    void everyStructureIsDecidableHere() {
        for (StructureType type : placements.types()) {
            assertTrue(StructureBiomeValidator.canDecide(type),
                    type + " must be decidable on 1.16.5, where the sampled position is fixed");
        }
    }

    @Test
    void everyDecisionMatchesVanillaExactly() {
        // 1.16.5 samples the biome at a fixed, terrain independent position and then asks that
        // biome which structures may start in it, so the check is fully reproducible and this is a
        // true oracle - candidate by candidate, no approximation.
        Registry<Biome> registry = BuiltinRegistries.BIOME;
        OverworldBiomeSource biomeSource = new OverworldBiomeSource(SEED, false, false, registry);
        int compared = 0;

        for (StructureType type : placements.types()) {
            StructureFeature<?> feature = vanillaFeature(type);
            assertNotNull(feature, "no vanilla feature for " + type);

            for (int[] candidate : candidates(placements.get(type), 40)) {
                Biome biome = biomeSource.getNoiseBiome(
                        (candidate[0] << 2) + 2, 0, (candidate[1] << 2) + 2);
                boolean vanillaAccepts = biome.getGenerationSettings().isValidStart(feature);
                StructureValidation result = StructureBiomeValidator
                        .validate(session, type, candidate[0], candidate[1]);

                assertEquals(vanillaAccepts, result.isCompatible(), type + " at chunk "
                        + candidate[0] + "," + candidate[1]
                        + " biome " + registry.getKey(biome));
                // 1.16.5 samples a fixed position at a literal height, so nothing here is ever
                // undecidable and the two answers are complements rather than three-way.
                assertEquals(!vanillaAccepts, result.isRejected(), type + " at chunk "
                        + candidate[0] + "," + candidate[1]);
                compared++;
            }
        }
        assertTrue(compared >= 100, "expected a broad comparison, compared " + compared);
    }

    // ------------------------------------------- Phase 3E-2b: nothing follows the biome check

    @Test
    void onlyTheRestrictedStructuresOverrideTheExtraPlacementPredicate() {
        // StructureFeature.generate applies isFeatureChunk between grid placement and building the
        // start, and the base implementation is a plain "return true". Asked of vanilla's real
        // class hierarchy rather than assumed: an override is a predicate, and the only ones this
        // version may have are the ones the placement table models as restrictions - checked chunk
        // by chunk against the overrides themselves in VanillaStructurePlacementTest.
        for (StructureType type : placements.types()) {
            StructureFeature<?> feature = vanillaFeature(type);
            assertNotNull(feature, "no vanilla feature for " + type);

            Class<?> declaring = null;
            for (Class<?> current = feature.getClass();
                    current != null && declaring == null;
                    current = current.getSuperclass()) {
                for (java.lang.reflect.Method method : current.getDeclaredMethods()) {
                    if ("isFeatureChunk".equals(method.getName())) {
                        declaring = current;
                        break;
                    }
                }
            }
            if (placements.get(type).hasRestrictions()) {
                assertNotEquals(StructureFeature.class, declaring,
                        type + " is modelled with restrictions vanilla no longer has");
            } else {
                assertEquals(StructureFeature.class, declaring,
                        type + " overrides isFeatureChunk, so an unmodelled predicate now exists");
            }
        }
    }

    @Test
    void nothingAfterTheBiomeCheckCanRejectAStart() {
        // The whole of Phase 3E-2b, answered end to end instead of by reading bytecode: run
        // vanilla's real ConfiguredStructureFeature.generate - with a real chunk generator, so a
        // terrain condition would be exercised, and a real template manager, so shipwreck and
        // village genuinely build their pieces - and require its answer to be exactly ours.
        //
        // Both mismatch directions matter. A "vanilla generated where we said no" would be a
        // hidden structure; a "we said yes where vanilla generated nothing" would mean a predicate
        // exists after the biome check that this phase does not model.
        int accepted = 0;
        int rejected = 0;

        for (StructureType type : placements.types()) {
            StructureFeature<?> feature = vanillaFeature(type);
            assertNotNull(feature, "no vanilla feature for " + type);

            for (int[] candidate : candidates(placements.get(type), isPhase3h1(type) ? 80 : 300)) {
                boolean ours = StructureBiomeValidator
                        .validate(session, type, candidate[0], candidate[1]).isCompatible();
                boolean vanilla = vanillaStarts(feature, candidate[0], candidate[1]);

                assertEquals(vanilla, ours, type + " at chunk "
                        + candidate[0] + "," + candidate[1]
                        + ": biome validity and real generation must agree on 1.16.5");
                if (vanilla) {
                    accepted++;
                } else {
                    rejected++;
                }
            }
        }
        // A run where nothing generated anywhere would pass vacuously.
        assertTrue(accepted > 0, "no candidate generated at all, so the oracle proved nothing");
        assertTrue(rejected > 0, "no candidate was rejected at all");
    }

    /^*
     * Vanilla's own answer for one candidate, assembled exactly as
     * {@code ChunkGenerator.createStructures} does: sample the biome at the fixed quart position,
     * walk that biome's configured structures, and run the one being asked about.
     ^/
    private static boolean vanillaStarts(StructureFeature<?> feature, int chunkX, int chunkZ) {
        StructureStart<?> start = vanillaStart(feature, chunkX, chunkZ);
        return start != null && start.isValid();
    }

    /^* The start vanilla builds there, or null when the biome does not list the feature at all. ^/
    private static StructureStart<?> vanillaStart(StructureFeature<?> feature, int chunkX,
                                                  int chunkZ) {
        return legacyStart(oracleBiomeSource(), oracleChunkGenerator(), SEED, feature, chunkX,
                chunkZ);
    }

    /^* The same, in a world of any seed. ^/
    private static StructureStart<?> legacyStart(OverworldBiomeSource biomeSource,
                                                 ChunkGenerator generator, long seed,
                                                 StructureFeature<?> feature, int chunkX,
                                                 int chunkZ) {
        Biome biome = biomeSource.getNoiseBiome((chunkX << 2) + 2, 0, (chunkZ << 2) + 2);

        for (java.util.function.Supplier<ConfiguredStructureFeature<?, ?>> supplier
                : biome.getGenerationSettings().structures()) {
            ConfiguredStructureFeature<?, ?> configured = supplier.get();
            if (configured.feature != feature) {
                continue;
            }
            StructureFeatureConfiguration placement = StructureSettings.DEFAULTS.get(feature);
            assertNotNull(placement, "no default placement for " + feature);
            return configured.generate(RegistryAccess.builtin(), generator, biomeSource,
                    oracleTemplates(), seed, new ChunkPos(chunkX, chunkZ), biome, 0, placement);
        }
        // Not in this biome's list at all, which is vanilla rejecting it on biome grounds.
        return null;
    }

    private static ChunkGenerator legacyGenerator(OverworldBiomeSource biomeSource, long seed) {
        final NoiseGeneratorSettings settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        return new NoiseBasedChunkGenerator(biomeSource, seed,
                new java.util.function.Supplier<NoiseGeneratorSettings>() {
                    @Override
                    public NoiseGeneratorSettings get() {
                        return settings;
                    }
                });
    }

    // ------------------------------------------- Phase 3H-1: the new structures, every edge seed

    @Test
    void phase3h1StructuresMatchVanillaGenerationOnEverySeed() {
        // Vanilla's whole ConfiguredStructureFeature.generate - isFeatureChunk, pieces and isValid -
        // against production, on the edge seeds, near the origin and a million chunks out. And the
        // other direction for the restricted ones: grid chunks the restrictions refuse, in a biome
        // that lists the structure, must not generate - so the restrictions are not decoration.
        java.util.Map<StructureType, int[]> tally = new java.util.LinkedHashMap<StructureType, int[]>();
        StructurePlacementEngine engine = new StructurePlacementEngine();

        for (long seed : EDGE_SEEDS) {
            BiomeWorldgenSession seedSession =
                    BiomeWorldgenSession.create(seed, BiomeWorldgenSession.OVERWORLD);
            OverworldBiomeSource biomeSource =
                    new OverworldBiomeSource(seed, false, false, BuiltinRegistries.BIOME);
            ChunkGenerator generator = legacyGenerator(biomeSource, seed);

            for (StructureType type : placements.types()) {
                if (!isPhase3h1(type)) {
                    continue;
                }
                StructureFeature<?> feature = vanillaFeature(type);
                StructurePlacementConfig config = placements.get(type);
                int[] counts = tally.computeIfAbsent(type, key -> new int[3]);
                for (int[] candidate : oracleCandidates(config, 25, 10, seed)) {
                    boolean ours = StructureBiomeValidator
                            .validate(seedSession, type, candidate[0], candidate[1]).isCompatible();
                    StructureStart<?> start = legacyStart(biomeSource, generator, seed, feature,
                            candidate[0], candidate[1]);
                    boolean vanilla = start != null && start.isValid();
                    assertEquals(vanilla, ours, type + " seed " + seed + " chunk "
                            + candidate[0] + "," + candidate[1]);
                    counts[0]++;
                    if (vanilla) {
                        counts[1]++;
                    }
                }
                if (!config.hasRestrictions()) {
                    continue;
                }
                int refused = 0;
                for (int regionX = -60; regionX < 60 && refused < 4; regionX++) {
                    for (int regionZ = -60; regionZ < 60 && refused < 4; regionZ++) {
                        long packed = engine.candidateChunk(seed, config, regionX, regionZ);
                        int x = StructurePlacementEngine.chunkX(packed);
                        int z = StructurePlacementEngine.chunkZ(packed);
                        if (engine.passesRestrictions(seed, config, x, z)
                                || !biomeSource.getNoiseBiome((x << 2) + 2, 0, (z << 2) + 2)
                                        .getGenerationSettings().isValidStart(feature)) {
                            continue;
                        }
                        StructureStart<?> start = legacyStart(biomeSource, generator, seed, feature,
                                x, z);
                        assertFalse(start != null && start.isValid(), type + " seed " + seed
                                + " chunk " + x + "," + z + ": refused by its restrictions, yet built");
                        refused++;
                        counts[2]++;
                    }
                }
            }
        }
        for (java.util.Map.Entry<StructureType, int[]> entry : tally.entrySet()) {
            int[] counts = entry.getValue();
            System.out.println("phase 3H-1 oracle " + entry.getKey() + ": " + counts[0]
                    + " candidates, " + counts[1] + " generated, " + counts[2]
                    + " refused-by-restriction chunks confirmed empty");
            // A refusal is compared either among the candidates or among the chunks the restrictions
            // refused: 1.16.5 lets the mineshaft start in nearly every overworld biome, so for it
            // the second is the only kind a run is guaranteed to meet.
            assertTrue(counts[1] > 0 && (counts[1] < counts[0] || counts[2] > 0),
                    entry.getKey() + ": both outcomes must be compared");
        }
    }

    // ------------------------------------------- Phase 3F: exact structure geometry

    @Test
    void villageGeometryIsVanillasOwnStructureStart() {
        startTimeGeometryIsVanillasOwnStructureStart(StructureType.VILLAGE, 3);
    }

    @Test
    void outpostAndMineshaftGeometryIsVanillasOwnStructureStart() {
        startTimeGeometryIsVanillasOwnStructureStart(StructureType.PILLAGER_OUTPOST, 2);
        startTimeGeometryIsVanillasOwnStructureStart(StructureType.MINESHAFT, 2);
    }

    /^*
     * Production builds its own start on the session's biome source and its own template manager;
     * the oracle is the start built here, independently, by the same vanilla call
     * ChunkGenerator.createStructures makes. A jigsaw's start is bearded - its box is the pieces'
     * extent inflated by 12 - and the mineshaft's is plain, its box the extent itself.
     ^/
    private static void startTimeGeometryIsVanillasOwnStructureStart(StructureType type, int wanted) {
        int compared = 0;
        boolean bearded = type != StructureType.MINESHAFT;
        int inflation = bearded ? 12 : 0;
        for (int[] candidate : candidates(placements.get(type), 400)) {
            if (compared >= wanted) {
                break;
            }
            if (!StructureBiomeValidator.validate(session, type, candidate[0],
                    candidate[1]).isCompatible()) {
                continue;
            }
            String where = type + " at chunk " + candidate[0] + "," + candidate[1];
            StructureGeometry ours = StructureGeometryGenerator.generate(session,
                    type, null, candidate[0], candidate[1]);
            assertTrue(ours.isAvailable(), where + ": " + ours);

            StructureStart<?> start = vanillaStart(vanillaFeature(type), candidate[0],
                    candidate[1]);
            assertNotNull(start, where);
            assertTrue(start.isValid(), where);
            assertEquals(bearded, start instanceof BeardedStructureStart,
                    where + ": start kind");
            BoundingBox box = start.getBoundingBox();
            StructureBounds bounds = ours.bounds();
            assertEquals(box.x0 + "," + box.y0 + "," + box.z0 + " .. " + box.x1 + "," + box.y1
                            + "," + box.z1,
                    (bounds.minX() - inflation) + "," + (bounds.minY() - inflation) + ","
                            + (bounds.minZ() - inflation) + " .. " + (bounds.maxX() + inflation)
                            + "," + (bounds.maxY() + inflation) + "," + (bounds.maxZ() + inflation),
                    where);

            boolean[] touched = new boolean[6];
            for (StructurePiece piece : start.getPieces()) {
                BoundingBox pieceBox = piece.getBoundingBox();
                assertTrue(bounds.contains(pieceBox.x0, pieceBox.y0, pieceBox.z0)
                        && bounds.contains(pieceBox.x1, pieceBox.y1, pieceBox.z1),
                        where + ": a piece lies outside the bounds");
                touched[0] |= pieceBox.x0 == bounds.minX();
                touched[1] |= pieceBox.y0 == bounds.minY();
                touched[2] |= pieceBox.z0 == bounds.minZ();
                touched[3] |= pieceBox.x1 == bounds.maxX();
                touched[4] |= pieceBox.y1 == bounds.maxY();
                touched[5] |= pieceBox.z1 == bounds.maxZ();
            }
            for (int face = 0; face < 6; face++) {
                assertTrue(touched[face], where + ": no piece reaches face " + face);
            }
            compared++;
        }
        assertEquals(wanted, compared, "too few " + type + " found");
    }

    @Test
    void structuresPlacedAtAPlaceholderHeightClaimNoBounds() {
        // Vanilla builds these at y 64 and y 90 and only moves them to the terrain while placing
        // them into generated chunks, so no bounds are claimed for either.
        // Phase 3H-1 adds the jungle temple and swamp hut at 64, ocean ruins and buried treasure at
        // 90, and the igloo, whose pieces reach below its placeholder so only the claim is checked.
        int[] placeholders = {64, 90, 64, 64, 90, 90, Integer.MIN_VALUE};
        StructureType[] types = {StructureType.DESERT_PYRAMID, StructureType.SHIPWRECK,
                StructureType.JUNGLE_TEMPLE, StructureType.SWAMP_HUT, StructureType.OCEAN_RUIN,
                StructureType.BURIED_TREASURE, StructureType.IGLOO};
        for (int i = 0; i < types.length; i++) {
            StructureType type = types[i];
            int checked = 0;
            for (int[] candidate : candidates(placements.get(type), 2000)) {
                if (checked >= 2) {
                    break;
                }
                if (!StructureBiomeValidator.validate(session, type, candidate[0], candidate[1])
                        .isCompatible()) {
                    continue;
                }
                assertFalse(StructureGeometryGenerator.generate(session, type, null, candidate[0],
                        candidate[1]).isAvailable(), type + " must not claim bounds");
                StructureStart<?> start = vanillaStart(vanillaFeature(type), candidate[0],
                        candidate[1]);
                if (placeholders[i] != Integer.MIN_VALUE) {
                    assertEquals(placeholders[i], start.getBoundingBox().y0,
                            type + " start-time height is vanilla's placeholder");
                }
                checked++;
            }
            assertEquals(2, checked, "too few " + type + " found");
        }
    }

    // The oracle's own worldgen, built once and independent of the session under test.

    private static OverworldBiomeSource oracleBiomeSource;
    private static ChunkGenerator oracleChunkGenerator;
    private static StructureManager oracleTemplates;
    private static LevelStorageSource.LevelStorageAccess oracleLevel;

    private static OverworldBiomeSource oracleBiomeSource() {
        if (oracleBiomeSource == null) {
            oracleBiomeSource =
                    new OverworldBiomeSource(SEED, false, false, BuiltinRegistries.BIOME);
        }
        return oracleBiomeSource;
    }

    private static ChunkGenerator oracleChunkGenerator() {
        if (oracleChunkGenerator == null) {
            final NoiseGeneratorSettings settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS
                    .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            // Real, so that a terrain-dependent check would actually run. No chunk is generated.
            oracleChunkGenerator = new NoiseBasedChunkGenerator(oracleBiomeSource(), SEED,
                    new java.util.function.Supplier<NoiseGeneratorSettings>() {
                        @Override
                        public NoiseGeneratorSettings get() {
                            return settings;
                        }
                    });
        }
        return oracleChunkGenerator;
    }

    /^*
     * A template manager over the vanilla data pack in the Minecraft jar. Shipwreck and village
     * both load a template while building their start, so without one neither could be asked.
     ^/
    private static StructureManager oracleTemplates() {
        if (oracleTemplates == null) {
            try {
                SimpleReloadableResourceManager resources =
                        new SimpleReloadableResourceManager(PackType.SERVER_DATA);
                resources.add(new VanillaPackResources("minecraft"));
                java.nio.file.Path temp =
                        java.nio.file.Files.createTempDirectory("seedchecker-oracle");
                temp.toFile().deleteOnExit();
                oracleLevel = LevelStorageSource.createDefault(temp).createAccess("oracle");
                oracleTemplates = new StructureManager(resources, oracleLevel,
                        DataFixers.getDataFixer());
            } catch (java.io.IOException unavailable) {
                throw new AssertionError("could not open the vanilla template manager", unavailable);
            }
        }
        return oracleTemplates;
    }

    @AfterAll
    static void releaseOracleLevel() throws Exception {
        if (oracleLevel != null) {
            oracleLevel.close();
            oracleLevel = null;
        }
    }
    *///?}
}
