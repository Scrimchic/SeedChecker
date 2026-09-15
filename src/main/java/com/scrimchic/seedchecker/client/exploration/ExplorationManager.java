package com.scrimchic.seedchecker.client.exploration;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import com.scrimchic.seedchecker.exploration.CustomMarker;
import com.scrimchic.seedchecker.exploration.MarkerType;
import com.scrimchic.seedchecker.exploration.StructureAnnotation;
import com.scrimchic.seedchecker.exploration.StructureKey;
import com.scrimchic.seedchecker.exploration.StructureStatus;
import com.scrimchic.seedchecker.exploration.WorldExploration;
import com.scrimchic.seedchecker.storage.ExplorationStorage;
import com.scrimchic.seedchecker.world.WorldIdentity;

/**
 * The exploration data of the world the player is in: loaded when its profile becomes active,
 * edited in memory, written back shortly after it changes, and dropped when the player leaves.
 *
 * <p>Driven by {@code WorldProfileManager}, which already decides which world is active, so the two
 * can never disagree about it; this class has no Minecraft types and no Fabric hooks of its own.
 *
 * <h2>Threads and disk</h2>
 *
 * <p>Everything here runs on the client thread - input, the tick and drawing - so there is no lock:
 * a draw sees an edit entirely or not at all. The disk is read once per world activation and written
 * from {@link #tick()} only, one second after the last edit, so a burst of clicks is one write and
 * no frame ever waits on a file. Switching world, leaving it and closing the game write at once.
 * The files are small - see {@code ExplorationPerformanceTest} - so a background writer would buy
 * nothing measurable.
 */
public final class ExplorationManager {

    /** How long after the last edit the tick writes; a burst of edits is one write. */
    static final long SAVE_DELAY_MILLIS = 1000L;

    /** After a failed write, how long before trying again, so a full disk is not hit every tick. */
    static final long RETRY_DELAY_MILLIS = 30_000L;

    private static final Logger LOGGER = Logger.getLogger("seedchecker");

    private static ExplorationManager instance;

    /** A clock, so tests can drive the save delay. */
    public interface Clock {
        long millis();
    }

    private final ExplorationStorage storage;
    private final Clock clock;

    private WorldIdentity activeIdentity;
    private WorldExploration active;
    private boolean writable;
    private int savedRevision;
    private long saveDueAt;

    public ExplorationManager(ExplorationStorage storage, Clock clock) {
        this.storage = storage;
        this.clock = clock;
    }

    /** @param configRoot the Seed Checker config directory exploration files live under */
    public static void initClient(Path configRoot) {
        instance = new ExplorationManager(new ExplorationStorage(configRoot), new Clock() {
            @Override
            public long millis() {
                return System.currentTimeMillis();
            }
        });
    }

    public static ExplorationManager get() {
        if (instance == null) {
            throw new IllegalStateException("Seed Checker exploration is client side only "
                    + "and is set up from the client entrypoint");
        }
        return instance;
    }

    /** @return the client's manager, or {@code null} where none was set up, e.g. in a unit test */
    public static ExplorationManager getIfInitialized() {
        return instance;
    }

    // ------------------------------------------------------------- lifecycle

    /** Makes that world's exploration the active one; nothing happens when it already is. */
    public void activate(WorldIdentity identity) {
        if (identity == null) {
            deactivate();
            return;
        }
        if (identity.equals(activeIdentity)) {
            return;
        }
        deactivate();
        ExplorationStorage.LoadResult loaded = storage.load(identity);
        activeIdentity = identity;
        active = loaded.exploration();
        writable = loaded.isWritable();
        savedRevision = active.revision();
        if (loaded.skippedEntries() > 0) {
            LOGGER.warning(loaded.skippedEntries() + " exploration entries of " + identity.displayName()
                    + " were not read; see the warnings above");
        }
    }

    /** Writes anything unsaved and forgets the active world. */
    public void deactivate() {
        flush();
        activeIdentity = null;
        active = null;
        writable = false;
    }

    /** Called every client tick: writes once the save delay after the last edit has passed. */
    public void tick() {
        if (isDirty() && clock.millis() >= saveDueAt) {
            flush();
        }
    }

    /**
     * Writes unsaved edits now.
     *
     * @return whether nothing is left unsaved
     */
    public boolean flush() {
        if (!isDirty()) {
            return true;
        }
        int revision = active.revision();
        if (storage.save(activeIdentity, active.snapshot())) {
            savedRevision = revision;
            return true;
        }
        saveDueAt = clock.millis() + RETRY_DELAY_MILLIS;
        return false;
    }

    public boolean isActive() {
        return active != null;
    }

    /** Whether edits are allowed: a world is active and its file is not from a newer format. */
    public boolean isWritable() {
        return active != null && writable;
    }

    /** Whether there are edits not yet on disk. */
    public boolean isDirty() {
        return active != null && writable && active.revision() != savedRevision;
    }

    /** @return the active world, or {@code null} */
    public WorldIdentity activeIdentity() {
        return activeIdentity;
    }

    // --------------------------------------------------------------- reading

    public StructureStatus statusOf(StructureKey key) {
        return active == null ? StructureStatus.UNVISITED : active.statusOf(key);
    }

    /** @return the note, or {@code null} */
    public String noteOf(StructureKey key) {
        return active == null ? null : active.noteOf(key);
    }

    /** @return the annotation, or {@code null} */
    public StructureAnnotation annotationOf(StructureKey key) {
        return active == null ? null : active.annotationOf(key);
    }

    /** Whether any structure is annotated at all; lets a draw skip every lookup when none is. */
    public boolean hasStructureAnnotations() {
        return active != null && active.structureCount() > 0;
    }

    /** @see WorldExploration#structuresNotPredictedFrom(Long) */
    public int structuresNotPredictedFrom(Long currentSeed) {
        return active == null ? 0 : active.structuresNotPredictedFrom(currentSeed);
    }

    public List<CustomMarker> markersIn(String dimensionId) {
        return active == null ? Collections.<CustomMarker>emptyList() : active.markersIn(dimensionId);
    }

    /** @return the marker, or {@code null} */
    public CustomMarker marker(String id) {
        return active == null ? null : active.marker(id);
    }

    // --------------------------------------------------------------- editing

    /** @return whether the status changed */
    public boolean setStatus(StructureKey key, StructureStatus status) {
        return isWritable() && edited(active.setStatus(key, status));
    }

    /** @return whether the note changed */
    public boolean setNote(StructureKey key, String note) {
        return isWritable() && edited(active.setNote(key, note));
    }

    /** @return the new marker, or {@code null} when nothing is editable */
    public CustomMarker createMarker(String dimensionId, int x, Integer y, int z, MarkerType type,
                                     String label, String note) {
        if (!isWritable()) {
            return null;
        }
        CustomMarker marker = CustomMarker.create(dimensionId, x, y, z, type, label, note);
        edited(active.putMarker(marker));
        return marker;
    }

    /**
     * Replaces an existing marker with a new value of the same id - moved, renamed, retyped.
     *
     * @return whether it changed; {@code false} for a marker that does not exist
     */
    public boolean updateMarker(CustomMarker marker) {
        return isWritable() && active.marker(marker.id()) != null && edited(active.putMarker(marker));
    }

    /** @return whether a marker was removed */
    public boolean removeMarker(String id) {
        return isWritable() && edited(active.removeMarker(id));
    }

    private boolean edited(boolean changed) {
        if (changed) {
            saveDueAt = clock.millis() + SAVE_DELAY_MILLIS;
        }
        return changed;
    }
}
