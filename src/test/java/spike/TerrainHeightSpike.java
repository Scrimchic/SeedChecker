package spike;

import java.util.ArrayList;
import java.util.List;

import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.platform.BiomeWorldgenSession;
import com.scrimchic.seedchecker.platform.StructureBiomeValidator;
import com.scrimchic.seedchecker.worldgen.StructurePlacementConfig;
import com.scrimchic.seedchecker.worldgen.StructureCandidateVisitor;
import com.scrimchic.seedchecker.worldgen.StructurePlacementEngine;
import com.scrimchic.seedchecker.worldgen.StructurePlacements;
import com.scrimchic.seedchecker.worldgen.StructureType;

/**
 * Phase 3E-2a spike. Two questions about the exact modern terrain path, neither of them answerable
 * by reading bytecode alone:
 *
 * <ol>
 *   <li><strong>Is it correct?</strong> Vanilla's own {@code Structure.findValidGenerationPoint} is
 *       run for every candidate and compared with a reproduction built out of
 *       {@code getFirstOccupiedHeight} and one biome sample. Desert pyramid and shipwreck reach
 *       that method without touching the template manager or the registry access, so a
 *       {@code GenerationContext} full of nulls in those two slots is enough to use vanilla as an
 *       oracle.</li>
 *   <li><strong>What does it cost?</strong> {@code getBaseHeight} walks a noise column, which is a
 *       different order of work from a biome sample. A desert pyramid needs five of them - four
 *       corners plus the centre - and that is the number that decides whether exact validation can
 *       run over a viewport at all.</li>
 * </ol>
 *
 * <p>Throwaway, not part of {@code build}:
 * {@code ./gradlew :1.20.1:worldgenSpike -PspikeMain=spike.TerrainHeightSpike}.
 */
public final class TerrainHeightSpike {

    private static final long SEED = -7407337299659424542L;

    private static String version() {
        return /*$ minecraft*/ "unknown";
    }

    public static void main(String[] args) {
        System.out.println("=== 3E-2a: exact terrain height path ===");
        System.out.println("minecraft   " + version());
        System.out.println("seed        " + SEED);
        System.out.println();
        run();
    }

    //? if >=1.18 {
    /** The pyramid's footprint, from {@code DesertPyramidStructure}'s call to super. */
    private static final int PYRAMID_WIDTH = 21;
    private static final int PYRAMID_DEPTH = 21;

    private static net.minecraft.core.HolderLookup.Provider registries;
    private static net.minecraft.world.level.levelgen.RandomState randomState;
    private static net.minecraft.world.level.biome.BiomeSource biomeSource;
    private static net.minecraft.world.level.chunk.ChunkGenerator chunkGenerator;
    private static net.minecraft.world.level.LevelHeightAccessor heightAccessor;
    private static int seaLevel;
    private static int minY;

    private static void run() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        long coldStart = System.nanoTime();
        buildSession();
        double coldMillis = (System.nanoTime() - coldStart) / 1e6;

        long warmStart = System.nanoTime();
        buildGeneratorOnly();
        double warmMillis = (System.nanoTime() - warmStart) / 1e6;

        System.out.printf("cold session   %8.1f ms  (registries + random state + generator)%n",
                coldMillis);
        System.out.printf("warm session   %8.1f ms  (generator only, registries shared)%n",
                warmMillis);
        System.out.printf("sea level      %8d%n", seaLevel);
        // Taken from the settings rather than the accessor: LevelHeightAccessor spells its lower
        // bound getMinBuildHeight() on 1.20.1 and getMinY() on 26.2.
        System.out.printf("height range   %8d .. %d%n", minY, minY + heightAccessor.getHeight());

