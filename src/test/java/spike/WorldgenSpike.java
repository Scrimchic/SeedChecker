package spike;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.minecraft.server.Bootstrap;

//? if >=1.18 {
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
//?} else {
/*import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.OverworldBiomeSource;
import net.minecraft.world.level.levelgen.StructureSettings;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.feature.configurations.StructureFeatureConfiguration;*/
//?}

/**
 * Research spike for docs/worldgen-engine-spike.md - NOT production code and not wired into the
 * build. Run it with {@code ./gradlew :<target>:worldgenSpike}.
 *
 * <p>It answers one question per Minecraft version: can Seed Checker stand up vanilla worldgen for
 * an arbitrary seed, with no save on disk, no integrated server and no world loaded, and how fast
 * and how thread safe is the result.
 */
public final class WorldgenSpike {

    private static final long SEED = -7407337299659424542L;

    /** The probe coordinates from the research brief. */
    private static final int[][] PROBES = {
            {0, 64, 0},
            {1000, 64, 1000},
            {-1000, 64, 500},
            {100000, 64, -100000},
    };

    private static final String[] STRUCTURE_SETS = {
            "villages", "desert_pyramids", "shipwrecks", "ancient_cities", "trial_chambers",
    };

    /** Block coordinates to a biome name, for whichever worldgen generation this version has. */
    private interface BiomeSampler {
        String biomeAt(int blockX, int blockY, int blockZ);
    }

    /** Whether a structure of that set may start in that chunk, ignoring biome and terrain. */
    private interface PlacementCheck {
        Boolean isCandidate(String structureSet, int chunkX, int chunkZ);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Seed Checker worldgen spike ===");
        System.out.println("minecraft   " + version());
        System.out.println("seed        " + SEED);
        System.out.println("java        " + System.getProperty("java.version"));

        long bootstrapStart = System.nanoTime();
        bootstrap();
        System.out.printf("bootstrap   %.0f ms%n", (System.nanoTime() - bootstrapStart) / 1e6);

        long heapBefore = usedHeap();
        long setupStart = System.nanoTime();
        BiomeSampler sampler = createBiomeSampler();
        System.out.printf("worldgen    %.0f ms to build a biome source for an arbitrary seed%n",
                (System.nanoTime() - setupStart) / 1e6);
        System.out.printf("heap        %+.1f MB retained by the worldgen state%n",
                (usedHeap() - heapBefore) / 1024.0 / 1024.0);

        probes(sampler);
        throughput(sampler);
        parallelism();
        structures();
        strongholds();
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        for (int i = 0; i < 4; i++) {
            System.gc();
            try {
                Thread.sleep(60L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return runtime.totalMemory() - runtime.freeMemory();
    }

    // ------------------------------------------------------------- reporting

    private static void probes(BiomeSampler sampler) {
        System.out.println();
        System.out.println("--- biome probes (y is a block coordinate) ---");
        for (int[] probe : PROBES) {
            System.out.printf("  %8d %4d %8d  ->  %s%n",
                    probe[0], probe[1], probe[2], sampler.biomeAt(probe[0], probe[1], probe[2]));
        }
    }

    private static void throughput(BiomeSampler sampler) {
        System.out.println();
        System.out.println("--- biome throughput, single thread ---");
        // Sampled on a quart grid, which is the resolution biomes actually have.
        for (int samples : new int[] {10_000, 10_000, 100_000, 1_000_000}) {
            int side = (int) Math.sqrt(samples);
            long start = System.nanoTime();
            int sink = 0;
            for (int i = 0; i < side; i++) {
                for (int j = 0; j < side; j++) {
                    sink += sampler.biomeAt(i * 4, 64, j * 4).length();
                }
            }
            long elapsed = System.nanoTime() - start;
            System.out.printf("  %,9d samples  %8.1f ms  %7.2f us/sample  %,10d samples/s   (sink %d)%n",
                    side * side, elapsed / 1e6, elapsed / 1e3 / (side * side),
                    (long) (side * (long) side / (elapsed / 1e9)), sink);
        }
    }

    /** Runs the same query set on four threads and checks the answers against a single thread. */
    private static void parallelism() throws Exception {
        System.out.println();
        System.out.println("--- thread safety ---");

        BiomeSampler shared = createBiomeSampler();
        List<String> expected = new ArrayList<String>();
        for (int i = 0; i < 512; i++) {
            expected.add(shared.biomeAt(i * 16, 64, i * 32));
        }

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            System.out.print("  one shared biome source across 4 threads: ");
            System.out.println(agrees(pool, expected, sharedTask(shared)));

            System.out.print("  one biome source per thread:              ");
            System.out.println(agrees(pool, expected, perThreadTask()));
        } finally {
            pool.shutdownNow();
        }
    }

    private static Callable<List<String>> sharedTask(final BiomeSampler shared) {
        return new Callable<List<String>>() {
            @Override
            public List<String> call() {
                return sample(shared);
            }
        };
    }

    private static Callable<List<String>> perThreadTask() {
        return new Callable<List<String>>() {
            @Override
            public List<String> call() {
                return sample(createBiomeSampler());
            }
        };
    }

    private static List<String> sample(BiomeSampler sampler) {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < 512; i++) {
            out.add(sampler.biomeAt(i * 16, 64, i * 32));
        }
        return out;
    }

