package com.scrimchic.seedchecker.platform;

import java.util.List;
import java.util.Set;

import com.scrimchic.seedchecker.worldgen.ConcentricRingConfig;
import com.scrimchic.seedchecker.worldgen.GenerationPoint;
import com.scrimchic.seedchecker.worldgen.StrongholdPlacementEngine;
import com.scrimchic.seedchecker.worldgen.StrongholdPosition;

//? if >=1.18 {
import com.google.gson.JsonObject;
//?} else {
/*import java.util.Collections;
import java.util.HashSet;

import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.OverworldBiomeSource;
import net.minecraft.world.level.levelgen.StructureSettings;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.feature.configurations.StrongholdConfiguration;*/
//?}

/**
 * Every stronghold of a world, for an arbitrary seed, exactly where vanilla puts it.
 *
 * <p>The ring arithmetic and the biome search are {@link StrongholdPlacementEngine}'s, in pure Java.
 * This class supplies the three things that are the version's to decide: the ring parameters, the
 * biomes the search prefers, and which generator the search draws from - and it answers the biome
 * question by sampling this session's own biome source.
 *
 * <h2>1.16.5</h2>
 *
 * <p>{@code ChunkGenerator.generateStrongholds}. Parameters from the chunk generator's structure
 * settings, which for the overworld are {@code StructureSettings.DEFAULT_STRONGHOLD}; preferred
 * biomes are the biome source's {@code possibleBiomes()} whose generation settings list the
 * stronghold ({@code isValidStart}) - the possible list, not the whole registry, which the first
 * version of this class used and a vanilla oracle caught; the search shares the ring generator.
 *
 * <h2>1.18 onwards</h2>
 *
 * <p>{@code ChunkGeneratorStructureState.generateRingPositions}, seeded with the world seed by
 * {@code createForNormal}. Parameters and preferred biomes from the {@code strongholds} structure set
 * in the vanilla data pack, its {@code #stronghold_biased_to} tag resolved the same way every other
 * structure's biome tag is, so 1.20.1 - whose loaded registries leave that tag unbound - and 26.2
 * answer alike; each stronghold's search forks its own generator.
 *
 * <p>Nothing here depends on a loaded world or on vanilla's structure state, which is what lets it
 * work for a seed typed in on a server.
 */
public final class StrongholdLocator {

    private static volatile ConcentricRingConfig ringConfig;
    private static volatile Set<String> preferredBiomes;

    private StrongholdLocator() {
    }

    /**
     * The whole list, in vanilla's placement order.
     *
     * <p>Worker threads only: over three thousand biome samples per stronghold.
     */
    public static List<StrongholdPosition> locate(final BiomeWorldgenSession session) {
        final Set<String> preferred = preferredBiomes();
        return StrongholdPlacementEngine.place(session.seed(), ringConfig(), randomModel(),
                (quartX, quartY, quartZ) ->
                        preferred.contains(session.sampleBiomeIdAtQuart(quartX, quartY, quartZ)));
    }

    /**
     * The block a stronghold's structure start is built from, or {@code null} on a version where it
     * is not computed.
     *
     * <p>From 1.18, {@code StrongholdStructure.findGenerationPoint} is unconditionally
     * {@code chunkPos.getWorldPosition()} - the chunk's minimum corner at y 0.
     */
    public static GenerationPoint generationPoint(StrongholdPosition position) {
        //? if >=1.18 {
        return new GenerationPoint(position.chunkX() << 4, 0, position.chunkZ() << 4);
        //?} else {
        /*return null;*/
        //?}
    }

    /** This version's ring parameters, read from vanilla. */
    public static ConcentricRingConfig ringConfig() {
        ConcentricRingConfig config = ringConfig;
        if (config == null) {
            config = loadRingConfig();
            ringConfig = config;
        }
        return config;
    }

    /** The biome ids the ring search is drawn towards, read from vanilla. */
    public static Set<String> preferredBiomes() {
        Set<String> biomes = preferredBiomes;
        if (biomes == null) {
            biomes = loadPreferredBiomes();
            preferredBiomes = biomes;
        }
        return biomes;
    }

    //? if >=1.18 {
    public static StrongholdPlacementEngine.RandomModel randomModel() {
        return StrongholdPlacementEngine.RandomModel.FORKED;
    }

    private static JsonObject placement() {
        JsonObject set = StructureBiomeValidator.vanillaDataJson("worldgen/structure_set/strongholds");
        if (set == null || !set.has("placement")) {
            throw new IllegalStateException("the vanilla data pack has no strongholds structure set");
        }
        JsonObject placement = set.getAsJsonObject("placement");
        if (!"minecraft:concentric_rings".equals(placement.get("type").getAsString())) {
            throw new IllegalStateException("strongholds no longer use concentric rings: " + placement);
        }
        return placement;
    }

    private static ConcentricRingConfig loadRingConfig() {
        JsonObject placement = placement();
        return new ConcentricRingConfig(placement.get("distance").getAsInt(),
                placement.get("spread").getAsInt(), placement.get("count").getAsInt());
    }

    private static Set<String> loadPreferredBiomes() {
        return StructureBiomeValidator.resolveBiomes(placement().get("preferred_biomes"));
    }
    //?} else {
    /*public static StrongholdPlacementEngine.RandomModel randomModel() {
        return StrongholdPlacementEngine.RandomModel.SHARED;
    }

    private static ConcentricRingConfig loadRingConfig() {
        StrongholdConfiguration stronghold = StructureSettings.DEFAULT_STRONGHOLD;
        return new ConcentricRingConfig(stronghold.distance(), stronghold.spread(),
                stronghold.count());
    }

    private static Set<String> loadPreferredBiomes() {
        // generateStrongholds walks biomeSource.possibleBiomes(), not the registry: a biome that
        // lists the stronghold but is not in the overworld source's possible list is never
        // preferred, even though the layer stack can return it. The list is seed independent.
        Set<String> biomes = new HashSet<String>();
        Registry<Biome> registry = BuiltinRegistries.BIOME;
        OverworldBiomeSource source = new OverworldBiomeSource(0L, false, false, registry);
        for (Biome biome : source.possibleBiomes()) {
            ResourceLocation id = registry.getKey(biome);
            if (id != null
                    && biome.getGenerationSettings().isValidStart(StructureFeature.STRONGHOLD)) {
                biomes.add(id.toString());
            }
        }
        return Collections.unmodifiableSet(biomes);
    }
    *///?}
}
