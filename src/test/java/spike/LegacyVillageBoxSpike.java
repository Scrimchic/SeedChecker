package spike;

/**
 * Diagnostic for 1.16.5 only: why a village StructureStart's bounding box is wider than the union
 * of its pieces' boxes. Prints the start box against the extremes of the pieces for a few real
 * villages. Throwaway: {@code ./gradlew :1.16.5:worldgenSpike -PspikeMain=spike.LegacyVillageBoxSpike}.
 */
public final class LegacyVillageBoxSpike {

    private static final long SEED = -7407337299659424542L;

    public static void main(String[] args) throws Exception {
        run();
    }

    //? if <1.18 {
    /*private static void run() throws Exception {
        net.minecraft.server.Bootstrap.bootStrap();
        net.minecraft.world.level.levelgen.feature.StructureFeature.bootstrap();

        net.minecraft.world.level.levelgen.structure.BoundingBox first =
                net.minecraft.world.level.levelgen.structure.BoundingBox.getUnknownBox();
        net.minecraft.world.level.levelgen.structure.BoundingBox second =
                net.minecraft.world.level.levelgen.structure.BoundingBox.getUnknownBox();
        System.out.println("getUnknownBox shared instance: " + (first == second)
                + "  value " + first.x0 + "," + first.y0 + "," + first.z0 + " .. "
                + first.x1 + "," + first.y1 + "," + first.z1);

        net.minecraft.core.RegistryAccess registries = net.minecraft.core.RegistryAccess.builtin();
        final net.minecraft.world.level.biome.OverworldBiomeSource biomeSource =
                new net.minecraft.world.level.biome.OverworldBiomeSource(SEED, false, false,
                        net.minecraft.data.BuiltinRegistries.BIOME);
        final net.minecraft.world.level.levelgen.NoiseGeneratorSettings settings =
                net.minecraft.data.BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(
                        net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);
        net.minecraft.world.level.chunk.ChunkGenerator chunkGenerator =
                new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(biomeSource, SEED,
                        () -> settings);
        net.minecraft.server.packs.resources.SimpleReloadableResourceManager resources =
                new net.minecraft.server.packs.resources.SimpleReloadableResourceManager(
                        net.minecraft.server.packs.PackType.SERVER_DATA);
        resources.add(new net.minecraft.server.packs.VanillaPackResources("minecraft"));
        java.nio.file.Path temp = java.nio.file.Files.createTempDirectory("seedchecker-box");
        net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess access =
                net.minecraft.world.level.storage.LevelStorageSource.createDefault(temp)
                        .createAccess("box");
        net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager templates =
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager(
                        resources, access, net.minecraft.util.datafix.DataFixers.getDataFixer());

        int[][] chunks = {{-137, -300}};
        int shown = 0;
        java.util.List<int[]> candidates = new java.util.ArrayList<int[]>();
        candidates.add(chunks[0]);
        com.scrimchic.seedchecker.worldgen.StructurePlacementConfig config =
                com.scrimchic.seedchecker.worldgen.StructurePlacements.forThisVersion()
                        .get(com.scrimchic.seedchecker.worldgen.StructureType.VILLAGE);
        com.scrimchic.seedchecker.worldgen.StructurePlacementEngine engine =
                new com.scrimchic.seedchecker.worldgen.StructurePlacementEngine();
        for (int rz = -10; rz <= 10; rz++) {
            for (int rx = -10; rx <= 10; rx++) {
                long packed = engine.candidateChunk(SEED, config, rx, rz);
                candidates.add(new int[] {
                        com.scrimchic.seedchecker.worldgen.StructurePlacementEngine.chunkX(packed),
                        com.scrimchic.seedchecker.worldgen.StructurePlacementEngine.chunkZ(packed)});
            }
        }

        for (int[] chunk : candidates) {
            if (shown >= 8) {
                break;
            }
            net.minecraft.world.level.biome.Biome biome =
                    biomeSource.getNoiseBiome((chunk[0] << 2) + 2, 0, (chunk[1] << 2) + 2);
            for (java.util.function.Supplier<net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature<?, ?>> supplier
                    : biome.getGenerationSettings().structures()) {
                net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature<?, ?> configured =
                        supplier.get();
                if (configured.feature != net.minecraft.world.level.levelgen.feature.StructureFeature.VILLAGE) {
                    continue;
                }
                net.minecraft.world.level.levelgen.structure.StructureStart<?> start = configured.generate(
                        registries, chunkGenerator, biomeSource, templates, SEED,
                        new net.minecraft.world.level.ChunkPos(chunk[0], chunk[1]), biome, 0,
                        net.minecraft.world.level.levelgen.StructureSettings.DEFAULTS.get(
                                net.minecraft.world.level.levelgen.feature.StructureFeature.VILLAGE));
                if (!start.isValid()) {
                    continue;
                }
                int[] min = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
                int[] max = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
                for (net.minecraft.world.level.levelgen.structure.StructurePiece piece : start.getPieces()) {
                    net.minecraft.world.level.levelgen.structure.BoundingBox b = piece.getBoundingBox();
                    min[0] = Math.min(min[0], b.x0); min[1] = Math.min(min[1], b.y0); min[2] = Math.min(min[2], b.z0);
                    max[0] = Math.max(max[0], b.x1); max[1] = Math.max(max[1], b.y1); max[2] = Math.max(max[2], b.z1);
                }
                net.minecraft.world.level.levelgen.structure.BoundingBox box = start.getBoundingBox();
                System.out.printf("chunk %d,%d  start box %d,%d,%d .. %d,%d,%d%n", chunk[0], chunk[1],
                        box.x0, box.y0, box.z0, box.x1, box.y1, box.z1);
                System.out.printf("             pieces    %d,%d,%d .. %d,%d,%d   (%d pieces)%n",
                        min[0], min[1], min[2], max[0], max[1], max[2], start.getPieces().size());
                shown++;
            }
        }
        access.close();
    }*/
    //?} else {
    private static void run() {
        System.out.println("1.16.5 only");
    }
    //?}
}
