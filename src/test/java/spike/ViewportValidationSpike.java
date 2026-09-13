package spike;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.core.util.FairWorkQueue;
import com.scrimchic.seedchecker.platform.BiomeWorldgenSession;
import com.scrimchic.seedchecker.platform.StructureBiomeValidator;
import com.scrimchic.seedchecker.platform.VanillaStructureData;
import com.scrimchic.seedchecker.worldgen.StructureCandidateVisitor;
import com.scrimchic.seedchecker.worldgen.StructurePlacementConfig;
import com.scrimchic.seedchecker.worldgen.StructurePlacementEngine;
import com.scrimchic.seedchecker.worldgen.StructurePlacements;
import com.scrimchic.seedchecker.worldgen.StructureType;
import com.scrimchic.seedchecker.worldgen.StructureValidation;

/**
 * Phase 3E-2 production measurement: what validating a realistic screenful of structure candidates
 * costs once the jigsaws are exact.
 *
 * <p>Uses the production validator, the production placement limits of {@code StructureLayer} and
 * the same worker count {@code WorldgenWorkers} picks at most, on real threads with one session per
 * thread. The numbers are the time to fill a fresh viewport from nothing; in game every result is
 * then cached.
 *
 * <p>Throwaway, not part of {@code build}:
 * {@code ./gradlew :26.2.x:worldgenSpike -PspikeMain=spike.ViewportValidationSpike}.
 */
public final class ViewportValidationSpike {

    private static final long SEED = -7407337299659424542L;

    /** A 1920x1080 window at GUI scale 2, which is what the map screen is laid out in. */
    private static final int SCREEN_WIDTH = 960;
    private static final int SCREEN_HEIGHT = 540;

    /** From StructureLayer: beyond this many regions the layer says "zoom in" and asks nothing. */
    private static final long MAX_REGIONS = 2048L;
    private static final int MAX_MARKERS = 1024;

    /** From WorldgenWorkers: min(3, cores / 2). */
    private static final int WORKERS = Math.max(1,
            Math.min(3, Runtime.getRuntime().availableProcessors() / 2));

    /** From StructureValidationManager: the queue bound a burst of checks sits behind. */
    private static final int MAX_PENDING = 256;

    private static String version() {
        return /*$ minecraft*/ "unknown";
    }

    private static final ThreadLocal<BiomeWorldgenSession> SESSIONS =
            new ThreadLocal<BiomeWorldgenSession>();

    public static void main(String[] args) throws Exception {
        System.out.println("=== 3E-2: viewport structure validation ===");
        System.out.println("minecraft   " + version());
        System.out.println("seed        " + SEED);
        System.out.println("workers     " + WORKERS);
        //? if >=1.18 {
        net.minecraft.SharedConstants.tryDetectVersion();
        //?}
        net.minecraft.server.Bootstrap.bootStrap();

        ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
        try {
            // Cold: sessions, structure data and templates all built by the first jobs.
            long heapBefore = usedHeap();
            long coldStart = System.nanoTime();
            run(pool, 0, 0, 0.25, false);
            System.out.printf("%ncold first screenful (0,0 at 4 blocks/px): %.0f ms wall, "
                            + "vanilla structure data loaded in %d ms%n",
                    (System.nanoTime() - coldStart) / 1e6, VanillaStructureData.loadMillis());
            System.out.printf("retained heap after it, crude gc delta: %.1f MB "
                            + "(structure data + %d sessions with their jigsaw generators)%n",
                    (usedHeap() - heapBefore) / 1048576.0, WORKERS);

            double[] scales = {1.0, 0.25, 1.0 / 16, 1.0 / 64};
            int[][] centres = {{0, 0}, {20000, -20000}};
            for (int[] centre : centres) {
                for (double scale : scales) {
                    run(pool, centre[0], centre[1], scale, true);
                }
            }

            System.out.println();
            System.out.println("--- biome tile latency behind one frame of structure checks ---");
            measureTileLatency(false);
            measureTileLatency(true);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
            VanillaStructureData.shutdown();
        }
    }

