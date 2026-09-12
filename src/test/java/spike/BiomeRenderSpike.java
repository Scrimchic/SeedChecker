package spike;

import com.scrimchic.seedchecker.core.map.MapViewport;
import com.scrimchic.seedchecker.platform.BiomeWorldgenSession;
import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;
import com.scrimchic.seedchecker.worldgen.biome.BiomeSampleLevel;
import com.scrimchic.seedchecker.worldgen.biome.BiomeTile;
import com.scrimchic.seedchecker.worldgen.biome.BiomeTileGrid;
import com.scrimchic.seedchecker.worldgen.biome.BiomeTileKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Profiles the biome render path: how many rectangles a frame costs today, and how many it would
 * cost with greedy two-dimensional merging. Throwaway measurement harness, not part of
 * {@code build}: {@code ./gradlew :<target>:worldgenSpike -PspikeMain=spike.BiomeRenderSpike}.
 */
public final class BiomeRenderSpike {

    private static final long SEED = -7407337299659424542L;
    private static final int SCREEN_WIDTH = 1920;
    private static final int SCREEN_HEIGHT = 1080;

    private static String version() {
        return /*$ minecraft*/ "unknown";
    }

    public static void main(String[] args) {
        //? if >=1.18 {
        net.minecraft.SharedConstants.tryDetectVersion();
        //?}
        net.minecraft.server.Bootstrap.bootStrap();

        System.out.println("=== biome render profiling ===");
        System.out.println("minecraft   " + version());
        System.out.println("screen      " + SCREEN_WIDTH + "x" + SCREEN_HEIGHT);
        System.out.println();

        BiomeWorldgenSession session =
                BiomeWorldgenSession.create(SEED, BiomeWorldgenSession.OVERWORLD);

        System.out.printf("%-9s %-7s %-9s %-14s %-14s %-8s %-14s %s%n",
                "level", "tiles", "samples", "BEFORE rects", "AFTER rects", "saving",
                "BEFORE ms/fr", "AFTER ms/fr");

        for (BiomeSampleLevel level : BiomeSampleLevel.values()) {
            if (level.blockStep() > BiomeWorldgenSession.coarsestBlockStep()) {
                System.out.printf("%-9s (over this version's affordable sample step)%n", level);
                continue;
            }
            profile(session, level, 4.0);
        }

        // How much does a coarser on-screen sample size buy? Same data, fewer cells.
        System.out.println();
        System.out.println("=== sweeping pixels per sample (level NEAR, step 4) ===");
        System.out.printf("%-9s %-7s %-9s %-13s %-14s %s%n",
                "px/sample", "tiles", "samples", "row-RLE rects", "2D-merge rects", "saving");
        for (double pixels : new double[] {4.0, 6.0, 8.0, 12.0, 16.0}) {
            profileAt(session, 4, pixels);
        }
    }

    private static void profileAt(BiomeWorldgenSession session, int step, double pixelsPerSample) {
        double scale = pixelsPerSample / step;
        Counts counts = collect(session, step, scale);
        System.out.printf("%-9.0f %-7d %-9d %-13d %-14d %.1fx%n",
                pixelsPerSample, counts.tiles, counts.samples, counts.rowRects, counts.mergedRects,
                counts.rowRects / (double) Math.max(1, counts.mergedRects));
    }

    private static void profile(BiomeWorldgenSession session, BiomeSampleLevel level,
                                double pixelsPerSample) {
        int step = level.blockStep();
        Counts counts = collect(session, step, pixelsPerSample / step);

        System.out.printf("%-9s %-7d %-9d %-14d %-14d %-8s %-14.2f %.2f%n",
                level, counts.tiles, counts.samples, counts.rowRects, counts.mergedRects,
                String.format("%.1fx", counts.rowRects / (double) Math.max(1, counts.mergedRects)),
                counts.scanMs, counts.emitMs);
    }

    private static final class Counts {
        int tiles;
        int samples;
        int rowRects;
        int mergedRects;
        double scanMs;
        double emitMs;
    }

    private static Counts collect(BiomeWorldgenSession session, int step, double scale) {
        MapViewport viewport = new MapViewport();
        viewport.resize(SCREEN_WIDTH, SCREEN_HEIGHT);
        viewport.setScale(scale);
        viewport.setCenter(0.0, 0.0);

        int minTileX = BiomeTileGrid.tileOf((int) Math.floor(viewport.screenToBlockX(0)), step);
        int maxTileX = BiomeTileGrid.tileOf((int) Math.ceil(viewport.screenToBlockX(SCREEN_WIDTH)), step);
        int minTileZ = BiomeTileGrid.tileOf((int) Math.floor(viewport.screenToBlockZ(0)), step);
        int maxTileZ = BiomeTileGrid.tileOf((int) Math.ceil(viewport.screenToBlockZ(SCREEN_HEIGHT)), step);

        BiomeMapKey map = new BiomeMapKey("spike", SEED, BiomeWorldgenSession.OVERWORLD,
                version(), 64);

        List<BiomeTile> tiles = new ArrayList<BiomeTile>();
        for (int tz = minTileZ; tz <= maxTileZ; tz++) {
            for (int tx = minTileX; tx <= maxTileX; tx++) {
                tiles.add(generate(session, new BiomeTileKey(map, step, tx, tz)));
            }
        }

        Counts counts = new Counts();
        counts.tiles = tiles.size();
        int side = BiomeTileGrid.SAMPLES_PER_SIDE;
        counts.samples = tiles.size() * side * side;
        for (BiomeTile tile : tiles) {
            counts.rowRects += countRowRuns(tile);
            counts.mergedRects += tile.rectCount();
        }

        int rounds = 20;
        int sink = 0;

        // BEFORE: rescan every sample of every visible tile, every frame, to find row runs.
        long scanStart = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            for (BiomeTile tile : tiles) {
                sink += countRowRuns(tile);
            }
        }
        counts.scanMs = (System.nanoTime() - scanStart) / 1e6 / rounds;

        // AFTER: walk the rectangles the worker already merged, unpacking each one.
        long emitStart = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            for (BiomeTile tile : tiles) {
                int rectCount = tile.rectCount();
                for (int r = 0; r < rectCount; r++) {
                    int rect = tile.rect(r);
                    sink += BiomeTile.rectSampleX(rect) + BiomeTile.rectSampleZ(rect)
                            + BiomeTile.rectWidth(rect) + BiomeTile.rectHeight(rect)
                            + tile.paletteColor(BiomeTile.rectPaletteIndex(rect));
                }
            }
        }
        counts.emitMs = (System.nanoTime() - emitStart) / 1e6 / rounds;

        if (sink == Integer.MIN_VALUE) {
            System.out.println("unreachable");
        }
        return counts;
    }

    /** Exactly what BiomeLayer does today: one run per stretch of equal biome, per row. */
    private static int countRowRuns(BiomeTile tile) {
        int side = tile.samplesPerSide();
        int runs = 0;
        for (int z = 0; z < side; z++) {
            int current = tile.paletteIndexAt(0, z);
            for (int x = 1; x <= side; x++) {
                int index = x < side ? tile.paletteIndexAt(x, z) : -1;
                if (index != current) {
                    runs++;
                    current = index;
                }
            }
        }
        return runs;
    }

    private static BiomeTile generate(BiomeWorldgenSession session, BiomeTileKey key) {
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
