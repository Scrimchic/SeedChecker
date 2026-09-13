package com.scrimchic.seedchecker.client.structure;

import java.util.logging.Level;

import com.scrimchic.seedchecker.SeedChecker;
import com.scrimchic.seedchecker.client.worldgen.WorldgenWorkers;
import com.scrimchic.seedchecker.platform.BiomeWorldgenSession;
import com.scrimchic.seedchecker.platform.StructureGeometryGenerator;
import com.scrimchic.seedchecker.worldgen.StructureGeometry;
import com.scrimchic.seedchecker.worldgen.StructureGeometryStore;
import com.scrimchic.seedchecker.worldgen.StructureValidationKey;
import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;

/**
 * Assembles the pieces of a structure in the background when its geometry is actually wanted - in
 * practice, when the developer selects its marker.
 *
 * <p>Lazy because it has to be. Measured per real structure, assembly took about a second for a
 * plains village and up to four, 30 to 50 ms for an ancient city and 100 ms or more for a trial
 * chamber; a single screenful at 16 blocks per pixel shows over a hundred villages. Validation
 * already says whether a structure exists and where it starts, and nothing about the map needs its
 * full extent until someone looks at it.
 *
 * <p>Runs in its own worker lane, so a selected village is neither stuck behind a queue of
 * structure checks nor able to hold biome tiles back by more than one job per worker. The render
 * thread only ever asks "is it known?" and "please assemble it"; both return at once.
 */
public final class StructureGeometryManager {

    /** A few dozen recently selected structures. */
    private static final int MAX_CACHED_RESULTS = 64;

    /** Selection is one structure at a time; two leaves room for a quick change of mind. */
    private static final int MAX_PENDING = 2;

    private static StructureGeometryManager instance;

    private final StructureGeometryStore store =
            new StructureGeometryStore(MAX_CACHED_RESULTS, MAX_PENDING);

    private StructureGeometryManager() {
    }

    public static void initClient() {
        instance = new StructureGeometryManager();
    }

    public static StructureGeometryManager get() {
        if (instance == null) {
            throw new IllegalStateException("Structure geometry is client side only "
                    + "and is set up from the client entrypoint");
        }
        return instance;
    }

    /** Declares which world is being drawn; a change drops every result and stale work. */
    public void useMap(BiomeMapKey map) {
        store.useMap(map);
    }

    /** @return the geometry, or {@code null} while it is not known. Never blocks. */
    public StructureGeometry resultIfReady(StructureValidationKey key) {
        return store.resultIfReady(key);
    }

    /**
     * Asks for a structure's geometry, unless it is known or already being assembled.
     *
     * @param variant the structure entry validation found vanilla builds there, or {@code null}
     *                where the version has no entries
     * @return whether a job was submitted
     */
    public boolean request(final StructureValidationKey key, final String variant) {
        final int jobGeneration = store.claim(key);
        if (jobGeneration == StructureGeometryStore.NO_JOB) {
            return false;
        }
        boolean submitted = WorldgenWorkers.get().submit(key.map(),
                WorldgenWorkers.Lane.STRUCTURE_GEOMETRY,
                session -> assemble(session, key, variant, jobGeneration));
        if (!submitted) {
            store.release(key);
        }
        return submitted;
    }

    private void assemble(BiomeWorldgenSession session, StructureValidationKey key, String variant,
                          int jobGeneration) {
        try {
            if (store.isStale(jobGeneration)) {
                return;
            }
            StructureGeometry geometry = StructureGeometryGenerator.generate(
                    session, key.type(), variant, key.chunkX(), key.chunkZ());
            if (geometry == null) {
                // Structure data still loading on another worker; asked for again next frame.
                return;
            }
            store.store(key, geometry, jobGeneration);
        } catch (Throwable failure) {
            // Filed as unavailable, so a structure that cannot be assembled is not retried per
            // frame; logged once for the same reason.
            store.store(key, StructureGeometry.unavailable("assembly failed"), jobGeneration);
            SeedChecker.LOGGER.log(Level.WARNING, "Assembling " + key + " failed", failure);
        } finally {
            store.release(key);
        }
    }
}
