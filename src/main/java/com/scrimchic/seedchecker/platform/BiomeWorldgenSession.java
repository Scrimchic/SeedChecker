package com.scrimchic.seedchecker.platform;

import java.util.IdentityHashMap;
import java.util.Map;

import com.scrimchic.seedchecker.worldgen.biome.BiomeSampleLevel;

import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;

//? if >=1.18 {
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
//?} else {
/*import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.world.level.biome.OverworldBiomeSource;*/
//?}

/**
 * Vanilla biome generation for an arbitrary seed, with nothing else attached.
 *
 * <p>This is the whole of the vanilla-backed side of the hybrid architecture from
 * {@code docs/worldgen-engine-spike.md}: biome noise is data driven and far too large to
 * reimplement, so Minecraft does it, and this class is the only place that knows how. Nothing
 * above it sees a {@code Biome}, a {@code Holder} or a {@code ResourceKey} - callers get canonical
 * id strings.
 *
 * <p>No world, no save, no integrated server and no {@code ChunkGenerator} are involved. The spike
 * established that biomes need only a biome source plus, from 1.18 onwards, a
 * {@code RandomState} - and that neither dereferences a biome tag, which is what makes this work
 * on 26.2 where the structure-state API does not.
 *
 * <p><strong>One session per thread.</strong> The spike observed no cross-thread corruption, but
 * 1.16.5's layer stack carries mutable per-instance caches and the modern density functions are
 * documented as single threaded, so a session is owned by whichever worker created it and the
 * render thread never touches one.
 */
public final class BiomeWorldgenSession {

    public static final String OVERWORLD = "minecraft:overworld";

    /** Vanilla's own bootstrap is idempotent, so calling it defensively costs nothing in game. */
    private static boolean bootstrapped;

    /**
     * Canonical ids, keyed by the biome object vanilla handed back.
     *
     * <p>Vanilla builds a fresh String every time an id is stringified, and a tile asks a thousand
     * times, so the id is resolved once per distinct biome. Identity keyed because both a registry
     * {@code Biome} and a {@code Holder.Reference} are stable single instances.
     */
    private final Map<Object, String> biomeIds = new IdentityHashMap<Object, String>();

    private final long seed;

    /**
     * The coarsest sample step this version can both generate and draw without the map costing
     * more than it is worth. Two measured constraints, and the tighter one wins.
     *
     * <p><strong>Generating.</strong> From 1.18 onwards the cost per sample is flat - 11 to 16 us
     * whatever the step - because climate noise is evaluated point by point anyway. 1.16.5's
     * layered biome system is the opposite: its zoom layers carry per-instance caches that only
     * pay off for samples close together, so a tile costs 7.6 ms at a four-block step, 18.1 ms at
     * sixteen and 98.8 ms at sixty-four.
     *
     * <p><strong>Drawing.</strong> A 1080p screenful reduces to 7,100 merged rectangles at a
     * four-block step on 1.20.1 and 20,900 at sixteen; 1.16.5's biome layout is more fragmented
     * and gives 10,900 and 37,900 for the same steps. Past that the rectangles stop merging at
     * all - 54,600 at sixty-four, 104,500 at 256 - because neighbouring samples are far enough
     * apart that the biome field is uncorrelated.
     *
     * <p>So 1.16.5 stops at four blocks and the newer versions at sixteen. Beyond that the layer
     * reports itself unavailable rather than stuttering or burning a worker for half a minute.
     */
    public static int coarsestBlockStep() {
        //? if >=1.18 {
        return BiomeSampleLevel.MEDIUM.blockStep();
        //?} else {
        /*return BiomeSampleLevel.NEAR.blockStep();*/
        //?}
    }

    /** @return whether this dimension can be generated at all; only the Overworld for now. */
    public static boolean supportsDimension(String dimensionId) {
        return OVERWORLD.equals(dimensionId);
    }

    /**
     * Builds a session. Slow enough to matter - the spike measured 65 ms on 1.16.5 and up to
     * 900 ms on 26.2 including the one-off registry build - so it must never run on the render
     * thread.
     *
     * @return the session, or {@code null} for a dimension that is not supported yet
     */
    public static BiomeWorldgenSession create(long seed, String dimensionId) {
        if (!supportsDimension(dimensionId)) {
            return null;
        }
        ensureBootstrapped();
        return new BiomeWorldgenSession(seed);
    }

