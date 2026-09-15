package com.scrimchic.seedchecker.client.map;

import java.nio.file.Path;

import com.scrimchic.seedchecker.core.map.MapPreferences;
import com.scrimchic.seedchecker.exploration.ExplorationFilters;
import com.scrimchic.seedchecker.storage.MapSettingsStorage;

/**
 * The client's map preferences: loaded once at start, changed by explicit clicks, and written at once
 * on each change. The file is a few hundred bytes, a click is not a frame, so there is no delay and no
 * background writer.
 *
 * <p>No Minecraft types; the map screen applies the preferences to its layers and reports back.
 */
public final class MapSettingsManager {

    private static MapSettingsManager instance;

    private final MapSettingsStorage storage;
    private final MapPreferences preferences;
    private final boolean writable;

    public MapSettingsManager(MapSettingsStorage storage) {
        this.storage = storage;
        MapSettingsStorage.LoadResult loaded = storage.load();
        this.preferences = loaded.preferences();
        this.writable = loaded.isWritable();
    }

    /** @param configRoot the Seed Checker config directory */
    public static void initClient(Path configRoot) {
        instance = new MapSettingsManager(new MapSettingsStorage(configRoot));
    }

    public static MapSettingsManager get() {
        if (instance == null) {
            throw new IllegalStateException("Seed Checker map settings are client side only "
                    + "and are set up from the client entrypoint");
        }
        return instance;
    }

    public MapPreferences preferences() {
        return preferences;
    }

    public ExplorationFilters filters() {
        return preferences.filters();
    }

    /** Records a layer switched on or off, and saves. */
    public void layerChanged(String layerId, boolean visible) {
        preferences.setLayerVisible(layerId, visible);
        save();
    }

    /**
     * Saves after a filter change.
     *
     * @return whether the preferences are on disk; always true for a file this version may not rewrite
     */
    public boolean save() {
        return !writable || storage.save(preferences);
    }
}
