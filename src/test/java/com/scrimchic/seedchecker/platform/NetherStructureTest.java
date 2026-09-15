package com.scrimchic.seedchecker.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.core.map.MapViewport;
import com.scrimchic.seedchecker.gui.map.layer.StructureLayer;
import com.scrimchic.seedchecker.worldgen.StructureBounds;
import com.scrimchic.seedchecker.worldgen.StructureGeometry;
import com.scrimchic.seedchecker.worldgen.StructurePlacementConfig;
import com.scrimchic.seedchecker.worldgen.StructurePlacementEngine;
import com.scrimchic.seedchecker.worldgen.StructurePlacements;
import com.scrimchic.seedchecker.worldgen.StructureType;
import com.scrimchic.seedchecker.worldgen.StructureValidation;
import com.scrimchic.seedchecker.worldgen.biome.BiomeTileGrid;

import net.minecraft.server.Bootstrap;

//? if >=1.18 {
import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.function.Predicate;

import com.scrimchic.seedchecker.worldgen.GenerationPoint;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
//?}
//? if >=26.1 {
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
//?} else if >=1.18 {
//?} else {
/*import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;

import net.minecraft.core.RegistryAccess;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.SimpleReloadableResourceManager;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.StructureSettings;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.feature.configurations.StructureFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BeardedStructureStart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.level.storage.LevelStorageSource;*/
//?}

/**
 * Phase 3H-2: the nether's structures, held to the Minecraft version this target builds.
 *
 * <p>Every oracle is vanilla's own generation, in a world this test builds itself rather than the
 * production session's, over the nether's own biome source, terrain and level height. From 1.18 it
 * is {@code ChunkGenerator.createStructures} on a bare {@code ProtoChunk} - the whole decision,
 * including the weighted choice between a set's entries and its fallback, read back as the start the
 * chunk is left with - plus {@code findValidGenerationPoint} for positions and
 * {@code Structure.generate} for bounds. On 1.16.5 it is {@code ConfiguredStructureFeature.generate}
 * - placement, {@code isFeatureChunk} and pieces - exactly as {@code createStructures} calls it.
 * Seeds at the edges of the seed space, candidates in the regions straddling the origin on both axes
 * - so negative coordinates and region boundaries - and a million chunks out.
 */
class NetherStructureTest {

    private static final long SEED = -7407337299659424542L;

    private static final long[] SEEDS = {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, SEED};

    private static final int FAR_CHUNK = 1_000_000;

    private static final String NETHER = BiomeWorldgenSession.NETHER;

    private static final StructureType[] NETHER_TYPES = {StructureType.NETHER_FORTRESS,
            StructureType.BASTION_REMNANT, StructureType.NETHER_FOSSIL, StructureType.RUINED_PORTAL};

    /** WorldgenWorkers' pool size on a machine with six or more cores. */
    private static final int WORKERS = 3;

    private static StructurePlacements placements;

    @BeforeAll
    static void bootstrap() {
        //? if >=1.18 {
        SharedConstants.tryDetectVersion();
        //?}
        Bootstrap.bootStrap();
        //? if <1.18 {
        /*StructureGeometryGenerator.ensureStructuresBootstrapped();*/
        //?}
        placements = StructurePlacements.forThisVersion();
    }

    // ------------------------------------------------------------------ candidates

    /** Structure chunks of a square of regions centred on a chunk, side regions per axis. */
    static List<int[]> gridChunks(StructureType type, long seed, int originChunkX, int originChunkZ,
                                  int side) {
        StructurePlacementConfig config = placements.get(type, NETHER);
        StructurePlacementEngine engine = new StructurePlacementEngine();
        int firstRegionX = Math.floorDiv(originChunkX, config.spacing()) - side / 2;
        int firstRegionZ = Math.floorDiv(originChunkZ, config.spacing()) - side / 2;
        List<int[]> chunks = new ArrayList<int[]>();
        for (int dz = 0; dz < side; dz++) {
            for (int dx = 0; dx < side; dx++) {
                long packed = engine.candidateChunk(seed, config, firstRegionX + dx, firstRegionZ + dz);
                int chunkX = StructurePlacementEngine.chunkX(packed);
                int chunkZ = StructurePlacementEngine.chunkZ(packed);
                if (engine.passesRestrictions(seed, config, chunkX, chunkZ)) {
                    chunks.add(new int[] {chunkX, chunkZ});
                }
            }
        }
        return chunks;
    }

    /** Regions straddling the origin on both axes, then regions a million chunks out, Z negative. */
    static List<int[]> oracleChunks(StructureType type, long seed, int nearSide, int farSide) {
        List<int[]> all = gridChunks(type, seed, 0, 0, nearSide);
        all.addAll(gridChunks(type, seed, FAR_CHUNK, -FAR_CHUNK, farSide));
        return all;
    }

    private static String where(StructureType type, long seed, int[] chunk) {
        return type + " seed " + seed + " chunk " + chunk[0] + "," + chunk[1];
    }

    // ------------------------------------------------------------ both versions

