package com.scrimchic.seedchecker.storage;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

import com.scrimchic.seedchecker.exploration.CustomMarker;
import com.scrimchic.seedchecker.exploration.ExplorationSnapshot;
import com.scrimchic.seedchecker.exploration.MarkerType;
import com.scrimchic.seedchecker.exploration.StructureAnnotation;
import com.scrimchic.seedchecker.exploration.StructureKey;
import com.scrimchic.seedchecker.exploration.StructureStatus;
import com.scrimchic.seedchecker.exploration.WorldExploration;
import com.scrimchic.seedchecker.world.WorldIdentity;

/**
 * Reads and writes a world's exploration data, next to its profile.
 *
 * <pre>
 * &lt;root&gt;/worlds/&lt;storage key&gt;/profile.json       what the world is
 * &lt;root&gt;/worlds/&lt;storage key&gt;/exploration.json   what the player recorded in it
 * </pre>
 *
 * <p>A file of its own rather than more fields in the profile: the profile is a handful of fields
 * rewritten on every join, exploration data grows with play and changes on its own schedule, and
 * keeping them apart means neither write can take the other with it. Later evidence data can have
 * files of its own in the same directory.
 *
 * <h2>Format, version 1</h2>
 *
 * <pre>
 * {
 *   "formatVersion": 1,
 *   "structures": [
 *     {"dimension": "minecraft:overworld", "type": "village", "chunkX": -3, "chunkZ": 12,
 *      "seed": "-7407337299659424542", "status": "looted", "note": "two chests"}
 *   ],
 *   "markers": [
 *     {"id": "0f8e...", "dimension": "minecraft:the_nether", "type": "portal",
 *      "x": 120, "y": 64, "z": -40, "label": "hub", "note": "..."}
 *   ]
 * }
 * </pre>
 *
 * <p>Every identifier is a canonical string: dimension ids as Minecraft spells them, structure
 * types, statuses and marker types by their stable ids. The seed is a string, so no JSON tool can
 * round it through a double; a structure without one is simply missing the field. {@code y},
 * {@code seed}, {@code label} and {@code note} are optional.
 *
 * <h2>What is never allowed to happen</h2>
 *
 * <ul>
 *   <li>A missing file is an empty exploration, and nothing is written until there is something to
 *       write.</li>
 *   <li>A corrupt file is copied aside as {@code exploration.json.corrupt-<time>} before anything
 *       can overwrite it, and the world starts empty - with a warning, not a crash.</li>
 *   <li>One unreadable entry does not cost the others. An entry naming a status, type or marker
 *       type this version does not know - written by a newer Seed Checker - is kept as it was and
 *       written back unchanged; an entry that is not an object at all is dropped with a warning.</li>
 *   <li>Unknown fields are ignored. A file of a newer {@code formatVersion} is loaded as far as it
 *       can be read and reported read-only, so this version never rewrites it.</li>
 * </ul>
 */
public final class ExplorationStorage {

    static final int FORMAT_VERSION = 1;

    private static final String WORLDS_DIRECTORY = "worlds";
    private static final String EXPLORATION_FILE = "exploration.json";
    private static final String CORRUPT_SUFFIX = ".corrupt-";

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /** By name, like {@link WorldProfileStorage}: this package stays loadable without Fabric. */
    private static final Logger LOGGER = Logger.getLogger("seedchecker");

    private final Path root;
    private final Gson gson;

