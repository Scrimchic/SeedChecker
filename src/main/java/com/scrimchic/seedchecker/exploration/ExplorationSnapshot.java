package com.scrimchic.seedchecker.exploration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One consistent, immutable copy of a world's exploration data, for writing to disk.
 *
 * <p>Taken on the client thread in one call, so it can never contain half of an edit; every
 * element is itself immutable, so it may be handed to any thread afterwards.
 */
public final class ExplorationSnapshot {

    private final List<StructureAnnotation> structures;
    private final List<CustomMarker> markers;
    private final List<String> preservedStructures;
    private final List<String> preservedMarkers;

    ExplorationSnapshot(List<StructureAnnotation> structures, List<CustomMarker> markers,
                        List<String> preservedStructures, List<String> preservedMarkers) {
        this.structures = Collections.unmodifiableList(new ArrayList<StructureAnnotation>(structures));
        this.markers = Collections.unmodifiableList(new ArrayList<CustomMarker>(markers));
        this.preservedStructures = Collections.unmodifiableList(new ArrayList<String>(preservedStructures));
        this.preservedMarkers = Collections.unmodifiableList(new ArrayList<String>(preservedMarkers));
    }

    public List<StructureAnnotation> structures() {
        return structures;
    }

    public List<CustomMarker> markers() {
        return markers;
    }

    /** Structure entries this version could not read, as the JSON text they were stored as. */
    public List<String> preservedStructures() {
        return preservedStructures;
    }

    /** Marker entries this version could not read, as the JSON text they were stored as. */
    public List<String> preservedMarkers() {
        return preservedMarkers;
    }

    /** Whether there is nothing at all to store. */
    public boolean isEmpty() {
        return structures.isEmpty() && markers.isEmpty() && preservedStructures.isEmpty()
                && preservedMarkers.isEmpty();
    }
}