    @Test
    void everyTypeStartsInExactlyTheDimensionsItDeclares() {
        // StructureType.generatesIn decides which layers a dimension lists. It must be the biome
        // data's answer: some entry that is this type accepts a biome the dimension's own source can
        // return. On 1.16.5 the entries are vanilla's isValidStart lists, from 1.18 the data pack's
        // sets, which StructureBiomeValidatorTest holds to vanilla's tags.
        BiomeWorldgenSession overworld = BiomeWorldgenSession.create(SEED, BiomeWorldgenSession.OVERWORLD);
        BiomeWorldgenSession nether = BiomeWorldgenSession.create(SEED, NETHER);
        List<StructureType> netherTypes = new ArrayList<StructureType>();
        for (StructureType type : placements.types()) {
            for (BiomeWorldgenSession session : new BiomeWorldgenSession[] {overworld, nether}) {
                boolean startsHere = false;
                for (String variant : StructureBiomeValidator.variantNames(type)) {
                    if (StructureBiomeValidator.ownsVariant(type, variant)
                            && !Collections.disjoint(StructureBiomeValidator.acceptedBiomes(type,
                                    variant), session.possibleBiomeIds())) {
                        startsHere = true;
                    }
                }
                assertEquals(startsHere, type.generatesIn(session.dimensionId()),
                        type + " in " + session.dimensionId());
            }
            if (type.generatesIn(NETHER)) {
                netherTypes.add(type);
            }
        }
        assertEquals(new HashSet<StructureType>(Arrays.asList(NETHER_TYPES)),
                new HashSet<StructureType>(netherTypes));
        assertFalse(StructureType.STRONGHOLD.generatesIn(NETHER));
        assertFalse(StructureType.NETHER_FORTRESS.generatesIn("minecraft:the_end"));
    }

