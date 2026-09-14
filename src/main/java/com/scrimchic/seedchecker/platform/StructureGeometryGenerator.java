package com.scrimchic.seedchecker.platform;

import com.scrimchic.seedchecker.worldgen.StructureGeometry;
import com.scrimchic.seedchecker.worldgen.StructureType;

//? if >=1.18 {
import com.scrimchic.seedchecker.core.util.LazyInit;
//?} else {
/*import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Supplier;

import com.scrimchic.seedchecker.worldgen.StructureBounds;

import net.minecraft.core.RegistryAccess;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.data.worldgen.StructureFeatures;
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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.level.storage.LevelStorageSource;*/
//?}

/**
 * The full extent of an exactly validated structure, assembled by vanilla.
 *
 * <h2>1.18 onwards</h2>
 *
 * <p>No {@code StructureStart}. A jigsaw's {@code GenerationStub} already carries its piece
 * assembly, deferred; {@code getPiecesBuilder()} runs it, {@code build()} hands the pieces over, and
 * {@code PiecesContainer.calculateBoundingBox()} encapsulates every piece's box. That is the whole
 * of what {@code StructureStart.getBoundingBox()} does too, except that for a structure with terrain
 * adaptation (village, ancient city, trial chamber) it inflates the result by 12 blocks - the reach
 * of the terrain beard, not of the structure - so that inflation is left out here.
 *
 * <h2>1.16.5</h2>
 *
 * <p>No stub exists; pieces are built into a {@code StructureStart} by {@code generatePieces}, which
 * is the only path, so a start is built through {@code ConfiguredStructureFeature.generate} exactly
 * as {@code ChunkGenerator.createStructures} would, and the extent of its pieces is read back. Not
 * the start's own box: the village's start is a {@code BeardedStructureStart}, whose
 * {@code calculateBoundingBox} inflates the pieces' extent by 12 - the same beard inflation modern
 * versions moved into {@code Structure.adjustBoundingBox} - so both versions report the same
 * thing.
 *
 * <p>On 1.16.5 the pillager outpost (another bearded jigsaw) and the mineshaft (a plain start its
 * own {@code generatePieces} moves below sea level) are built and measured the same way; from 1.18
 * the outpost, the trail ruins and the mineshaft come out of their stubs like the other jigsaws.
 *
 * <h2>Where there is no exact geometry</h2>
 *
 * <p>The desert pyramid, the jungle temple, the swamp hut and the igloo on every version, and the
 * shipwreck, the ocean ruins and the buried treasure on 1.16.5, are built at a placeholder height -
 * y 64 or y 90 - that vanilla only corrects while placing them into the world, from the heightmap of
 * chunks that have already been generated ({@code updateHeightPositionToLowestGroundHeight},
 * {@code updateAverageGroundHeight}, {@code WorldGenLevel.getHeight}). Their horizontal footprint is
 * exact, their vertical extent is not knowable without generating chunks, so no bounds are claimed
 * for them. The modern shipwreck, ocean ruins and buried treasure are not exactly validated in the
 * first place.
 */
public final class StructureGeometryGenerator {

    private static final String NOT_EXACT = "not computed for a non-exact structure";

    private static final String HEIGHT_AT_PLACEMENT =
            "its height is only fixed when the chunk generates";

    private static final String NO_START = "vanilla assembled no structure start";

    private StructureGeometryGenerator() {
    }

    //? if >=1.18 {
    private static final String DATA_UNAVAILABLE = "vanilla structure data could not be loaded";

