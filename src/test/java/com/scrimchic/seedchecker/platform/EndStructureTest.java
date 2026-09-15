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
import java.util.List;
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
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import com.google.gson.JsonObject;
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
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.block.Blocks;
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
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
//?}
//? if >=26.1 {
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
//?} else if >=1.18 {
//?} else {
/*import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.SimpleReloadableResourceManager;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.StructureSettings;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.feature.configurations.StructureFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.level.storage.LevelStorageSource;*/
//?}

/**
 * Phase 3H-3: the End, held to the Minecraft version this target builds.
 *
 * <p>The inventory is derived from vanilla, not written from memory: from 1.18 every structure of
 * the loaded data pack is asked, through its own bound biome set, whether any biome the End's biome
 * source can return lets it start; on 1.16.5 every biome of the End's source is asked which
 * configured structures it lists. The oracles are vanilla's own generation in an End this test
 * builds itself - {@code ChunkGenerator.createStructures} on a bare {@code ProtoChunk} plus
 * {@code findValidGenerationPoint} and {@code Structure.generate} from 1.18,
 * {@code ConfiguredStructureFeature.generate} on 1.16.5 - over the End's own biome source, noise
 * settings and level height. Candidates straddle the origin on both axes, so the central island,
 * negative coordinates and region boundaries; a ring of regions some sixteen thousand blocks out,
 * among the outer islands; and a ring a million chunks out.
 */
class EndStructureTest {

    private static final long SEED = -7407337299659424542L;

    private static final long[] SEEDS = {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, SEED};

    /** Outer-island distance: a thousand chunks, far past the void ring around the main island. */
    private static final int OUTER_CHUNK = 1_000;

    private static final int FAR_CHUNK = 1_000_000;

    private static final String END = BiomeWorldgenSession.END;

    private static final Set<String> END_BIOMES = new HashSet<String>(Arrays.asList(
            "minecraft:the_end", "minecraft:end_highlands", "minecraft:end_midlands",
            "minecraft:small_end_islands", "minecraft:end_barrens"));

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

    /** Grid chunks of a square of regions centred on a chunk, side regions per axis. */
    static List<int[]> gridChunks(long seed, int originChunkX, int originChunkZ, int side) {
        StructurePlacementConfig config = placements.get(StructureType.END_CITY, END);
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

    /** Around the origin, among the outer islands on the negative X axis, and a million chunks out. */
    static List<int[]> oracleChunks(long seed, int nearSide, int outerSide, int farSide) {
        List<int[]> all = gridChunks(seed, 0, 0, nearSide);
        all.addAll(gridChunks(seed, -OUTER_CHUNK, OUTER_CHUNK / 3, outerSide));
        all.addAll(gridChunks(seed, FAR_CHUNK, -FAR_CHUNK, farSide));
        return all;
    }

    private static String where(long seed, int[] chunk) {
        return "end city seed " + seed + " chunk " + chunk[0] + "," + chunk[1];
    }

    /** TheEndBiomeSource's own test for the central island: the chunk within 64 of the origin. */
    private static boolean isCentralIsland(int chunkX, int chunkZ) {
        return (long) chunkX * chunkX + (long) chunkZ * chunkZ <= 4096L;
    }

    // ------------------------------------------------------------ every version

    @Test
    void theEndSessionIsTheEndsOwn() {
        BiomeWorldgenSession end = BiomeWorldgenSession.create(SEED, END);
        assertNotNull(end);
        assertEquals(END, end.dimensionId());
        assertTrue(end.isBiomeColumnConstant());
        assertEquals(END_BIOMES, end.possibleBiomeIds());

        // The central island is one biome out to chunk radius 64, whatever the seed.
        assertEquals("minecraft:the_end", end.sampleBiomeId(0, 0, 0));
        assertEquals("minecraft:the_end", end.sampleBiomeId(1023, 70, 0));
        assertEquals("minecraft:the_end", end.sampleBiomeId(-720, 70, -720));
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < 4000; i++) {
            int x = (int) ((i * 7919L) % 120000) - 60000;
            int z = (int) ((i * 104729L) % 120000) - 60000;
            String id = end.sampleBiomeId(x, 64, z);
            assertTrue(END_BIOMES.contains(id), "not an End biome: " + id);
            assertEquals(isCentralIsland(x >> 4, z >> 4), "minecraft:the_end".equals(id),
                    "block " + x + "," + z);
            seen.add(id);
        }
        assertEquals(END_BIOMES, seen, "a wide scatter should meet every End biome");
        //? if >=1.18 {
        assertEquals(0, end.seaLevel());
        //?}
    }

