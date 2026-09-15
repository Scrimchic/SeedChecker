package com.scrimchic.seedchecker.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.scrimchic.seedchecker.exploration.CustomMarker;
import com.scrimchic.seedchecker.exploration.MarkerType;
import com.scrimchic.seedchecker.exploration.StructureKey;
import com.scrimchic.seedchecker.exploration.StructureStatus;
import com.scrimchic.seedchecker.exploration.WorldExploration;
import com.scrimchic.seedchecker.world.WorldIdentity;
import com.scrimchic.seedchecker.worldgen.StructureType;

class ExplorationStorageTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final WorldIdentity SERVER = WorldIdentity.multiplayer("mc.example.com", "Example");
    private static final long SEED = -7407337299659424542L;
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    private static StructureKey key(StructureType type, int chunkX, int chunkZ) {
        return StructureKey.predicted(SEED, OVERWORLD, type, chunkX, chunkZ);
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(UTF_8));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), UTF_8);
    }

    private static WorldExploration roundTrip(ExplorationStorage storage, WorldExploration exploration) {
        assertTrue(storage.save(SERVER, exploration.snapshot()));
        ExplorationStorage.LoadResult loaded = storage.load(SERVER);
        assertTrue(loaded.isWritable());
        assertEquals(0, loaded.skippedEntries());
        return loaded.exploration();
    }

    @Test
    void aMissingFileIsAnEmptyWritableExploration(@TempDir Path root) {
        ExplorationStorage.LoadResult loaded = new ExplorationStorage(root).load(SERVER);
        assertTrue(loaded.isWritable());
        assertEquals(0, loaded.exploration().structureCount());
        assertEquals(0, loaded.exploration().markerCount());
    }

    @Test
    void itLivesNextToTheProfile(@TempDir Path root) {
        assertEquals(new WorldProfileStorage(root).profilePath(SERVER).resolveSibling("exploration.json"),
                new ExplorationStorage(root).explorationPath(SERVER));
    }

    @Test
    void nothingIsWrittenForAWorldWithNothingRecorded(@TempDir Path root) {
        ExplorationStorage storage = new ExplorationStorage(root);
        assertTrue(storage.save(SERVER, new WorldExploration().snapshot()));
        assertFalse(Files.exists(storage.explorationPath(SERVER)));
    }

    @Test
    void removingTheLastAnnotationIsWrittenToo(@TempDir Path root) {
        ExplorationStorage storage = new ExplorationStorage(root);
        WorldExploration exploration = new WorldExploration();
        exploration.setStatus(key(StructureType.IGLOO, 1, 1), StructureStatus.VISITED);
        assertTrue(storage.save(SERVER, exploration.snapshot()));
        exploration.setStatus(key(StructureType.IGLOO, 1, 1), StructureStatus.UNVISITED);
        assertTrue(storage.save(SERVER, exploration.snapshot()));
        assertEquals(0, storage.load(SERVER).exploration().structureCount());
    }

    @Test
    void everyStatusAndEveryKeySurvivesARoundTrip(@TempDir Path root) {
        ExplorationStorage storage = new ExplorationStorage(root);
        WorldExploration exploration = new WorldExploration();
        StructureStatus[] statuses = StructureStatus.values();
        List<StructureKey> keys = new ArrayList<StructureKey>();
        int i = 0;
        for (StructureType type : StructureType.values()) {
            StructureKey key = StructureKey.predicted(i % 2 == 0 ? Long.MIN_VALUE : Long.MAX_VALUE,
                    i % 3 == 0 ? NETHER : OVERWORLD, type, -1_875_000 + i, Integer.MIN_VALUE + i);
            keys.add(key);
            exploration.setStatus(key, statuses[1 + i % (statuses.length - 1)]);
            i++;
        }
        StructureKey seedless = StructureKey.of(null, "modded:mining_world", "village", 0, 0);
        exploration.setNote(seedless, "no seed needed");

        WorldExploration loaded = roundTrip(storage, exploration);
        assertEquals(keys.size() + 1, loaded.structureCount());
        for (int k = 0; k < keys.size(); k++) {
            assertEquals(exploration.annotationOf(keys.get(k)), loaded.annotationOf(keys.get(k)));
        }
        assertEquals("no seed needed", loaded.noteOf(seedless));
        assertEquals(StructureStatus.UNVISITED, loaded.statusOf(seedless));
    }

    @Test
    void unicodeAndMultilineNotesSurviveExactly(@TempDir Path root) throws IOException {
        ExplorationStorage storage = new ExplorationStorage(root);
        WorldExploration exploration = new WorldExploration();
        String note = "Скарб під водою\n\"quotes\" <tags> & \\slashes\\\n\tтаб 🏰 ✓\n\nкінець";
        StructureKey key = key(StructureType.BURIED_TREASURE, -7, 3);
        exploration.setStatus(key, StructureStatus.LOOTED);
        exploration.setNote(key, note);

        WorldExploration loaded = roundTrip(storage, exploration);
        assertEquals(note, loaded.noteOf(key));
        String file = read(storage.explorationPath(SERVER));
        assertTrue(file.contains("Скарб під водою"), "written as UTF-8, not escaped");
        assertTrue(file.contains("<tags>"));
        assertTrue(file.contains("\"seed\": \"-7407337299659424542\""), file);
        assertTrue(file.contains("\"status\": \"looted\""));
        assertTrue(file.contains("\"type\": \"buried_treasure\""));
        assertFalse(file.contains("StructureStatus") || file.contains("LOOTED"), "no Java names on disk");
    }

    @Test
    void markerCrudSurvivesRoundTrips(@TempDir Path root) {
        ExplorationStorage storage = new ExplorationStorage(root);
        WorldExploration exploration = new WorldExploration();
        List<CustomMarker> created = new ArrayList<CustomMarker>();
        MarkerType[] types = MarkerType.values();
        for (int i = 0; i < types.length; i++) {
            CustomMarker marker = CustomMarker.create(i % 2 == 0 ? OVERWORLD : NETHER, -i * 1000, i % 3 == 0 ? null : i,
                    i * 37, types[i], i % 2 == 0 ? "мітка " + i : null, i == 0 ? "line one\nline two" : null);
            exploration.putMarker(marker);
            created.add(marker);
        }

        WorldExploration loaded = roundTrip(storage, exploration);
        assertEquals(types.length, loaded.markerCount());
        for (CustomMarker marker : created) {
            assertEquals(marker, loaded.marker(marker.id()));
        }
        assertEquals(exploration.markersIn(NETHER), loaded.markersIn(NETHER));

        CustomMarker moved = created.get(0).movedTo(NETHER, 5, null, 5).withType(MarkerType.DANGER).withLabel("lava");
        loaded.putMarker(moved);
        loaded.removeMarker(created.get(1).id());
        WorldExploration again = roundTrip(storage, loaded);
        assertEquals(moved, again.marker(created.get(0).id()));
        assertNull(again.marker(created.get(1).id()));
        assertEquals(types.length - 1, again.markerCount());
        assertTrue(again.markersIn(NETHER).contains(moved));
        assertFalse(again.markersIn(OVERWORLD).contains(moved));
    }

    @Test
    void aCorruptFileIsCopiedAsideAndTheWorldStartsEmpty(@TempDir Path root) throws IOException {
        ExplorationStorage storage = new ExplorationStorage(root);
        Path path = storage.explorationPath(SERVER);
        write(path, "{ \"formatVersion\": 1, \"structures\": [ {\"dimension\": ");

        ExplorationStorage.LoadResult loaded = storage.load(SERVER);
        assertTrue(loaded.isWritable());
        assertEquals(0, loaded.exploration().structureCount());
        assertEquals("{ \"formatVersion\": 1, \"structures\": [ {\"dimension\": ", read(path),
                "loading never modifies the file");
        List<Path> aside = new ArrayList<Path>();
        DirectoryStream<Path> stream = Files.newDirectoryStream(path.getParent(), "exploration.json.corrupt-*");
        try {
            for (Path candidate : stream) {
                aside.add(candidate);
            }
        } finally {
            stream.close();
        }
        assertEquals(1, aside.size());
        assertEquals(read(path), read(aside.get(0)));

        for (String nonsense : new String[] {"", "[1, 2]", "null", "\"text\""}) {
            write(path, nonsense);
            ExplorationStorage.LoadResult again = storage.load(SERVER);
            assertEquals(0, again.exploration().structureCount(), "'" + nonsense + "'");
        }
    }

    @Test
    void unknownFieldsAreIgnoredAndUnknownValuesAreKeptForTheNewerVersion(@TempDir Path root)
            throws IOException {
        ExplorationStorage storage = new ExplorationStorage(root);
        Path path = storage.explorationPath(SERVER);
        write(path, "{\n"
                + "  \"formatVersion\": 1,\n"
                + "  \"futureTopLevel\": {\"anything\": true},\n"
                + "  \"structures\": [\n"
                + "    {\"dimension\": \"minecraft:overworld\", \"type\": \"village\", \"chunkX\": -3, \"chunkZ\": 12,"
                + " \"seed\": \"42\", \"status\": \"visited\", \"futureField\": [1, 2]},\n"
                + "    {\"dimension\": \"minecraft:overworld\", \"type\": \"village\", \"chunkX\": 1, \"chunkZ\": 1,"
                + " \"seed\": \"42\", \"status\": \"haunted\"},\n"
                + "    {\"dimension\": \"minecraft:overworld\", \"type\": \"sky_castle\", \"chunkX\": 2, \"chunkZ\": 2,"
                + " \"status\": \"looted\"},\n"
                + "    {\"dimension\": \"minecraft:overworld\", \"type\": \"village\", \"chunkX\": 3.5, \"chunkZ\": 2},\n"
                + "    {\"dimension\": \"minecraft:overworld\", \"type\": \"village\", \"chunkX\": 4, \"chunkZ\": 4,"
                + " \"seed\": \"not a number\", \"status\": \"looted\"},\n"
                + "    17,\n"
                + "    {\"dimension\": \"minecraft:overworld\", \"type\": \"village\", \"chunkX\": 5, \"chunkZ\": 5}\n"
                + "  ],\n"
                + "  \"markers\": [\n"
                + "    {\"id\": \"a\", \"dimension\": \"minecraft:the_nether\", \"type\": \"portal\", \"x\": 1, \"z\": 2,"
                + " \"colour\": \"red\"},\n"
                + "    {\"id\": \"b\", \"dimension\": \"minecraft:the_nether\", \"type\": \"villager_hall\", \"x\": 1, \"z\": 2}\n"
                + "  ]\n"
                + "}\n");

        ExplorationStorage.LoadResult loaded = storage.load(SERVER);
        WorldExploration exploration = loaded.exploration();
        assertTrue(loaded.isWritable());
        assertEquals(StructureStatus.VISITED,
                exploration.statusOf(StructureKey.predicted(42L, OVERWORLD, StructureType.VILLAGE, -3, 12)));
        // A structure type this version does not know is still a valid key: kept as an annotation.
        StructureKey skyCastle = StructureKey.of(null, OVERWORLD, "sky_castle", 2, 2);
        assertEquals(StructureStatus.LOOTED, exploration.statusOf(skyCastle));
        assertNull(skyCastle.structureType());
        assertEquals(2, exploration.structureCount(), "the empty entry at 5,5 is not an annotation");
        assertEquals(1, exploration.markerCount());
        assertEquals(MarkerType.PORTAL, exploration.marker("a").type());
        assertEquals(5, loaded.skippedEntries(), "haunted, 3.5, bad seed, 17, villager_hall");

        // Written back: the readable entries in this version's form, the unreadable objects as they
        // were, the non-object dropped.
        exploration.setStatus(StructureKey.predicted(42L, OVERWORLD, StructureType.VILLAGE, -3, 12),
                StructureStatus.LOOTED);
        assertTrue(storage.save(SERVER, exploration.snapshot()));
        String file = read(path);
        assertTrue(file.contains("\"haunted\""));
        assertTrue(file.contains("\"sky_castle\""));
        assertTrue(file.contains("\"villager_hall\""));
        assertTrue(file.contains("\"not a number\""));
        assertFalse(file.contains("futureTopLevel") || file.contains("futureField") || file.contains("colour"));
        JsonObject written = new Gson().fromJson(file, JsonObject.class);
        assertEquals(1, written.get("formatVersion").getAsInt());
        assertEquals(5, written.getAsJsonArray("structures").size());
        assertEquals(2, written.getAsJsonArray("markers").size());

        ExplorationStorage.LoadResult reloaded = storage.load(SERVER);
        assertEquals(StructureStatus.LOOTED, reloaded.exploration()
                .statusOf(StructureKey.predicted(42L, OVERWORLD, StructureType.VILLAGE, -3, 12)));
        assertEquals(StructureStatus.LOOTED, reloaded.exploration().statusOf(skyCastle));
        assertEquals(4, reloaded.skippedEntries());
    }

    @Test
    void duplicateMarkerIdsAreAllKeptAndDuplicateStructuresKeepTheFirst(@TempDir Path root) throws IOException {
        ExplorationStorage storage = new ExplorationStorage(root);
        write(storage.explorationPath(SERVER), "{\"formatVersion\": 1,"
                + " \"structures\": ["
                + "  {\"dimension\": \"minecraft:overworld\", \"type\": \"igloo\", \"chunkX\": 1, \"chunkZ\": 1,"
                + "   \"seed\": \"1\", \"status\": \"looted\"},"
                + "  {\"dimension\": \"minecraft:overworld\", \"type\": \"igloo\", \"chunkX\": 1, \"chunkZ\": 1,"
                + "   \"seed\": \"1\", \"status\": \"empty\"}],"
                + " \"markers\": ["
                + "  {\"id\": \"dup\", \"dimension\": \"minecraft:overworld\", \"type\": \"base\", \"x\": 1, \"z\": 1},"
                + "  {\"id\": \"dup\", \"dimension\": \"minecraft:overworld\", \"type\": \"stash\", \"x\": 9, \"z\": 9}]}");

        WorldExploration exploration = storage.load(SERVER).exploration();
        assertEquals(StructureStatus.LOOTED,
                exploration.statusOf(StructureKey.predicted(1L, OVERWORLD, StructureType.IGLOO, 1, 1)));
        assertEquals(2, exploration.markerCount());
        assertEquals(MarkerType.BASE, exploration.marker("dup").type());
        List<CustomMarker> markers = exploration.markersIn(OVERWORLD);
        assertNotEquals(markers.get(0).id(), markers.get(1).id());

        WorldExploration again = roundTrip(storage, exploration);
        assertEquals(2, again.markerCount(), "after one save the ids are unique and stay so");
    }

    @Test
    void aNewerFormatIsReadButNeverRewritten(@TempDir Path root) throws IOException {
        ExplorationStorage storage = new ExplorationStorage(root);
        String newer = "{\"formatVersion\": 2, \"structures\": ["
                + "{\"dimension\": \"minecraft:overworld\", \"type\": \"village\", \"chunkX\": 0, \"chunkZ\": 0,"
                + " \"seed\": \"5\", \"status\": \"visited\"}], \"markers\": [], \"evidence\": {}}";
        write(storage.explorationPath(SERVER), newer);

        ExplorationStorage.LoadResult loaded = storage.load(SERVER);
        assertFalse(loaded.isWritable());
        assertEquals(StructureStatus.VISITED, loaded.exploration()
                .statusOf(StructureKey.predicted(5L, OVERWORLD, StructureType.VILLAGE, 0, 0)));
        assertEquals(newer, read(storage.explorationPath(SERVER)));
    }

    @Test
    void aMissingFormatVersionIsReadAsTheCurrentOne(@TempDir Path root) throws IOException {
        ExplorationStorage storage = new ExplorationStorage(root);
        write(storage.explorationPath(SERVER), "{\"markers\": [{\"id\": \"m\", \"dimension\": \"minecraft:overworld\","
                + " \"type\": \"farm\", \"x\": -1, \"y\": -60, \"z\": 1, \"label\": \"iron\"}]}");
        ExplorationStorage.LoadResult loaded = storage.load(SERVER);
        assertTrue(loaded.isWritable());
        assertEquals(Integer.valueOf(-60), loaded.exploration().marker("m").y());
    }

    @Test
    void aSaveReplacesTheFileWholeAndLeavesNoTemporaryBehind(@TempDir Path root) throws IOException {
        ExplorationStorage storage = new ExplorationStorage(root);
        Path path = storage.explorationPath(SERVER);
        WorldExploration exploration = new WorldExploration();
        exploration.setStatus(key(StructureType.VILLAGE, 0, 0), StructureStatus.VISITED);
        // A temporary left by a crash mid-write must neither break the next save nor be loaded.
        write(path.resolveSibling("exploration.json.tmp"), "{\"half\": ");
        assertTrue(storage.save(SERVER, exploration.snapshot()));
        assertFalse(Files.exists(path.resolveSibling("exploration.json.tmp")));

        exploration.setStatus(key(StructureType.VILLAGE, 0, 0), StructureStatus.LOOTED);
        assertTrue(storage.save(SERVER, exploration.snapshot()));
        JsonElement parsed = new Gson().fromJson(read(path), JsonElement.class);
        assertEquals("looted", parsed.getAsJsonObject().getAsJsonArray("structures").get(0)
                .getAsJsonObject().get("status").getAsString());
    }

    @Test
    void aFailedSaveLeavesThePreviousFileIntact(@TempDir Path root) throws IOException {
        ExplorationStorage storage = new ExplorationStorage(root);
        Path path = storage.explorationPath(SERVER);
        WorldExploration exploration = new WorldExploration();
        exploration.setStatus(key(StructureType.VILLAGE, 0, 0), StructureStatus.VISITED);
        assertTrue(storage.save(SERVER, exploration.snapshot()));
        String before = read(path);

        // The temporary cannot be created: a non-empty directory is in its place.
        Path blocker = path.resolveSibling("exploration.json.tmp");
        Files.createDirectories(blocker);
        write(blocker.resolve("keep"), "x");
        exploration.setStatus(key(StructureType.VILLAGE, 0, 0), StructureStatus.DESTROYED);
        assertFalse(storage.save(SERVER, exploration.snapshot()));
        assertEquals(before, read(path));
        assertEquals(StructureStatus.VISITED,
                storage.load(SERVER).exploration().statusOf(key(StructureType.VILLAGE, 0, 0)));
    }
}
