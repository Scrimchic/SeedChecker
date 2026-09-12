package com.scrimchic.seedchecker.client.structure;

import java.util.logging.Level;

import com.scrimchic.seedchecker.SeedChecker;
import com.scrimchic.seedchecker.client.worldgen.WorldgenWorkers;
import com.scrimchic.seedchecker.platform.BiomeWorldgenSession;
import com.scrimchic.seedchecker.platform.StructureBiomeValidator;
import com.scrimchic.seedchecker.worldgen.StructureValidation;
import com.scrimchic.seedchecker.worldgen.StructureValidationKey;
import com.scrimchic.seedchecker.worldgen.StructureValidationStore;
import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;

/**
 * Decides, in the background, which structure candidates vanilla's biome rules would keep.
 *
 * <p>Runs on the shared {@link WorldgenWorkers} pool, so it uses the same per-thread worldgen
 * session biome tiles do and works whether or not the biome layer is switched on.
 *
 * <p>The render thread only ever asks "is this candidate decided yet?" and "please decide it", and
 * both return immediately. All the state lives in {@link StructureValidationStore}, which is pure
 * and tested.
 */
public final class StructureValidationManager {

    /** A few screenfuls of candidates across every structure type. */
    private static final int MAX_CACHED_RESULTS = 4096;

    /** Queue bound, so a sudden pan cannot enqueue thousands of checks. */
    private static final int MAX_PENDING = 256;

    private static StructureValidationManager instance;

    private final StructureValidationStore store =
            new StructureValidationStore(MAX_CACHED_RESULTS, MAX_PENDING);

    private StructureValidationManager() {
    }

    public static void initClient() {
        instance = new StructureValidationManager();
    }

    public static StructureValidationManager get() {
        if (instance == null) {
            throw new IllegalStateException("Structure validation is client side only "
                    + "and is set up from the client entrypoint");
        }
        return instance;
    }

    // --------------------------------------------------------- render thread side

    /** Declares which world is being drawn; a change drops every decision and stale work. */
    public void useMap(BiomeMapKey map) {
        store.useMap(map);
    }

    /** @return the decision, or {@code null} while it is still unknown. Never blocks. */
    public StructureValidation resultIfReady(StructureValidationKey key) {
        return store.resultIfReady(key);
    }

    /**
     * Asks for a candidate to be validated, unless it is decided or already queued.
     *
     * @return whether a job was submitted
     */
    public boolean request(final StructureValidationKey key) {
        // Deliberately no "is this type supported" check here: answering it loads the version's
        // structure-biome data, which on 1.16.5 means initialising the whole builtin biome
        // registry. That must happen on a worker, never on the render thread.
        final int jobGeneration = store.claim(key);
        if (jobGeneration == StructureValidationStore.NO_JOB) {
            return false;
        }
        boolean submitted = WorldgenWorkers.get().submit(key.map(), new WorldgenWorkers.SessionTask() {
            @Override
            public void run(BiomeWorldgenSession session) {
                validate(session, key, jobGeneration);
            }
        });
        if (!submitted) {
            store.release(key);
        }
        return submitted;
    }

    public StructureValidationStore.Metrics metrics() {
        return store.metrics();
    }

    // --------------------------------------------------------------- worker side

    private void validate(BiomeWorldgenSession session, StructureValidationKey key,
                          int jobGeneration) {
        try {
            if (store.isStale(jobGeneration)) {
                return;
            }
            long start = System.nanoTime();
            // A structure this version has no biome rules for is shown rather than hidden: with
            // nothing to check against, filtering it would be guessing. validate() says so itself
            // for an unknown type, so there is no "supported?" branch here.
            StructureValidation validation =
                    StructureBiomeValidator.validate(session, key.type(), key.chunkX(), key.chunkZ());
            // Stored outside the sampling, so worldgen never runs while the store lock is held.
            store.store(key, validation, jobGeneration, System.nanoTime() - start);
        } catch (Throwable failure) {
            store.recordFailure();
            // Once per failing candidate, never per biome sample.
            SeedChecker.LOGGER.log(Level.WARNING, "Validating " + key + " failed", failure);
        } finally {
            store.release(key);
        }
    }
}