    /** @param root the Seed Checker config directory, e.g. {@code .minecraft/config/seedchecker} */
    public ExplorationStorage(Path root) {
        this.root = root;
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    public Path explorationPath(WorldIdentity identity) {
        return root.resolve(WORLDS_DIRECTORY).resolve(identity.storageKey()).resolve(EXPLORATION_FILE);
    }

    /** What a load produced. */
    public static final class LoadResult {

        private final WorldExploration exploration;
        private final boolean writable;
        private final int skippedEntries;

        LoadResult(WorldExploration exploration, boolean writable, int skippedEntries) {
            this.exploration = exploration;
            this.writable = writable;
            this.skippedEntries = skippedEntries;
        }

        /** Never {@code null}. */
        public WorldExploration exploration() {
            return exploration;
        }

        /**
         * Whether saving is allowed. False only for a file of a newer format, which this version must
         * not rewrite in its own.
         */
        public boolean isWritable() {
            return writable;
        }

        /** Entries dropped, or kept unread for a newer version, while loading; for diagnostics. */
        public int skippedEntries() {
            return skippedEntries;
        }
    }

    /** Loads a world's exploration. Never throws and never returns {@code null}. */
    public LoadResult load(WorldIdentity identity) {
        Path path = explorationPath(identity);
        if (!Files.isRegularFile(path)) {
            return new LoadResult(new WorldExploration(), true, 0);
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
            // Unreadable, not necessarily corrupt: the file is left alone, and so is every later
            // save, since an empty exploration must not replace data that may still be fine.
            LOGGER.log(Level.WARNING, "Could not read " + path + "; exploration is read-only this session", e);
            return new LoadResult(new WorldExploration(), false, 0);
        } catch (JsonParseException | IllegalStateException e) {
            return corrupt(path, "malformed JSON", e);
        }
        if (parsed == null || !parsed.isJsonObject()) {
            return corrupt(path, parsed == null ? "an empty file" : "not a JSON object", null);
        }

        JsonObject rootObject = parsed.getAsJsonObject();
        int formatVersion = intOr(rootObject.get("formatVersion"), -1);
        boolean writable = true;
        if (formatVersion > FORMAT_VERSION) {
            LOGGER.warning(path + " has format version " + formatVersion + ", newer than "
                    + FORMAT_VERSION + "; it is read as far as possible and not rewritten");
            writable = false;
        } else if (formatVersion < 1) {
            LOGGER.warning(path + " has no valid format version; reading it as version " + FORMAT_VERSION);
        }

        WorldExploration exploration = new WorldExploration();
        int skipped = 0;
        JsonArray structures = arrayOrNull(rootObject.get("structures"));
        if (structures != null) {
            for (JsonElement element : structures) {
                if (!readStructure(element, exploration, path)) {
                    skipped++;
                }
            }
        }
        JsonArray markers = arrayOrNull(rootObject.get("markers"));
        if (markers != null) {
            for (JsonElement element : markers) {
                if (!readMarker(element, exploration, path)) {
                    skipped++;
                }
            }
        }
        return new LoadResult(exploration, writable, skipped);
    }

    private LoadResult corrupt(Path path, String what, Exception cause) {
        Path aside = path.resolveSibling(EXPLORATION_FILE + CORRUPT_SUFFIX + System.currentTimeMillis());
        try {
            Files.copy(path, aside, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.log(Level.WARNING, path + " is " + what + "; copied it to " + aside.getFileName()
                    + " and starting with empty exploration data", cause);
            return new LoadResult(new WorldExploration(), true, 0);
        } catch (IOException e) {
            // Could not keep a copy, so the original must not be overwritten either.
            LOGGER.log(Level.WARNING, path + " is " + what + " and could not be copied aside; "
                    + "exploration is read-only this session", e);
            return new LoadResult(new WorldExploration(), false, 0);
        }
    }

    /** @return whether the entry was read; a preserved or dropped entry counts as not read */
    private boolean readStructure(JsonElement element, WorldExploration exploration, Path path) {
        if (element == null || !element.isJsonObject()) {
            LOGGER.warning("Dropping a structure entry that is not an object in " + path);
            return false;
        }
        JsonObject entry = element.getAsJsonObject();
        String dimension = stringOrNull(entry.get("dimension"));
        String type = stringOrNull(entry.get("type"));
        Integer chunkX = intOrNull(entry.get("chunkX"));
        Integer chunkZ = intOrNull(entry.get("chunkZ"));
        String seedText = stringOrNull(entry.get("seed"));
        String statusId = stringOrNull(entry.get("status"));
        StructureStatus status = statusId == null ? StructureStatus.UNVISITED : StructureStatus.fromId(statusId);
        Long seed = seedText == null ? null : parseLong(seedText);

        if (isBlank(dimension) || isBlank(type) || chunkX == null || chunkZ == null || status == null
                || (seedText != null && seed == null)
                || (entry.has("seed") && seedText == null && !entry.get("seed").isJsonNull())) {
            preserve(exploration, entry, true, path);
            return false;
        }
        StructureAnnotation annotation = StructureAnnotation.of(
                StructureKey.of(seed, dimension, type, chunkX.intValue(), chunkZ.intValue()),
                status, stringOrNull(entry.get("note")));
        if (annotation.isEmpty()) {
            return true;
        }
        if (!exploration.restoreAnnotation(annotation)) {
            LOGGER.warning("Dropping a second entry for " + annotation.key() + " in " + path);
            return false;
        }
        return true;
    }

    private boolean readMarker(JsonElement element, WorldExploration exploration, Path path) {
        if (element == null || !element.isJsonObject()) {
            LOGGER.warning("Dropping a marker entry that is not an object in " + path);
            return false;
        }
        JsonObject entry = element.getAsJsonObject();
        String id = stringOrNull(entry.get("id"));
        String dimension = stringOrNull(entry.get("dimension"));
        Integer x = intOrNull(entry.get("x"));
        Integer z = intOrNull(entry.get("z"));
        JsonElement yElement = entry.get("y");
        Integer y = intOrNull(yElement);
        MarkerType type = MarkerType.fromId(stringOrNull(entry.get("type")));

        if (isBlank(id) || id.length() > 64 || isBlank(dimension) || x == null || z == null || type == null
                || (yElement != null && !yElement.isJsonNull() && y == null)) {
            preserve(exploration, entry, false, path);
            return false;
        }
        CustomMarker restored = exploration.restoreMarker(CustomMarker.restore(id, dimension,
                x.intValue(), y, z.intValue(), type, stringOrNull(entry.get("label")),
                stringOrNull(entry.get("note"))));
        if (restored == null) {
            LOGGER.warning("Dropping an exact duplicate of marker " + id + " in " + path);
            return false;
        }
        if (!restored.id().equals(id)) {
            LOGGER.warning("Marker id " + id + " appears twice in " + path + "; the second one is kept as "
                    + restored.id());
        }
        return true;
    }

    private void preserve(WorldExploration exploration, JsonObject entry, boolean structure, Path path) {
        LOGGER.warning("Keeping a " + (structure ? "structure" : "marker") + " entry this version "
                + "cannot read, unchanged, in " + path + ": " + gson.toJson(entry).replace('\n', ' '));
        String json = gson.toJson(entry);
        if (structure) {
            exploration.preserveStructureEntry(json);
        } else {
            exploration.preserveMarkerEntry(json);
        }
    }

    /**
     * Writes a snapshot. A world with nothing recorded and no file yet gets no file; a world whose
     * last annotation was just removed gets an empty one, so the removal sticks.
     *
     * @return whether the snapshot is now what is on disk
     */
    public boolean save(WorldIdentity identity, ExplorationSnapshot snapshot) {
        Path path = explorationPath(identity);
        if (snapshot.isEmpty() && !Files.exists(path)) {
            return true;
        }
        try {
            AtomicFiles.write(path, gson.toJson(toJson(snapshot)));
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not save exploration data to " + path, e);
            return false;
        }
    }

    private JsonObject toJson(ExplorationSnapshot snapshot) {
        JsonObject rootObject = new JsonObject();
        rootObject.addProperty("formatVersion", FORMAT_VERSION);

        JsonArray structures = new JsonArray();
        for (StructureAnnotation annotation : snapshot.structures()) {
            StructureKey key = annotation.key();
            JsonObject entry = new JsonObject();
            entry.addProperty("dimension", key.dimensionId());
            entry.addProperty("type", key.structureTypeId());
            entry.addProperty("chunkX", key.chunkX());
            entry.addProperty("chunkZ", key.chunkZ());
            if (key.hasSeed()) {
                entry.addProperty("seed", Long.toString(key.seed()));
            }
            entry.addProperty("status", annotation.status().id());
            if (annotation.note() != null) {
                entry.addProperty("note", annotation.note());
            }
            structures.add(entry);
        }
        for (String preserved : snapshot.preservedStructures()) {
            structures.add(gson.fromJson(preserved, JsonElement.class));
        }
        rootObject.add("structures", structures);

        JsonArray markers = new JsonArray();
        for (CustomMarker marker : snapshot.markers()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", marker.id());
            entry.addProperty("dimension", marker.dimensionId());
            entry.addProperty("type", marker.type().id());
            entry.addProperty("x", marker.x());
            if (marker.y() != null) {
                entry.addProperty("y", marker.y());
            }
            entry.addProperty("z", marker.z());
            if (marker.label() != null) {
                entry.addProperty("label", marker.label());
            }
            if (marker.note() != null) {
                entry.addProperty("note", marker.note());
            }
            markers.add(entry);
        }
        for (String preserved : snapshot.preservedMarkers()) {
            markers.add(gson.fromJson(preserved, JsonElement.class));
        }
        rootObject.add("markers", markers);
        return rootObject;
    }

    // ------------------------------------------------------------ JSON reading

    private static JsonArray arrayOrNull(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String stringOrNull(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        return primitive.isString() ? primitive.getAsString() : null;
    }

    /** An integral JSON number that fits an int, or {@code null}. */
    private static Integer intOrNull(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        try {
            BigDecimal value = element.getAsJsonPrimitive().getAsBigDecimal();
            return Integer.valueOf(value.intValueExact());
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private static int intOr(JsonElement element, int fallback) {
        Integer value = intOrNull(element);
        return value == null ? fallback : value.intValue();
    }

    private static Long parseLong(String text) {
        try {
            return Long.valueOf(Long.parseLong(text.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
