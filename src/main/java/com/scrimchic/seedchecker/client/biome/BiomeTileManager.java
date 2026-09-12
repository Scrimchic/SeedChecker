package com.scrimchic.seedchecker.client.biome;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import com.scrimchic.seedchecker.SeedChecker;
import com.scrimchic.seedchecker.platform.BiomeWorldgenSession;
import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;
import com.scrimchic.seedchecker.worldgen.biome.BiomeTile;
import com.scrimchic.seedchecker.worldgen.biome.BiomeTileGrid;
import com.scrimchic.seedchecker.worldgen.biome.BiomeTileKey;
import com.scrimchic.seedchecker.worldgen.biome.BiomeTileStore;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

/**
 * Runs biome tile generation on background workers.
 *
 * <p>Deliberately thin: every decision about what to generate, what to keep and what to throw away
 * lives in {@link BiomeTileStore}, which is pure and tested. What is left here is the executor, the
 * per-thread {@link BiomeWorldgenSession} and the sampling loop.
 *
 * <p>The render thread only ever asks "is this tile ready?" and "please start it", and both return
 * immediately. There is no {@code get()}, {@code join()} or sleep anywhere on the render path; a
 * tile that is not ready is simply not drawn this frame.
 *
 * <p>Client side only: it reaches Minecraft through {@link BiomeWorldgenSession}, so it is set up
 * from the client entrypoint and nothing on a dedicated server can reach it.
 */
public final class BiomeTileManager {

    /** About 1 KB per tile, so roughly half a megabyte of biome map held in memory. */
    private static final int MAX_CACHED_TILES = 512;

    /** Queue bound, so a sudden change of zoom cannot enqueue thousands of tiles. */
    private static final int MAX_PENDING_TILES = 128;

    private static final int MAX_WORKERS = 3;

    private static BiomeTileManager instance;

    private final ExecutorService workers;
    private final BiomeTileStore store =
            new BiomeTileStore(MAX_CACHED_TILES, MAX_PENDING_TILES);

    /** Each worker keeps its own session and replaces it when the world changes. */
    private final ThreadLocal<ThreadSession> threadSession = new ThreadLocal<ThreadSession>();

    private BiomeTileManager(int workerCount) {
        this.workers = Executors.newFixedThreadPool(workerCount, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "seedchecker-biome-" + counter.incrementAndGet());
                // Daemon and low priority: biome tiles must never keep Minecraft from exiting, and
                // must never compete with the render thread.
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            }
        });
    }

    public static void initClient() {
        int workerCount = Math.max(1,
                Math.min(MAX_WORKERS, Runtime.getRuntime().availableProcessors() / 2));
        final BiomeTileManager manager = new BiomeTileManager(workerCount);
        instance = manager;
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> manager.shutdown());
        SeedChecker.LOGGER.info("Biome tile engine started with " + workerCount + " worker(s).");
    }

    public static BiomeTileManager get() {
        if (instance == null) {
            throw new IllegalStateException("Biome tiles are client side only "
                    + "and are set up from the client entrypoint");
        }
        return instance;
    }

    // --------------------------------------------------------- render thread side

    /** Declares which biome map is being drawn; a change drops the cache and stale work. */
    public void useMap(BiomeMapKey map) {
        store.useMap(map);
    }

    /** @return the tile if it is already generated, otherwise {@code null}. Never blocks. */
    public BiomeTile tileIfReady(BiomeTileKey key) {
        return store.tileIfReady(key);
    }

    /**
     * Asks for a tile, unless it is cached, queued, or the queue is full.
     *
     * @return whether a job was submitted
     */
    public boolean request(final BiomeTileKey key) {
        final int jobGeneration = store.claim(key);
        if (jobGeneration == BiomeTileStore.NO_JOB) {
            return false;
        }
        try {
            workers.execute(() -> runJob(key, jobGeneration));
            return true;
        } catch (RejectedExecutionException shuttingDown) {
            store.release(key);
            return false;
        }
    }

    public BiomeTileStore.Metrics metrics() {
        return store.metrics();
    }

    public void shutdown() {
        workers.shutdownNow();
    }

    // --------------------------------------------------------------- worker side

    private void runJob(BiomeTileKey key, int jobGeneration) {
        try {
            if (store.isStale(jobGeneration)) {
                return;
            }
            BiomeWorldgenSession session = sessionFor(key.map());
            if (session == null) {
                return;
            }

            long start = System.nanoTime();
            BiomeTile tile = generate(key, session);
            store.store(key, tile, jobGeneration, System.nanoTime() - start);
        } catch (Throwable failure) {
            store.recordFailure();
            // Once per failing tile, never per sample: a broken session would otherwise write a
            // thousand lines per tile.
            SeedChecker.LOGGER.log(Level.WARNING, "Biome tile " + key + " failed", failure);
        } finally {
            store.release(key);
        }
    }

    private BiomeWorldgenSession sessionFor(BiomeMapKey map) {
        ThreadSession held = threadSession.get();
        if (held == null) {
            held = new ThreadSession();
            threadSession.set(held);
        }
        if (!map.equals(held.map)) {
            held.session = BiomeWorldgenSession.create(map.seed(), map.dimensionId());
            // Recorded even when the session came back null, so an unsupported dimension is not
            // retried once per tile.
            held.map = map;
        }
        return held.session;
    }

    private static BiomeTile generate(BiomeTileKey key, BiomeWorldgenSession session) {
        BiomeTile.Builder builder = BiomeTile.builder(key);
        int step = key.blockStep();
        int originX = key.originBlockX();
        int originZ = key.originBlockZ();
        int sampleY = key.map().sampleY();
        int side = BiomeTileGrid.SAMPLES_PER_SIDE;

        for (int sampleZ = 0; sampleZ < side; sampleZ++) {
            int blockZ = originZ + sampleZ * step;
            for (int sampleX = 0; sampleX < side; sampleX++) {
                builder.set(sampleX, sampleZ,
                        session.sampleBiomeId(originX + sampleX * step, sampleY, blockZ));
            }
        }
        return builder.build();
    }

    private static final class ThreadSession {
        private BiomeMapKey map;
        private BiomeWorldgenSession session;
    }
}