    /**
     * What a biome tile waits for when the map has just queued a frame's worth of checks.
     *
     * <p>Reproduces the render thread's first frame over an unexplored 16 blocks/px screen: each
     * structure layer requests up to 64 checks until the 256-job queue bound is hit, then the biome
     * layer requests its 32 tiles. A tile here is the production job - 32 by 32 biome samples at a
     * sixteen block step. One queue first in first out, as before, against two lanes in rotation.
     */
    private static void measureTileLatency(boolean fair) throws Exception {
        final FairWorkQueue queue = new FairWorkQueue(fair ? 2 : 1);
        final int tileLane = 0;
        final int checkLane = fair ? 1 : 0;
        Thread[] threads = new Thread[WORKERS];
        for (int i = 0; i < WORKERS; i++) {
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Runnable task;
                        while ((task = queue.take()) != null) {
                            task.run();
                        }
                    } catch (InterruptedException ignored) {
                        // Done.
                    }
                }
            });
            threads[i].start();
        }

        // Warm every worker's session and JIT before timing anything.
        final java.util.concurrent.CountDownLatch warm =
                new java.util.concurrent.CountDownLatch(WORKERS * 6);
        for (int i = 0; i < WORKERS * 6; i++) {
            final int index = i;
            queue.offer(tileLane, new Runnable() {
                @Override
                public void run() {
                    buildTile(40000 + index * 512, 40000);
                    validate(StructureType.VILLAGE, 1000 + index, 1000);
                    warm.countDown();
                }
            });
        }
        warm.await();

        StructurePlacements placements = StructurePlacements.forThisVersion();
        ChunkRange visible = ChunkRange.of(-7000 >> 4, -1000, -7000 / 16 + 960, -1000 + 540);
        List<int[]> checks = new ArrayList<int[]>();
        List<StructureType> checkTypes = new ArrayList<StructureType>();
        for (StructureType type : StructureType.values()) {
            if (!placements.supports(type) || checks.size() >= MAX_PENDING) {
                continue;
            }
            final List<int[]> chunks = new ArrayList<int[]>();
            new StructurePlacementEngine().forEachCandidate(SEED, placements.get(type), visible,
                    Math.min(64, MAX_PENDING - checks.size()), new StructureCandidateVisitor() {
                        @Override
                        public boolean visit(int chunkX, int chunkZ) {
                            chunks.add(new int[] {chunkX, chunkZ});
                            return true;
                        }
                    });
            for (int[] chunk : chunks) {
                checks.add(chunk);
                checkTypes.add(type);
            }
        }

        final java.util.concurrent.CountDownLatch checksDone =
                new java.util.concurrent.CountDownLatch(checks.size());
        final long start = System.nanoTime();
        for (int i = 0; i < checks.size(); i++) {
            final int[] chunk = checks.get(i);
            final StructureType type = checkTypes.get(i);
            queue.offer(checkLane, new Runnable() {
                @Override
                public void run() {
                    validate(type, chunk[0], chunk[1]);
                    checksDone.countDown();
                }
            });
        }
        final int tiles = 32;
        final long[] waited = new long[tiles];
        final java.util.concurrent.CountDownLatch tilesDone =
                new java.util.concurrent.CountDownLatch(tiles);
        final long tilesQueued = System.nanoTime();
        for (int i = 0; i < tiles; i++) {
            final int index = i;
            queue.offer(tileLane, new Runnable() {
                @Override
                public void run() {
                    buildTile(-80000 + index * 512, -80000);
                    waited[index] = System.nanoTime() - tilesQueued;
                    tilesDone.countDown();
                }
            });
        }
        tilesDone.await();
        double tilesMillis = (System.nanoTime() - tilesQueued) / 1e6;
        checksDone.await();
        double checksMillis = (System.nanoTime() - start) / 1e6;
        queue.close();
        for (Thread thread : threads) {
            thread.join();
        }

        java.util.Arrays.sort(waited);
        System.out.printf("  %-26s %d checks + %d tiles: tile done after median %.0f ms, "
                        + "max %.0f ms (all tiles %.0f ms); all checks done %.0f ms%n",
                fair ? "two lanes in rotation" : "one FIFO queue (before)", checks.size(), tiles,
                waited[tiles / 2] / 1e6, waited[tiles - 1] / 1e6, tilesMillis, checksMillis);
    }

    private static StructureValidation validate(StructureType type, int chunkX, int chunkZ) {
        BiomeWorldgenSession session = session();
        StructureValidation result;
        while ((result = StructureBiomeValidator.validate(session, type, chunkX, chunkZ)) == null) {
            Thread.yield();
        }
        return result;
    }

    /** The production biome tile job: 32 x 32 samples at a sixteen block step, y = 64. */
    private static void buildTile(int originX, int originZ) {
        BiomeWorldgenSession session = session();
        int sink = 0;
        for (int sampleZ = 0; sampleZ < 32; sampleZ++) {
            for (int sampleX = 0; sampleX < 32; sampleX++) {
                sink += session.sampleBiomeId(originX + sampleX * 16, 64, originZ + sampleZ * 16)
                        .length();
            }
        }
        if (sink < 0) {
            throw new IllegalStateException();
        }
    }

    private static BiomeWorldgenSession session() {
        BiomeWorldgenSession session = SESSIONS.get();
        if (session == null) {
            session = BiomeWorldgenSession.create(SEED, BiomeWorldgenSession.OVERWORLD);
            SESSIONS.set(session);
        }
        return session;
    }

    private static long usedHeap() {
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void run(ExecutorService pool, int centreX, int centreZ, double scale,
                            boolean print) throws Exception {
        int halfWidthBlocks = (int) Math.ceil(SCREEN_WIDTH / 2.0 / scale);
        int halfHeightBlocks = (int) Math.ceil(SCREEN_HEIGHT / 2.0 / scale);
        ChunkRange visible = ChunkRange.of((centreX - halfWidthBlocks) >> 4,
                (centreZ - halfHeightBlocks) >> 4, (centreX + halfWidthBlocks) >> 4,
                (centreZ + halfHeightBlocks) >> 4);

        if (print) {
            System.out.printf("%n--- centre %d,%d  %s  visible %d x %d chunks ---%n", centreX,
                    centreZ, scale >= 1 ? String.format("%.0f px/block", scale)
                            : String.format("%.0f blocks/px", 1 / scale),
                    visible.maxChunkX() - visible.minChunkX() + 1,
                    visible.maxChunkZ() - visible.minChunkZ() + 1);
            System.out.printf("  %-15s %8s %8s %11s %10s %9s%n", "structure", "cands", "shown",
                    "cpu ms", "ms each", "exact");
        }

        StructurePlacements placements = StructurePlacements.forThisVersion();
        long wallStart = System.nanoTime();
        List<TypeRun> runs = new ArrayList<TypeRun>();
        for (StructureType type : StructureType.values()) {
            if (!placements.supports(type)) {
                continue;
            }
            StructurePlacementConfig config = placements.get(type);
            if (StructurePlacementEngine.regionCount(config, visible) > MAX_REGIONS) {
                runs.add(new TypeRun(type, null));
                continue;
            }
            final List<int[]> chunks = new ArrayList<int[]>();
            new StructurePlacementEngine().forEachCandidate(SEED, config, visible, MAX_MARKERS,
                    new StructureCandidateVisitor() {
                        @Override
                        public boolean visit(int chunkX, int chunkZ) {
                            chunks.add(new int[] {chunkX, chunkZ});
                            return true;
                        }
                    });
            TypeRun typeRun = new TypeRun(type, chunks);
            for (final int[] chunk : chunks) {
                typeRun.futures.add(pool.submit(new ValidationJob(typeRun, chunk)));
            }
            runs.add(typeRun);
        }

        long totalNanos = 0;
        int totalJobs = 0;
        for (TypeRun typeRun : runs) {
            for (Future<StructureValidation> future : typeRun.futures) {
                StructureValidation result = future.get();
                if (!result.isRejected()) {
                    typeRun.shown++;
                }
            }
            totalNanos += typeRun.nanos.get();
            totalJobs += typeRun.futures.size();
        }
        double wallMillis = (System.nanoTime() - wallStart) / 1e6;
        if (!print) {
            return;
        }
        for (TypeRun typeRun : runs) {
            if (typeRun.chunks == null) {
                System.out.printf("  %-15s zoom in (over %d regions)%n",
                        typeRun.type.name().toLowerCase(), MAX_REGIONS);
                continue;
            }
            int count = typeRun.chunks.size();
            System.out.printf("  %-15s %8d %8d %11.0f %10.2f %9s%n", typeRun.type.name().toLowerCase(),
                    count, typeRun.shown, typeRun.nanos.get() / 1e6,
                    count == 0 ? 0.0 : typeRun.nanos.get() / 1e6 / count,
                    StructureBiomeValidator.isExact(typeRun.type) ? "yes" : "no");
        }
        double averageMillis = totalJobs == 0 ? 0 : totalNanos / 1e6 / totalJobs;
        System.out.printf("  all: %d checks, %.0f ms cpu, %.0f ms wall on %d workers%n", totalJobs,
                totalNanos / 1e6, wallMillis, WORKERS);
        System.out.printf("  a biome tile queued behind a full check queue waits about %.0f ms%n",
                Math.min(MAX_PENDING, totalJobs) * averageMillis / WORKERS);
    }

    private static final class TypeRun {
        final StructureType type;
        final List<int[]> chunks;
        final List<Future<StructureValidation>> futures = new ArrayList<Future<StructureValidation>>();
        final AtomicLong nanos = new AtomicLong();
        int shown;

        TypeRun(StructureType type, List<int[]> chunks) {
            this.type = type;
            this.chunks = chunks;
        }
    }

    private static final class ValidationJob implements java.util.concurrent.Callable<StructureValidation> {
        private final TypeRun typeRun;
        private final int[] chunk;

        ValidationJob(TypeRun typeRun, int[] chunk) {
            this.typeRun = typeRun;
            this.chunk = chunk;
        }

        @Override
        public StructureValidation call() {
            BiomeWorldgenSession session = SESSIONS.get();
            if (session == null) {
                session = BiomeWorldgenSession.create(SEED, BiomeWorldgenSession.OVERWORLD);
                SESSIONS.set(session);
            }
            long start = System.nanoTime();
            StructureValidation result;
            // Pending means another worker is still loading the data pack; production files
            // nothing and asks again later, which this loop reproduces by retrying.
            while ((result = StructureBiomeValidator.validate(session, typeRun.type, chunk[0],
                    chunk[1])) == null) {
                Thread.yield();
            }
            typeRun.nanos.addAndGet(System.nanoTime() - start);
            return result;
        }
    }
}
