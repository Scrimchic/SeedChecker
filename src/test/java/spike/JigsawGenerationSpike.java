package spike;

import java.util.ArrayList;
import java.util.List;

import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.worldgen.StructureCandidateVisitor;
import com.scrimchic.seedchecker.worldgen.StructurePlacementConfig;
import com.scrimchic.seedchecker.worldgen.StructurePlacementEngine;
import com.scrimchic.seedchecker.worldgen.StructurePlacements;
import com.scrimchic.seedchecker.worldgen.StructureType;

/**
 * Phase 3E-2c spike. Asks whether an exact jigsaw generation point is reachable at all, client
 * side, for an arbitrary seed, with no save and no server - starting with the ancient city.
 *
 * <p>The two things a jigsaw needs that biomes never did:
 *
 * <ol>
 *   <li>a real {@code RegistryAccess} carrying {@code Registries.TEMPLATE_POOL}, because
 *       {@code JigsawPlacement.addPieces} looks the start pool up in it - and
 *       {@code VanillaRegistries.createLookup()} is a {@code HolderLookup.Provider}, not a
 *       {@code RegistryAccess};</li>
 *   <li>a {@code StructureTemplateManager}, because the start piece's bounding box comes from the
 *       size of an actual structure NBT.</li>
 * </ol>
 *
 * <p>Both are attempted here out of the vanilla data pack inside the Minecraft jar. If they can be
 * built, the spike runs vanilla's own {@code findValidGenerationPoint} on real grid candidates and
 * reports where the stub actually lands relative to the candidate chunk - which is the number that
 * decides whether a bounded-column rejection could ever be proved.
 *
 * <p>Throwaway, not part of {@code build}:
 * {@code ./gradlew :1.20.1:worldgenSpike -PspikeMain=spike.JigsawGenerationSpike}.
 */
public final class JigsawGenerationSpike {

    private static final long SEED = -7407337299659424542L;

    private static String version() {
        return /*$ minecraft*/ "unknown";
    }

    public static void main(String[] args) {
        System.out.println("=== 3E-2c: exact jigsaw generation point ===");
        System.out.println("minecraft   " + version());
        System.out.println("seed        " + SEED);
        System.out.println();
        run();
    }

    //? if >=1.18 {
    private static net.minecraft.core.RegistryAccess.Frozen registries;
    private static net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates;
    private static net.minecraft.world.level.levelgen.RandomState randomState;
    private static net.minecraft.world.level.biome.BiomeSource biomeSource;
    private static net.minecraft.world.level.chunk.ChunkGenerator chunkGenerator;
    private static net.minecraft.world.level.LevelHeightAccessor heightAccessor;

    private static void run() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        net.minecraft.server.packs.resources.CloseableResourceManager resources;
        try {
            long start = System.nanoTime();
            resources = openVanillaData();
            System.out.printf("vanilla data pack     %.0f ms%n", (System.nanoTime() - start) / 1e6);
        } catch (Throwable blocked) {
            System.out.println("BLOCKER: could not open the vanilla data pack: " + blocked);
            blocked.printStackTrace(System.out);
            return;
        }

        try {
            long start = System.nanoTime();
            registries = loadWorldgenRegistries(resources);
            System.out.printf("worldgen registries   %.0f ms%n", (System.nanoTime() - start) / 1e6);
        } catch (Throwable blocked) {
            System.out.println("BLOCKER: RegistryDataLoader failed: " + blocked);
            blocked.printStackTrace(System.out);
            return;
        }

        try {
            long start = System.nanoTime();
            templates = openTemplateManager(resources);
            System.out.printf("template manager      %.0f ms%n", (System.nanoTime() - start) / 1e6);
        } catch (Throwable blocked) {
            System.out.println("BLOCKER: could not build a StructureTemplateManager: " + blocked);
            blocked.printStackTrace(System.out);
            return;
        }

