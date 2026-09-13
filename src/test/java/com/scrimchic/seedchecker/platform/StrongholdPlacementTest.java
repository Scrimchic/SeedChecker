package com.scrimchic.seedchecker.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.scrimchic.seedchecker.worldgen.ConcentricRingConfig;
import com.scrimchic.seedchecker.worldgen.StrongholdPosition;
import com.scrimchic.seedchecker.worldgen.StructureBounds;
import com.scrimchic.seedchecker.worldgen.StructureGeometry;
import com.scrimchic.seedchecker.worldgen.StructureType;

import net.minecraft.server.Bootstrap;

//? if >=1.18 {
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.scrimchic.seedchecker.core.util.LazyInit;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
//?}
//? if >=26.1 {
import net.minecraft.world.level.Level;
//?} else if >=1.18 {
//?} else {
/*import java.lang.reflect.Field;

import net.minecraft.core.RegistryAccess;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.data.worldgen.StructureFeatures;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.OverworldBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.feature.configurations.StructureFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;*/
//?}

/**
 * Proves {@link StrongholdLocator} returns vanilla's own stronghold list, chunk for chunk and in
 * vanilla's order, for seeds chosen to reach the edges of the seed space.
 *
 * <p>The oracle is vanilla's list itself: {@code ChunkGenerator.strongholdPositions} after
 * {@code generateStrongholds} on 1.16.5, and {@code ChunkGeneratorStructureState.generateRingPositions}
 * from 1.18. Both are private, and both are reached here by reflection rather than reimplemented.
 */
class StrongholdPlacementTest {

    private static final long REPRESENTATIVE = -7407337299659424542L;

    private static final long[] SEEDS = {
            0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, REPRESENTATIVE,
    };

    @BeforeAll
    static void bootstrap() {
        //? if >=1.18 {
        SharedConstants.tryDetectVersion();
        //?}
        Bootstrap.bootStrap();
    }

    @Test
    void everyStrongholdIsVanillasInVanillasOrder() throws Exception {
        int compared = 0;
        int adjusted = 0;
        int negative = 0;
        int farthestRing = 0;
        long locateNanos = 0;

        for (long seed : SEEDS) {
            BiomeWorldgenSession session =
                    BiomeWorldgenSession.create(seed, BiomeWorldgenSession.OVERWORLD);
            long start = System.nanoTime();
            List<StrongholdPosition> ours = StrongholdLocator.locate(session);
            locateNanos += System.nanoTime() - start;
            List<int[]> vanilla = vanillaStrongholds(seed);

            assertEquals(vanilla.size(), ours.size(), "seed " + seed + " stronghold count");
            assertEquals(StrongholdLocator.ringConfig().count(), ours.size());
            for (int i = 0; i < vanilla.size(); i++) {
                StrongholdPosition position = ours.get(i);
                String where = "seed " + seed + " stronghold #" + i + " " + position;
                assertEquals(i, position.index(), where);
                assertEquals(vanilla.get(i)[0], position.chunkX(), where + " chunk x");
                assertEquals(vanilla.get(i)[1], position.chunkZ(), where + " chunk z");
                compared++;
                if (position.isBiomeAdjusted()) {
                    adjusted++;
                }
                if (position.chunkX() < 0 || position.chunkZ() < 0) {
                    negative++;
                }
                farthestRing = Math.max(farthestRing, position.ring());
            }
        }
        System.out.printf("stronghold oracle: %d strongholds over %d seeds, %d moved by the biome "
                        + "search, %d with a negative coordinate, rings 0..%d; %.0f ms per list%n",
                compared, SEEDS.length, adjusted, negative, farthestRing,
                locateNanos / 1e6 / SEEDS.length);
        assertTrue(adjusted > compared / 2, "the biome search must be exercised, moved " + adjusted);
        assertTrue(negative > 0);
        assertEquals(7, farthestRing, "128 strongholds reach the eighth ring");
    }

    @Test
    void theRingParametersAreVanillas() throws Exception {
        ConcentricRingConfig config = StrongholdLocator.ringConfig();
        assertEquals(vanillaRingConfig(), config);
    }

