package com.scrimchic.seedchecker.core.map;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.scrimchic.seedchecker.exploration.ExplorationFilters;

/**
 * How the player wants the map drawn, the same in every world: which layers are on, and the
 * exploration filters.
 *
 * <p>Not what is in a world - that is exploration data, one file per world - and nothing from a
 * session: no selection, pointer or draft. Layers are remembered by their stable id, and only once
 * the player has set one: a layer without an entry takes its default from the code, so a layer added
 * in a later version is not switched off by an older file that never heard of it.
 *
 * <p>Ids this version does not know - a layer or filter value from a newer Seed Checker - are kept as
 * they were read and written back, and otherwise ignored.
 */
public final class MapPreferences {

    private final Map<String, Boolean> layers = new LinkedHashMap<String, Boolean>();
    private final ExplorationFilters filters = new ExplorationFilters();
    private final Map<String, Boolean> unknownStatuses = new LinkedHashMap<String, Boolean>();
    private final Map<String, Boolean> unknownMarkerTypes = new LinkedHashMap<String, Boolean>();

    /**
     * @param codeDefault what the layer is when the player never set it
     */
    public boolean isLayerVisible(String layerId, boolean codeDefault) {
        Boolean stored = layers.get(layerId);
        return stored == null ? codeDefault : stored.booleanValue();
    }

    public void setLayerVisible(String layerId, boolean visible) {
        layers.put(layerId, Boolean.valueOf(visible));
    }

    /** Every layer entry, known or not, in the order first set or read. */
    public Map<String, Boolean> layerVisibility() {
        return Collections.unmodifiableMap(layers);
    }

    public ExplorationFilters filters() {
        return filters;
    }

    /** Keeps a structure status id this version does not know, for writing back. */
    public void keepUnknownStatus(String id, boolean visible) {
        unknownStatuses.put(id, Boolean.valueOf(visible));
    }

    public Map<String, Boolean> unknownStatuses() {
        return Collections.unmodifiableMap(unknownStatuses);
    }

    /** Keeps a marker type id this version does not know, for writing back. */
    public void keepUnknownMarkerType(String id, boolean visible) {
        unknownMarkerTypes.put(id, Boolean.valueOf(visible));
    }

    public Map<String, Boolean> unknownMarkerTypes() {
        return Collections.unmodifiableMap(unknownMarkerTypes);
    }
}
