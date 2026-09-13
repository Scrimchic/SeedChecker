package spike;

import java.util.ArrayList;
import java.util.List;

import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.platform.BiomeWorldgenSession;
import com.scrimchic.seedchecker.platform.StructureBiomeValidator;
import com.scrimchic.seedchecker.worldgen.StructurePlacementConfig;
import com.scrimchic.seedchecker.worldgen.StructurePlacementEngine;
import com.scrimchic.seedchecker.worldgen.StructurePlacements;
import com.scrimchic.seedchecker.worldgen.StructureType;

/**
 * Phase 3E-2b. Asks one question about 1.16.5, end to end against vanilla:
 *
 * <pre>
 * biome-valid == actual start possible ?
 * </pre>
 *
 * <p>The biome half is already known to be exact. What is not known is whether anything
 * <em>after</em> the biome check can still reject a candidate - {@code isFeatureChunk}, a terrain
 * condition, or a {@code generatePieces} that produces nothing. So this runs vanilla's real
 * {@code ConfiguredStructureFeature.generate} on every candidate and compares the answer with ours,
 * rather than reasoning about the bytecode.
 *
 * <p>Throwaway, not part of {@code build}:
 * {@code ./gradlew :1.16.5:worldgenSpike -PspikeMain=spike.StructureValidityAuditSpike}.
 */
public final class StructureValidityAuditSpike {

    private static final long SEED = -7407337299659424542L;

    /** Wide enough that every biome a structure cares about actually turns up. */
    private static final int RADIUS_CHUNKS = 2000;

    private static String version() {
        return /*$ minecraft*/ "unknown";
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 3E-2b: structure generation validity audit ===");
        System.out.println("minecraft   " + version());
        System.out.println("seed        " + SEED);
        System.out.println("area        chunks +-" + RADIUS_CHUNKS);
        System.out.println();
        run();
    }

