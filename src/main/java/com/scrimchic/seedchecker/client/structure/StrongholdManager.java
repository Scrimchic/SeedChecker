package com.scrimchic.seedchecker.client.structure;

import java.util.List;
import java.util.logging.Level;

import com.scrimchic.seedchecker.SeedChecker;
import com.scrimchic.seedchecker.client.worldgen.WorldgenWorkers;
import com.scrimchic.seedchecker.platform.BiomeWorldgenSession;
import com.scrimchic.seedchecker.platform.StrongholdLocator;
import com.scrimchic.seedchecker.worldgen.StrongholdListStore;
import com.scrimchic.seedchecker.worldgen.StrongholdPosition;
import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;

/**
 * Computes a world's strongholds in the background, once per map, and keeps them in memory.
 *
 * <p>One job for the whole list, because that is how vanilla places them - measured at around five
 * seconds on a single worker, nearly all of it the biome search around each ring position. The
 * render thread only asks "is the list known?" and "please compute it"; both return at once.
 */
public final class StrongholdManager {

    private static StrongholdManager instance;

    private final StrongholdListStore store = new StrongholdListStore();

    private StrongholdManager() {
    }

    public static void initClient() {
        instance = new StrongholdManager();
    }

    public static StrongholdManager get() {
        if (instance == null) {
            throw new IllegalStateException("Strongholds are client side only "
                    + "and are set up from the client entrypoint");
        }
        return instance;
    }

    /** Declares which map is drawn; a change drops the list and any job computing the old one. */
    public void useMap(BiomeMapKey map) {
        store.useMap(map);
    }

    /** @return the current map's strongholds in vanilla's order, or {@code null} while unknown. */
    public List<StrongholdPosition> positionsIfReady() {
        return store.positionsIfReady(currentMap);
    }

    /** @return the stronghold in that chunk of that map, or {@code null}. */
    public StrongholdPosition positionAt(BiomeMapKey map, int chunkX, int chunkZ) {
        return store.positionAt(map, chunkX, chunkZ);
    }

    public boolean isComputing() {
        return store.isPending();
    }

    public String failure() {
        return store.failure();
    }

    public long lastMillis() {
        return store.millis();
    }

    /** The map the last request was for, which is the map the panel reports on. */
    private volatile BiomeMapKey currentMap;

    /**
     * Asks for the list of a map, unless it is known, being computed, or failed.
     *
     * <p>The caller must only ask for a dimension {@link BiomeWorldgenSession#supportsDimension}
     * accepts: a worker skips a job it cannot build a session for.
     *
     * @return whether a job was submitted
     */
    public boolean request(final BiomeMapKey map) {
        currentMap = map;
        final int jobGeneration = store.claim(map);
        if (jobGeneration == StrongholdListStore.NO_JOB) {
            return false;
        }
        boolean submitted = WorldgenWorkers.get().submit(map,
                WorldgenWorkers.Lane.STRUCTURE_CHECKS, session -> locate(session, jobGeneration));
        if (!submitted) {
            store.release(jobGeneration);
        }
        return submitted;
    }

    private void locate(BiomeWorldgenSession session, int jobGeneration) {
        try {
            if (store.isStale(jobGeneration)) {
                return;
            }
            long start = System.nanoTime();
            List<StrongholdPosition> positions = StrongholdLocator.locate(session);
            long millis = (System.nanoTime() - start) / 1_000_000L;
            if (store.store(jobGeneration, positions, millis)) {
                SeedChecker.LOGGER.info("Located " + positions.size() + " strongholds in "
                        + millis + " ms");
            }
        } catch (Throwable failure) {
            store.storeFailure(jobGeneration, failure.getClass().getSimpleName());
            SeedChecker.LOGGER.log(Level.WARNING, "Locating strongholds failed", failure);
        } finally {
            store.release(jobGeneration);
        }
    }
}
