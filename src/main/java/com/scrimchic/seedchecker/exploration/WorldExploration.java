package com.scrimchic.seedchecker.exploration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the player recorded about one world, in memory.
 *
 * <p>Structure annotations by {@link StructureKey} and custom markers by id, for all dimensions of
 * the world together - every read that draws something filters by dimension, so a nether marker can
 * never be returned for the overworld.
 *
 * <p><strong>Single threaded.</strong> Owned by the client thread, which is where input, the tick
 * and drawing all happen, so no reader can ever see an edit half applied. Each edit replaces one
 * immutable value in a map; {@link #snapshot()} copies them out for storage. {@link #revision()}
 * counts edits, which is how the owner knows there is something to save.
 *
 * <p>An annotation that says nothing - unvisited, no note - is removed rather than kept, so the
 * data only ever grows with what the player actually recorded.
 */
public final class WorldExploration {

    private final Map<StructureKey, StructureAnnotation> structures =
            new LinkedHashMap<StructureKey, StructureAnnotation>();
    private final Map<String, CustomMarker> markers = new LinkedHashMap<String, CustomMarker>();

    /** Entries a newer Seed Checker wrote and this one cannot read; written back untouched. */
    private final List<String> preservedStructures = new ArrayList<String>();
    private final List<String> preservedMarkers = new ArrayList<String>();

    /** Markers per dimension, rebuilt on first read after a marker edit. */
    private Map<String, List<CustomMarker>> markersByDimension;

    private int revision;

    // ------------------------------------------------------------ structures

    /** @return the annotation for that structure, or {@code null} when nothing is recorded */
    public StructureAnnotation annotationOf(StructureKey key) {
        return structures.get(key);
    }

    /** @return the recorded status, {@link StructureStatus#UNVISITED} when there is none */
    public StructureStatus statusOf(StructureKey key) {
        StructureAnnotation annotation = structures.get(key);
        return annotation == null ? StructureStatus.UNVISITED : annotation.status();
    }

    /** @return the note, or {@code null} when there is none */
    public String noteOf(StructureKey key) {
        StructureAnnotation annotation = structures.get(key);
        return annotation == null ? null : annotation.note();
    }

    /** @return whether anything changed */
    public boolean setStatus(StructureKey key, StructureStatus status) {
        return put(current(key).withStatus(status));
    }

    /** @return whether anything changed; an empty or blank note removes the note */
    public boolean setNote(StructureKey key, String note) {
        return put(current(key).withNote(note));
    }

    private StructureAnnotation current(StructureKey key) {
        StructureAnnotation annotation = structures.get(key);
        return annotation != null ? annotation : StructureAnnotation.of(key, null, null);
    }

    private boolean put(StructureAnnotation annotation) {
        StructureAnnotation previous = structures.get(annotation.key());
        if (annotation.isEmpty()) {
            if (previous == null) {
                return false;
            }
            structures.remove(annotation.key());
        } else {
            if (annotation.equals(previous)) {
                return false;
            }
            structures.put(annotation.key(), annotation);
        }
        revision++;
        return true;
    }

    /**
     * Adds an annotation read back from storage.
     *
     * @return {@code false} when that structure already has one, which is then kept
     */
    public boolean restoreAnnotation(StructureAnnotation annotation) {
        if (annotation.isEmpty() || structures.containsKey(annotation.key())) {
            return false;
        }
        structures.put(annotation.key(), annotation);
        revision++;
        return true;
    }

    public int structureCount() {
        return structures.size();
    }

    /**
     * How many annotations belong to structures that are not predicted from this seed - kept, not
     * shown, and found again if the seed comes back.
     *
     * @param currentSeed the seed the map is drawn from, or {@code null} when there is none
     */
    public int structuresNotPredictedFrom(Long currentSeed) {
        int count = 0;
        for (StructureKey key : structures.keySet()) {
            if (key.hasSeed() && (currentSeed == null || key.seed() != currentSeed.longValue())) {
                count++;
            }
        }
        return count;
    }

    // --------------------------------------------------------------- markers

    /** @return the marker with that id, or {@code null} */
    public CustomMarker marker(String id) {
        return markers.get(id);
    }

    /** Every marker of one dimension, in creation order. Cheap after the first call. */
    public List<CustomMarker> markersIn(String dimensionId) {
        if (dimensionId == null) {
            return Collections.emptyList();
        }
        if (markersByDimension == null) {
            Map<String, List<CustomMarker>> index = new HashMap<String, List<CustomMarker>>();
            for (CustomMarker marker : markers.values()) {
                List<CustomMarker> list = index.get(marker.dimensionId());
                if (list == null) {
                    list = new ArrayList<CustomMarker>();
                    index.put(marker.dimensionId(), list);
                }
                list.add(marker);
            }
            for (Map.Entry<String, List<CustomMarker>> entry : index.entrySet()) {
                entry.setValue(Collections.unmodifiableList(entry.getValue()));
            }
            markersByDimension = index;
        }
        List<CustomMarker> list = markersByDimension.get(dimensionId);
        return list == null ? Collections.<CustomMarker>emptyList() : list;
    }

    public int markerCount() {
        return markers.size();
    }

    /**
     * Adds a new marker, or replaces the marker with the same id - which is how a marker is moved,
     * renamed or retyped.
     *
     * @return whether anything changed
     */
    public boolean putMarker(CustomMarker marker) {
        if (marker.equals(markers.get(marker.id()))) {
            return false;
        }
        markers.put(marker.id(), marker);
        markersChanged();
        return true;
    }

    /** @return whether a marker was removed */
    public boolean removeMarker(String id) {
        if (id == null || markers.remove(id) == null) {
            return false;
        }
        markersChanged();
        return true;
    }

    /**
     * Adds a marker read back from storage. A marker whose id is already taken is kept under a new
     * id rather than dropped or allowed to overwrite the first.
     *
     * @return the marker as added, or {@code null} when it was an exact duplicate
     */
    public CustomMarker restoreMarker(CustomMarker marker) {
        CustomMarker existing = markers.get(marker.id());
        if (existing != null) {
            if (existing.equals(marker)) {
                return null;
            }
            marker = marker.withId(CustomMarker.newId());
        }
        markers.put(marker.id(), marker);
        markersChanged();
        return marker;
    }

    private void markersChanged() {
        markersByDimension = null;
        revision++;
    }

    // ------------------------------------------------------ storage support

    /** Keeps a structure entry this version cannot read, so saving writes it back unchanged. */
    public void preserveStructureEntry(String json) {
        preservedStructures.add(json);
    }

    /** Keeps a marker entry this version cannot read, so saving writes it back unchanged. */
    public void preserveMarkerEntry(String json) {
        preservedMarkers.add(json);
    }

    /** Increases with every change; equal revisions mean equal content. */
    public int revision() {
        return revision;
    }

    public ExplorationSnapshot snapshot() {
        return new ExplorationSnapshot(new ArrayList<StructureAnnotation>(structures.values()),
                new ArrayList<CustomMarker>(markers.values()), preservedStructures, preservedMarkers);
    }
}