    //? if <1.18 {
    /*private static void run() throws Exception {
        net.minecraft.server.Bootstrap.bootStrap();
        net.minecraft.world.level.levelgen.feature.StructureFeature.bootstrap();

        net.minecraft.core.RegistryAccess registries = net.minecraft.core.RegistryAccess.builtin();
        net.minecraft.core.Registry<net.minecraft.world.level.biome.Biome> biomeRegistry =
                net.minecraft.data.BuiltinRegistries.BIOME;
        final net.minecraft.world.level.biome.OverworldBiomeSource biomeSource =
                new net.minecraft.world.level.biome.OverworldBiomeSource(
                        SEED, false, false, biomeRegistry);

        // A real chunk generator, because a terrain-dependent check - if one existed - would go
        // through it. Cheap here: no chunk is ever generated.
        final net.minecraft.world.level.levelgen.NoiseGeneratorSettings noiseSettings =
                net.minecraft.data.BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(
                        net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);
        net.minecraft.world.level.chunk.ChunkGenerator chunkGenerator =
                new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(
                        biomeSource, SEED,
                        new java.util.function.Supplier<net.minecraft.world.level.levelgen.NoiseGeneratorSettings>() {
                            @Override
                            public net.minecraft.world.level.levelgen.NoiseGeneratorSettings get() {
                                return noiseSettings;
                            }
                        });

        net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager templates =
                openTemplateManager();
        System.out.println("template manager: " + (templates == null ? "UNAVAILABLE" : "ready"));

        reportIsFeatureChunkOverrides();
        reportNoiseAffecting();

        StructurePlacements placements = StructurePlacements.forThisVersion();
        BiomeWorldgenSession session =
                BiomeWorldgenSession.create(SEED, BiomeWorldgenSession.OVERWORLD);

        System.out.println();
        System.out.printf("%-16s %10s %10s %10s %12s %12s%n", "structure", "candidates",
                "biome ok", "vanilla ok", "ours-only", "vanilla-only");

        for (StructureType type : StructureType.values()) {
            if (!placements.supports(type)) {
                continue;
            }
            net.minecraft.world.level.levelgen.feature.StructureFeature<?> feature = featureOf(type);
            if (feature == null) {
                continue;
            }

            int candidates = 0;
            int biomeOk = 0;
            int vanillaOk = 0;
            int oursOnly = 0;
            int vanillaOnly = 0;

            for (int[] chunk : candidateChunks(placements.get(type))) {
                candidates++;
                boolean ours = StructureBiomeValidator
                        .validate(session, type, chunk[0], chunk[1]).isCompatible();
                boolean vanilla = vanillaGenerates(registries, chunkGenerator, biomeSource,
                        templates, feature, chunk[0], chunk[1]);
                if (ours) {
                    biomeOk++;
                }
                if (vanilla) {
                    vanillaOk++;
                }
                if (ours && !vanilla) {
                    if (oursOnly < 5) {
                        System.out.println("  MISMATCH ours-only " + type + " at "
                                + chunk[0] + "," + chunk[1]);
                    }
                    oursOnly++;
                }
                if (vanilla && !ours) {
                    if (vanillaOnly < 5) {
                        System.out.println("  MISMATCH vanilla-only " + type + " at "
                                + chunk[0] + "," + chunk[1]);
                    }
                    vanillaOnly++;
                }
            }

            System.out.printf("%-16s %10d %10d %10d %12d %12d%n", type.name().toLowerCase(),
                    candidates, biomeOk, vanillaOk, oursOnly, vanillaOnly);
        }
    }

    /^*
     * Vanilla's own answer, assembled the way {@code ChunkGenerator.createStructures} does it:
     * sample the biome at the fixed quart position, walk that biome's configured structures, and
     * run the one we are asking about. A start that comes back invalid means something after the
     * biome check rejected the candidate.
     ^/
    private static boolean vanillaGenerates(
            net.minecraft.core.RegistryAccess registries,
            net.minecraft.world.level.chunk.ChunkGenerator chunkGenerator,
            net.minecraft.world.level.biome.BiomeSource biomeSource,
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager templates,
            net.minecraft.world.level.levelgen.feature.StructureFeature<?> feature,
            int chunkX, int chunkZ) {

        net.minecraft.world.level.biome.Biome biome = biomeSource.getNoiseBiome(
                (chunkX << 2) + 2, 0, (chunkZ << 2) + 2);

        for (java.util.function.Supplier<net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature<?, ?>> supplier
                : biome.getGenerationSettings().structures()) {
            net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature<?, ?> configured =
                    supplier.get();
            if (configured.feature != feature) {
                continue;
            }
            net.minecraft.world.level.levelgen.feature.configurations.StructureFeatureConfiguration placement =
                    net.minecraft.world.level.levelgen.StructureSettings.DEFAULTS.get(feature);
            if (placement == null) {
                return false;
            }
            net.minecraft.world.level.levelgen.structure.StructureStart<?> start = configured.generate(
                    registries, chunkGenerator, biomeSource, templates, SEED,
                    new net.minecraft.world.level.ChunkPos(chunkX, chunkZ), biome, 0, placement);
            return start.isValid();
        }
        return false;
    }

    /^*
     * Whether any of the three overrides {@code isFeatureChunk}, resolved through the real class
     * hierarchy rather than assumed. An override is the obvious place an extra predicate would
     * live.
     ^/
    private static void reportIsFeatureChunkOverrides() {
        System.out.println();
        System.out.println("isFeatureChunk declared by:");
        for (StructureType type : StructureType.values()) {
            net.minecraft.world.level.levelgen.feature.StructureFeature<?> feature = featureOf(type);
            if (feature == null) {
                continue;
            }
            Class<?> declaring = null;
            for (Class<?> c = feature.getClass(); c != null; c = c.getSuperclass()) {
                for (java.lang.reflect.Method method : c.getDeclaredMethods()) {
                    if (method.getName().equals("isFeatureChunk")) {
                        declaring = c;
                        break;
                    }
                }
                if (declaring != null) {
                    break;
                }
            }
            System.out.printf("  %-16s %s%n", type.name().toLowerCase(),
                    declaring == null ? "nowhere" : declaring.getName());
        }
    }

    /^* 1.16.5's analogue of NoiseAffectingStructureFeature is a static list; report membership. ^/
    private static void reportNoiseAffecting() {
        System.out.println();
        System.out.println("in StructureFeature.NOISE_AFFECTING_FEATURES:");
        for (StructureType type : StructureType.values()) {
            net.minecraft.world.level.levelgen.feature.StructureFeature<?> feature = featureOf(type);
            if (feature == null) {
                continue;
            }
            System.out.printf("  %-16s %s%n", type.name().toLowerCase(),
                    net.minecraft.world.level.levelgen.feature.StructureFeature
                            .NOISE_AFFECTING_FEATURES.contains(feature));
        }
    }

    /^*
     * A template manager over the vanilla data pack. Shipwreck and village both load a template
     * while building their start, so without one those two cannot be asked at all.
     ^/
    private static net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager
            openTemplateManager() {
        try {
            net.minecraft.server.packs.resources.SimpleReloadableResourceManager resources =
                    new net.minecraft.server.packs.resources.SimpleReloadableResourceManager(
                            net.minecraft.server.packs.PackType.SERVER_DATA);
            resources.add(new net.minecraft.server.packs.VanillaPackResources("minecraft"));
            java.nio.file.Path temp = java.nio.file.Files.createTempDirectory("seedchecker-audit");
            temp.toFile().deleteOnExit();
            net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess access =
                    net.minecraft.world.level.storage.LevelStorageSource
                            .createDefault(temp).createAccess("audit");
            return new net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager(
                    resources, access, net.minecraft.util.datafix.DataFixers.getDataFixer());
        } catch (Throwable unavailable) {
            System.out.println("template manager failed: " + unavailable);
            return null;
        }
    }

    private static net.minecraft.world.level.levelgen.feature.StructureFeature<?> featureOf(
            StructureType type) {
        switch (type) {
            case VILLAGE:
                return net.minecraft.world.level.levelgen.feature.StructureFeature.VILLAGE;
            case DESERT_PYRAMID:
                return net.minecraft.world.level.levelgen.feature.StructureFeature.DESERT_PYRAMID;
            case SHIPWRECK:
                return net.minecraft.world.level.levelgen.feature.StructureFeature.SHIPWRECK;
            default:
                return null;
        }
    }

    private static List<int[]> candidateChunks(StructurePlacementConfig config) {
        final List<int[]> chunks = new ArrayList<int[]>();
        new StructurePlacementEngine().forEachCandidate(SEED, config,
                ChunkRange.of(-RADIUS_CHUNKS, -RADIUS_CHUNKS, RADIUS_CHUNKS, RADIUS_CHUNKS),
                100_000, new com.scrimchic.seedchecker.worldgen.StructureCandidateVisitor() {
                    @Override
                    public boolean visit(int chunkX, int chunkZ) {
                        chunks.add(new int[] {chunkX, chunkZ});
                        return true;
                    }
                });
        return chunks;
    }
    *///?} else {
    private static void run() {
        System.out.println("Not applicable: 3E-2b audits 1.16.5, where the biome check is exact.");
        System.out.println("Run it with ./gradlew :1.16.5:worldgenSpike "
                + "-PspikeMain=spike.StructureValidityAuditSpike");
    }
    //?}
}