        try {
            long start = System.nanoTime();
            buildWorldgen();
            System.out.printf("worldgen session      %.0f ms%n", (System.nanoTime() - start) / 1e6);
        } catch (Throwable blocked) {
            System.out.println("BLOCKER: could not build worldgen off the loaded registries: "
                    + blocked);
            blocked.printStackTrace(System.out);
            return;
        }

        reportTagsBound();
        measure(StructureType.ANCIENT_CITY, "ancient_city");
        measure(StructureType.VILLAGE, "village_plains");
        measure(StructureType.TRIAL_CHAMBER, "trial_chambers");
    }

    /** The whole vanilla data pack, as a resource manager, with no world and no server. */
    private static net.minecraft.server.packs.resources.CloseableResourceManager openVanillaData() {
        net.minecraft.server.packs.repository.PackRepository repository = vanillaRepository();
        repository.reload();
        repository.setSelected(repository.getAvailableIds());
        return new net.minecraft.server.packs.resources.MultiPackResourceManager(
                net.minecraft.server.packs.PackType.SERVER_DATA, repository.openAllSelected());
    }

    private static net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager
            openTemplateManager(net.minecraft.server.packs.resources.ResourceManager resources)
            throws Exception {
        java.nio.file.Path temp = java.nio.file.Files.createTempDirectory("seedchecker-jigsaw");
        temp.toFile().deleteOnExit();
        net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess level =
                net.minecraft.world.level.storage.LevelStorageSource
                        .createDefault(temp).createAccess("jigsaw");
        // Blocks are a static registry, not a worldgen one, so they come from BuiltInRegistries
        // rather than from what RegistryDataLoader just produced.
        return new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager(
                resources, level, net.minecraft.util.datafix.DataFixers.getDataFixer(),
                blockLookup());
    }

    /**
     * Worldgen built off the <em>loaded</em> registries rather than {@code VanillaRegistries}, so
     * the biome objects a structure's tag resolves to are the same instances the biome source hands
     * back. Mixing the two would compare biomes by identity across two registry sets and quietly
     * never match.
     */
    private static void buildWorldgen() {
        net.minecraft.core.Holder<net.minecraft.world.level.levelgen.NoiseGeneratorSettings> settings =
                registries.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                        .getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);
        randomState = net.minecraft.world.level.levelgen.RandomState.create(
                settings.value(),
                registries.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), SEED);
        biomeSource = net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(
                registries.lookupOrThrow(net.minecraft.core.registries.Registries
                                .MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(net.minecraft.world.level.biome
                                .MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        chunkGenerator = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(
                biomeSource, settings);
        heightAccessor = net.minecraft.world.level.LevelHeightAccessor.create(
                settings.value().noiseSettings().minY(), settings.value().noiseSettings().height());
    }

    /**
     * Whether the biome tags actually bound. If they did, vanilla's own
     * {@code structure.biomes()::contains} can be used as the predicate, which makes the comparison
     * below a full oracle rather than a half one.
     */
    private static void reportTagsBound() {
        try {
            net.minecraft.world.level.levelgen.structure.Structure city = structure("ancient_city");
            int size = city.biomes().size();
            System.out.println("biome tags bound      yes, ancient_city accepts " + size
                    + " biomes");
        } catch (Throwable unbound) {
            System.out.println("biome tags bound      NO: " + unbound);
        }
    }

    private static net.minecraft.world.level.levelgen.structure.Structure structure(String name) {
        return registries.lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .listElements()
                .filter(holder -> holder.unwrapKey().isPresent()
                        && keyName(holder.unwrapKey().get()).endsWith(":" + name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no structure " + name))
                .value();
    }

    private static String keyName(net.minecraft.resources.ResourceKey<?> key) {
        //? if >=26.1 {
        return key.identifier().toString();
        //?} else {
        /*return key.location().toString();*/
        //?}
    }

    private static void measure(StructureType type, String variant) {
        StructurePlacements placements = StructurePlacements.forThisVersion();
        if (!placements.supports(type)) {
            return;
        }
        System.out.println();
        System.out.println("--- " + type.name().toLowerCase() + " / " + variant + " ---");

        final net.minecraft.world.level.levelgen.structure.Structure structure;
        try {
            structure = structure(variant);
        } catch (Throwable missing) {
            System.out.println("  not in this version: " + missing);
            return;
        }

        java.util.Set<String> accepted = com.scrimchic.seedchecker.platform.StructureBiomeValidator
                .acceptedBiomes(type, variant);

        List<int[]> chunks = candidates(placements.get(type), 400);
        int assembled = 0;
        int biomeValid = 0;
        int failed = 0;
        int maxOffsetX = 0;
        int maxOffsetZ = 0;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        long totalNanos = 0;
        int printed = 0;

        for (int[] chunk : chunks) {
            long start = System.nanoTime();
            java.util.Optional<net.minecraft.world.level.levelgen.structure.Structure.GenerationStub> stub;
            try {
                // An always-true biome predicate, so the two halves of vanilla's answer can be
                // counted apart: whether the jigsaw assembled a start piece at all, and whether the
                // biome at the position it chose is accepted.
                stub = structure.findValidGenerationPoint(context(chunk[0], chunk[1]));
            } catch (Throwable failure) {
                if (failed == 0) {
                    System.out.println("  BLOCKER at chunk " + chunk[0] + "," + chunk[1] + ": "
                            + failure);
                    failure.printStackTrace(System.out);
                }
                failed++;
                continue;
            }
            totalNanos += System.nanoTime() - start;

            if (!stub.isPresent()) {
                continue;
            }
            assembled++;
            net.minecraft.core.BlockPos position = stub.get().position();
            int centreX = (chunk[0] << 4) + 8;
            int centreZ = (chunk[1] << 4) + 8;
            maxOffsetX = Math.max(maxOffsetX, Math.abs(position.getX() - centreX));
            maxOffsetZ = Math.max(maxOffsetZ, Math.abs(position.getZ() - centreZ));
            minY = Math.min(minY, position.getY());
            maxY = Math.max(maxY, position.getY());

            String biomeId = biomeIdAt(position);
            if (accepted.contains(biomeId)) {
                biomeValid++;
            }
            if (printed < 5) {
                System.out.printf("  chunk %6d,%6d  stub %7d,%5d,%7d  offset %+4d,%+4d  %s%n",
                        chunk[0], chunk[1], position.getX(), position.getY(), position.getZ(),
                        position.getX() - centreX, position.getZ() - centreZ, biomeId);
                printed++;
            }
        }

        System.out.printf("  candidates %d, start piece assembled %d, biome accepted %d, threw %d%n",
                chunks.size(), assembled, biomeValid, failed);
        System.out.printf("  max |offset| from chunk centre: X %d, Z %d blocks%n",
                maxOffsetX, maxOffsetZ);
        if (assembled > 0) {
            System.out.printf("  stub Y range: %d .. %d%n", minY, maxY);
        }
        System.out.printf("  %.2f ms per candidate%n",
                totalNanos / 1e6 / Math.max(1, chunks.size()));
    }

    private static String biomeIdAt(net.minecraft.core.BlockPos position) {
        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome =
                biomeSource.getNoiseBiome(
                        net.minecraft.core.QuartPos.fromBlock(position.getX()),
                        net.minecraft.core.QuartPos.fromBlock(position.getY()),
                        net.minecraft.core.QuartPos.fromBlock(position.getZ()),
                        randomState.sampler());
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> key =
                biome.unwrapKey().orElse(null);
        return key == null ? "seedchecker:unnamed" : keyName(key);
    }

    /**
     * Vanilla's real generation context, with a biome predicate that accepts everything.
     *
     * <p>The tags did not bind through {@code RegistryDataLoader} alone, so
     * {@code structure.biomes()} is empty and using it would reject every candidate for the wrong
     * reason. Accepting everything isolates the part this spike is actually about - where the stub
     * lands - and the biome is then tested separately against the sets Phase 3E-1 already checked
     * against vanilla's own tag names.
     */
    private static net.minecraft.world.level.levelgen.structure.Structure.GenerationContext context(
            int chunkX, int chunkZ) {
        return new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(
                registries, chunkGenerator, biomeSource, randomState, templates, SEED,
                new net.minecraft.world.level.ChunkPos(chunkX, chunkZ), heightAccessor,
                holder -> true);
    }

    private static List<int[]> candidates(StructurePlacementConfig config, int wanted) {
        final List<int[]> chunks = new ArrayList<int[]>();
        int radius = 64;
        while (chunks.size() < wanted && radius <= 8192) {
            chunks.clear();
            new StructurePlacementEngine().forEachCandidate(SEED, config,
                    ChunkRange.of(-radius, -radius, radius, radius), wanted,
                    new StructureCandidateVisitor() {
                        @Override
                        public boolean visit(int chunkX, int chunkZ) {
                            chunks.add(new int[] {chunkX, chunkZ});
                            return true;
                        }
                    });
            radius *= 2;
        }
        return chunks;
    }

    // ------------------------------------------------- version-specific registry loading

    //? if >=26.1 {
    /** 26.x made Registry itself a HolderLookup.RegistryLookup, so no conversion is needed. */
    private static net.minecraft.core.HolderGetter<net.minecraft.world.level.block.Block> blockLookup() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK;
    }

    private static net.minecraft.server.packs.repository.PackRepository vanillaRepository() {
        return net.minecraft.server.packs.repository.ServerPacksSource.createVanillaTrustedRepository();
    }

    /**
     * 26.x turned the loader asynchronous, takes the static registries as a list of lookups rather
     * than as a {@code RegistryAccess}, and - the part that actually bites - validates block tags
     * while loading worldgen data, so the static tags have to be bound first or the load dies on
     * {@code Missing tag: 'minecraft:features_cannot_replace'}.
     *
     * <p>This is vanilla's own sequence out of {@code WorldLoader}, minus the world. Run on the
     * calling thread, because there is nothing else to do while it loads.
     */
    private static net.minecraft.core.RegistryAccess.Frozen loadWorldgenRegistries(
            net.minecraft.server.packs.resources.ResourceManager resources) {
        net.minecraft.core.LayeredRegistryAccess<net.minecraft.server.RegistryLayer> layers =
                net.minecraft.server.RegistryLayer.createRegistryAccess();
        List<net.minecraft.core.Registry.PendingTags<?>> pendingTags =
                net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(
                        resources, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
        List<net.minecraft.core.HolderLookup.RegistryLookup<?>> lookups =
                net.minecraft.tags.TagLoader.buildUpdatedLookups(
                        layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN),
                        pendingTags);
        return net.minecraft.resources.RegistryDataLoader.load(
                resources, lookups, net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES,
                Runnable::run).join();
    }
    //?} else {
    /*private static net.minecraft.core.HolderGetter<net.minecraft.world.level.block.Block> blockLookup() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup();
    }

    private static net.minecraft.server.packs.repository.PackRepository vanillaRepository() {
        return new net.minecraft.server.packs.repository.PackRepository(
                new net.minecraft.server.packs.repository.ServerPacksSource());
    }

    private static net.minecraft.core.RegistryAccess.Frozen loadWorldgenRegistries(
            net.minecraft.server.packs.resources.ResourceManager resources) {
        net.minecraft.core.RegistryAccess.Frozen builtin =
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                        net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        return net.minecraft.resources.RegistryDataLoader.load(
                resources, builtin,
                net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
    }*/
    //?}
    //?} else {
    /*private static void run() {
        System.out.println("Not applicable: 1.16.5 jigsaw validity is already exact (3E-2b).");
    }*///?}
}