    @Test
    void theEndBiomeIsOnePerChunkAtEveryHeight() {
        // What the biome map's semantic slice height and the chunk refusal before the terrain walk
        // rest on, checked against the biome source itself: every quart of a chunk, every quart row
        // well past the level both ways, near the origin, negative and four million blocks out.
        int chunks = 0;
        for (long seed : new long[] {SEED, 0L, Long.MIN_VALUE}) {
            BiomeWorldgenSession end = BiomeWorldgenSession.create(seed, END);
            for (int i = 0; i < 200; i++) {
                int chunkX = (int) ((i * 7919L) % 8000) - 4000 + (i % 3 == 0 ? 250_000 : 0);
                int chunkZ = (int) ((i * 104729L) % 8000) - 4000;
                String base = end.sampleBiomeIdAtQuart(chunkX << 2, 0, chunkZ << 2);
                for (int quartY = -20; quartY <= 80; quartY += 5) {
                    for (int dz = 0; dz < 4; dz++) {
                        for (int dx = 0; dx < 4; dx++) {
                            assertEquals(base, end.sampleBiomeIdAtQuart((chunkX << 2) + dx, quartY,
                                    (chunkZ << 2) + dz), "seed " + seed + " chunk " + chunkX + ","
                                    + chunkZ + " quart row " + quartY);
                        }
                    }
                }
                chunks++;
            }
        }
        assertEquals(600, chunks);
    }

    @Test
    void theEndCityIsTheOnlyTypeTheEndStarts() {
        // StructureType.generatesIn decides which layers the End lists; it must be the biome data's
        // answer for every placed type, and the answer must be the end city alone. A second, vanilla
        // side inventory that does not go through the placement table follows per version.
        BiomeWorldgenSession end = BiomeWorldgenSession.create(SEED, END);
        List<StructureType> endTypes = new ArrayList<StructureType>();
        for (StructureType type : placements.types()) {
            boolean startsHere = false;
            for (String variant : StructureBiomeValidator.variantNames(type)) {
                if (StructureBiomeValidator.ownsVariant(type, variant)
                        && !Collections.disjoint(StructureBiomeValidator.acceptedBiomes(type, variant),
                                end.possibleBiomeIds())) {
                    startsHere = true;
                }
            }
            assertEquals(startsHere, type.generatesIn(END), type.toString());
            if (startsHere) {
                endTypes.add(type);
            }
        }
        assertEquals(Collections.singletonList(StructureType.END_CITY), endTypes);
        assertFalse(StructureType.STRONGHOLD.generatesIn(END));
        assertFalse(StructureType.RUINED_PORTAL.generatesIn(END));
        assertFalse(StructureType.END_CITY.generatesIn(BiomeWorldgenSession.OVERWORLD));
        assertFalse(StructureType.END_CITY.generatesIn(BiomeWorldgenSession.NETHER));
        assertTrue(StructureBiomeValidator.isExact(StructureType.END_CITY));
        assertTrue(StructureBiomeValidator.canDecide(StructureType.END_CITY));

        StructurePlacementConfig config = placements.get(StructureType.END_CITY, END);
        assertSame(placements.get(StructureType.END_CITY), config, "the End has no grid of its own");
        assertEquals("20,11,10387313,TRIANGULAR", config.spacing() + "," + config.separation() + ","
                + config.salt() + "," + config.spreadType());
        assertFalse(config.hasRestrictions());
    }

