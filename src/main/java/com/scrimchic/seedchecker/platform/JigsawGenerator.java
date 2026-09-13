package com.scrimchic.seedchecker.platform;

//? if >=1.18 {
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.scrimchic.seedchecker.worldgen.GenerationPoint;
import com.scrimchic.seedchecker.worldgen.StructureBounds;
import com.scrimchic.seedchecker.worldgen.StructureGeometry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
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
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
//?}

/**
 * Vanilla's own jigsaw generation point, for one seed, owned by one worker.
 *
 * <p>Everything seed dependent that {@code Structure.findValidGenerationPoint} needs, built from
 * {@link VanillaStructureData}'s registries and from nothing else, so no object here is ever
 * compared with one from the biome tile session's registries. Not thread safe - the template
 * manager's templates are not - and never shared: {@link BiomeWorldgenSession} builds one lazily
 * and it dies with the session.
 */
final class JigsawGenerator {

    //? if >=1.18 {
    /**
     * Accepts every biome, so the stub comes back wherever vanilla puts it.
     *
     * <p>The biome is then tested separately, against the datapack biome sets Phase 3E-1 checked
     * against vanilla's tag names. That keeps the answer the same on 1.20.1, where the loaded
     * registries leave biome tags unbound, and on 26.2, where they are bound.
     */
    private static final Predicate<Holder<Biome>> ANY_BIOME = holder -> true;

    private final VanillaStructureData data;
    private final RegistryAccess registries;
    private final StructureTemplateManager templates;
    private final long seed;
    private final RandomState randomState;
    private final BiomeSource biomeSource;
    private final ChunkGenerator chunkGenerator;
    private final LevelHeightAccessor heightAccessor;

    JigsawGenerator(VanillaStructureData data, long seed) throws IOException {
        this.data = data;
        this.registries = data.registries();
        this.templates = data.newTemplateManager();
        this.seed = seed;

        Holder<NoiseGeneratorSettings> settings = registries
                .lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        this.randomState = RandomState.create(settings.value(),
                registries.lookupOrThrow(Registries.NOISE), seed);
        this.biomeSource = MultiNoiseBiomeSource.createFromPreset(
                registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        this.chunkGenerator = new NoiseBasedChunkGenerator(biomeSource, settings);
        this.heightAccessor = LevelHeightAccessor.create(
                settings.value().noiseSettings().minY(), settings.value().noiseSettings().height());
    }

    /**
     * Where vanilla would build this structure from, if it builds it at all.
     *
     * <p>Runs {@code JigsawStructure.findGenerationPoint} for real: the start pool draw, the
     * rotation, the template's size, a named start jigsaw, and - for the village - the projection
     * onto the terrain. The pieces themselves are not assembled; vanilla defers that into the stub.
     *
     * @return the stub position, or {@code null} when vanilla produced no start piece
     */
    GenerationPoint generationPoint(String structureId, int chunkX, int chunkZ) {
        Optional<Structure.GenerationStub> stub = stub(structureId, chunkX, chunkZ);
        if (!stub.isPresent()) {
            return null;
        }
        BlockPos position = stub.get().position();
        return new GenerationPoint(position.getX(), position.getY(), position.getZ());
    }

    /**
     * The full extent of the structure vanilla assembles from that generation point.
     *
     * <p>{@code getPiecesBuilder()} runs the assembly vanilla deferred into the stub - for a jigsaw,
     * every piece out to its size and maximum distance - and {@code calculateBoundingBox()}
     * encapsulates the pieces' boxes, which is exactly what {@code StructureStart.getBoundingBox()}
     * computes before inflating it by 12 for terrain adaptation. No start is built and no chunk is
     * touched. Expensive: a plains village measured about a second.
     *
     * @return the geometry, or {@code null} when vanilla produced no start piece
     */
    StructureGeometry geometry(String structureId, int chunkX, int chunkZ) {
        Optional<Structure.GenerationStub> stub = stub(structureId, chunkX, chunkZ);
        if (!stub.isPresent()) {
            return null;
        }
        BlockPos position = stub.get().position();
        BoundingBox box = stub.get().getPiecesBuilder().build().calculateBoundingBox();
        return StructureGeometry.of(
                new GenerationPoint(position.getX(), position.getY(), position.getZ()),
                new StructureBounds(box.minX(), box.minY(), box.minZ(),
                        box.maxX(), box.maxY(), box.maxZ()));
    }

    /**
     * Each call builds a fresh context and with it a fresh {@code WorldgenRandom} seeded from the
     * world seed and chunk, as vanilla does per attempt - which is why the geometry assembled later
     * starts from the very same draw the validation did.
     */
    private Optional<Structure.GenerationStub> stub(String structureId, int chunkX, int chunkZ) {
        Structure structure = data.structure(structureId);
        if (structure == null) {
            throw new IllegalStateException("the vanilla data pack has no structure " + structureId);
        }
        return structure.findValidGenerationPoint(
                new Structure.GenerationContext(registries, chunkGenerator, biomeSource, randomState,
                        templates, seed, new ChunkPos(chunkX, chunkZ), heightAccessor, ANY_BIOME));
    }

    /** The biome {@code Structure.isValidBiome} would test at that point. */
    String biomeIdAt(GenerationPoint point) {
        Holder<Biome> biome = biomeSource.getNoiseBiome(QuartPos.fromBlock(point.x()),
                QuartPos.fromBlock(point.y()), QuartPos.fromBlock(point.z()), randomState.sampler());
        ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
        if (key == null) {
            return "seedchecker:unnamed";
        }
        //? if >=26.1 {
        return key.identifier().toString();
        //?} else {
        /*return key.location().toString();*/
        //?}
    }

    /**
     * The order vanilla tries a multi-entry structure set's entries in, for one chunk.
     *
     * <p>{@code ChunkGenerator.createStructures}, for a set of more than one entry: a
     * {@code WorldgenRandom} over {@code LegacyRandomSource(0)}, reseeded with
     * {@code setLargeFeatureSeed(levelSeed, chunkX, chunkZ)}, then repeatedly {@code nextInt} of the
     * remaining total weight, walked down the remaining list until it drops below zero; the entry
     * found is tried, and on failure removed and its weight subtracted. Identical on 1.20.1 and
     * 26.2.
     *
     * <p>Every round draws exactly one number whatever the attempt's outcome, so the whole order can
     * be computed up front, and the first entry in it that generates is exactly the one vanilla
     * builds.
     *
     * @return indices into {@code weights}, in trial order
     */
    static int[] selectionOrder(int[] weights, long seed, int chunkX, int chunkZ) {
        List<Integer> remaining = new ArrayList<Integer>(weights.length);
        int total = 0;
        for (int i = 0; i < weights.length; i++) {
            remaining.add(i);
            total += weights[i];
        }
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunkX, chunkZ);

        int[] order = new int[weights.length];
        int next = 0;
        while (!remaining.isEmpty()) {
            int roll = random.nextInt(total);
            int position = 0;
            for (Integer index : remaining) {
                roll -= weights[index];
                if (roll < 0) {
                    break;
                }
                position++;
            }
            int chosen = remaining.remove(position);
            order[next++] = chosen;
            total -= weights[chosen];
        }
        return order;
    }
    //?} else {
    /*private JigsawGenerator() {
    }
    *///?}
}
