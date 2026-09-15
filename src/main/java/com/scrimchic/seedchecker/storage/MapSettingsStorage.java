package com.scrimchic.seedchecker.storage;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

import com.scrimchic.seedchecker.core.map.MapPreferences;
import com.scrimchic.seedchecker.exploration.ExplorationFilters;
import com.scrimchic.seedchecker.exploration.MarkerType;
import com.scrimchic.seedchecker.exploration.StructureStatus;

/**
 * Reads and writes the client's map preferences.
 *
 * <pre>
 * &lt;root&gt;/map-settings.json
 * {
 *   "formatVersion": 1,
 *   "layers": {"mineshaft": false, "custom_markers": true},
 *   "structureStatuses": {"unvisited": true, "looted": false, ...},
 *   "markerTypes": {"base": true, "danger": false, ...}
 * }
 * </pre>
 *
 * <p>One file for the whole client, beside the per-world directories and never inside one: map
 * preferences are how the player wants to see any map, exploration data is what is in one world.
 *
 * <p>Every id is a stable string - layer ids, status and marker type storage ids - never a Java name.
 * An entry that is missing takes the code's default; one that is not a boolean is ignored; one whose
 * id is unknown is kept and written back. A corrupt file is copied aside and the defaults used, with
 * a warning; a newer {@code formatVersion} is read but not rewritten. Written atomically, whole.
 */
public final class MapSettingsStorage {

    static final int FORMAT_VERSION = 1;

    static final String FILE_NAME = "map-settings.json";

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final Logger LOGGER = Logger.getLogger("seedchecker");

    private final Path path;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** @param root the Seed Checker config directory, e.g. {@code .minecraft/config/seedchecker} */
    public MapSettingsStorage(Path root) {
        this.path = root.resolve(FILE_NAME);
    }

    public Path path() {
        return path;
    }

    /** What a load produced. */
    public static final class LoadResult {

        private final MapPreferences preferences;
        private final boolean writable;

        LoadResult(MapPreferences preferences, boolean writable) {
            this.preferences = preferences;
            this.writable = writable;
        }

        /** Never {@code null}: the defaults when nothing usable was on disk. */
        public MapPreferences preferences() {
            return preferences;
        }

        /** False only for a file of a newer format, which this version must not rewrite. */
        public boolean isWritable() {
            return writable;
        }
    }

    /** Never throws and never returns {@code null}. */
    public LoadResult load() {
        if (!Files.isRegularFile(path)) {
            return new LoadResult(new MapPreferences(), true);
        }
        JsonElement parsed;
        try {
            BufferedReader reader = Files.newBufferedReader(path, UTF_8);
            try {
                parsed = gson.fromJson(reader, JsonElement.class);
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not read " + path + "; using default map settings this session", e);
            return new LoadResult(new MapPreferences(), false);
        } catch (JsonParseException | IllegalStateException e) {
            return corrupt("malformed JSON", e);
        }
        if (parsed == null || !parsed.isJsonObject()) {
            return corrupt(parsed == null ? "an empty file" : "not a JSON object", null);
        }

        JsonObject root = parsed.getAsJsonObject();
        boolean writable = true;
        JsonElement version = root.get("formatVersion");
        if (version != null && version.isJsonPrimitive() && version.getAsJsonPrimitive().isNumber()
                && version.getAsInt() > FORMAT_VERSION) {
            LOGGER.warning(path + " is from a newer Seed Checker; it is read but not rewritten");
            writable = false;
        }

        MapPreferences preferences = new MapPreferences();
        ExplorationFilters filters = preferences.filters();
        for (Map.Entry<String, JsonElement> entry : entries(root, "layers")) {
            Boolean visible = booleanOrNull(entry.getValue());
            if (visible != null) {
                preferences.setLayerVisible(entry.getKey(), visible.booleanValue());
            }
        }
        for (Map.Entry<String, JsonElement> entry : entries(root, "structureStatuses")) {
            Boolean visible = booleanOrNull(entry.getValue());
            if (visible == null) {
                continue;
            }
            StructureStatus status = StructureStatus.fromId(entry.getKey());
            if (status != null) {
                filters.setStatusVisible(status, visible.booleanValue());
            } else {
                preferences.keepUnknownStatus(entry.getKey(), visible.booleanValue());
            }
        }
        for (Map.Entry<String, JsonElement> entry : entries(root, "markerTypes")) {
            Boolean visible = booleanOrNull(entry.getValue());
            if (visible == null) {
                continue;
            }
            MarkerType type = MarkerType.fromId(entry.getKey());
            if (type != null) {
                filters.setMarkerTypeVisible(type, visible.booleanValue());
            } else {
                preferences.keepUnknownMarkerType(entry.getKey(), visible.booleanValue());
            }
        }
        return new LoadResult(preferences, writable);
    }

    private LoadResult corrupt(String what, Exception cause) {
        Path aside = path.resolveSibling(FILE_NAME + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.copy(path, aside, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.log(Level.WARNING, path + " is " + what + "; copied it to " + aside.getFileName()
                    + " and using default map settings", cause);
            return new LoadResult(new MapPreferences(), true);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, path + " is " + what + " and could not be copied aside; "
                    + "using default map settings without saving this session", e);
            return new LoadResult(new MapPreferences(), false);
        }
    }

    /** @return whether the preferences are now what is on disk */
    public boolean save(MapPreferences preferences) {
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", FORMAT_VERSION);

        JsonObject layers = new JsonObject();
        for (Map.Entry<String, Boolean> entry : preferences.layerVisibility().entrySet()) {
            layers.addProperty(entry.getKey(), entry.getValue());
        }
        root.add("layers", layers);

        JsonObject statuses = new JsonObject();
        for (StructureStatus status : StructureStatus.values()) {
            statuses.addProperty(status.id(), preferences.filters().showsStatus(status));
        }
        for (Map.Entry<String, Boolean> entry : preferences.unknownStatuses().entrySet()) {
            statuses.addProperty(entry.getKey(), entry.getValue());
        }
        root.add("structureStatuses", statuses);

        JsonObject markerTypes = new JsonObject();
        for (MarkerType type : MarkerType.values()) {
            markerTypes.addProperty(type.id(), preferences.filters().showsMarkerType(type));
        }
        for (Map.Entry<String, Boolean> entry : preferences.unknownMarkerTypes().entrySet()) {
            markerTypes.addProperty(entry.getKey(), entry.getValue());
        }
        root.add("markerTypes", markerTypes);

        try {
            AtomicFiles.write(path, gson.toJson(root));
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not save map settings to " + path, e);
            return false;
        }
    }

    private static Iterable<Map.Entry<String, JsonElement>> entries(JsonObject root, String name) {
        JsonElement element = root.get(name);
        if (element == null || !element.isJsonObject()) {
            return java.util.Collections.<Map.Entry<String, JsonElement>>emptySet();
        }
        return element.getAsJsonObject().entrySet();
    }

    private static Boolean booleanOrNull(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        return primitive.isBoolean() ? Boolean.valueOf(primitive.getAsBoolean()) : null;
    }
}