    /**
     * Assembles the structure vanilla builds at that candidate and measures it.
     *
     * <p>Worker threads only: a village takes about a second.
     *
     * @param variant the entry validation found vanilla builds, e.g. {@code village_plains}
     * @return the geometry, or {@code null} while vanilla structure data is still loading on another
     *         worker
     */
    public static StructureGeometry generate(BiomeWorldgenSession session, StructureType type,
                                             String variant, int chunkX, int chunkZ) {
        // The stronghold is placed by its own ring list; its pieces are assembled through the same
        // stub path, and StrongholdStructure.findGenerationPoint never refuses.
        String structureId = type == StructureType.STRONGHOLD ? "minecraft:stronghold"
                : variant == null ? null : StructureBiomeValidator.jigsawStructureId(type, variant);
        if (structureId == null) {
            return StructureGeometry.unavailable(
                    isBoundedOnly(type) ? NOT_EXACT : HEIGHT_AT_PLACEMENT);
        }
        LazyInit.State state = session.prepareJigsaw();
        if (state == LazyInit.State.FAILED) {
            return StructureGeometry.unavailable(DATA_UNAVAILABLE);
        }
        if (state != LazyInit.State.READY) {
            return null;
        }
        StructureGeometry geometry = session.jigsawGeometry(structureId, chunkX, chunkZ);
        return geometry != null ? geometry : StructureGeometry.unavailable(NO_START);
    }

