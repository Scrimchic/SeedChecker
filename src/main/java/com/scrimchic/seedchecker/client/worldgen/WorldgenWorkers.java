package com.scrimchic.seedchecker.client.worldgen;

import java.util.logging.Level;

import com.scrimchic.seedchecker.SeedChecker;
import com.scrimchic.seedchecker.core.util.FairWorkQueue;
import com.scrimchic.seedchecker.platform.BiomeWorldgenSession;
import com.scrimchic.seedchecker.platform.VanillaStructureData;
import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

/**
 * The background workers everything vanilla-backed runs on, and the one
 * {@link BiomeWorldgenSession} each of them owns.
 *
 * <p>Biome tiles and structure validation both need the same thing - a worldgen session for the
 * world being looked at - and a session is not safe to share between threads. So there is one pool,
 * and one session per worker thread, replaced when the world changes. Two pools would mean twice
 * the sessions, twice the registry memory, and twice the setup cost for no benefit.
 *
 * <h2>Two lanes, taken in turn</h2>
 *
 * <p>One pool, but not one first-in-first-out queue. Exact jigsaw validation made a village check
 * cost around 20 ms, and a screenful at 16 blocks per pixel queues hundreds of them; measured behind
 * a full queue of checks, a biome tile waited several hundred milliseconds before it started. So
 * tiles and structure checks each have a {@link Lane}, and the workers take from the two in
 * rotation through {@link FairWorkQueue}: whichever kind is behind waits for at most one job of the
 * other kind per worker, never for the other's whole queue. Neither lane has priority, so neither
 * can starve the other.
 *
 * <p>Client side only: it reaches Minecraft through {@link BiomeWorldgenSession}, so it is created
 * from the client entrypoint and nothing on a dedicated server can reach it.
 */
public final class WorldgenWorkers {

    /** What kind of work a task is, which decides the queue it waits in. */
    public enum Lane {
        BIOME_TILES,
        STRUCTURE_CHECKS
    }

    private static final int MAX_WORKERS = 3;

    private static WorldgenWorkers instance;

    private final FairWorkQueue queue = new FairWorkQueue(Lane.values().length);

    /** Each worker keeps its own session and replaces it when the world changes. */
    private final ThreadLocal<ThreadSession> threadSession = new ThreadLocal<ThreadSession>();

    private final Thread[] threads;

    /** A unit of work that needs vanilla worldgen for one particular world. */
    public interface SessionTask {

        /** @param session never {@code null}; the task is skipped if no session could be built */
        void run(BiomeWorldgenSession session);
    }

    private WorldgenWorkers(int workerCount) {
        this.threads = new Thread[workerCount];
        for (int i = 0; i < workerCount; i++) {
            Thread thread = new Thread(this::workLoop, "seedchecker-worldgen-" + (i + 1));
            // Daemon and low priority: worldgen must never keep Minecraft from exiting, and must
            // never compete with the render thread.
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            threads[i] = thread;
        }
        for (Thread thread : threads) {
            thread.start();
        }
    }

    public static void initClient() {
        int workerCount = Math.max(1,
                Math.min(MAX_WORKERS, Runtime.getRuntime().availableProcessors() / 2));
        final WorldgenWorkers created = new WorldgenWorkers(workerCount);
        instance = created;
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> created.shutdown());
        SeedChecker.LOGGER.info("Worldgen workers started: " + workerCount);
    }

    public static WorldgenWorkers get() {
        if (instance == null) {
            throw new IllegalStateException("Worldgen workers are client side only "
                    + "and are set up from the client entrypoint");
        }
        return instance;
    }

    /**
     * Runs a task on a worker, handing it the session for that world.
     *
     * @return whether the task was accepted; {@code false} once shutting down
     */
    public boolean submit(final BiomeMapKey map, Lane lane, final SessionTask task) {
        return queue.offer(lane.ordinal(), () -> {
            BiomeWorldgenSession session = sessionFor(map);
            if (session != null) {
                task.run(session);
            }
        });
    }

    private void workLoop() {
        try {
            Runnable task;
            while ((task = queue.take()) != null) {
                try {
                    task.run();
                } catch (Throwable failure) {
                    // Jobs handle their own failures; this only catches one that escaped, so a bad
                    // job cannot take a worker down with it.
                    SeedChecker.LOGGER.log(Level.WARNING, "A worldgen task failed", failure);
                }
            }
        } catch (InterruptedException shuttingDown) {
            // The client is stopping.
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
            // retried once per job.
            held.map = map;
        }
        return held.session;
    }

    public void shutdown() {
        queue.close();
        for (Thread thread : threads) {
            thread.interrupt();
        }
        // After the workers: a load still running on one of them closes its own result.
        VanillaStructureData.shutdown();
    }

    private static final class ThreadSession {
        private BiomeMapKey map;
        private BiomeWorldgenSession session;
    }
}
