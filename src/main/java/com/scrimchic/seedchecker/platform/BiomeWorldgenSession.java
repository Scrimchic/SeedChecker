package com.scrimchic.seedchecker.platform;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.scrimchic.seedchecker.worldgen.biome.BiomeSampleLevel;

import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;

//? if >=1.18 {
import java.util.logging.Level;

import com.scrimchic.seedchecker.SeedChecker;
import com.scrimchic.seedchecker.core.util.LazyInit;
import com.scrimchic.seedchecker.worldgen.GenerationPoint;
import com.scrimchic.seedchecker.worldgen.StructureGeometry;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
//?} else {
/*import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.OverworldBiomeSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;*/
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

    public static final String NETHER = "minecraft:the_nether";

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
    private final String dimensionId;

    /** Built on first use; the session is thread confined, so no lock. */
    private Set<String> possibleBiomeIds;

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

    /**
     * @return whether this dimension can be generated at all: the Overworld and, since Phase 3H-2,
     *         the Nether - each from its own vanilla noise settings, biome source and level height
     */
    public static boolean supportsDimension(String dimensionId) {
        return OVERWORLD.equals(dimensionId) || NETHER.equals(dimensionId);
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
        return new BiomeWorldgenSession(seed, dimensionId);
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

    /** The dimension this session generates, e.g. {@code minecraft:the_nether}. */
    public String dimensionId() {
        return dimensionId;
    }

    /**
     * Whether this dimension's biome at a block column is the same at every height.
     *
     * <p>True for the nether on every target, from vanilla's own definitions rather than from
     * sampling: 1.16.5's {@code MultiNoiseBiomeSource} is constructed with {@code useY} false, and
     * the modern nether noise router gives the climate sampler temperature and vegetation noises of
     * {@code y_scale} 0 and constant zero continents, erosion, depth and ridges. False for the
     * overworld, whose biomes are three dimensional from 1.18. {@code NetherStructureTest} also
     * checks it sample by sample on each target.
     */
    public boolean isBiomeColumnConstant() {
        return NETHER.equals(dimensionId);
    }

    /**
     * Every biome this session's biome source can return - vanilla's
     * {@code BiomeSource.possibleBiomes()} - as ids.
     *
     * <p>The proof an entry can never start here: a biome test, wherever its position lands, only
     * ever sees one of these. Computed once per session.
     */
    public Set<String> possibleBiomeIds() {
        if (possibleBiomeIds == null) {
            Set<String> ids = new HashSet<String>();
            for (Object biome : possibleBiomesRaw()) {
                ids.add(resolveId(biome));
            }
            possibleBiomeIds = Collections.unmodifiableSet(ids);
        }
        return possibleBiomeIds;
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

    /**
     * The biome at a position already expressed in quart coordinates.
     *
     * <p>The same call {@link #sampleBiomeId} makes, minus the conversion. Structure validation
     * works in quart space because that is the space vanilla's own check is defined in - it
     * converts the structure's block position with {@code QuartPos.fromBlock} before sampling - and
     * because whole runs of block heights collapse onto one quart row, which is what makes
     * enumerating a column affordable.
     *
     * @return a canonical id such as {@code minecraft:plains}, never {@code null}
     */
    public String sampleBiomeIdAtQuart(int quartX, int quartY, int quartZ) {
        return resolveId(sampleRaw(quartX, quartY, quartZ));
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
    private final int lowestQuartY;
    private final int highestQuartY;

    /**
     * Terrain, for the structures whose generation position is a height rather than a constant.
     *
     * <p>A plain {@code NoiseBasedChunkGenerator} over the same biome source and noise settings:
     * two public constructor arguments, no chunk, no world and no bound tags. Only
     * {@code getFirstOccupiedHeight} is ever called on it, which builds its own column and touches
     * nothing shared, so it is as thread-confined as the rest of the session.
     */
    private final ChunkGenerator terrain;

    private final LevelHeightAccessor heightAccessor;
    private final RandomState randomState;
    private final int seaLevel;

    /**
     * Exact jigsaw generation for this session's seed, built on first use.
     *
     * <p>Not built with the session: it needs the shared vanilla structure data, which is only
     * loaded once something actually validates a jigsaw candidate, and it is seed dependent, so it
     * cannot outlive the session.
     */
    private JigsawGenerator jigsaw;
    private boolean jigsawBroken;

    private BiomeWorldgenSession(long seed, String dimensionId) {
        this.seed = seed;
        this.dimensionId = dimensionId;
        HolderLookup.Provider registries = registries();

        Holder<NoiseGeneratorSettings> settingsHolder =
                registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                        .getOrThrow(noiseSettingsOf(dimensionId));
        NoiseGeneratorSettings settings = settingsHolder.value();

        // The (Provider, ResourceKey, long) overload of RandomState.create only exists on 26.x,
        // where HolderLookup.Provider gained "extends HolderGetter.Provider"; this one is on both.
        RandomState randomState = RandomState.create(
                settings, registries.lookupOrThrow(Registries.NOISE), seed);

        this.randomState = randomState;
        this.climate = randomState.sampler();
        this.biomeSource = MultiNoiseBiomeSource.createFromPreset(
                registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(biomeParametersOf(dimensionId)));
        this.terrain = new NoiseBasedChunkGenerator(biomeSource, settingsHolder);
        // The level's own height, which is what a chunk hands createStructures: the dimension type,
        // not the noise settings. The overworld's two agree; the nether's do not - its terrain
        // noise is 128 blocks tall inside a 256 block level.
        this.heightAccessor = heightAccessorOf(registries, dimensionId);
        this.seaLevel = terrain.getSeaLevel();

        // Every height a terrain-derived structure position can land on, widened by one block at
        // each end. NoiseBasedChunkGenerator.getBaseHeight walks the column that
        // NoiseSettings.clampToHeightAccessor produces and falls back to the accessor's minimum,
        // so the answer never leaves [minY, minY + height]; getFirstOccupiedHeight then subtracts
        // one. Clamping shrinks the range and never grows it, so taking the unclamped settings
        // keeps this an over-estimate - which is the safe direction for a filter that must not
        // hide anything.
        int minY = settings.noiseSettings().minY();
        int height = settings.noiseSettings().height();
        this.lowestQuartY = (minY - 1) >> 2;
        this.highestQuartY = (minY + height) >> 2;
    }

    /** The noise settings a dimension generates with; the overworld's for anything else. */
    static ResourceKey<NoiseGeneratorSettings> noiseSettingsOf(String dimensionId) {
        return NETHER.equals(dimensionId) ? NoiseGeneratorSettings.NETHER
                : NoiseGeneratorSettings.OVERWORLD;
    }

    /** The multi-noise preset a dimension's biome source is built from. */
    static ResourceKey<MultiNoiseBiomeSourceParameterList> biomeParametersOf(String dimensionId) {
        return NETHER.equals(dimensionId) ? MultiNoiseBiomeSourceParameterLists.NETHER
                : MultiNoiseBiomeSourceParameterLists.OVERWORLD;
    }

    /** The build height of a dimension's level, from its vanilla dimension type. */
    static LevelHeightAccessor heightAccessorOf(HolderLookup.Provider registries,
                                                String dimensionId) {
        net.minecraft.world.level.dimension.DimensionType type = registries
                .lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(NETHER.equals(dimensionId) ? BuiltinDimensionTypes.NETHER
                        : BuiltinDimensionTypes.OVERWORLD)
                .value();
        return LevelHeightAccessor.create(type.minY(), type.height());
    }

    /** Lowest quart row a terrain-derived structure position in this dimension can sample. */
    public int lowestQuartY() {
        return lowestQuartY;
    }

    /** Highest quart row a terrain-derived structure position in this dimension can sample. */
    public int highestQuartY() {
        return highestQuartY;
    }

    /**
     * The exact height vanilla anchors a surface structure at, for one block column.
     *
     * <p>{@code ChunkGenerator.getFirstOccupiedHeight(x, z, WORLD_SURFACE_WG, ...)}, which is
     * {@code getBaseHeight(...) - 1}. That is the call {@code Structure.onTopOfChunkCenter} makes,
     * so the answer is the stub's Y rather than an estimate of it.
     *
     * <p><strong>Expensive.</strong> Measured at 1.8 ms on 1.20.1 and 2.5 ms on 26.2 per column -
     * roughly two hundred times a biome sample - because every call walks a fresh noise column.
     * Call it once per position and never on the render thread.
     */
    public int surfaceOccupiedHeight(int blockX, int blockZ) {
        return terrain.getFirstOccupiedHeight(blockX, blockZ, Heightmap.Types.WORLD_SURFACE_WG,
                heightAccessor, randomState);
    }

    /**
     * {@code getFirstOccupiedHeight(x, z, OCEAN_FLOOR_WG, ...)}: where {@code onTopOfChunkCenter}
     * anchors an ocean-floor structure. As expensive as {@link #surfaceOccupiedHeight}.
     */
    public int oceanFloorOccupiedHeight(int blockX, int blockZ) {
        return terrain.getFirstOccupiedHeight(blockX, blockZ, Heightmap.Types.OCEAN_FLOOR_WG,
                heightAccessor, randomState);
    }

    /** The dimension's sea level, which some structures refuse to generate below. */
    public int seaLevel() {
        return seaLevel;
    }

    /**
     * Makes exact jigsaw generation usable on this worker.
     *
     * <p>If no worker has started loading the shared vanilla structure data, this one does, inline,
     * and pays for it once. If another worker is already loading it, this returns at once rather
     * than wait.
     *
     * @return {@code READY} when the jigsaw methods below may be called; {@code INITIALIZING} when
     *         another worker is still loading, so the caller should give up and ask again later;
     *         {@code FAILED} when the data could not be loaded or this session could not use it
     */
    public LazyInit.State prepareJigsaw() {
        if (jigsaw != null) {
            return LazyInit.State.READY;
        }
        if (jigsawBroken) {
            return LazyInit.State.FAILED;
        }
        LazyInit.State state = VanillaStructureData.loadIfNeeded();
        if (state != LazyInit.State.READY) {
            return state;
        }
        VanillaStructureData data = VanillaStructureData.get();
        if (data == null) {
            // Closed at shutdown between the two reads.
            return LazyInit.State.FAILED;
        }
        try {
            jigsaw = new JigsawGenerator(data, seed, dimensionId);
            return LazyInit.State.READY;
        } catch (Exception | LinkageError failure) {
            // Remembered, so a session that cannot build one does not try again per candidate.
            jigsawBroken = true;
            SeedChecker.LOGGER.log(Level.WARNING, "Exact jigsaw generation is unavailable", failure);
            return LazyInit.State.FAILED;
        }
    }

    /**
     * Vanilla's own jigsaw generation point. Only after {@link #prepareJigsaw} returned
     * {@code READY}.
     *
     * @param structureId the structure entry, e.g. {@code minecraft:village_plains}
     * @return the stub position, or {@code null} when vanilla assembles no start piece there
     */
    public GenerationPoint jigsawGenerationPoint(String structureId, int chunkX, int chunkZ) {
        return jigsaw.generationPoint(structureId, chunkX, chunkZ);
    }

    /**
     * The biome at a jigsaw generation point, sampled by the same biome source that produced the
     * point rather than by this session's own - the two agree, but the point came from the
     * structure data's registries and the check stays inside them.
     */
    public String jigsawBiomeIdAt(GenerationPoint point) {
        return jigsaw.biomeIdAt(point);
    }

    /** The order vanilla tries a multi-entry structure set's entries in at that chunk. */
    public int[] structureSelectionOrder(int[] weights, int chunkX, int chunkZ) {
        return JigsawGenerator.selectionOrder(weights, seed, chunkX, chunkZ);
    }

    /**
     * Vanilla's own assembly of a jigsaw structure, measured. Only after {@link #prepareJigsaw}
     * returned {@code READY}; expensive, see {@link JigsawGenerator#geometry}.
     *
     * @return the geometry, or {@code null} when vanilla assembles no start piece there
     */
    public StructureGeometry jigsawGeometry(String structureId, int chunkX, int chunkZ) {
        return jigsaw.geometry(structureId, chunkX, chunkZ);
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

    private Collection<?> possibleBiomesRaw() {
        return biomeSource.possibleBiomes();
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
    private final BiomeSource biomeSource;

    private BiomeWorldgenSession(long seed, String dimensionId) {
        this.seed = seed;
        this.dimensionId = dimensionId;
        // 1.16.5 keeps all worldgen content in code, so the registry is simply a static; the
        // legacy layer stack takes the seed directly and owns mutable caches, which is exactly why
        // each thread gets its own source. The nether's is the one
        // DimensionType.defaultNetherGenerator builds.
        this.biomeRegistry = BuiltinRegistries.BIOME;
        this.biomeSource = NETHER.equals(dimensionId)
                ? MultiNoiseBiomeSource.Preset.NETHER.biomeSource(biomeRegistry, seed)
                : new OverworldBiomeSource(seed, false, false, biomeRegistry);
    }

    /^* The noise settings this dimension's chunk generator is built with. ^/
    ResourceKey<NoiseGeneratorSettings> legacyNoiseSettings() {
        return NETHER.equals(dimensionId) ? NoiseGeneratorSettings.NETHER
                : NoiseGeneratorSettings.OVERWORLD;
    }

    private Object sampleRaw(int quartX, int quartY, int quartZ) {
        return biomeSource.getNoiseBiome(quartX, quartY, quartZ);
    }

    private Collection<?> possibleBiomesRaw() {
        return biomeSource.possibleBiomes();
    }

    /^* This session's biome source, for building a structure start from it on this worker. ^/
    BiomeSource legacyBiomeSource() {
        return biomeSource;
    }

    private String readId(Object biome) {
        net.minecraft.resources.ResourceLocation id = biomeRegistry.getKey((Biome) biome);
        return id == null ? "seedchecker:unnamed" : id.toString();
    }
    *///?}
}