    @Test
    void endCostAndDensityAreReported() {
        // A measurement for the phase report, on one representative seed, after warm-up: biome tile
        // cost next to the overworld's and the nether's; candidates per 10k chunks, placement cost per
        // region, validation cost, positive rate and geometry cost; and how long a 480 by 270 GUI
        // pixel viewport keeps the validation lane busy, near the island and among the outer ones.
        long seed = SEED;
        BiomeWorldgenSession end = BiomeWorldgenSession.create(seed, END);
        int side = BiomeTileGrid.SAMPLES_PER_SIDE;
        for (String dimension : new String[] {BiomeWorldgenSession.OVERWORLD, BiomeWorldgenSession.NETHER, END}) {
            BiomeWorldgenSession session = BiomeWorldgenSession.create(seed, dimension);
            long start = 0L;
            for (int tile = 0; tile < 24; tile++) {
                if (tile == 4) {
                    start = System.nanoTime();
                }
                for (int z = 0; z < side; z++) {
                    for (int x = 0; x < side; x++) {
                        session.sampleBiomeId(tile * side * 16 + x * 16, 64, z * 16 - 5000);
                    }
                }
            }
            System.out.printf("phase 3H-3 biome tile %-20s %.2f ms per %dx%d tile at 16 blocks%n",
                    dimension, (System.nanoTime() - start) / 1e6 / 20, side, side);
        }

        StructurePlacementEngine engine = new StructurePlacementEngine();
        StructurePlacementConfig config = placements.get(StructureType.END_CITY, END);
        ChunkRange area = ChunkRange.of(-256, -256, 255, 255);
        final int[] found = {0};
        // Placement is timed on the last of several passes, so the JIT has compiled the walk.
        long placementStart = 0L;
        for (int pass = 0; pass < 20; pass++) {
            found[0] = 0;
            placementStart = System.nanoTime();
            engine.forEachCandidate(seed, config, area, Integer.MAX_VALUE, (x, z) -> {
                found[0]++;
                return true;
            });
        }
        double placementNs = (System.nanoTime() - placementStart)
                / (double) StructurePlacementEngine.regionCount(config, area);

        List<int[]> sample = gridChunks(seed, 0, 0, 12);
        sample.addAll(gridChunks(seed, -OUTER_CHUNK, OUTER_CHUNK / 3, 12));
        for (int i = 0; i < 6; i++) {
            StructureBiomeValidator.validate(end, StructureType.END_CITY, sample.get(i)[0], sample.get(i)[1]);
        }
        int accepted = 0;
        int[] firstAccepted = null;
        StructureValidation firstResult = null;
        long start = System.nanoTime();
        for (int[] candidate : sample) {
            StructureValidation result =
                    StructureBiomeValidator.validate(end, StructureType.END_CITY, candidate[0], candidate[1]);
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
            StructureGeometryGenerator.generate(end, StructureType.END_CITY, firstResult.variant(),
                    firstAccepted[0], firstAccepted[1]);
            long geometryStart = System.nanoTime();
            int rounds = 5;
            StructureGeometry measured = null;
            for (int i = 0; i < rounds; i++) {
                measured = StructureGeometryGenerator.generate(end, StructureType.END_CITY,
                        firstResult.variant(), firstAccepted[0], firstAccepted[1]);
            }
            geometry = String.format("geometry %.2f ms%s", (System.nanoTime() - geometryStart) / 1e6 / rounds,
                    measured != null && measured.isAvailable() ? "" : " (no bounds)");
        }
        System.out.printf("phase 3H-3 END_CITY %7.2f candidates per 10k chunks, placement %5.1f ns per "
                        + "region, %6.3f ms per validation, %d of %d accepted, %s%n",
                found[0] * 10000.0 / (512 * 512), placementNs, millis, accepted, sample.size(), geometry);

        for (int[] origin : new int[][] {{0, 0}, {-OUTER_CHUNK, OUTER_CHUNK / 3}}) {
            for (double scale : new double[] {1.0 / 64, 1.0 / 16, 1.0 / 4, 1.0 / 2}) {
                MapViewport viewport = new MapViewport();
                viewport.resize(480, 270);
                viewport.setScale(scale);
                viewport.setCenter(origin[0] * 16.0, origin[1] * 16.0);
                ChunkRange visible = ChunkRange.visibleIn(viewport);
                if (!StructureLayer.isDrawableAt(config, visible, scale)) {
                    System.out.printf("phase 3H-3 END_CITY viewport 480x270 at 1/%d px/block around chunk %d: "
                            + "layer says zoom in%n", Math.round(1 / scale), origin[0]);
                    continue;
                }
                final List<int[]> inView = new ArrayList<int[]>();
                engine.forEachCandidate(seed, config, visible, Integer.MAX_VALUE, (x, z) -> {
                    inView.add(new int[] {x, z});
                    return true;
                });
                long viewStart = System.nanoTime();
                int shown = 0;
                for (int[] candidate : inView) {
                    if (StructureBiomeValidator.validate(end, StructureType.END_CITY, candidate[0],
                            candidate[1]).isCompatible()) {
                        shown++;
                    }
                }
                double seconds = (System.nanoTime() - viewStart) / 1e9;
                System.out.printf("phase 3H-3 END_CITY viewport 480x270 at 1/%d px/block around chunk %d: "
                                + "%d candidates, %d shown, %.3f s serial, %.3f s to settle on %d workers%n",
                        Math.round(1 / scale), origin[0], inView.size(), shown, seconds, seconds / WORKERS,
                        WORKERS);
            }
        }
        assertTrue(found[0] > 0, "no end city candidate in 512 by 512 chunks");
    }

    // ------------------------------------------------------------- 1.18 onwards

    //? if >=1.18 {
    /**
     * The End, built by this test over the loaded data pack from the End's noise settings, biome
     * source type and dimension type: separate template manager, noise and generator from anything
     * production built.
     */
    static final class EndWorld {

        final VanillaStructureData data;
        final long seed;
        final StructureTemplateManager templates;
        final RandomState randomState;
        final BiomeSource biomeSource;
        final ChunkGenerator chunkGenerator;
        final LevelHeightAccessor heightAccessor;

