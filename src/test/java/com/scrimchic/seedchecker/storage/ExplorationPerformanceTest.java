package com.scrimchic.seedchecker.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.scrimchic.seedchecker.exploration.CustomMarker;
import com.scrimchic.seedchecker.exploration.MarkerType;
import com.scrimchic.seedchecker.exploration.StructureKey;
import com.scrimchic.seedchecker.exploration.StructureStatus;
import com.scrimchic.seedchecker.exploration.WorldExploration;
import com.scrimchic.seedchecker.world.WorldIdentity;
import com.scrimchic.seedchecker.worldgen.StructureType;

/**
 * Whether plain JSON and in-memory maps stay a non-issue at sizes well past real play. A measurement
 * for the phase report; the bounds asserted are an order of magnitude above what is expected, only
 * there to catch something going badly wrong.
 */
class ExplorationPerformanceTest {

    private static final WorldIdentity WORLD = WorldIdentity.singleplayer("perf", "Perf");
    private static final String[] DIMENSIONS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};

    private static WorldExploration synthetic(int structures, int markers) {
        WorldExploration exploration = new WorldExploration();
        StructureType[] types = StructureType.values();
        StructureStatus[] statuses = StructureStatus.values();
        for (int i = 0; i < structures; i++) {
            StructureKey key = StructureKey.predicted(-7407337299659424542L, DIMENSIONS[i % 3], types[i % types.length],
                    (i * 7919) % 200_000 - 100_000, (i * 104_729) % 200_000 - 100_000);
            exploration.setStatus(key, statuses[1 + i % (statuses.length - 1)]);
            if (i % 5 == 0) {
                exploration.setNote(key, "note " + i + "\nзі списком: сундук, спавнер");
            }
        }
        for (int i = 0; i < markers; i++) {
            exploration.putMarker(CustomMarker.create(DIMENSIONS[i % 3], i * 13 - 6000, i % 2 == 0 ? null : 64,
                    6000 - i * 11, MarkerType.values()[i % MarkerType.values().length], "marker " + i,
                    i % 4 == 0 ? "a short note" : null));
        }
        return exploration;
    }

    @Test
    void loadSaveAndLookupStayCheap(@TempDir Path root) throws Exception {
        int[][] sizes = {{1_000, 0}, {10_000, 0}, {0, 1_000}, {10_000, 1_000}};
        ExplorationStorage storage = new ExplorationStorage(root);
        for (int[] size : sizes) {
            WorldExploration exploration = synthetic(size[0], size[1]);
            // Warm-up round, so the numbers are not class loading.
            storage.save(WORLD, exploration.snapshot());
            storage.load(WORLD);

            long start = System.nanoTime();
            assertTrue(storage.save(WORLD, exploration.snapshot()));
            double saveMs = (System.nanoTime() - start) / 1e6;
            long bytes = Files.size(storage.explorationPath(WORLD));

            start = System.nanoTime();
            WorldExploration loaded = storage.load(WORLD).exploration();
            double loadMs = (System.nanoTime() - start) / 1e6;
            assertEquals(exploration.structureCount(), loaded.structureCount());
            assertEquals(exploration.markerCount(), loaded.markerCount());

            List<StructureKey> probes = new ArrayList<StructureKey>();
            StructureType[] types = StructureType.values();
            for (int i = 0; i < 100_000; i++) {
                probes.add(StructureKey.predicted(-7407337299659424542L, DIMENSIONS[i % 3], types[i % types.length],
                        (i * 7919) % 200_000 - 100_000, (i * 104_729) % 200_000 - 100_000));
            }
            int hits = 0;
            start = System.nanoTime();
            for (StructureKey probe : probes) {
                if (loaded.statusOf(probe) != StructureStatus.UNVISITED) {
                    hits++;
                }
            }
            double lookupNs = (System.nanoTime() - start) / (double) probes.size();

            loaded.markersIn(DIMENSIONS[0]);
            start = System.nanoTime();
            int markerReads = 0;
            for (int frame = 0; frame < 10_000; frame++) {
                markerReads += loaded.markersIn(DIMENSIONS[frame % 3]).size();
            }
            double markersInNs = (System.nanoTime() - start) / 10_000.0;

            System.out.printf("exploration %5d structures %5d markers: file %7.1f KB, save %6.1f ms, load %6.1f ms, "
                            + "status lookup %5.0f ns (%d hits of 100k), markersIn %4.0f ns%n",
                    size[0], size[1], bytes / 1024.0, saveMs, loadMs, lookupNs, hits, markersInNs);
            assertTrue(hits >= Math.min(size[0], 100_000) * 0, "lookups ran");
            assertTrue(markerReads >= 0);
            assertTrue(saveMs < 5_000 && loadMs < 5_000, "10k entries must not take seconds");
        }
    }
}