    private static String agrees(ExecutorService pool, List<String> expected,
                                 Callable<List<String>> task) throws Exception {
        List<Future<List<String>>> futures = new ArrayList<Future<List<String>>>();
        for (int i = 0; i < 4; i++) {
            futures.add(pool.submit(task));
        }
        try {
            for (Future<List<String>> future : futures) {
                if (!expected.equals(future.get())) {
                    return "MISMATCH - results differ from the single-threaded run";
                }
            }
            return "identical results";
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return "THREW " + cause.getClass().getName() + ": " + cause.getMessage();
        }
    }

    private static void structures() {
        System.out.println();
        System.out.println("--- structure grid placement in chunks [-64..64] ---");
        System.out.println("  vanilla = the version's own placement call, pure = reimplemented math");

        PlacementCheck vanilla;
        String vanillaNote;
        try {
            vanilla = createPlacementCheck();
            vanillaNote = null;
        } catch (RuntimeException e) {
            vanilla = null;
            vanillaNote = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        if (vanillaNote != null) {
            System.out.println("  vanilla path unavailable -> " + vanillaNote);
        }

        for (String set : STRUCTURE_SETS) {
            int[] params = placementParameters(set);
            if (params == null && (vanilla == null || vanilla.isCandidate(set, 0, 0) == null)) {
                System.out.printf("  %-16s not in this version%n", set);
                continue;
            }

            int pureHits = 0;
            int vanillaHits = 0;
            int disagreements = 0;
            List<String> first = new ArrayList<String>();
            long pureStart = System.nanoTime();
            for (int cx = -64; cx <= 64; cx++) {
                for (int cz = -64; cz <= 64; cz++) {
                    boolean pure = false;
                    if (params != null) {
                        pure = pureIsPlacementChunk(SEED, params[0], params[1], params[2],
                                params[3] == 1, cx, cz);
                        if (pure) {
                            pureHits++;
                            if (first.size() < 4) {
                                first.add("(" + cx + "," + cz + ")");
                            }
                        }
                    }
                    if (vanilla != null) {
                        Boolean fromVanilla = vanilla.isCandidate(set, cx, cz);
                        if (fromVanilla != null) {
                            if (fromVanilla.booleanValue()) {
                                vanillaHits++;
                            }
                            if (params != null && fromVanilla.booleanValue() != pure) {
                                disagreements++;
                            }
                        }
                    }
                }
            }

            long pureNanos = System.nanoTime() - pureStart;
            String agreement = vanilla == null || params == null
                    ? "vanilla n/a"
                    : (disagreements == 0 ? "pure == vanilla" : disagreements + " DISAGREEMENTS");
            System.out.printf("  %-16s pure %3d  vanilla %3d  %-18s %5.2f us/chunk pure   %s%n",
                    set, pureHits, vanillaHits, agreement, pureNanos / 1e3 / 16641, first);
        }
    }

    //? if >=1.18 {
    /** Strongholds sit on concentric rings, and the ring solve is the expensive part. */
    private static void strongholds() {
        System.out.println();
        System.out.println("--- strongholds (concentric rings, not a grid) ---");
        try {
            strongholdRing();
        } catch (RuntimeException e) {
            System.out.println("  unavailable -> " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    private static void strongholdRing() {
        HolderLookup.Provider lookup = registries();
        RandomState randomState = RandomState.create(
                lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
                        .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value(),
                lookup.lookupOrThrow(Registries.NOISE), SEED);
        BiomeSource biomeSource = MultiNoiseBiomeSource.createFromPreset(
                lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        HolderLookup.RegistryLookup<StructureSet> sets = lookup.lookupOrThrow(Registries.STRUCTURE_SET);
        ChunkGeneratorStructureState state = ChunkGeneratorStructureState.createForFlat(
                randomState, SEED, biomeSource, sets.listElements().map(h -> (Holder<StructureSet>) h));

        System.out.println("  possibleStructureSets(): " + state.possibleStructureSets().size()
                + " of " + sets.listElements().count() + " registered");
        boolean strongholdsPossible = false;
        for (Holder<StructureSet> set : state.possibleStructureSets()) {
            if (set.unwrapKey().isPresent()
                    && BuiltinStructureSets.STRONGHOLDS.equals(set.unwrapKey().get())) {
                strongholdsPossible = true;
            }
        }
        System.out.println("  strongholds survived the biome filter: " + strongholdsPossible);

        StructurePlacement placement =
                sets.getOrThrow(BuiltinStructureSets.STRONGHOLDS).value().placement();
        if (!(placement instanceof ConcentricRingsStructurePlacement)) {
            System.out.println("  unexpected placement type " + placement.getClass().getName());
            return;
        }

        // The ring is solved lazily and the getter returns null until it has been. The solve
        // itself samples biomes AND dereferences the preferred-biome tag, which is the part that
        // needs a real datapack behind the registries.
        long start = System.nanoTime();
        List<net.minecraft.world.level.ChunkPos> ring;
        try {
            state.ensureStructuresGenerated();
            ring = state.getRingPositionsFor((ConcentricRingsStructurePlacement) placement);
        } catch (RuntimeException e) {
            System.out.println("  ring solve  FAILED: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            return;
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("  ring solve  %8.1f ms for %d positions%n", elapsed / 1e6,
                ring == null ? -1 : ring.size());
        if (ring != null) {
            // ChunkPos.x/z are public fields up to 1.20.1 but private with x()/z() accessors in
            // 26.x, so the spike just prints the position rather than branching on that.
            for (int i = 0; i < Math.min(4, ring.size()); i++) {
                System.out.println("    chunk " + ring.get(i));
            }
        }
    }
    //?} else {
    /*private static void strongholds() {
        System.out.println();
        System.out.println("--- strongholds (concentric rings, not a grid) ---");
        System.out.println("  1.16.5 computes the ring inside ChunkGenerator.generateStrongholds(),");
        System.out.println("  which is private and runs from the ChunkGenerator constructor, so the");
        System.out.println("  positions are not reachable without building a full ChunkGenerator.");
    }
    *///?}

    // ------------------------------------------- pure Seed Checker reimplementation

    /**
     * The grid placement every "random spread" structure uses, reimplemented from the bytecode of
     * {@code RandomSpreadStructurePlacement.getPotentialStructureChunk} plus
     * {@code WorldgenRandom.setLargeFeatureWithSalt}. Verified identical on 1.16.5 and 1.20.1
     * against the vanilla call in the same run.
     */
    private static boolean pureIsPlacementChunk(long seed, int spacing, int separation, int salt,
                                                boolean linear, int chunkX, int chunkZ) {
        int gridX = Math.floorDiv(chunkX, spacing);
        int gridZ = Math.floorDiv(chunkZ, spacing);
        long state = scramble(gridX * 341873128712L + gridZ * 132897987541L + seed + salt);

        int range = spacing - separation;
        long[] rng = {state};
        int offsetX = linear ? nextInt(rng, range) : (nextInt(rng, range) + nextInt(rng, range)) / 2;
        int offsetZ = linear ? nextInt(rng, range) : (nextInt(rng, range) + nextInt(rng, range)) / 2;

        return gridX * spacing + offsetX == chunkX && gridZ * spacing + offsetZ == chunkZ;
    }

    private static long scramble(long seed) {
        return (seed ^ 0x5DEECE66DL) & ((1L << 48) - 1L);
    }

    /** java.util.Random.nextInt(bound), which is what LegacyRandomSource is. */
    private static int nextInt(long[] state, int bound) {
        if ((bound & (bound - 1)) == 0) {
            return (int) ((bound * (long) next(state, 31)) >> 31);
        }
        while (true) {
            int bits = next(state, 31);
            int value = bits % bound;
            if (bits - value + (bound - 1) >= 0) {
                return value;
            }
        }
    }

    private static int next(long[] state, int bits) {
        state[0] = (state[0] * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1L);
        return (int) (state[0] >>> (48 - bits));
    }

    /**
     * Spacing / separation / salt for a structure set, however this version stores them.
     *
     * @return {spacing, separation, salt, linear} or {@code null} if this version has no such set
     */
    //? if <1.18 {
    /*private static int[] placementParameters(String structureSet) {
        // 1.16.5 ships no worldgen datapack at all; the numbers are hardcoded in StructureSettings.
        StructureFeature<?> feature = null;
        if ("villages".equals(structureSet)) {
            feature = StructureFeature.VILLAGE;
        } else if ("desert_pyramids".equals(structureSet)) {
            feature = StructureFeature.DESERT_PYRAMID;
        } else if ("shipwrecks".equals(structureSet)) {
            feature = StructureFeature.SHIPWRECK;
        }
        if (feature == null) {
            return null;
        }
        StructureFeatureConfiguration config = new StructureSettings(true).getConfig(feature);
        if (config == null) {
            return null;
        }
        // linearSeparation() is protected in 1.16.5; it is true for all three of these, and the
        // vanilla cross-check in the same run is what proves that assumption.
        return new int[] {config.spacing(), config.separation(), config.salt(), 1};
    }
    *///?} else {
    private static int[] placementParameters(String structureSet) {
        String json = readResource("/data/minecraft/worldgen/structure_set/" + structureSet + ".json");
        if (json == null || !json.contains("random_spread")) {
            return null;
        }
        Integer spacing = readInt(json, "spacing");
        Integer separation = readInt(json, "separation");
        Integer salt = readInt(json, "salt");
        if (spacing == null || separation == null || salt == null) {
            return null;
        }
        // The codec defaults spread_type to LINEAR, and no vanilla set spells it out, so absence
        // means linear - assuming triangular here is what made the first run disagree.
        boolean linear = !json.contains("triangular");
        return new int[] {spacing.intValue(), separation.intValue(), salt.intValue(), linear ? 1 : 0};
    }
    //?}

    private static String readResource(String path) {
        java.io.InputStream in = WorldgenSpike.class.getResourceAsStream(path);
        if (in == null) {
            return null;
        }
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), "UTF-8");
        } catch (java.io.IOException e) {
            return null;
        } finally {
            try {
                in.close();
            } catch (java.io.IOException ignored) {
                // nothing useful to do in a spike
            }
        }
    }

    private static Integer readInt(String json, String field) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + field + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    // --------------------------------------------------- version-specific set-up

    private static String version() {
        return /*$ minecraft*/ "26.2";
    }

    //? if >=26.1 {
    private static String keyName(net.minecraft.resources.ResourceKey<?> key) {
        return key.identifier().toString();
    }
    //?}
    //? if >=1.18 && <26.1 {
    /*private static String keyName(net.minecraft.resources.ResourceKey<?> key) {
        return key.location().toString();
    }*/
    //?}

    private static void bootstrap() {
        //? if >=1.18 {
        SharedConstants.tryDetectVersion();
        //?}
        Bootstrap.bootStrap();
    }

    //? if >=1.18 {
    private static HolderLookup.Provider registries;

    private static HolderLookup.Provider registries() {
        if (registries == null) {
            registries = VanillaRegistries.createLookup();
        }
        return registries;
    }

    private static BiomeSampler createBiomeSampler() {
        HolderLookup.Provider lookup = registries();
        // The (Provider, ResourceKey, long) overload only compiles on 26.x, where
        // HolderLookup.Provider gained "extends HolderGetter.Provider". This overload is the one
        // that exists unchanged on both.
        final RandomState randomState = RandomState.create(
                lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
                        .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value(),
                lookup.lookupOrThrow(Registries.NOISE), SEED);
        final BiomeSource biomeSource = MultiNoiseBiomeSource.createFromPreset(
                lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        final Climate.Sampler climate = randomState.sampler();

        return new BiomeSampler() {
            @Override
            public String biomeAt(int blockX, int blockY, int blockZ) {
                Holder<Biome> biome =
                        biomeSource.getNoiseBiome(blockX >> 2, blockY >> 2, blockZ >> 2, climate);
                return biome.unwrapKey().isPresent()
                        ? keyName(biome.unwrapKey().get())
                        : "unnamed";
            }
        };
    }

    private static PlacementCheck createPlacementCheck() {
        HolderLookup.Provider lookup = registries();
        RandomState randomState = RandomState.create(
                lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
                        .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value(),
                lookup.lookupOrThrow(Registries.NOISE), SEED);
        BiomeSource biomeSource = MultiNoiseBiomeSource.createFromPreset(
                lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        final HolderLookup.RegistryLookup<StructureSet> sets =
                lookup.lookupOrThrow(Registries.STRUCTURE_SET);
        // createForNormal() filters the sets by "does any of their biomes exist in this biome
        // source", and those biome sets are TAGS. VanillaRegistries.createLookup() does not bind
        // tags, so on 1.20.1 that filter quietly drops every set and on 26.2 it throws outright.
        // createForFlat() takes the sets verbatim and never touches the tag, which is all a grid
        // placement query needs.
        final ChunkGeneratorStructureState state = ChunkGeneratorStructureState.createForFlat(
                randomState, SEED, biomeSource, sets.listElements().map(h -> (Holder<StructureSet>) h));

        return new PlacementCheck() {
            @Override
            public Boolean isCandidate(String structureSet, int chunkX, int chunkZ) {
                StructurePlacement placement = placementOf(structureSet);
                if (placement == null) {
                    return null;
                }
                return Boolean.valueOf(placement.isStructureChunk(state, chunkX, chunkZ));
            }

            private StructurePlacement placementOf(String structureSet) {
                if ("villages".equals(structureSet)) {
                    return sets.getOrThrow(BuiltinStructureSets.VILLAGES).value().placement();
                }
                if ("desert_pyramids".equals(structureSet)) {
                    return sets.getOrThrow(BuiltinStructureSets.DESERT_PYRAMIDS).value().placement();
                }
                if ("shipwrecks".equals(structureSet)) {
                    return sets.getOrThrow(BuiltinStructureSets.SHIPWRECKS).value().placement();
                }
                if ("strongholds".equals(structureSet)) {
                    return sets.getOrThrow(BuiltinStructureSets.STRONGHOLDS).value().placement();
                }
                if ("ancient_cities".equals(structureSet)) {
                    return sets.getOrThrow(BuiltinStructureSets.ANCIENT_CITIES).value().placement();
                }
                //? if >=1.21 {
                if ("trial_chambers".equals(structureSet)) {
                    return sets.getOrThrow(BuiltinStructureSets.TRIAL_CHAMBERS).value().placement();
                }
                //?}
                return null;
            }
        };
    }
    //?} else {
    /*private static BiomeSampler createBiomeSampler() {
        final Registry<Biome> biomes = BuiltinRegistries.BIOME;
        final OverworldBiomeSource biomeSource =
                new OverworldBiomeSource(SEED, false, false, biomes);

        return new BiomeSampler() {
            @Override
            public String biomeAt(int blockX, int blockY, int blockZ) {
                Biome biome = biomeSource.getNoiseBiome(blockX >> 2, blockY >> 2, blockZ >> 2);
                return String.valueOf(biomes.getKey(biome));
            }
        };
    }

    private static PlacementCheck createPlacementCheck() {
        final StructureSettings settings = new StructureSettings(true);
        final WorldgenRandom random = new WorldgenRandom();

        return new PlacementCheck() {
            @Override
            public Boolean isCandidate(String structureSet, int chunkX, int chunkZ) {
                StructureFeature<?> feature = featureOf(structureSet);
                if (feature == null) {
                    return null;
                }
                StructureFeatureConfiguration config = settings.getConfig(feature);
                if (config == null) {
                    return null;
                }
                ChunkPos candidate =
                        feature.getPotentialFeatureChunk(config, SEED, random, chunkX, chunkZ);
                return Boolean.valueOf(candidate.x == chunkX && candidate.z == chunkZ);
            }

            private StructureFeature<?> featureOf(String structureSet) {
                if ("villages".equals(structureSet)) {
                    return StructureFeature.VILLAGE;
                }
                if ("desert_pyramids".equals(structureSet)) {
                    return StructureFeature.DESERT_PYRAMID;
                }
                if ("shipwrecks".equals(structureSet)) {
                    return StructureFeature.SHIPWRECK;
                }
                return null;
            }
        };
    }
    *///?}
}