        measureRawHeights();
        measurePerCandidate();
        compareWithVanilla();
    }

    private static void buildSession() {
        registries = net.minecraft.data.registries.VanillaRegistries.createLookup();
        buildGeneratorOnly();
    }

    private static void buildGeneratorOnly() {
        net.minecraft.core.Holder<net.minecraft.world.level.levelgen.NoiseGeneratorSettings> settings =
                registries.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                        .getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);

        randomState = net.minecraft.world.level.levelgen.RandomState.create(
                settings.value(),
                registries.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE),
                SEED);
        biomeSource = net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(
                registries.lookupOrThrow(net.minecraft.core.registries.Registries
                                .MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(net.minecraft.world.level.biome
                                .MultiNoiseBiomeSourceParameterLists.OVERWORLD));

        // Two public arguments, no chunk, no world, no tags.
        chunkGenerator = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(
                biomeSource, settings);
        minY = settings.value().noiseSettings().minY();
        heightAccessor = net.minecraft.world.level.LevelHeightAccessor.create(
                minY, settings.value().noiseSettings().height());
        seaLevel = chunkGenerator.getSeaLevel();
    }

    private static int occupiedHeight(int blockX, int blockZ,
                                      net.minecraft.world.level.levelgen.Heightmap.Types type) {
        return chunkGenerator.getFirstOccupiedHeight(blockX, blockZ, type, heightAccessor,
                randomState);
    }

    // ------------------------------------------------------------------ performance

    private static void measureRawHeights() {
        System.out.println();
        System.out.println("getFirstOccupiedHeight, WORLD_SURFACE_WG, scattered columns:");
        System.out.printf("  %8s %12s %12s%n", "samples", "total ms", "us each");

        // Warm the JIT without counting it.
        sampleColumns(200, 7_000_000);

        int[] counts = {100, 1000, 10000};
        for (int count : counts) {
            long start = System.nanoTime();
            sampleColumns(count, 1_000_000);
            long elapsed = System.nanoTime() - start;
            System.out.printf("  %8d %12.1f %12.1f%n", count, elapsed / 1e6,
                    elapsed / 1e3 / count);
        }

        // The same measurement on a cold run matters too: the first call into a region pays for
        // density function setup that later nearby calls do not.
        buildGeneratorOnly();
        long coldStart = System.nanoTime();
        sampleColumns(100, 3_000_000);
        System.out.printf("  %8s %12.1f %12.1f   (first calls after a fresh generator)%n",
                "100 cold", (System.nanoTime() - coldStart) / 1e6,
                (System.nanoTime() - coldStart) / 1e3 / 100);
    }

    private static void sampleColumns(int count, int origin) {
        int sink = 0;
        for (int i = 0; i < count; i++) {
            // Spread far apart, so nothing is answered out of a neighbouring column's cache.
            int x = origin + i * 373;
            int z = origin - i * 517;
            sink += occupiedHeight(x, z,
                    net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG);
        }
        if (sink == Integer.MIN_VALUE) {
            System.out.println("unreachable");
        }
    }

    private static void measurePerCandidate() {
        System.out.println();
        System.out.println("per candidate, exact path vs the current column enumeration:");
        System.out.printf("  %-16s %10s %14s %14s %14s%n", "structure", "candidates",
                "exact ms each", "3E-1 ms each", "ratio");

        StructurePlacements placements = StructurePlacements.forThisVersion();
        BiomeWorldgenSession session =
                BiomeWorldgenSession.create(SEED, BiomeWorldgenSession.OVERWORLD);

        for (StructureType type : new StructureType[] {
                StructureType.DESERT_PYRAMID, StructureType.SHIPWRECK}) {
            if (!placements.supports(type)) {
                continue;
            }
            List<int[]> chunks = candidates(placements.get(type), 400);

            // One candidate first, so the single-candidate latency is visible separately from the
            // batch rate - the map validates a few at a time, not four hundred.
            long singleStart = System.nanoTime();
            exactlyValid(type, chunks.get(0)[0], chunks.get(0)[1]);
            double singleMillis = (System.nanoTime() - singleStart) / 1e6;

            long exactStart = System.nanoTime();
            for (int[] chunk : chunks) {
                exactlyValid(type, chunk[0], chunk[1]);
            }
            double exactEach = (System.nanoTime() - exactStart) / 1e6 / chunks.size();

            long cheapStart = System.nanoTime();
            for (int[] chunk : chunks) {
                exactlyValidBiomeFirst(type, chunk[0], chunk[1]);
            }
            double cheapEach = (System.nanoTime() - cheapStart) / 1e6 / chunks.size();

            long oldStart = System.nanoTime();
            for (int[] chunk : chunks) {
                StructureBiomeValidator.validate(session, type, chunk[0], chunk[1]);
            }
            double oldEach = (System.nanoTime() - oldStart) / 1e6 / chunks.size();

            System.out.printf("  %-16s %10d %14.3f %14.3f %13.2fx%n", type.name().toLowerCase(),
                    chunks.size(), exactEach, oldEach, exactEach / Math.max(1e-9, oldEach));
            System.out.printf("  %-16s biome-first order: %.3f ms each (%.2fx of 3E-1), "
                            + "single candidate cold: %.3f ms%n",
                    "", cheapEach, cheapEach / Math.max(1e-9, oldEach), singleMillis);
        }
    }

    // ------------------------------------------------------------------ correctness

    /**
     * Our reproduction of vanilla's decision: the exact column, the exact heightmap, the exact
     * off-by-one, and one biome sample at the stub position.
     */
    private static boolean exactlyValid(StructureType type, int chunkX, int chunkZ) {
        int middleX = (chunkX << 4) + 8;
        int middleZ = (chunkZ << 4) + 8;

        if (type == StructureType.DESERT_PYRAMID) {
            int minX = chunkX << 4;
            int minZ = chunkZ << 4;
            net.minecraft.world.level.levelgen.Heightmap.Types surface =
                    net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG;
            int lowest = Math.min(
                    Math.min(occupiedHeight(minX, minZ, surface),
                            occupiedHeight(minX, minZ + PYRAMID_DEPTH, surface)),
                    Math.min(occupiedHeight(minX + PYRAMID_WIDTH, minZ, surface),
                            occupiedHeight(minX + PYRAMID_WIDTH, minZ + PYRAMID_DEPTH, surface)));
            if (lowest < seaLevel) {
                return false;
            }
            int y = occupiedHeight(middleX, middleZ, surface);
            return accepts("desert_pyramid", middleX, y, middleZ);
        }

        // The shipwreck set holds two entries with different heightmaps and different biome tags,
        // and vanilla tries them until one works, so either one generating is a yes.
        int oceanY = occupiedHeight(middleX, middleZ,
                net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG);
        if (accepts("shipwreck", middleX, oceanY, middleZ)) {
            return true;
        }
        int beachedY = occupiedHeight(middleX, middleZ,
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG);
        return accepts("shipwreck_beached", middleX, beachedY, middleZ);
    }

    /**
     * The same decision with the conjunction evaluated in the cheaper order.
     *
     * <p>Vanilla's answer for a desert pyramid is
     * {@code lowestCornerY >= seaLevel && biomeAtCentreIsAccepted}. Both must hold, so testing the
     * biome first cannot change the result - it only skips the four corner columns for the great
     * majority of candidates, which fail on biome anyway. Measured rather than assumed, because
     * whether it is worth doing depends entirely on how lopsided that majority is.
     */
    private static boolean exactlyValidBiomeFirst(StructureType type, int chunkX, int chunkZ) {
        if (type != StructureType.DESERT_PYRAMID) {
            return exactlyValid(type, chunkX, chunkZ);
        }
        int middleX = (chunkX << 4) + 8;
        int middleZ = (chunkZ << 4) + 8;
        net.minecraft.world.level.levelgen.Heightmap.Types surface =
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG;

        int y = occupiedHeight(middleX, middleZ, surface);
        if (!accepts("desert_pyramid", middleX, y, middleZ)) {
            return false;
        }
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int lowest = Math.min(
                Math.min(occupiedHeight(minX, minZ, surface),
                        occupiedHeight(minX, minZ + PYRAMID_DEPTH, surface)),
                Math.min(occupiedHeight(minX + PYRAMID_WIDTH, minZ, surface),
                        occupiedHeight(minX + PYRAMID_WIDTH, minZ + PYRAMID_DEPTH, surface)));
        return lowest >= seaLevel;
    }

    private static boolean accepts(String variant, int blockX, int blockY, int blockZ) {
        java.util.Set<String> allowed = StructureBiomeValidator.acceptedBiomes(
                variant.startsWith("shipwreck") ? StructureType.SHIPWRECK
                        : StructureType.DESERT_PYRAMID, variant);
        return allowed.contains(biomeIdAt(blockX, blockY, blockZ));
    }

    private static String biomeIdAt(int blockX, int blockY, int blockZ) {
        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome =
                biomeSource.getNoiseBiome(
                        net.minecraft.core.QuartPos.fromBlock(blockX),
                        net.minecraft.core.QuartPos.fromBlock(blockY),
                        net.minecraft.core.QuartPos.fromBlock(blockZ),
                        randomState.sampler());
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> key =
                biome.unwrapKey().orElse(null);
        return key == null ? "seedchecker:unnamed" : keyName(key);
    }

    private static String keyName(net.minecraft.resources.ResourceKey<?> key) {
        //? if >=26.1 {
        return key.identifier().toString();
        //?} else {
        /*return key.location().toString();*/
        //?}
    }

    /**
     * Vanilla's own answer, through the real {@code findValidGenerationPoint}. The biome predicate
     * is ours rather than {@code structure.biomes()::contains}, because the tags are unbound on
     * this path - which is fine, since the biome sets were already checked against vanilla's tags
     * in Phase 3E-1. What is under test here is the <em>position</em>.
     */
    private static boolean vanillaValid(final StructureType type, String variant,
                                        int chunkX, int chunkZ) {
        net.minecraft.world.level.levelgen.structure.Structure structure =
                vanillaStructure(variant);
        final java.util.Set<String> allowed =
                StructureBiomeValidator.acceptedBiomes(type, variant);

        net.minecraft.world.level.levelgen.structure.Structure.GenerationContext context =
                new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(
                        null, chunkGenerator, biomeSource, randomState, null, SEED,
                        new net.minecraft.world.level.ChunkPos(chunkX, chunkZ), heightAccessor,
                        holder -> {
                            net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> key =
                                    holder.unwrapKey().orElse(null);
                            return key != null && allowed.contains(keyName(key));
                        });
        return structure.findValidGenerationPoint(context).isPresent();
    }

    private static net.minecraft.world.level.levelgen.structure.Structure vanillaStructure(
            final String variant) {
        return registries.lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .listElements()
                .filter(holder -> holder.unwrapKey().isPresent()
                        && keyName(holder.unwrapKey().get()).endsWith(":" + variant))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no structure " + variant))
                .value();
    }

    private static void compareWithVanilla() {
        System.out.println();
        System.out.println("exact reproduction vs vanilla findValidGenerationPoint:");
        System.out.printf("  %-16s %10s %10s %10s %10s %10s%n", "structure", "candidates",
                "ours yes", "vanilla yes", "mismatch", "3E-1 kept");

        StructurePlacements placements = StructurePlacements.forThisVersion();
        BiomeWorldgenSession session =
                BiomeWorldgenSession.create(SEED, BiomeWorldgenSession.OVERWORLD);

        for (StructureType type : new StructureType[] {
                StructureType.DESERT_PYRAMID, StructureType.SHIPWRECK}) {
            if (!placements.supports(type)) {
                continue;
            }
            List<int[]> chunks = candidates(placements.get(type), 3000);
            int oursYes = 0;
            int vanillaYes = 0;
            int mismatch = 0;
            int oldKept = 0;

            for (int[] chunk : chunks) {
                boolean ours = exactlyValid(type, chunk[0], chunk[1]);
                boolean vanilla;
                if (type == StructureType.DESERT_PYRAMID) {
                    vanilla = vanillaValid(type, "desert_pyramid", chunk[0], chunk[1]);
                } else {
                    vanilla = vanillaValid(type, "shipwreck", chunk[0], chunk[1])
                            || vanillaValid(type, "shipwreck_beached", chunk[0], chunk[1]);
                }
                if (ours) {
                    oursYes++;
                }
                if (vanilla) {
                    vanillaYes++;
                }
                // What the shipped 3E-1 filter keeps on the very same candidates, so the accuracy
                // the exact path buys is a difference on one sample rather than across two runs.
                if (!StructureBiomeValidator.validate(session, type, chunk[0], chunk[1])
                        .isRejected()) {
                    oldKept++;
                }
                if (ours != vanilla) {
                    if (mismatch < 5) {
                        System.out.println("    MISMATCH " + type + " at " + chunk[0] + ","
                                + chunk[1] + " ours=" + ours + " vanilla=" + vanilla);
                    }
                    mismatch++;
                }
            }
            System.out.printf("  %-16s %10d %10d %10d %10d %10d%n", type.name().toLowerCase(),
                    chunks.size(), oursYes, vanillaYes, mismatch, oldKept);
        }
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
    //?} else {
    /*private static void run() {
        System.out.println("Not applicable: 1.16.5 samples a fixed, terrain independent position.");
    }*///?}
}