    //? if >=1.18 {
    private static VanillaStructureData data() {
        assertEquals(LazyInit.State.READY, VanillaStructureData.loadIfNeeded());
        return VanillaStructureData.get();
    }

    private static Holder<StructureSet> strongholdSet() {
        return data().registries().lookupOrThrow(Registries.STRUCTURE_SET).listElements()
                .filter(holder -> keyName(holder.key()).equals("minecraft:strongholds"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no strongholds structure set"));
    }

    private static ConcentricRingConfig vanillaRingConfig() {
        ConcentricRingsStructurePlacement placement =
                (ConcentricRingsStructurePlacement) strongholdSet().value().placement();
        return new ConcentricRingConfig(placement.distance(), placement.spread(),
                placement.count());
    }

    private static final class World {
        final RegistryAccess registries = data().registries();
        final Holder<NoiseGeneratorSettings> settings = registries
                .lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        final RandomState randomState;
        final BiomeSource biomeSource = MultiNoiseBiomeSource.createFromPreset(
                registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));

        World(long seed) {
            randomState = RandomState.create(settings.value(),
                    registries.lookupOrThrow(Registries.NOISE), seed);
        }
    }

    /**
     * Vanilla's ring generation, run by reflection on its private method, with a placement whose
     * preferred biomes are the loaded registry's own holders for the ids the locator uses. On 1.20.1
     * the registry's tag is unbound, so this is the only way to run vanilla's loop with any preferred
     * biomes at all; on 26.2 {@link #boundTagStateAgrees} checks the same answer through the fully
     * vanilla path as well.
     */
    @SuppressWarnings("unchecked")
    private static List<int[]> vanillaStrongholds(long seed) throws Exception {
        World world = new World(seed);
        java.util.Set<String> preferred = StrongholdLocator.preferredBiomes();
        List<Holder<Biome>> holders = world.registries.lookupOrThrow(Registries.BIOME)
                .listElements()
                .filter(holder -> preferred.contains(keyName(holder.key())))
                .collect(Collectors.toList());
        assertEquals(preferred.size(), holders.size(), "every preferred biome must exist");
        ConcentricRingConfig config = vanillaRingConfig();
        ConcentricRingsStructurePlacement placement = new ConcentricRingsStructurePlacement(
                config.distance(), config.spread(), config.count(), HolderSet.direct(holders));

        Constructor<ChunkGeneratorStructureState> constructor =
                ChunkGeneratorStructureState.class.getDeclaredConstructor(RandomState.class,
                        BiomeSource.class, long.class, long.class, List.class);
        constructor.setAccessible(true);
        Holder<StructureSet> set = strongholdSet();
        ChunkGeneratorStructureState state = constructor.newInstance(world.randomState,
                world.biomeSource, seed, seed, java.util.Collections.singletonList(set));
        Method generate = ChunkGeneratorStructureState.class.getDeclaredMethod(
                "generateRingPositions", Holder.class, ConcentricRingsStructurePlacement.class);
        generate.setAccessible(true);
        List<ChunkPos> positions =
                ((CompletableFuture<List<ChunkPos>>) generate.invoke(state, set, placement)).join();
        return toPairs(positions);
    }

    private static List<int[]> toPairs(List<ChunkPos> positions) {
        List<int[]> pairs = new ArrayList<int[]>();
        for (ChunkPos position : positions) {
            //? if >=26.1 {
            pairs.add(new int[] {position.x(), position.z()});
            //?} else {
            /*pairs.add(new int[] {position.x, position.z});*/
            //?}
        }
        return pairs;
    }

    private static String keyName(ResourceKey<?> key) {
        //? if >=26.1 {
        return key.identifier().toString();
        //?} else {
        /*return key.location().toString();*/
        //?}
    }

    @Test
    void aStrongholdGeneratesExactlyAtItsChunkCorner() throws Exception {
        // Placement is the whole story: StrongholdStructure.findGenerationPoint puts the stub at the
        // chunk's world position with no condition, the has_structure/stronghold tag is
        // #is_overworld, and piece assembly retries until it has a portal room. Checked through
        // vanilla's full Structure.generate on real positions.
        long seed = REPRESENTATIVE;
        World world = new World(seed);
        ChunkGenerator chunkGenerator = new NoiseBasedChunkGenerator(world.biomeSource, world.settings);
        LevelHeightAccessor height = LevelHeightAccessor.create(
                world.settings.value().noiseSettings().minY(),
                world.settings.value().noiseSettings().height());
        Holder<Structure> stronghold = world.registries.lookupOrThrow(Registries.STRUCTURE)
                .listElements().filter(holder -> keyName(holder.key()).equals("minecraft:stronghold"))
                .findFirst().orElseThrow(() -> new AssertionError("no stronghold structure"));
        net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates =
                data().newTemplateManager();

        List<StrongholdPosition> ours = StrongholdLocator.locate(
                BiomeWorldgenSession.create(seed, BiomeWorldgenSession.OVERWORLD));
        for (int i = 0; i < 6; i++) {
            StrongholdPosition position = ours.get(i);
            ChunkPos chunk = new ChunkPos(position.chunkX(), position.chunkZ());
            Structure.GenerationContext context = new Structure.GenerationContext(world.registries,
                    chunkGenerator, world.biomeSource, world.randomState, templates, seed, chunk,
                    height, holder -> true);
            net.minecraft.core.BlockPos stub = stronghold.value().findValidGenerationPoint(context)
                    .orElseThrow(() -> new AssertionError("no stub for " + position)).position();
            assertEquals(position.chunkX() << 4, stub.getX(), position + " stub x");
            assertEquals(0, stub.getY(), position + " stub y");
            assertEquals(position.chunkZ() << 4, stub.getZ(), position + " stub z");

            //? if >=26.1 {
            boolean valid = stronghold.value().generate(stronghold, Level.OVERWORLD,
                    world.registries, chunkGenerator, world.biomeSource, world.randomState, templates,
                    seed, chunk, 0, height, holder -> true).isValid();
            //?} else {
            /*boolean valid = stronghold.value().generate(world.registries, chunkGenerator,
                    world.biomeSource, world.randomState, templates, seed, chunk, 0, height,
                    holder -> true).isValid();*/
            //?}
            assertTrue(valid, position + " must generate");
        }
    }

    @Test
    void strongholdBoundsAreVanillasOwnStructureStart() throws Exception {
        // Production assembles the pieces through the stub; the oracle is vanilla's StructureStart,
        // whose box is ours put through vanilla's own adjustBoundingBox (bury: +12), with every
        // piece inside our bounds and every face reached.
        long seed = REPRESENTATIVE;
        World world = new World(seed);
        ChunkGenerator chunkGenerator = new NoiseBasedChunkGenerator(world.biomeSource, world.settings);
        LevelHeightAccessor height = LevelHeightAccessor.create(
                world.settings.value().noiseSettings().minY(),
                world.settings.value().noiseSettings().height());
        Holder<Structure> stronghold = world.registries.lookupOrThrow(Registries.STRUCTURE)
                .listElements().filter(holder -> keyName(holder.key()).equals("minecraft:stronghold"))
                .findFirst().orElseThrow(() -> new AssertionError("no stronghold structure"));
        net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates =
                data().newTemplateManager();
        BiomeWorldgenSession session =
                BiomeWorldgenSession.create(seed, BiomeWorldgenSession.OVERWORLD);
        List<StrongholdPosition> ours = StrongholdLocator.locate(session);

        long assemblyNanos = 0;
        for (int i = 0; i < 4; i++) {
            StrongholdPosition position = ours.get(i);
            long started = System.nanoTime();
            StructureGeometry geometry = StructureGeometryGenerator.generate(session,
                    StructureType.STRONGHOLD, "stronghold", position.chunkX(), position.chunkZ());
            assemblyNanos += System.nanoTime() - started;
            assertNotNull(geometry);
            assertTrue(geometry.isAvailable(), position + ": " + geometry);
            assertEquals(StrongholdLocator.generationPoint(position), geometry.generationPoint());

            ChunkPos chunk = new ChunkPos(position.chunkX(), position.chunkZ());
            //? if >=26.1 {
            StructureStart start = stronghold.value().generate(stronghold, Level.OVERWORLD,
                    world.registries, chunkGenerator, world.biomeSource, world.randomState, templates,
                    seed, chunk, 0, height, holder -> true);
            //?} else {
            /*StructureStart start = stronghold.value().generate(world.registries, chunkGenerator,
                    world.biomeSource, world.randomState, templates, seed, chunk, 0, height,
                    holder -> true);*/
            //?}
            StructureBounds bounds = geometry.bounds();
            BoundingBox adjusted = stronghold.value().adjustBoundingBox(new BoundingBox(
                    bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(),
                    bounds.maxZ()));
            BoundingBox box = start.getBoundingBox();
            assertEquals(box.minX() + "," + box.minY() + "," + box.minZ() + " .. " + box.maxX()
                            + "," + box.maxY() + "," + box.maxZ(),
                    adjusted.minX() + "," + adjusted.minY() + "," + adjusted.minZ() + " .. "
                            + adjusted.maxX() + "," + adjusted.maxY() + "," + adjusted.maxZ(),
                    position + " StructureStart box");
            boolean[] touched = new boolean[6];
            for (StructurePiece piece : start.getPieces()) {
                BoundingBox p = piece.getBoundingBox();
                assertTrue(bounds.contains(p.minX(), p.minY(), p.minZ())
                        && bounds.contains(p.maxX(), p.maxY(), p.maxZ()), position + " piece " + p);
                touched[0] |= p.minX() == bounds.minX();
                touched[1] |= p.minY() == bounds.minY();
                touched[2] |= p.minZ() == bounds.minZ();
                touched[3] |= p.maxX() == bounds.maxX();
                touched[4] |= p.maxY() == bounds.maxY();
                touched[5] |= p.maxZ() == bounds.maxZ();
            }
            for (int face = 0; face < 6; face++) {
                assertTrue(touched[face], position + ": no piece reaches face " + face);
            }
        }
        System.out.printf("stronghold geometry oracle: 4 strongholds, %.1f ms assembly each%n",
                assemblyNanos / 1e6 / 4);
    }

    @Test
    void aRingChunkTheBiomeSearchMovedAwayFromIsNotAStronghold() throws Exception {
        List<StrongholdPosition> ours = StrongholdLocator.locate(
                BiomeWorldgenSession.create(REPRESENTATIVE, BiomeWorldgenSession.OVERWORLD));
        List<int[]> vanilla = vanillaStrongholds(REPRESENTATIVE);
        int checked = 0;
        for (StrongholdPosition position : ours) {
            if (!position.isBiomeAdjusted()) {
                continue;
            }
            for (int[] chunk : vanilla) {
                assertFalse(chunk[0] == position.ringChunkX() && chunk[1] == position.ringChunkZ(),
                        "the raw ring chunk of " + position + " is in vanilla's list");
            }
            checked++;
        }
        assertTrue(checked > 0);
    }
    //?} else {
    /*private static ConcentricRingConfig vanillaRingConfig() {
        // The overworld chunk generator's own settings, not the DEFAULT constant the locator reads.
        net.minecraft.world.level.levelgen.feature.configurations.StrongholdConfiguration stronghold =
                BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD)
                        .structureSettings().stronghold();
        return new ConcentricRingConfig(stronghold.distance(), stronghold.spread(),
                stronghold.count());
    }

    private static ChunkGenerator vanillaGenerator(long seed) {
        final NoiseGeneratorSettings settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        return new NoiseBasedChunkGenerator(
                new OverworldBiomeSource(seed, false, false, BuiltinRegistries.BIOME), seed,
                () -> settings);
    }

    /^* ChunkGenerator.strongholdPositions, filled by hasStronghold's call to generateStrongholds. ^/
    @SuppressWarnings("unchecked")
    private static List<int[]> vanillaStrongholds(long seed) throws Exception {
        ChunkGenerator generator = vanillaGenerator(seed);
        generator.hasStronghold(new ChunkPos(0, 0));
        Field field = ChunkGenerator.class.getDeclaredField("strongholdPositions");
        field.setAccessible(true);
        List<int[]> pairs = new ArrayList<int[]>();
        for (ChunkPos position : (List<ChunkPos>) field.get(generator)) {
            pairs.add(new int[] {position.x, position.z});
        }
        return pairs;
    }

    @Test
    void aStrongholdGeneratesExactlyWhereItIsPlaced() throws Exception {
        // StrongholdFeature.isFeatureChunk is ChunkGenerator.hasStronghold, and the start assembles
        // until it has a portal room. Checked through vanilla's real generate() on real positions,
        // and against a ring chunk the biome search moved away from.
        long seed = REPRESENTATIVE;
        ChunkGenerator generator = vanillaGenerator(seed);
        List<StrongholdPosition> ours = StrongholdLocator.locate(
                BiomeWorldgenSession.create(seed, BiomeWorldgenSession.OVERWORLD));
        StructureFeatureConfiguration placement = BuiltinRegistries.NOISE_GENERATOR_SETTINGS
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD).structureSettings()
                .getConfig(StructureFeature.STRONGHOLD);
        assertNotNull(placement, "vanilla needs a grid configuration to start any stronghold");

        int adjustedChecked = 0;
        for (int i = 0; i < 8; i++) {
            StrongholdPosition position = ours.get(i);
            assertTrue(starts(generator, placement, seed, position.chunkX(), position.chunkZ()),
                    position + " must generate");
            if (position.isBiomeAdjusted() && adjustedChecked < 2) {
                assertFalse(starts(generator, placement, seed, position.ringChunkX(),
                        position.ringChunkZ()), "the raw ring chunk of " + position + " generated");
                adjustedChecked++;
            }
        }
        assertTrue(adjustedChecked > 0);
    }

    @Test
    void preferredBiomesAreThePossibleOnesThatAllowAStronghold() {
        // Vanilla's predicate is possibleBiomes() filtered by isValidStart, not the registry.
        java.util.Set<String> vanilla = new java.util.HashSet<String>();
        java.util.Set<String> registryOnly = new java.util.TreeSet<String>();
        for (Biome biome : vanillaGenerator(0L).getBiomeSource().possibleBiomes()) {
            if (biome.getGenerationSettings().isValidStart(StructureFeature.STRONGHOLD)) {
                vanilla.add(BuiltinRegistries.BIOME.getKey(biome).toString());
            }
        }
        for (Biome biome : BuiltinRegistries.BIOME) {
            String id = BuiltinRegistries.BIOME.getKey(biome).toString();
            if (biome.getGenerationSettings().isValidStart(StructureFeature.STRONGHOLD)
                    && !vanilla.contains(id)) {
                registryOnly.add(id);
            }
        }
        System.out.println("stronghold biomes: " + vanilla.size() + " preferred; allowed by the "
                + "registry but not possible in the overworld source: " + registryOnly);
        assertEquals(vanilla, StrongholdLocator.preferredBiomes());
    }

    @Test
    void strongholdBoundsAreVanillasOwnStructureStart() {
        // StrongholdStart is a plain StructureStart, so its box is exactly the pieces' extent.
        long seed = REPRESENTATIVE;
        ChunkGenerator generator = vanillaGenerator(seed);
        StructureFeatureConfiguration placement = BuiltinRegistries.NOISE_GENERATOR_SETTINGS
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD).structureSettings()
                .getConfig(StructureFeature.STRONGHOLD);
        BiomeWorldgenSession session =
                BiomeWorldgenSession.create(seed, BiomeWorldgenSession.OVERWORLD);
        List<StrongholdPosition> ours = StrongholdLocator.locate(session);

        long assemblyNanos = 0;
        for (int i = 0; i < 4; i++) {
            StrongholdPosition position = ours.get(i);
            long started = System.nanoTime();
            StructureGeometry geometry = StructureGeometryGenerator.generate(session,
                    StructureType.STRONGHOLD, "stronghold", position.chunkX(), position.chunkZ());
            assemblyNanos += System.nanoTime() - started;
            assertTrue(geometry.isAvailable(), position + ": " + geometry);

            Biome biome = generator.getBiomeSource().getNoiseBiome((position.chunkX() << 2) + 2, 0,
                    (position.chunkZ() << 2) + 2);
            StructureStart<?> start = StructureFeatures.STRONGHOLD.generate(RegistryAccess.builtin(),
                    generator, generator.getBiomeSource(), null, seed,
                    new ChunkPos(position.chunkX(), position.chunkZ()), biome, 0, placement);
            assertTrue(start.isValid());
            BoundingBox box = start.getBoundingBox();
            StructureBounds bounds = geometry.bounds();
            assertEquals(box.x0 + "," + box.y0 + "," + box.z0 + " .. " + box.x1 + "," + box.y1 + ","
                            + box.z1,
                    bounds.minX() + "," + bounds.minY() + "," + bounds.minZ() + " .. "
                            + bounds.maxX() + "," + bounds.maxY() + "," + bounds.maxZ(),
                    position + " StructureStart box");
            for (StructurePiece piece : start.getPieces()) {
                BoundingBox p = piece.getBoundingBox();
                assertTrue(bounds.contains(p.x0, p.y0, p.z0) && bounds.contains(p.x1, p.y1, p.z1),
                        position + " piece outside the bounds");
            }
        }
        // The first call pays for the geometry worker's own vanilla stronghold list.
        System.out.printf("stronghold geometry oracle: 4 strongholds, %.1f ms assembly each "
                + "(first includes the generator's stronghold list)%n", assemblyNanos / 1e6 / 4);
    }

    private static boolean starts(ChunkGenerator generator, StructureFeatureConfiguration placement,
                                  long seed, int chunkX, int chunkZ) {
        Biome biome = generator.getBiomeSource().getNoiseBiome((chunkX << 2) + 2, 0,
                (chunkZ << 2) + 2);
        StructureStart<?> start = StructureFeatures.STRONGHOLD.generate(RegistryAccess.builtin(),
                generator, generator.getBiomeSource(), null, seed, new ChunkPos(chunkX, chunkZ),
                biome, 0, placement);
        return start.isValid();
    }
    *///?}

    //? if >=26.1 {
    @Test
    void boundTagStateAgrees() {
        // 26.2 binds the data pack's tags, so vanilla's whole path can run untouched:
        // createForNormal with the real registry and the real placement.
        World world = new World(REPRESENTATIVE);
        ChunkGeneratorStructureState state = ChunkGeneratorStructureState.createForNormal(
                world.randomState, REPRESENTATIVE, world.biomeSource,
                world.registries.lookupOrThrow(Registries.STRUCTURE_SET));
        ConcentricRingsStructurePlacement placement =
                (ConcentricRingsStructurePlacement) strongholdSet().value().placement();
        List<int[]> vanilla = toPairs(state.getRingPositionsFor(placement));
        List<StrongholdPosition> ours = StrongholdLocator.locate(
                BiomeWorldgenSession.create(REPRESENTATIVE, BiomeWorldgenSession.OVERWORLD));

        java.util.Set<String> bound = placement.preferredBiomes().stream()
                .map(holder -> keyName(holder.unwrapKey().get())).collect(Collectors.toSet());
        assertEquals(bound, StrongholdLocator.preferredBiomes(), "preferred biomes");
        assertEquals(vanilla.size(), ours.size());
        for (int i = 0; i < ours.size(); i++) {
            assertEquals(vanilla.get(i)[0], ours.get(i).chunkX(), "#" + i);
            assertEquals(vanilla.get(i)[1], ours.get(i).chunkZ(), "#" + i);
        }
    }
    //?}
}