    @Test
    void theNetherSessionIsTheNethersOwn() {
        BiomeWorldgenSession nether = BiomeWorldgenSession.create(SEED, NETHER);
        BiomeWorldgenSession overworld = BiomeWorldgenSession.create(SEED, BiomeWorldgenSession.OVERWORLD);
        assertNotNull(nether);
        assertEquals(NETHER, nether.dimensionId());
        assertTrue(nether.isBiomeColumnConstant());
        assertFalse(overworld.isBiomeColumnConstant());
        assertEquals(null, BiomeWorldgenSession.create(SEED, "minecraft:the_end"));

        Set<String> expected = new HashSet<String>(Arrays.asList("minecraft:nether_wastes",
                "minecraft:soul_sand_valley", "minecraft:crimson_forest", "minecraft:warped_forest",
                "minecraft:basalt_deltas"));
        assertEquals(expected, nether.possibleBiomeIds());
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < 3000; i++) {
            String id = nether.sampleBiomeId((int) ((i * 7919L) % 60000) - 30000, 32,
                    (int) ((i * 104729L) % 60000) - 30000);
            assertTrue(expected.contains(id), "not a nether biome: " + id);
            seen.add(id);
        }
        assertEquals(expected, seen, "a wide scatter should meet every nether biome");
        //? if >=1.18 {
        assertEquals(32, nether.seaLevel());
        assertEquals(63, overworld.seaLevel());
        //?}
    }

    @Test
    void theNetherBiomeIsTheSameAtEveryHeight() {
        // What the biome map's nether slice height and the fossil's chunk-column refusal rest on,
        // checked against the biome source itself: every quart row, well past the level both ways,
        // for columns near the origin, negative and a million blocks out.
        int columns = 0;
        for (long seed : new long[] {SEED, 0L, Long.MIN_VALUE}) {
            BiomeWorldgenSession nether = BiomeWorldgenSession.create(seed, NETHER);
            for (int i = 0; i < 300; i++) {
                int quartX = (int) ((i * 7919L) % 20000) - 10000 + (i % 3 == 0 ? 4_000_000 : 0);
                int quartZ = (int) ((i * 104729L) % 20000) - 10000;
                String base = nether.sampleBiomeIdAtQuart(quartX, 0, quartZ);
                for (int quartY = -20; quartY <= 80; quartY += 3) {
                    assertEquals(base, nether.sampleBiomeIdAtQuart(quartX, quartY, quartZ),
                            "seed " + seed + " quart " + quartX + "," + quartY + "," + quartZ);
                }
                columns++;
            }
        }
        assertEquals(900, columns);
    }

    @Test
    void netherCostAndDensityAreReported() {
        // A measurement for the phase report, on one representative seed, after warm-up: biome tile
        // cost next to the overworld's; for each nether type candidates per 10k chunks, placement
        // cost per region, validation cost, positive rate and geometry cost; and how long a viewport
        // of 480 by 270 GUI pixels keeps the validation lane busy. Nothing is asserted beyond "found
        // some".
        long seed = SEED;
        BiomeWorldgenSession nether = BiomeWorldgenSession.create(seed, NETHER);
        BiomeWorldgenSession overworld = BiomeWorldgenSession.create(seed, BiomeWorldgenSession.OVERWORLD);
        int side = BiomeTileGrid.SAMPLES_PER_SIDE;
        for (BiomeWorldgenSession session : new BiomeWorldgenSession[] {overworld, nether}) {
            int y = NETHER.equals(session.dimensionId()) ? 32 : 64;
            long start = 0L;
            for (int tile = 0; tile < 24; tile++) {
                if (tile == 4) {
                    start = System.nanoTime();
                }
                for (int z = 0; z < side; z++) {
                    for (int x = 0; x < side; x++) {
                        session.sampleBiomeId(tile * side * 16 + x * 16, y, z * 16 - 5000);
                    }
                }
            }
            System.out.printf("phase 3H-2 biome tile %-20s %.2f ms per %dx%d tile at 16 blocks%n",
                    session.dimensionId(), (System.nanoTime() - start) / 1e6 / 20, side, side);
        }

        StructurePlacementEngine engine = new StructurePlacementEngine();
        ChunkRange area = ChunkRange.of(-256, -256, 255, 255);
        for (StructureType type : NETHER_TYPES) {
            StructurePlacementConfig config = placements.get(type, NETHER);
            final int[] found = {0};
            long placementStart = System.nanoTime();
            engine.forEachCandidate(seed, config, area, Integer.MAX_VALUE, (x, z) -> {
                found[0]++;
                return true;
            });
            double placementNs = (System.nanoTime() - placementStart)
                    / (double) StructurePlacementEngine.regionCount(config, area);

            List<int[]> sample = gridChunks(type, seed, 0, 0, type == StructureType.NETHER_FOSSIL ? 12 : 8);
            for (int i = 0; i < 5; i++) {
                StructureBiomeValidator.validate(nether, type, sample.get(i)[0], sample.get(i)[1]);
            }
            int accepted = 0;
            int[] firstAccepted = null;
            StructureValidation firstResult = null;
            long start = System.nanoTime();
            for (int[] candidate : sample) {
                StructureValidation result =
                        StructureBiomeValidator.validate(nether, type, candidate[0], candidate[1]);
                if (result != null && result.isCompatible()) {
                    accepted++;
                    if (firstAccepted == null) {
                        firstAccepted = candidate;
                        firstResult = result;
                    }
                }
            }
            double millis = (System.nanoTime() - start) / 1e6 / sample.size();
            String geometry = "no acceptance to measure";
            if (firstAccepted != null) {
                StructureGeometryGenerator.generate(nether, type, firstResult.variant(),
                        firstAccepted[0], firstAccepted[1]);
                long geometryStart = System.nanoTime();
                StructureGeometry measured = StructureGeometryGenerator.generate(nether, type,
                        firstResult.variant(), firstAccepted[0], firstAccepted[1]);
                geometry = String.format("geometry %.1f ms%s", (System.nanoTime() - geometryStart) / 1e6,
                        measured != null && measured.isAvailable() ? "" : " (no bounds)");
            }
            System.out.printf("phase 3H-2 %-16s %7.2f candidates per 10k chunks, placement %5.1f ns per "
                            + "region, %6.2f ms per validation, %d of %d accepted, %s%n", type,
                    found[0] * 10000.0 / (512 * 512), placementNs, millis, accepted, sample.size(),
                    geometry);
            // Only zooms the layer actually draws at: past its region budget or marker density it
            // says "zoom in" and requests nothing, so there is no backlog to report there.
            for (double scale : new double[] {1.0 / 64, 1.0 / 16, 1.0 / 4, 1.0 / 2}) {
                MapViewport viewport = new MapViewport();
                viewport.resize(480, 270);
                viewport.setScale(scale);
                viewport.setCenter(0.0, 0.0);
                ChunkRange visible = ChunkRange.visibleIn(viewport);
                if (!StructureLayer.isDrawableAt(config, visible, scale)) {
                    System.out.printf("phase 3H-2 %-16s viewport 480x270 at 1/%d px/block: layer says "
                            + "zoom in%n", type, Math.round(1 / scale));
                    continue;
                }
                final int[] inView = {0};
                engine.forEachCandidate(seed, config, visible, Integer.MAX_VALUE, (x, z) -> {
                    inView[0]++;
                    return true;
                });
                System.out.printf("phase 3H-2 %-16s viewport 480x270 at 1/%d px/block: %d candidates, "
                                + "%.2f s to settle on %d workers%n", type, Math.round(1 / scale), inView[0],
                        inView[0] * millis / 1000.0 / WORKERS, WORKERS);
            }
            assertTrue(found[0] > 0, type + " has no candidate in 512 by 512 chunks");
        }

        // The shared grid: both layers ask about every candidate of it.
        List<int[]> complexes = gridChunks(StructureType.NETHER_FORTRESS, seed, 0, 0, 8);
        long start = System.nanoTime();
        for (int[] candidate : complexes) {
            StructureBiomeValidator.validate(nether, StructureType.NETHER_FORTRESS, candidate[0], candidate[1]);
            StructureBiomeValidator.validate(nether, StructureType.BASTION_REMNANT, candidate[0], candidate[1]);
        }
        System.out.printf("phase 3H-2 nether_complexes both types %.2f ms per candidate%n",
                (System.nanoTime() - start) / 1e6 / complexes.size());
    }

    // ------------------------------------------------------------- 1.18 onwards

    //? if >=1.18 {
    /**
     * The nether, built by this test over the loaded data pack from the nether's noise settings,
     * biome preset and dimension type: separate template manager, noise and generator from anything
     * production built.
     */
    static final class NetherWorld {

        final VanillaStructureData data;
        final long seed;
        final StructureTemplateManager templates;
        final RandomState randomState;
        final BiomeSource biomeSource;
        final ChunkGenerator chunkGenerator;
        final LevelHeightAccessor heightAccessor;

        NetherWorld(long seed) throws Exception {
            this.data = StructureBiomeValidatorTest.structureData();
            this.seed = seed;
            this.templates = data.newTemplateManager();
            Holder<NoiseGeneratorSettings> settings = data.registries()
                    .lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.NETHER);
            this.randomState = RandomState.create(settings.value(),
                    data.registries().lookupOrThrow(Registries.NOISE), seed);
            this.biomeSource = MultiNoiseBiomeSource.createFromPreset(
                    data.registries().lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                            .getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER));
            this.chunkGenerator = new NoiseBasedChunkGenerator(biomeSource, settings);
            net.minecraft.world.level.dimension.DimensionType dimension = data.registries()
                    .lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.NETHER)
                    .value();
            this.heightAccessor = LevelHeightAccessor.create(dimension.minY(), dimension.height());
        }

        Holder<Structure> structure(final String name) {
            return data.registries().lookupOrThrow(Registries.STRUCTURE).listElements()
                    .filter(holder -> StructureBiomeValidatorTest.keyName(holder.key()).endsWith(":" + name))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no loaded structure " + name));
        }

        Structure.GenerationContext context(int chunkX, int chunkZ, Predicate<Holder<Biome>> biomes) {
            return new Structure.GenerationContext(data.generationRegistries(), chunkGenerator,
                    biomeSource, randomState, templates, seed, new ChunkPos(chunkX, chunkZ),
                    heightAccessor, biomes);
        }

        /** The stub vanilla's own findValidGenerationPoint returns, or null. */
        GenerationPoint point(String name, int chunkX, int chunkZ, Predicate<Holder<Biome>> biomes) {
            Optional<Structure.GenerationStub> stub =
                    structure(name).value().findValidGenerationPoint(context(chunkX, chunkZ, biomes));
            if (!stub.isPresent()) {
                return null;
            }
            BlockPos position = stub.get().position();
            return new GenerationPoint(position.getX(), position.getY(), position.getZ());
        }

        StructureStart start(String name, int chunkX, int chunkZ, Predicate<Holder<Biome>> biomes) {
            Holder<Structure> holder = structure(name);
            //? if >=26.1 {
            return holder.value().generate(holder, Level.NETHER, data.generationRegistries(),
                    chunkGenerator, biomeSource, randomState, templates, seed,
                    new ChunkPos(chunkX, chunkZ), 0, heightAccessor, biomes);
            //?} else {
            /*return holder.value().generate(data.generationRegistries(), chunkGenerator, biomeSource,
                    randomState, templates, seed, new ChunkPos(chunkX, chunkZ), 0, heightAccessor,
                    biomes);*/
            //?}
        }

        /** A structure state that offers only that set, as createStructures reads it. */
        ChunkGeneratorStructureState state(final String setPath) throws Exception {
            Holder<StructureSet> set = data.registries().lookupOrThrow(Registries.STRUCTURE_SET)
                    .listElements()
                    .filter(holder -> StructureBiomeValidatorTest.keyName(holder.key()).endsWith(":" + setPath))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no structure set " + setPath));
            Constructor<ChunkGeneratorStructureState> constructor =
                    ChunkGeneratorStructureState.class.getDeclaredConstructor(RandomState.class,
                            BiomeSource.class, long.class, long.class, List.class);
            constructor.setAccessible(true);
            return constructor.newInstance(randomState, biomeSource, seed, seed,
                    Collections.singletonList(set));
        }

        /** The entry vanilla's createStructures leaves a valid start for in that chunk, or null. */
        String built(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
            //? if >=26.1 {
            ProtoChunk chunk = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY,
                    heightAccessor, PalettedContainerFactory.create(data.generationRegistries()), null);
            //?} else {
            /*ProtoChunk chunk = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY,
                    heightAccessor, data.registries().registryOrThrow(Registries.BIOME), null);*/
            //?}
            StructureManager manager =
                    new StructureManager(null, new WorldOptions(seed, true, false), null);
            //? if >=26.1 {
            chunkGenerator.createStructures(data.generationRegistries(), state, manager, chunk,
                    templates, Level.NETHER);
            //?} else {
            /*chunkGenerator.createStructures(data.generationRegistries(), state, manager, chunk,
                    templates);*/
            //?}
            String built = null;
            for (Map.Entry<Structure, StructureStart> start : chunk.getAllStarts().entrySet()) {
                if (!start.getValue().isValid()) {
                    continue;
                }
                assertEquals(null, built, "more than one start of one set in one chunk");
                final Structure structure = start.getKey();
                String id = StructureBiomeValidatorTest.keyName(data.registries()
                        .lookupOrThrow(Registries.STRUCTURE).listElements()
                        .filter(holder -> holder.value() == structure).findFirst()
                        .orElseThrow(() -> new AssertionError("unregistered structure")).key());
                built = id.substring(id.indexOf(':') + 1);
            }
            return built;
        }
    }

    @Test
    void theNetherUsesTheOverworldsGridsOnModernVersions() {
        // From 1.18 a structure set places its structures in every dimension alike - the nether has
        // no grid of its own, unlike 1.16.5's generator settings. VanillaStructurePlacementTest holds
        // these grids to the sets, and the createStructures oracles below, which re-check placement
        // inside vanilla, are fed candidates from them.
        for (StructureType type : placements.types()) {
            assertSame(placements.get(type), placements.get(type, NETHER), type.toString());
        }
    }

    @Test
    void theNetherComplexesSetIsTheFortressThenTheBastion() {
        for (StructureType type : new StructureType[] {StructureType.NETHER_FORTRESS,
                StructureType.BASTION_REMNANT}) {
            assertEquals(Arrays.asList("fortress", "bastion_remnant"),
                    StructureBiomeValidator.variantNames(type), type + " entries in vanilla's order");
            assertEquals(2, StructureBiomeValidator.variantWeight(type, "fortress"));
            assertEquals(3, StructureBiomeValidator.variantWeight(type, "bastion_remnant"));
        }
        assertTrue(StructureBiomeValidator.ownsVariant(StructureType.NETHER_FORTRESS, "fortress"));
        assertFalse(StructureBiomeValidator.ownsVariant(StructureType.NETHER_FORTRESS, "bastion_remnant"));
        assertTrue(StructureBiomeValidator.ownsVariant(StructureType.BASTION_REMNANT, "bastion_remnant"));
        assertFalse(StructureBiomeValidator.ownsVariant(StructureType.BASTION_REMNANT, "fortress"));
        assertEquals(Collections.singletonList("nether_fossil"),
                StructureBiomeValidator.variantNames(StructureType.NETHER_FOSSIL));
        for (StructureType type : NETHER_TYPES) {
            assertTrue(StructureBiomeValidator.isExact(type), type + " must be exact");
        }
    }

    @Test
    void theNetherComplexesMatchVanillaCreateStructuresOnEverySeed() throws Exception {
        // The shared set's regression oracle: the entry vanilla's createStructures builds in each
        // grid chunk against the one production reports - the fortress, the bastion, or neither -
        // compared per entry, plus the position. A chunk where vanilla built the entry that came
        // second in the weighted order is the fallback case: the first one's biome refused.
        int fortresses = 0;
        int bastions = 0;
        int empty = 0;
        int fallbacks = 0;
        int far = 0;
        for (long seed : SEEDS) {
            BiomeWorldgenSession session = BiomeWorldgenSession.create(seed, NETHER);
            NetherWorld world = new NetherWorld(seed);
            ChunkGeneratorStructureState state = world.state("nether_complexes");
            List<int[]> candidates = oracleChunks(StructureType.NETHER_FORTRESS, seed, 10, 4);
            for (int c = 0; c < candidates.size(); c++) {
                int[] candidate = candidates.get(c);
                String where = where(StructureType.NETHER_FORTRESS, seed, candidate);
                String built = world.built(state, candidate[0], candidate[1]);
                StructureValidation fortress = StructureBiomeValidator.validate(session,
                        StructureType.NETHER_FORTRESS, candidate[0], candidate[1]);
                StructureValidation bastion = StructureBiomeValidator.validate(session,
                        StructureType.BASTION_REMNANT, candidate[0], candidate[1]);
                assertNotNull(fortress, where);
                assertNotNull(bastion, where);
                assertFalse(fortress.isCompatible() && bastion.isCompatible(), where + ": both");
                assertEquals("fortress".equals(built), fortress.isCompatible(), where + ": " + built
                        + " / " + fortress);
                assertEquals("bastion_remnant".equals(built), bastion.isCompatible(), where + ": "
                        + built + " / " + bastion);
                if (built == null) {
                    empty++;
                    continue;
                }
                StructureType type = "fortress".equals(built)
                        ? StructureType.NETHER_FORTRESS : StructureType.BASTION_REMNANT;
                StructureValidation ours = type == StructureType.NETHER_FORTRESS ? fortress : bastion;
                assertTrue(ours.isExact(), where);
                assertEquals(built, ours.variant(), where);
                assertEquals(world.point(built, candidate[0], candidate[1],
                        StructureBiomeValidatorTest.predicateFor(type, built)),
                        ours.generationPoint(), where + ": generation point");
                int[] order = session.structureSelectionOrder(new int[] {2, 3}, candidate[0], candidate[1]);
                if (order[0] != (type == StructureType.NETHER_FORTRESS ? 0 : 1)) {
                    fallbacks++;
                }
                if (type == StructureType.NETHER_FORTRESS) {
                    fortresses++;
                } else {
                    bastions++;
                }
                if (Math.abs(candidate[0]) > FAR_CHUNK / 2) {
                    far++;
                }
            }
        }
        System.out.println("nether complexes oracle: " + (fortresses + bastions + empty)
                + " candidates, " + fortresses + " fortresses, " + bastions + " bastions, " + empty
                + " empty, " + fallbacks + " built by the fallback entry, " + far + " a million chunks out");
        assertTrue(fortresses > 0 && bastions > 0, "both entries must be compared");
        assertTrue(fallbacks > 0, "the fallback of the weighted order was never exercised");
        assertTrue(far > 0, "nothing far out was compared");
    }

    @Test
    void netherFossilsMatchVanillaCreateStructuresOnEverySeed() throws Exception {
        int built = 0;
        int refusedByTerrain = 0;
        int refusedBeforeTheColumn = 0;
        int compared = 0;
        for (long seed : SEEDS) {
            BiomeWorldgenSession session = BiomeWorldgenSession.create(seed, NETHER);
            NetherWorld world = new NetherWorld(seed);
            ChunkGeneratorStructureState state = world.state("nether_fossils");
            for (int[] candidate : oracleChunks(StructureType.NETHER_FOSSIL, seed, 14, 5)) {
                String where = where(StructureType.NETHER_FOSSIL, seed, candidate);
                String vanilla = world.built(state, candidate[0], candidate[1]);
                StructureValidation ours = StructureBiomeValidator.validate(session,
                        StructureType.NETHER_FOSSIL, candidate[0], candidate[1]);
                assertNotNull(ours, where);
                assertEquals(vanilla != null, ours.isCompatible(), where + ": " + ours);
                compared++;
                if (ours.isCompatible()) {
                    built++;
                    assertTrue(ours.isExact(), where);
                    assertEquals("nether_fossil", ours.variant(), where);
                    assertEquals(world.point("nether_fossil", candidate[0], candidate[1],
                            StructureBiomeValidatorTest.predicateFor(StructureType.NETHER_FOSSIL,
                                    "nether_fossil")), ours.generationPoint(), where);
                    continue;
                }
                boolean soulSandInChunk = false;
                for (int dz = 0; dz < 4; dz++) {
                    for (int dx = 0; dx < 4; dx++) {
                        soulSandInChunk |= "minecraft:soul_sand_valley".equals(session.sampleBiomeIdAtQuart(
                                (candidate[0] << 2) + dx, 8, (candidate[1] << 2) + dz));
                    }
                }
                if (!soulSandInChunk) {
                    refusedBeforeTheColumn++;
                } else if (world.point("nether_fossil", candidate[0], candidate[1], holder -> true) == null) {
                    refusedByTerrain++;
                }
            }
        }
        System.out.println("nether fossil oracle: " + compared + " candidates, " + built + " built, "
                + refusedByTerrain + " refused by the terrain walk, " + refusedBeforeTheColumn
                + " refused by the chunk's columns before the walk");
        assertTrue(built > 0, "no fossil was built");
        assertTrue(refusedByTerrain > 0, "the terrain condition was never exercised");
        assertTrue(refusedBeforeTheColumn > 0, "the chunk column refusal was never exercised");
    }

    @Test
    void netherRuinedPortalsMatchVanillaCreateStructuresOnEverySeed() throws Exception {
        // The whole seven-entry set is offered, as in a real nether: vanilla tries the overworld
        // entries in its weighted order and its tags refuse them, production skips them as provably
        // refused. Both must end on the nether entry at the same position.
        int built = 0;
        int compared = 0;
        for (long seed : SEEDS) {
            BiomeWorldgenSession session = BiomeWorldgenSession.create(seed, NETHER);
            NetherWorld world = new NetherWorld(seed);
            ChunkGeneratorStructureState state = world.state("ruined_portals");
            for (int[] candidate : oracleChunks(StructureType.RUINED_PORTAL, seed, 6, 2)) {
                String where = where(StructureType.RUINED_PORTAL, seed, candidate);
                String vanilla = world.built(state, candidate[0], candidate[1]);
                StructureValidation ours = StructureBiomeValidator.validate(session,
                        StructureType.RUINED_PORTAL, candidate[0], candidate[1]);
                assertNotNull(ours, where);
                assertEquals(vanilla != null, ours.isCompatible(), where + ": " + ours);
                compared++;
                if (vanilla == null) {
                    continue;
                }
                built++;
                assertEquals("ruined_portal_nether", vanilla, where);
                assertEquals(vanilla, ours.variant(), where);
                assertEquals(world.point(vanilla, candidate[0], candidate[1],
                        StructureBiomeValidatorTest.predicateFor(StructureType.RUINED_PORTAL, vanilla)),
                        ours.generationPoint(), where);
            }
        }
        System.out.println("nether ruined portal oracle: " + compared + " candidates, " + built
                + " built, all ruined_portal_nether");
        assertTrue(built > 0, "no nether portal was built");
    }

    @Test
    void netherGeometryIsVanillasOwnStructureStart() throws Exception {
        // Our bounds against vanilla's StructureStart: its box must be our bounds through the
        // structure's own adjustBoundingBox (the fossil's thin beard inflates it), every piece must
        // lie inside our bounds, and each face must be reached by a piece - so the bastion is all of
        // its jigsaw, not its start template, and the fortress all of its corridors.
        Map<StructureType, Integer> compared = new LinkedHashMap<StructureType, Integer>();
        for (long seed : new long[] {SEED, 0L, -1L}) {
            BiomeWorldgenSession session = BiomeWorldgenSession.create(seed, NETHER);
            NetherWorld world = new NetherWorld(seed);
            for (StructureType type : NETHER_TYPES) {
                int found = 0;
                for (int[] candidate : gridChunks(type, seed, 0, 0, type == StructureType.NETHER_FOSSIL ? 30 : 6)) {
                    if (found >= 2) {
                        break;
                    }
                    StructureValidation validation =
                            StructureBiomeValidator.validate(session, type, candidate[0], candidate[1]);
                    if (validation == null || !validation.isCompatible()) {
                        continue;
                    }
                    String where = where(type, seed, candidate);
                    StructureGeometry ours = StructureGeometryGenerator.generate(session, type,
                            validation.variant(), candidate[0], candidate[1]);
                    assertNotNull(ours, where);
                    assertTrue(ours.isAvailable(), where + ": " + ours);
                    assertEquals(validation.generationPoint(), ours.generationPoint(), where);
                    StructureStart start = world.start(validation.variant(), candidate[0], candidate[1],
                            StructureBiomeValidatorTest.predicateFor(type, validation.variant()));
                    assertTrue(start.isValid(), where);
                    StructureBounds bounds = ours.bounds();
                    BoundingBox adjusted = world.structure(validation.variant()).value().adjustBoundingBox(
                            new BoundingBox(bounds.minX(), bounds.minY(), bounds.minZ(),
                                    bounds.maxX(), bounds.maxY(), bounds.maxZ()));
                    assertEquals(boxText(start.getBoundingBox()), boxText(adjusted), where + " box");
                    assertPiecesSpan(bounds, start.getPieces(), where);
                    found++;
                }
                assertTrue(found > 0, type + " seed " + seed + ": no structure to measure");
                compared.merge(type, found, Integer::sum);
            }
        }
        System.out.println("nether geometry oracle: " + compared);
    }

    private static String boxText(BoundingBox box) {
        return box.minX() + "," + box.minY() + "," + box.minZ() + " .. "
                + box.maxX() + "," + box.maxY() + "," + box.maxZ();
    }

    private static void assertPiecesSpan(StructureBounds bounds, List<StructurePiece> pieces, String where) {
        assertFalse(pieces.isEmpty(), where + ": vanilla generated no pieces");
        boolean[] touched = new boolean[6];
        for (StructurePiece piece : pieces) {
            BoundingBox box = piece.getBoundingBox();
            assertTrue(bounds.contains(box.minX(), box.minY(), box.minZ())
                    && bounds.contains(box.maxX(), box.maxY(), box.maxZ()), where + ": piece outside " + box);
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
    //?} else {
    /*// -------------------------------------------------------------------- 1.16.5

    private static StructureFeature<?> featureOf(StructureType type) {
        switch (type) {
            case NETHER_FORTRESS:
                return StructureFeature.NETHER_BRIDGE;
            case BASTION_REMNANT:
                return StructureFeature.BASTION_REMNANT;
            case NETHER_FOSSIL:
                return StructureFeature.NETHER_FOSSIL;
            case RUINED_PORTAL:
                return StructureFeature.RUINED_PORTAL;
            default:
                throw new AssertionError(type);
        }
    }

    private static StructureManager templates;
    private static LevelStorageSource.LevelStorageAccess level;

    private static StructureManager templates() throws Exception {
        if (templates == null) {
            SimpleReloadableResourceManager resources = new SimpleReloadableResourceManager(PackType.SERVER_DATA);
            resources.add(new VanillaPackResources("minecraft"));
            java.nio.file.Path temp = java.nio.file.Files.createTempDirectory("seedchecker-nether-oracle");
            temp.toFile().deleteOnExit();
            level = LevelStorageSource.createDefault(temp).createAccess("oracle");
            templates = new StructureManager(resources, level, DataFixers.getDataFixer());
        }
        return templates;
    }

    @AfterAll
    static void closeLevel() throws Exception {
        if (level != null) {
            level.close();
            level = null;
        }
    }

    /^* DimensionType.defaultNetherGenerator's generator, built here independently of production. ^/
    private static ChunkGenerator netherGenerator(BiomeSource biomeSource, long seed) {
        final NoiseGeneratorSettings settings =
                BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.NETHER);
        return new NoiseBasedChunkGenerator(biomeSource, seed, new Supplier<NoiseGeneratorSettings>() {
            @Override
            public NoiseGeneratorSettings get() {
                return settings;
            }
        });
    }

    /^*
     * Vanilla's start, as ChunkGenerator.createStructures builds it: the biome at the chunk's fixed
     * quart, that biome's configured feature, the nether generator's own grid settings, and
     * ConfiguredStructureFeature.generate. Null when the biome does not list the feature at all.
     ^/
    private static StructureStart<?> vanillaStart(BiomeSource biomeSource, ChunkGenerator generator,
                                                  long seed, StructureFeature<?> feature, int chunkX,
                                                  int chunkZ) throws Exception {
        Biome biome = biomeSource.getNoiseBiome((chunkX << 2) + 2, 0, (chunkZ << 2) + 2);
        for (Supplier<ConfiguredStructureFeature<?, ?>> supplier : biome.getGenerationSettings().structures()) {
            ConfiguredStructureFeature<?, ?> configured = supplier.get();
            if (configured.feature != feature) {
                continue;
            }
            StructureFeatureConfiguration placement = generator.getSettings().getConfig(feature);
            assertNotNull(placement, "the nether generator has no grid for " + feature);
            return configured.generate(RegistryAccess.builtin(), generator, biomeSource, templates(),
                    seed, new ChunkPos(chunkX, chunkZ), biome, 0, placement);
        }
        return null;
    }

    @Test
    void theNetherGeneratorGridsAreTheTable() {
        // The nether's chunk generator carries its own StructureSettings, and on this version they
        // are not the defaults for every feature: NoiseGeneratorSettings.nether gives the ruined
        // portal a grid of its own. The numbers, then structure chunk by structure chunk against
        // vanilla's own getPotentialFeatureChunk over those settings - around the origin, so
        // negative regions and their boundaries, and a million chunks out.
        StructureSettings nether = BuiltinRegistries.NOISE_GENERATOR_SETTINGS
                .getOrThrow(NoiseGeneratorSettings.NETHER).structureSettings();
        StructurePlacementEngine engine = new StructurePlacementEngine();
        WorldgenRandom random = new WorldgenRandom();
        int structureChunks = 0;
        for (StructureType type : NETHER_TYPES) {
            StructureFeature<?> feature = featureOf(type);
            StructureFeatureConfiguration vanilla = nether.getConfig(feature);
            StructurePlacementConfig ours = placements.get(type, NETHER);
            assertEquals(vanilla.spacing() + "," + vanilla.separation() + "," + vanilla.salt(),
                    ours.spacing() + "," + ours.separation() + "," + ours.salt(), type.toString());
            for (long seed : SEEDS) {
                for (int origin : new int[] {0, FAR_CHUNK}) {
                    for (int chunkZ = -origin - 60; chunkZ < -origin + 60; chunkZ++) {
                        for (int chunkX = origin - 60; chunkX < origin + 60; chunkX++) {
                            ChunkPos potential = feature.getPotentialFeatureChunk(vanilla, seed, random,
                                    chunkX, chunkZ);
                            boolean vanillaChunk = potential.x == chunkX && potential.z == chunkZ;
                            assertEquals(vanillaChunk, engine.isStructureChunk(seed, ours, chunkX, chunkZ),
                                    type + " seed " + seed + " chunk " + chunkX + "," + chunkZ);
                            if (vanillaChunk) {
                                structureChunks++;
                            }
                        }
                    }
                }
            }
        }
        assertNotEquals(placements.get(StructureType.RUINED_PORTAL).toString(),
                placements.get(StructureType.RUINED_PORTAL, NETHER).toString(),
                "the nether's portal grid is its own on this version");
        System.out.println("nether 1.16.5 grid oracle: " + structureChunks
                + " structure chunks agreed with the nether generator's settings");
    }

    @Test
    void netherStructuresMatchVanillaGenerateOnEverySeed() throws Exception {
        // isFeatureChunk and generatePieces, for every nether type, against production; and the
        // shared grid: a chunk is never both, and a chunk drawn for one type whose biome does not
        // list it is nothing - 1.16.5 has no fallback.
        Map<StructureType, int[]> tally = new LinkedHashMap<StructureType, int[]>();
        int neitherComplex = 0;
        for (long seed : SEEDS) {
            BiomeWorldgenSession session = BiomeWorldgenSession.create(seed, NETHER);
            BiomeSource biomeSource = MultiNoiseBiomeSource.Preset.NETHER.biomeSource(BuiltinRegistries.BIOME, seed);
            ChunkGenerator generator = netherGenerator(biomeSource, seed);
            for (StructureType type : NETHER_TYPES) {
                int[] counts = tally.computeIfAbsent(type, key -> new int[2]);
                boolean fossil = type == StructureType.NETHER_FOSSIL;
                for (int[] candidate : oracleChunks(type, seed, fossil ? 16 : 10, fossil ? 5 : 3)) {
                    String where = where(type, seed, candidate);
                    StructureValidation ours =
                            StructureBiomeValidator.validate(session, type, candidate[0], candidate[1]);
                    StructureStart<?> start = vanillaStart(biomeSource, generator, seed, featureOf(type),
                            candidate[0], candidate[1]);
                    boolean vanilla = start != null && start.isValid();
                    assertEquals(vanilla, ours.isCompatible(), where + ": " + ours);
                    assertTrue(ours.isExact() || !ours.isCompatible(), where);
                    counts[0]++;
                    if (vanilla) {
                        counts[1]++;
                    }
                    if (type == StructureType.NETHER_FORTRESS) {
                        boolean bastion = StructureBiomeValidator.validate(session,
                                StructureType.BASTION_REMNANT, candidate[0], candidate[1]).isCompatible();
                        assertFalse(vanilla && bastion, where + ": both");
                        if (!vanilla && !bastion) {
                            neitherComplex++;
                        }
                    }
                }
            }
        }
        for (Map.Entry<StructureType, int[]> entry : tally.entrySet()) {
            System.out.println("nether 1.16.5 oracle " + entry.getKey() + ": " + entry.getValue()[0]
                    + " candidates, " + entry.getValue()[1] + " generated");
            assertTrue(entry.getValue()[1] > 0, entry.getKey() + " generated nothing");
        }
        System.out.println("nether 1.16.5 oracle: " + neitherComplex
                + " complex grid chunks building neither");
        assertTrue(tally.get(StructureType.NETHER_FORTRESS)[1] < tally.get(StructureType.NETHER_FORTRESS)[0]);
        assertTrue(tally.get(StructureType.BASTION_REMNANT)[1] < tally.get(StructureType.BASTION_REMNANT)[0]);
        assertTrue(tally.get(StructureType.NETHER_FOSSIL)[1] < tally.get(StructureType.NETHER_FOSSIL)[0]);
        assertTrue(neitherComplex > 0, "a bastion drawn outside a bastion biome was never met");
    }

    @Test
    void netherGeometryIsVanillasOwnStructureStart() throws Exception {
        Map<StructureType, Integer> compared = new LinkedHashMap<StructureType, Integer>();
        BiomeWorldgenSession session = BiomeWorldgenSession.create(SEED, NETHER);
        BiomeSource biomeSource = MultiNoiseBiomeSource.Preset.NETHER.biomeSource(BuiltinRegistries.BIOME, SEED);
        ChunkGenerator generator = netherGenerator(biomeSource, SEED);
        for (StructureType type : NETHER_TYPES) {
            int found = 0;
            for (int[] candidate : gridChunks(type, SEED, 0, 0, type == StructureType.NETHER_FOSSIL ? 30 : 8)) {
                if (found >= 2) {
                    break;
                }
                if (!StructureBiomeValidator.validate(session, type, candidate[0], candidate[1]).isCompatible()) {
                    continue;
                }
                String where = where(type, SEED, candidate);
                StructureGeometry ours = StructureGeometryGenerator.generate(session, type, null,
                        candidate[0], candidate[1]);
                assertTrue(ours.isAvailable(), where + ": " + ours);
                StructureStart<?> start = vanillaStart(biomeSource, generator, SEED, featureOf(type),
                        candidate[0], candidate[1]);
                assertTrue(start != null && start.isValid(), where);
                int inflation = start instanceof BeardedStructureStart ? 12 : 0;
                BoundingBox box = start.getBoundingBox();
                StructureBounds bounds = ours.bounds();
                assertEquals(box.x0 + "," + box.y0 + "," + box.z0 + " .. " + box.x1 + "," + box.y1 + "," + box.z1,
                        (bounds.minX() - inflation) + "," + (bounds.minY() - inflation) + ","
                                + (bounds.minZ() - inflation) + " .. " + (bounds.maxX() + inflation) + ","
                                + (bounds.maxY() + inflation) + "," + (bounds.maxZ() + inflation), where);
                boolean[] touched = new boolean[6];
                for (StructurePiece piece : start.getPieces()) {
                    BoundingBox p = piece.getBoundingBox();
                    assertTrue(bounds.contains(p.x0, p.y0, p.z0) && bounds.contains(p.x1, p.y1, p.z1), where);
                    touched[0] |= p.x0 == bounds.minX();
                    touched[1] |= p.y0 == bounds.minY();
                    touched[2] |= p.z0 == bounds.minZ();
                    touched[3] |= p.x1 == bounds.maxX();
                    touched[4] |= p.y1 == bounds.maxY();
                    touched[5] |= p.z1 == bounds.maxZ();
                }
                for (int face = 0; face < 6; face++) {
                    assertTrue(touched[face], where + ": no piece reaches face " + face);
                }
                found++;
            }
            assertEquals(2, found, "too few " + type + " found");
            compared.put(type, found);
        }
        System.out.println("nether 1.16.5 geometry oracle: " + compared);
    }
    *///?}
}