    /** The structures validation only bounds on this version, so no start was reproduced. */
    private static boolean isBoundedOnly(StructureType type) {
        return type == StructureType.SHIPWRECK || type == StructureType.OCEAN_RUIN
                || type == StructureType.BURIED_TREASURE;
    }
    //?} else {
    /*/^* Per worker: the template manager's repository is a plain HashMap on this version. ^/
    private static final ThreadLocal<LegacyStructureWorld> WORLDS =
            new ThreadLocal<LegacyStructureWorld>();

    private static boolean structuresBootstrapped;

    /^*
     * Builds the start vanilla builds at that candidate and reads its bounding box. Only the
     * village, the pillager outpost, the mineshaft and the stronghold have exact geometry here; see
     * the class comment for the others.
     *
     * @return the geometry; never {@code null} on this version, which has no data to wait for
     ^/
    public static StructureGeometry generate(BiomeWorldgenSession session, StructureType type,
                                             String variant, int chunkX, int chunkZ) {
        if (type == StructureType.STRONGHOLD) {
            return strongholdGeometry(session, chunkX, chunkZ);
        }
        StructureFeature<?> feature = startTimeGeometryFeature(type);
        if (feature == null) {
            return StructureGeometry.unavailable(HEIGHT_AT_PLACEMENT);
        }
        LegacyStructureWorld world = worldFor(session);
        OverworldBiomeSource biomeSource = session.legacyBiomeSource();
        Biome biome = biomeSource.getNoiseBiome((chunkX << 2) + 2, 0, (chunkZ << 2) + 2);

        for (Supplier<ConfiguredStructureFeature<?, ?>> supplier
                : biome.getGenerationSettings().structures()) {
            ConfiguredStructureFeature<?, ?> configured = supplier.get();
            if (configured.feature != feature) {
                continue;
            }
            StructureStart<?> start = configured.generate(world.registries, world.chunkGenerator,
                    biomeSource, world.templates, session.seed(), new ChunkPos(chunkX, chunkZ),
                    biome, 0, StructureSettings.DEFAULTS.get(feature));
            if (!start.isValid()) {
                return StructureGeometry.unavailable(NO_START);
            }
            // This version computes no generation point. A jigsaw start's own box is inflated by 12
            // for the beard, so the pieces are measured directly instead.
            return StructureGeometry.of(null, piecesExtent(start));
        }
        return StructureGeometry.unavailable(NO_START);
    }

    /^*
     * The structures whose pieces already stand at their final height once the start is built: the
     * jigsaws, projected onto the terrain by JigsawPlacement, and the mineshaft, which its own
     * generatePieces moves below sea level. Every other structure here waits for the chunk.
     ^/
    private static StructureFeature<?> startTimeGeometryFeature(StructureType type) {
        switch (type) {
            case VILLAGE:
                return StructureFeature.VILLAGE;
            case PILLAGER_OUTPOST:
                return StructureFeature.PILLAGER_OUTPOST;
            case MINESHAFT:
                return StructureFeature.MINESHAFT;
            default:
                return null;
        }
    }

    /^*
     * A stronghold's start, built as ChunkGenerator.createStructures builds it: the configured
     * stronghold, the generator's own grid configuration for it, and isFeatureChunk answered by the
     * generator's own vanilla stronghold list - computed once per worker session on first use.
     ^/
    private static StructureGeometry strongholdGeometry(BiomeWorldgenSession session, int chunkX,
                                                        int chunkZ) {
        LegacyStructureWorld world = worldFor(session);
        OverworldBiomeSource biomeSource = session.legacyBiomeSource();
        Biome biome = biomeSource.getNoiseBiome((chunkX << 2) + 2, 0, (chunkZ << 2) + 2);
        StructureStart<?> start = StructureFeatures.STRONGHOLD.generate(world.registries,
                world.chunkGenerator, biomeSource, world.templates, session.seed(),
                new ChunkPos(chunkX, chunkZ), biome, 0,
                BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD)
                        .structureSettings().getConfig(StructureFeature.STRONGHOLD));
        if (!start.isValid()) {
            return StructureGeometry.unavailable(NO_START);
        }
        return StructureGeometry.of(null, piecesExtent(start));
    }

    /^* The extent of a start's pieces, independent of whatever inflation its own box carries. ^/
    private static StructureBounds piecesExtent(StructureStart<?> start) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            minX = Math.min(minX, box.x0);
            minY = Math.min(minY, box.y0);
            minZ = Math.min(minZ, box.z0);
            maxX = Math.max(maxX, box.x1);
            maxY = Math.max(maxY, box.y1);
            maxZ = Math.max(maxZ, box.z1);
        }
        return new StructureBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static final class LegacyStructureWorld {
        private final BiomeWorldgenSession session;
        private final RegistryAccess registries;
        private final ChunkGenerator chunkGenerator;
        private final StructureManager templates;

        LegacyStructureWorld(BiomeWorldgenSession session, RegistryAccess registries,
                             ChunkGenerator chunkGenerator, StructureManager templates) {
            this.session = session;
            this.registries = registries;
            this.chunkGenerator = chunkGenerator;
            this.templates = templates;
        }
    }

    private static LegacyStructureWorld worldFor(BiomeWorldgenSession session) {
        LegacyStructureWorld held = WORLDS.get();
        if (held != null && held.session == session) {
            return held;
        }
        ensureStructuresBootstrapped();
        final NoiseGeneratorSettings settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        ChunkGenerator chunkGenerator = new NoiseBasedChunkGenerator(session.legacyBiomeSource(),
                session.seed(), () -> settings);
        LegacyStructureWorld created = new LegacyStructureWorld(session, RegistryAccess.builtin(),
                chunkGenerator, openTemplateManager());
        WORLDS.set(created);
        return created;
    }

    /^* The village's template pools are registered by this, and must be before a start is built. ^/
    private static synchronized void ensureStructuresBootstrapped() {
        if (!structuresBootstrapped) {
            StructureFeature.bootstrap();
            structuresBootstrapped = true;
        }
    }

    /^*
     * A template manager over the vanilla data pack. Its constructor reads the save's generated
     * structures path from the level access and keeps only the path, so a throwaway access over a
     * temporary directory is opened, closed and deleted at once.
     ^/
    private static StructureManager openTemplateManager() {
        try {
            SimpleReloadableResourceManager resources =
                    new SimpleReloadableResourceManager(PackType.SERVER_DATA);
            resources.add(new VanillaPackResources("minecraft"));
            Path temporary = Files.createTempDirectory("seedchecker-templates");
            try {
                LevelStorageSource.LevelStorageAccess access =
                        LevelStorageSource.createDefault(temporary).createAccess("templates");
                try {
                    return new StructureManager(resources, access, DataFixers.getDataFixer());
                } finally {
                    access.close();
                }
            } finally {
                deleteRecursively(temporary);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void deleteRecursively(Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                        throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException failure)
                        throws IOException {
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // A leftover empty temp directory is not worth failing geometry over.
        }
    }
    *///?}
}
