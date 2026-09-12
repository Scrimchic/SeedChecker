package spike;

import com.scrimchic.seedchecker.platform.BiomeWorldgenSession;
import com.scrimchic.seedchecker.worldgen.biome.BiomeSampleLevel;
import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;
import com.scrimchic.seedchecker.worldgen.biome.BiomeTile;
import com.scrimchic.seedchecker.worldgen.biome.BiomeTileGrid;
import com.scrimchic.seedchecker.worldgen.biome.BiomeTileKey;

/**
 * Measures the biome tile engine for docs and for the numbers quoted in the summary. Throwaway,
 * not part of {@code build}: {@code ./gradlew :<target>:worldgenSpike -PspikeMain=spike.BiomeTileSpike}.
 */
public final class BiomeTileSpike {

    private static final long SEED = -7407337299659424542L;

    private static String version() {
        return /*$ minecraft*/ "unknown";
    }

    public static void main(String[] args) {
        System.out.println("=== biome tile engine spike ===");
        System.out.println("minecraft   " + version());
        System.out.println("tile        " + BiomeTileGrid.SAMPLES_PER_SIDE + "x"
                + BiomeTileGrid.SAMPLES_PER_SIDE + " samples");

        // In game the client has already bootstrapped, so pay that here and keep it out of the
        // session numbers below.
        long bootstrapStart = System.nanoTime();
        //? if >=1.18 {
        net.minecraft.SharedConstants.tryDetectVersion();
        //?}
        net.minecraft.server.Bootstrap.bootStrap();
        System.out.printf("bootstrap   %.0f ms (already paid by the client in game)%n",
                (System.nanoTime() - bootstrapStart) / 1e6);

        long sessionStart = System.nanoTime();
        BiomeWorldgenSession session =
                BiomeWorldgenSession.create(SEED, BiomeWorldgenSession.OVERWORLD);
        System.out.printf("session     %.0f ms for the first one (registries + noise)%n",
                (System.nanoTime() - sessionStart) / 1e6);

        long secondStart = System.nanoTime();
        BiomeWorldgenSession.create(SEED + 1, BiomeWorldgenSession.OVERWORLD);
        System.out.printf("session     %.0f ms for each one after that (noise only)%n",
                (System.nanoTime() - secondStart) / 1e6);

        System.out.println();
        System.out.printf("%-10s %-12s %-14s %-14s %s%n",
                "level", "block step", "tile blocks", "ms per tile", "1080p screen");
        for (BiomeSampleLevel level : BiomeSampleLevel.values()) {
            measure(session, level);
        }

        System.out.println();
        System.out.println("palette pressure (distinct biomes in one tile):");
        for (BiomeSampleLevel level : BiomeSampleLevel.values()) {
            BiomeTile tile = generate(session, level, 0, 0);
            System.out.printf("  %-10s %d%n", level, tile.paletteSize());
        }
    }

    private static void measure(BiomeWorldgenSession session, BiomeSampleLevel level) {
        // Warm up, then time a block of tiles so JIT noise does not dominate.
        generate(session, level, -1, -1);

        int tiles = 8;
        long start = System.nanoTime();
        for (int i = 0; i < tiles; i++) {
            generate(session, level, i, i);
        }
        double msPerTile = (System.nanoTime() - start) / 1e6 / tiles;

        int tileBlocks = BiomeTileGrid.tileSizeInBlocks(level.blockStep());
        // Tiles needed to cover 1920x1080 at the scale where this level is selected.
        double scale = 4.0 / level.blockStep();
        int across = (int) Math.ceil(1920.0 / scale / tileBlocks) + 1;
        int down = (int) Math.ceil(1080.0 / scale / tileBlocks) + 1;
        int screenTiles = across * down;

        System.out.printf("%-10s %-12d %-14d %-14.1f %d tiles, %.0f ms of work%n",
                level, level.blockStep(), tileBlocks, msPerTile, screenTiles,
                screenTiles * msPerTile);
    }

    private static BiomeTile generate(BiomeWorldgenSession session, BiomeSampleLevel level,
                                      int tileX, int tileZ) {
        BiomeMapKey map = new BiomeMapKey("spike", SEED, BiomeWorldgenSession.OVERWORLD,
                version(), 64);
        BiomeTileKey key = new BiomeTileKey(map, level.blockStep(), tileX, tileZ);

        BiomeTile.Builder builder = BiomeTile.builder(key);
        int step = key.blockStep();
        int side = BiomeTileGrid.SAMPLES_PER_SIDE;
        for (int z = 0; z < side; z++) {
            int blockZ = key.originBlockZ() + z * step;
            for (int x = 0; x < side; x++) {
                builder.set(x, z, session.sampleBiomeId(key.originBlockX() + x * step, 64, blockZ));
            }
        }
        return builder.build();
    }
}