        EndWorld(long seed) throws Exception {
            this.data = StructureBiomeValidatorTest.structureData();
            this.seed = seed;
            this.templates = data.newTemplateManager();
            Holder<NoiseGeneratorSettings> settings = data.registries()
                    .lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.END);
            this.randomState = RandomState.create(settings.value(),
                    data.registries().lookupOrThrow(Registries.NOISE), seed);
            this.biomeSource = TheEndBiomeSource.create(data.registries().lookupOrThrow(Registries.BIOME));
            this.chunkGenerator = new NoiseBasedChunkGenerator(biomeSource, settings);
            net.minecraft.world.level.dimension.DimensionType dimension = data.registries()
                    .lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.END)
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
        GenerationPoint point(int chunkX, int chunkZ, Predicate<Holder<Biome>> biomes) {
            Optional<Structure.GenerationStub> stub =
                    structure("end_city").value().findValidGenerationPoint(context(chunkX, chunkZ, biomes));
            if (!stub.isPresent()) {
                return null;
            }
            BlockPos position = stub.get().position();
            return new GenerationPoint(position.getX(), position.getY(), position.getZ());
        }

        StructureStart start(int chunkX, int chunkZ, Predicate<Holder<Biome>> biomes) {
            Holder<Structure> holder = structure("end_city");
            //? if >=26.1 {
            return holder.value().generate(holder, Level.END, data.generationRegistries(),
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

        /** The structure vanilla's createStructures leaves a valid start for in that chunk, or null. */
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
                    templates, Level.END);
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
                built = structureName(start.getKey());
            }
            return built;
        }

        String structureName(final Structure structure) {
            String id = StructureBiomeValidatorTest.keyName(data.registries()
                    .lookupOrThrow(Registries.STRUCTURE).listElements()
                    .filter(holder -> holder.value() == structure).findFirst()
                    .orElseThrow(() -> new AssertionError("unregistered structure")).key());
            return id.substring(id.indexOf(':') + 1);
        }
    }

    @Test
    void theVanillaInventoryOfTheEndIsTheEndCitySetAlone() throws Exception {
        // Asked of vanilla's loaded registries, not of the placement table: every structure whose
        // own bound biome set holds any biome the End's biome source can return, and every set
        // holding such a structure. Also that the vanilla world preset really pairs the End with
        // this biome source and these noise settings.
        EndWorld world = new EndWorld(SEED);
        Set<Holder<Biome>> endBiomes = world.biomeSource.possibleBiomes();
        Set<String> endBiomeIds = new HashSet<String>();
        for (Holder<Biome> biome : endBiomes) {
            endBiomeIds.add(StructureBiomeValidatorTest.keyName(biome.unwrapKey().get()));
        }
        assertEquals(END_BIOMES, endBiomeIds);

        List<String> structures = new ArrayList<String>();
        int asked = 0;
        for (Holder.Reference<Structure> structure
                : (Iterable<Holder.Reference<Structure>>) world.data.registries()
                        .lookupOrThrow(Registries.STRUCTURE).listElements()::iterator) {
            asked++;
            for (Holder<Biome> biome : endBiomes) {
                if (structure.value().biomes().contains(biome)) {
                    structures.add(world.structureName(structure.value()));
                    break;
                }
            }
        }
        assertEquals(Collections.singletonList("end_city"), structures, "of " + asked + " structures");

        List<String> sets = new ArrayList<String>();
        for (Holder.Reference<StructureSet> set
                : (Iterable<Holder.Reference<StructureSet>>) world.data.registries()
                        .lookupOrThrow(Registries.STRUCTURE_SET).listElements()::iterator) {
            for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
                if (structures.contains(world.structureName(entry.structure().value()))) {
                    String id = StructureBiomeValidatorTest.keyName(set.key());
                    sets.add(id.substring(id.indexOf(':') + 1));
                    break;
                }
            }
        }
        assertEquals(Collections.singletonList("end_cities"), sets);
        assertEquals(Collections.singletonList("end_city"),
                StructureBiomeValidator.variantNames(StructureType.END_CITY));
        assertEquals(new HashSet<String>(Arrays.asList("minecraft:end_highlands", "minecraft:end_midlands")),
                StructureBiomeValidator.acceptedBiomes(StructureType.END_CITY, "end_city"));

        JsonObject preset = StructureBiomeValidator.vanillaDataJson("worldgen/world_preset/normal");
        JsonObject generator = preset.getAsJsonObject("dimensions").getAsJsonObject("minecraft:the_end")
                .getAsJsonObject("generator");
        assertEquals("minecraft:noise", generator.get("type").getAsString());
        assertEquals("minecraft:end", generator.get("settings").getAsString());
        assertEquals("minecraft:the_end",
                generator.getAsJsonObject("biome_source").get("type").getAsString());
        System.out.println("end inventory: " + asked + " structures asked, " + structures
                + " start in the End, sets " + sets);
    }

    @Test
    void endCitiesMatchVanillaCreateStructuresOnEverySeed() throws Exception {
        // The regression oracle: the start vanilla's createStructures leaves in each grid chunk of
        // the end_cities set against production, and for every built city the generation point.
        // Refusals are split by what refused them - the central island's biome, an outer biome that
        // does not list the city, or the terrain below y 60 - and each kind must be met.
        int built = 0;
        int central = 0;
        int refusedByBiome = 0;
        int refusedByTerrain = 0;
        int outer = 0;
        int far = 0;
        int compared = 0;
        // Outcomes a million chunks out: built, refused by biome, refused by terrain.
        int[] farOutcomes = new int[3];
        for (long seed : SEEDS) {
            BiomeWorldgenSession session = BiomeWorldgenSession.create(seed, END);
            EndWorld world = new EndWorld(seed);
            ChunkGeneratorStructureState state = world.state("end_cities");
            Predicate<Holder<Biome>> predicate =
                    StructureBiomeValidatorTest.predicateFor(StructureType.END_CITY, "end_city");
            for (int[] candidate : oracleChunks(seed, 10, 6, 8)) {
                String where = where(seed, candidate);
                boolean isFar = Math.abs(candidate[0]) > FAR_CHUNK / 2;
                String vanilla = world.built(state, candidate[0], candidate[1]);
                StructureValidation ours = StructureBiomeValidator.validate(session,
                        StructureType.END_CITY, candidate[0], candidate[1]);
                assertNotNull(ours, where);
                assertEquals(vanilla != null, ours.isCompatible(), where + ": " + vanilla + " / " + ours);
                assertTrue(ours.isCompatible() || ours.isRejected(), where + ": undecided " + ours);
                compared++;
                if (ours.isCompatible()) {
                    built++;
                    assertEquals("end_city", vanilla, where);
                    assertTrue(ours.isExact(), where);
                    assertEquals("end_city", ours.variant(), where);
                    GenerationPoint point = world.point(candidate[0], candidate[1], predicate);
                    assertEquals(point, ours.generationPoint(), where + ": generation point");
                    assertEquals((candidate[0] << 4) + 7, point.x(), where);
                    assertEquals((candidate[1] << 4) + 7, point.z(), where);
                    assertTrue(point.y() >= 60, where);
                    if (isFar) {
                        far++;
                        farOutcomes[0]++;
                    } else if (Math.abs(candidate[0]) > OUTER_CHUNK / 2) {
                        outer++;
                    }
                    continue;
                }
                String biome = session.sampleBiomeIdAtQuart(candidate[0] << 2, 0, candidate[1] << 2);
                if (isCentralIsland(candidate[0], candidate[1])) {
                    assertEquals("minecraft:the_end", biome, where);
                    central++;
                } else if (!"minecraft:end_highlands".equals(biome) && !"minecraft:end_midlands".equals(biome)) {
                    refusedByBiome++;
                    if (isFar) {
                        farOutcomes[1]++;
                    }
                } else {
                    // The chunk's biome lists the city, so terrain must be what refused it.
                    assertEquals(null, world.point(candidate[0], candidate[1], holder -> true), where);
                    refusedByTerrain++;
                    if (isFar) {
                        farOutcomes[2]++;
                    }
                }
            }
        }
        System.out.println("end city oracle: " + compared + " candidates, " + built + " built ("
                + outer + " among the outer islands, " + far + " a million chunks out), " + central
                + " on the central island, " + refusedByBiome + " refused by an outer biome, "
                + refusedByTerrain + " refused by terrain below y 60; a million chunks out "
                + Arrays.toString(farOutcomes) + " built / biome / terrain");
        assertTrue(built > 0, "no end city was built");
        assertTrue(central > 0, "the central island was never met");
        assertTrue(refusedByBiome > 0, "an outer biome refusal was never met");
        assertTrue(refusedByTerrain > 0, "the terrain condition was never exercised");
        assertTrue(outer > 0 && far > 0, "no city far out was compared");
    }

    @Test
    void endCityGeometryIsVanillasOwnStructureStart() throws Exception {
        // Our bounds against vanilla's StructureStart: its box must be our bounds through the
        // structure's own adjustBoundingBox, every piece must lie inside our bounds, and each face
        // must be reached by a piece - so the bounds are the whole city, not its first tower. Also
        // counts what End Ship detection would cost: the ship is the piece built from template
        // "ship", and its elytra item frame is that template's "Elytra" data marker.
        int compared = 0;
        int pieces = 0;
        int ships = 0;
        long shipNanos = 0L;
        for (long seed : new long[] {SEED, 0L, -1L, Long.MAX_VALUE}) {
            BiomeWorldgenSession session = BiomeWorldgenSession.create(seed, END);
            EndWorld world = new EndWorld(seed);
            int found = 0;
            List<int[]> candidates = gridChunks(seed, 0, 0, 10);
            candidates.addAll(gridChunks(seed, -OUTER_CHUNK, OUTER_CHUNK / 3, 8));
            for (int[] candidate : candidates) {
                if (found >= 4) {
                    break;
                }
                StructureValidation validation =
                        StructureBiomeValidator.validate(session, StructureType.END_CITY, candidate[0], candidate[1]);
                if (validation == null || !validation.isCompatible()) {
                    continue;
                }
                String where = where(seed, candidate);
                StructureGeometry ours = StructureGeometryGenerator.generate(session, StructureType.END_CITY,
                        validation.variant(), candidate[0], candidate[1]);
                assertNotNull(ours, where);
                assertTrue(ours.isAvailable(), where + ": " + ours);
                assertEquals(validation.generationPoint(), ours.generationPoint(), where);
                StructureStart start = world.start(candidate[0], candidate[1],
                        StructureBiomeValidatorTest.predicateFor(StructureType.END_CITY, "end_city"));
                assertTrue(start.isValid(), where);
                StructureBounds bounds = ours.bounds();
                BoundingBox adjusted = world.structure("end_city").value().adjustBoundingBox(
                        new BoundingBox(bounds.minX(), bounds.minY(), bounds.minZ(),
                                bounds.maxX(), bounds.maxY(), bounds.maxZ()));
                StructureBiomeValidatorTest.assertSameBox(start.getBoundingBox(), adjusted, where + " box");
                StructureBiomeValidatorTest.assertPiecesSpan(bounds, start.getPieces(), where);
                assertTrue(start.getPieces().size() > 1, where + ": a city is more than one piece");
                pieces += start.getPieces().size();

                long shipStart = System.nanoTime();
                BlockPos elytra = elytraFrame(start.getPieces());
                shipNanos += System.nanoTime() - shipStart;
                if (elytra != null) {
                    ships++;
                    assertTrue(bounds.contains(elytra.getX(), elytra.getY(), elytra.getZ()), where);
                    System.out.println("end city " + where + ": ship, elytra frame at " + elytra.getX() + ","
                            + elytra.getY() + "," + elytra.getZ());
                }
                found++;
            }
            assertTrue(found > 0, "seed " + seed + ": no end city to measure");
            compared += found;
        }
        System.out.printf("end city geometry oracle: %d cities, %d pieces, %d with a ship, ship lookup %.3f ms "
                + "per city%n", compared, pieces, ships, shipNanos / 1e6 / compared);
    }

    /** The world position of the "Elytra" data marker of the city's "ship" piece, or null. */
    private static BlockPos elytraFrame(List<StructurePiece> pieces) throws Exception {
        java.lang.reflect.Field name = TemplateStructurePiece.class.getDeclaredField("templateName");
        java.lang.reflect.Field template = TemplateStructurePiece.class.getDeclaredField("template");
        java.lang.reflect.Field position = TemplateStructurePiece.class.getDeclaredField("templatePosition");
        java.lang.reflect.Field settings = TemplateStructurePiece.class.getDeclaredField("placeSettings");
        name.setAccessible(true);
        template.setAccessible(true);
        position.setAccessible(true);
        settings.setAccessible(true);
        BlockPos found = null;
        for (StructurePiece piece : pieces) {
            if (!(piece instanceof TemplateStructurePiece) || !"ship".equals(name.get(piece))) {
                continue;
            }
            assertEquals(null, found, "more than one ship in one city");
            for (StructureTemplate.StructureBlockInfo info : ((StructureTemplate) template.get(piece)).filterBlocks(
                    (BlockPos) position.get(piece), (StructurePlaceSettings) settings.get(piece),
                    Blocks.STRUCTURE_BLOCK)) {
                //? if >=26.1 {
                String metadata = info.nbt() == null ? "" : info.nbt().getStringOr("metadata", "");
                //?} else {
                /*String metadata = info.nbt() == null ? "" : info.nbt().getString("metadata");*/
                //?}
                if (metadata.startsWith("Elytra")) {
                    assertEquals(null, found, "more than one elytra marker");
                    found = info.pos();
                }
            }
            assertNotNull(found, "a ship without an elytra marker");
        }
        return found;
    }
    //?} else {
    /*// -------------------------------------------------------------------- 1.16.5

    private static StructureManager templates;
    private static LevelStorageSource.LevelStorageAccess level;

    private static StructureManager templates() throws Exception {
        if (templates == null) {
            SimpleReloadableResourceManager resources = new SimpleReloadableResourceManager(PackType.SERVER_DATA);
            resources.add(new VanillaPackResources("minecraft"));
            java.nio.file.Path temp = java.nio.file.Files.createTempDirectory("seedchecker-end-oracle");
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

    private static NoiseGeneratorSettings endSettings() {
        return BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.END);
    }

    /^* DimensionType.defaultEndGenerator's generator, built here independently of production. ^/
    private static ChunkGenerator endGenerator(BiomeSource biomeSource, long seed) {
        final NoiseGeneratorSettings settings = endSettings();
        return new NoiseBasedChunkGenerator(biomeSource, seed, new Supplier<NoiseGeneratorSettings>() {
            @Override
            public NoiseGeneratorSettings get() {
                return settings;
            }
        });
    }

    /^*
     * Vanilla's start, as ChunkGenerator.createStructures builds it: the biome at the chunk's fixed
     * quart, that biome's configured end city, the End generator's own grid settings, and
     * ConfiguredStructureFeature.generate. Null when the biome does not list the feature at all.
     ^/
    private static StructureStart<?> vanillaStart(BiomeSource biomeSource, ChunkGenerator generator,
                                                  long seed, int chunkX, int chunkZ) throws Exception {
        Biome biome = biomeSource.getNoiseBiome((chunkX << 2) + 2, 0, (chunkZ << 2) + 2);
        for (Supplier<ConfiguredStructureFeature<?, ?>> supplier : biome.getGenerationSettings().structures()) {
            ConfiguredStructureFeature<?, ?> configured = supplier.get();
            if (configured.feature != StructureFeature.END_CITY) {
                continue;
            }
            StructureFeatureConfiguration placement = generator.getSettings().getConfig(StructureFeature.END_CITY);
            assertNotNull(placement, "the End generator has no grid for the end city");
            return configured.generate(RegistryAccess.builtin(), generator, biomeSource, templates(),
                    seed, new ChunkPos(chunkX, chunkZ), biome, 0, placement);
        }
        return null;
    }

    @Test
    void theVanillaInventoryOfTheEndIsTheEndCityAlone() {
        // Every biome the End's source can return, asked which configured structures it lists; and
        // the End generator's settings, which carry no stronghold, so createStructures' unconditional
        // stronghold attempt can never build one here.
        TheEndBiomeSource source = new TheEndBiomeSource(BuiltinRegistries.BIOME, SEED);
        Set<String> biomeIds = new HashSet<String>();
        Set<String> listed = new HashSet<String>();
        Set<String> cityBiomes = new HashSet<String>();
        for (Biome biome : source.possibleBiomes()) {
            String id = BuiltinRegistries.BIOME.getKey(biome).toString();
            biomeIds.add(id);
            for (Supplier<ConfiguredStructureFeature<?, ?>> supplier : biome.getGenerationSettings().structures()) {
                StructureFeature<?> feature = supplier.get().feature;
                listed.add(Registry.STRUCTURE_FEATURE.getKey(feature).toString());
                if (feature == StructureFeature.END_CITY) {
                    cityBiomes.add(id);
                }
            }
        }
        assertEquals(END_BIOMES, biomeIds);
        assertEquals(Collections.singleton("minecraft:endcity"), listed);
        assertEquals(new HashSet<String>(Arrays.asList("minecraft:end_highlands", "minecraft:end_midlands")),
                cityBiomes);
        assertEquals(cityBiomes, StructureBiomeValidator.acceptedBiomes(StructureType.END_CITY, null));

        StructureSettings settings = endSettings().structureSettings();
        assertEquals(null, settings.stronghold());
        ChunkGenerator generator = endGenerator(source, SEED);
        for (int chunk = -300; chunk <= 300; chunk += 7) {
            assertFalse(generator.hasStronghold(new ChunkPos(chunk, -chunk / 2)));
        }
        System.out.println("end 1.16.5 inventory: biomes " + biomeIds + " list " + listed);
    }

    @Test
    void theEndGeneratorGridIsTheTable() {
        // The End's chunk generator carries its own StructureSettings; on this version they are a
        // copy of the defaults. The numbers, then structure chunk by structure chunk against vanilla's
        // own getPotentialFeatureChunk over those settings - so the triangular spread is compared too
        // - around the origin, across region boundaries and a million chunks out.
        StructureFeatureConfiguration vanilla = endSettings().structureSettings().getConfig(StructureFeature.END_CITY);
        StructurePlacementConfig ours = placements.get(StructureType.END_CITY, END);
        assertEquals(vanilla.spacing() + "," + vanilla.separation() + "," + vanilla.salt(),
                ours.spacing() + "," + ours.separation() + "," + ours.salt());
        StructurePlacementEngine engine = new StructurePlacementEngine();
        WorldgenRandom random = new WorldgenRandom();
        int structureChunks = 0;
        for (long seed : SEEDS) {
            for (int origin : new int[] {0, FAR_CHUNK}) {
                for (int chunkZ = -origin - 60; chunkZ < -origin + 60; chunkZ++) {
                    for (int chunkX = origin - 60; chunkX < origin + 60; chunkX++) {
                        ChunkPos potential = StructureFeature.END_CITY.getPotentialFeatureChunk(vanilla, seed,
                                random, chunkX, chunkZ);
                        boolean vanillaChunk = potential.x == chunkX && potential.z == chunkZ;
                        assertEquals(vanillaChunk, engine.isStructureChunk(seed, ours, chunkX, chunkZ),
                                "seed " + seed + " chunk " + chunkX + "," + chunkZ);
                        if (vanillaChunk) {
                            structureChunks++;
                        }
                    }
                }
            }
        }
        System.out.println("end 1.16.5 grid oracle: " + structureChunks
                + " structure chunks agreed with the End generator's settings");
    }

    @Test
    void endCitiesMatchVanillaGenerateOnEverySeed() throws Exception {
        int built = 0;
        int central = 0;
        int refusedByBiome = 0;
        int refusedByTerrain = 0;
        int compared = 0;
        int far = 0;
        for (long seed : SEEDS) {
            BiomeWorldgenSession session = BiomeWorldgenSession.create(seed, END);
            BiomeSource biomeSource = new TheEndBiomeSource(BuiltinRegistries.BIOME, seed);
            ChunkGenerator generator = endGenerator(biomeSource, seed);
            for (int[] candidate : oracleChunks(seed, 10, 6, 8)) {
                String where = where(seed, candidate);
                StructureValidation ours =
                        StructureBiomeValidator.validate(session, StructureType.END_CITY, candidate[0], candidate[1]);
                StructureStart<?> start = vanillaStart(biomeSource, generator, seed, candidate[0], candidate[1]);
                boolean vanilla = start != null && start.isValid();
                assertEquals(vanilla, ours.isCompatible(), where + ": " + ours);
                assertTrue(ours.isExact() || ours.isRejected(), where + ": " + ours);
                compared++;
                if (vanilla) {
                    built++;
                    if (Math.abs(candidate[0]) > FAR_CHUNK / 2) {
                        far++;
                    }
                } else if (start != null) {
                    refusedByTerrain++;
                } else if (isCentralIsland(candidate[0], candidate[1])) {
                    central++;
                } else {
                    refusedByBiome++;
                }
            }
        }
        System.out.println("end city 1.16.5 oracle: " + compared + " candidates, " + built + " built (" + far
                + " a million chunks out), " + central + " on the central island, " + refusedByBiome
                + " refused by an outer biome, " + refusedByTerrain + " refused by terrain below y 60");
        assertTrue(built > 0 && central > 0 && refusedByBiome > 0 && refusedByTerrain > 0,
                "every outcome must be compared");
        assertTrue(far > 0, "no city a million chunks out was compared");
    }

    @Test
    void endCityGeometryIsVanillasOwnStructureStart() throws Exception {
        int compared = 0;
        int ships = 0;
        for (long seed : new long[] {SEED, 0L}) {
            BiomeWorldgenSession session = BiomeWorldgenSession.create(seed, END);
            BiomeSource biomeSource = new TheEndBiomeSource(BuiltinRegistries.BIOME, seed);
            ChunkGenerator generator = endGenerator(biomeSource, seed);
            int found = 0;
            List<int[]> candidates = gridChunks(seed, 0, 0, 10);
            candidates.addAll(gridChunks(seed, -OUTER_CHUNK, OUTER_CHUNK / 3, 8));
            for (int[] candidate : candidates) {
                if (found >= 3) {
                    break;
                }
                if (!StructureBiomeValidator.validate(session, StructureType.END_CITY, candidate[0], candidate[1])
                        .isCompatible()) {
                    continue;
                }
                String where = where(seed, candidate);
                StructureGeometry ours = StructureGeometryGenerator.generate(session, StructureType.END_CITY, null,
                        candidate[0], candidate[1]);
                assertTrue(ours.isAvailable(), where + ": " + ours);
                StructureStart<?> start = vanillaStart(biomeSource, generator, seed, candidate[0], candidate[1]);
                assertTrue(start != null && start.isValid(), where);
                BoundingBox box = start.getBoundingBox();
                StructureBounds bounds = ours.bounds();
                assertEquals(box.x0 + "," + box.y0 + "," + box.z0 + " .. " + box.x1 + "," + box.y1 + "," + box.z1,
                        bounds.minX() + "," + bounds.minY() + "," + bounds.minZ() + " .. " + bounds.maxX() + ","
                                + bounds.maxY() + "," + bounds.maxZ(), where);
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
                    java.lang.reflect.Field name = piece.getClass().getDeclaredField("templateName");
                    name.setAccessible(true);
                    if ("ship".equals(name.get(piece))) {
                        ships++;
                    }
                }
                for (int face = 0; face < 6; face++) {
                    assertTrue(touched[face], where + ": no piece reaches face " + face);
                }
                assertTrue(start.getPieces().size() > 1, where);
                found++;
            }
            assertTrue(found > 0, "seed " + seed + ": no end city to measure");
            compared += found;
        }
        System.out.println("end city 1.16.5 geometry oracle: " + compared + " cities, " + ships + " ships");
    }
    *///?}
}
