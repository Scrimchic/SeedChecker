package com.scrimchic.seedchecker.client.worldgen;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import com.scrimchic.seedchecker.SeedChecker;
import com.scrimchic.seedchecker.platform.BiomeWorldgenSession;
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
 * <p>Client side only: it reaches Minecraft through {@link BiomeWorldgenSession}, so it is created
 * from the client entrypoint and nothing on a dedicated server can reach it.
 */
public final class WorldgenWorkers {

    private static final int MAX_WORKERS = 3;

    private static WorldgenWorkers instance;

    private final ExecutorService workers;

    /** Each worker keeps its own session and replaces it when the world changes. */
    private final ThreadLocal<ThreadSession> threadSession = new ThreadLocal<ThreadSession>();

    /** A unit of work that needs vanilla worldgen for one particular world. */
    public interface SessionTask {

        /** @param session never {@code null}; the task is skipped if no session could be built */
        void run(BiomeWorldgenSession session);
    }

    private WorldgenWorkers(int workerCount) {
        this.workers = Executors.newFixedThreadPool(workerCount, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "seedchecker-worldgen-"
                        + counter.incrementAndGet());
                // Daemon and low priority: worldgen must never keep Minecraft from exiting, and
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
     * @return whether the task was accepted
     */
    public boolean submit(final BiomeMapKey map, final SessionTask task) {
        try {
            workers.execute(() -> {
                BiomeWorldgenSession session = sessionFor(map);
                if (session != null) {
                    task.run(session);
                }
            });
            return true;
        } catch (RejectedExecutionException shuttingDown) {
            return false;
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
        workers.shutdownNow();
    }

    private static final class ThreadSession {
        private BiomeMapKey map;
        private BiomeWorldgenSession session;
    }
}