    private static synchronized void ensureBootstrapped() {
        if (bootstrapped) {
            return;
        }
        //? if >=1.18 {
        SharedConstants.tryDetectVersion();
        //?}
        Bootstrap.bootStrap();
        bootstrapped = true;
    }

    public long seed() {
        return seed;
    }

    /**
     * The biome vanilla would generate at that block position.
     *
     * <p>{@code blockY} is honoured: from 1.18 onwards biomes are three dimensional and different
     * heights genuinely return different biomes. On 1.16.5 the overworld source ignores it.
     *
     * @return a canonical id such as {@code minecraft:plains}, never {@code null}
     */
    public String sampleBiomeId(int blockX, int blockY, int blockZ) {
        // Biomes live on a four-block grid, so block coordinates are reduced to quart coordinates
        // exactly the way vanilla does it internally.
        return resolveId(sampleRaw(blockX >> 2, blockY >> 2, blockZ >> 2));
    }

    private String resolveId(Object biome) {
        String cached = biomeIds.get(biome);
        if (cached == null) {
            cached = readId(biome);
            biomeIds.put(biome, cached);
        }
        return cached;
    }

    // ------------------------------------------------------- version-specific worldgen

    //? if >=1.18 {
    /** Seed independent and immutable once built, so all sessions share one. */
    private static HolderLookup.Provider sharedRegistries;

    private final BiomeSource biomeSource;
    private final Climate.Sampler climate;

    private BiomeWorldgenSession(long seed) {
        this.seed = seed;
        HolderLookup.Provider registries = registries();

        // The (Provider, ResourceKey, long) overload of RandomState.create only exists on 26.x,
        // where HolderLookup.Provider gained "extends HolderGetter.Provider"; this one is on both.
        RandomState randomState = RandomState.create(
                registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                        .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value(),
                registries.lookupOrThrow(Registries.NOISE),
                seed);

        this.climate = randomState.sampler();
        this.biomeSource = MultiNoiseBiomeSource.createFromPreset(
                registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
    }

    private static synchronized HolderLookup.Provider registries() {
        if (sharedRegistries == null) {
            // Registry contents only - no tags are bound, and none are needed for biomes. This is
            // the expensive part of session setup, and it happens once for the whole game.
            sharedRegistries = VanillaRegistries.createLookup();
        }
        return sharedRegistries;
    }

    private Object sampleRaw(int quartX, int quartY, int quartZ) {
        return biomeSource.getNoiseBiome(quartX, quartY, quartZ, climate);
    }

    @SuppressWarnings("unchecked")
    private static String readId(Object biome) {
        ResourceKey<Biome> key = ((Holder<Biome>) biome).unwrapKey().orElse(null);
        return key == null ? "seedchecker:unnamed" : keyName(key);
    }

    private static String keyName(ResourceKey<Biome> key) {
        //? if >=26.1 {
        return key.identifier().toString();
        //?} else {
        /*return key.location().toString();*/
        //?}
    }
    //?} else {
    /*private final Registry<Biome> biomeRegistry;
    private final OverworldBiomeSource biomeSource;

    private BiomeWorldgenSession(long seed) {
        this.seed = seed;
        // 1.16.5 keeps all worldgen content in code, so the registry is simply a static; the
        // legacy layer stack takes the seed directly and owns mutable caches, which is exactly why
        // each thread gets its own source.
        this.biomeRegistry = BuiltinRegistries.BIOME;
        this.biomeSource = new OverworldBiomeSource(seed, false, false, biomeRegistry);
    }

    private Object sampleRaw(int quartX, int quartY, int quartZ) {
        return biomeSource.getNoiseBiome(quartX, quartY, quartZ);
    }

    private String readId(Object biome) {
        net.minecraft.resources.ResourceLocation id = biomeRegistry.getKey((Biome) biome);
        return id == null ? "seedchecker:unnamed" : id.toString();
    }
    *///?}
}
