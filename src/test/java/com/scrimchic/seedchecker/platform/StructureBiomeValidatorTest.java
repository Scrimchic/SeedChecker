package com.scrimchic.seedchecker.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.scrimchic.seedchecker.worldgen.StructurePlacementConfig;
import com.scrimchic.seedchecker.worldgen.StructurePlacementEngine;
import com.scrimchic.seedchecker.worldgen.StructurePlacements;
import com.scrimchic.seedchecker.worldgen.StructureType;
import com.scrimchic.seedchecker.worldgen.StructureValidation;

//? if >=1.18 {
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
//?} else {
/*import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.OverworldBiomeSource;
import net.minecraft.world.level.levelgen.feature.StructureFeature;*/
//?}

import net.minecraft.server.Bootstrap;

/**
 * Proves the biome filter agrees with the Minecraft version it is built against.
 *
 * <p>Where vanilla can be used as an oracle, it is. On 1.16.5 the whole check is reproducible
 * exactly, so the test compares candidate by candidate against vanilla's own answer.
 *
 * <p>From 1.18 onwards vanilla's answer depends on the position of a structure's start piece, which
 * this phase computes for some structures and not others, so the oracle is split into three:
 * vanilla's own {@code Structure} object says which biome tag each structure uses and whether it is
 * a {@code JigsawStructure}, which is what decides whether a candidate may be rejected at all; and
 * the column enumeration behind every rejection is re-derived at block granularity and compared,
 * so a rejection can only stand if no height in the dimension would have been accepted.
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
        StructurePlacementEngine engine = new StructurePlacementEngine();
        int side = (int) Math.ceil(Math.sqrt(wanted));
        List<int[]> found = new ArrayList<int[]>();
        for (int regionZ = -side / 2; regionZ <= side / 2 && found.size() < wanted; regionZ++) {
            for (int regionX = -side / 2; regionX <= side / 2 && found.size() < wanted; regionX++) {
                long packed = engine.candidateChunk(SEED, config, regionX, regionZ);
                found.add(new int[] {
                        StructurePlacementEngine.chunkX(packed),
                        StructurePlacementEngine.chunkZ(packed),
                });
            }
        }
        return found;
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
    void onlyStructuresWithAKnownSamplePositionCanBeRejected() {
        // Vanilla itself decides this: a JigsawStructure takes its stub position from the start
        // piece's bounding box centre, which depends on the template drawn from the start pool, so
        // the candidate chunk's own column is not the column vanilla looks at. Anything built on
        // Structure.onTopOfChunkCenter does use that column and can be decided.
        HolderLookup.RegistryLookup<Structure> structures =
                registries().lookupOrThrow(Registries.STRUCTURE);
        int checked = 0;

        for (StructureType type : placements.types()) {
            boolean anyJigsaw = false;
            for (String variant : StructureBiomeValidator.variantNames(type)) {
                if (vanillaStructure(structures, variant) instanceof JigsawStructure) {
                    anyJigsaw = true;
                }
                checked++;
            }
            assertEquals(!anyJigsaw, StructureBiomeValidator.canDecide(type),
                    type + " decidability must follow whether vanilla places it with a jigsaw");
        }
        assertTrue(checked >= 8, "expected a broad comparison, checked " + checked);
    }

    @Test
    void aJigsawCandidateIsNeverRejected() {
        // The rule this audit exists to enforce: where the sampled position is unknown, no marker
        // may be hidden, whatever the biomes under the chunk happen to be.
        int checked = 0;
        for (StructureType type : placements.types()) {
            if (StructureBiomeValidator.canDecide(type)) {
                continue;
            }
            for (int[] candidate : candidates(placements.get(type), 200)) {
                StructureValidation result =
                        StructureBiomeValidator.validate(session, type, candidate[0], candidate[1]);
                assertFalse(result.isRejected(), type + " must not be rejected at chunk "
                        + candidate[0] + "," + candidate[1]);
                assertNotNull(result.reason(), type + " must say why it cannot be decided");
                checked++;
            }
        }
        assertTrue(checked > 0, "no undecidable structure was checked");
    }

    @Test
    void aRejectionMeansNoHeightWouldHaveWorked() {
        // The claim every hidden marker rests on, re-derived independently: walk the candidate's
        // centre column one block at a time over the whole dimension, and the validator must reject
        // exactly when not one of those heights carries an accepted biome. Block granularity here
        // against quart granularity in the validator, so a gap in the enumeration shows up.
        int rejections = 0;
        int acceptances = 0;

        for (StructureType type : placements.types()) {
            if (!StructureBiomeValidator.canDecide(type)) {
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
    *///?}
}
