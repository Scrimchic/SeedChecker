package com.scrimchic.seedchecker.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.scrimchic.seedchecker.client.map.MapSettingsManager;
import com.scrimchic.seedchecker.core.map.MapPreferences;
import com.scrimchic.seedchecker.exploration.MarkerType;
import com.scrimchic.seedchecker.exploration.StructureStatus;

class MapSettingsStorageTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(UTF_8));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), UTF_8);
    }

    @Test
    void aMissingFileIsTheDefaults(@TempDir Path root) {
        MapSettingsStorage.LoadResult loaded = new MapSettingsStorage(root).load();
        assertTrue(loaded.isWritable());
        assertTrue(loaded.preferences().filters().showsAllStatuses());
        assertTrue(loaded.preferences().filters().showsAllMarkerTypes());
        assertTrue(loaded.preferences().isLayerVisible("mineshaft", true));
        assertEquals(root.resolve("map-settings.json"), new MapSettingsStorage(root).path());
    }

    @Test
    void layersAndBothFiltersSurviveARoundTrip(@TempDir Path root) throws IOException {
        MapSettingsStorage storage = new MapSettingsStorage(root);
        MapPreferences preferences = new MapPreferences();
        preferences.setLayerVisible("mineshaft", false);
        preferences.setLayerVisible("custom_markers", true);
        preferences.filters().setStatusVisible(StructureStatus.LOOTED, false);
        preferences.filters().setStatusVisible(StructureStatus.EMPTY, false);
        preferences.filters().setMarkerTypeVisible(MarkerType.DANGER, false);
        assertTrue(storage.save(preferences));

        MapPreferences loaded = storage.load().preferences();
        assertFalse(loaded.isLayerVisible("mineshaft", true));
        assertTrue(loaded.isLayerVisible("custom_markers", false));
        assertFalse(loaded.filters().showsStatus(StructureStatus.LOOTED));
        assertFalse(loaded.filters().showsStatus(StructureStatus.EMPTY));
        assertTrue(loaded.filters().showsStatus(StructureStatus.VISITED));
        assertFalse(loaded.filters().showsMarkerType(MarkerType.DANGER));
        assertTrue(loaded.filters().showsMarkerType(MarkerType.BASE));

        String file = read(storage.path());
        assertTrue(file.contains("\"looted\": false"));
        assertTrue(file.contains("\"danger\": false"));
        assertTrue(file.contains("\"mineshaft\": false"));
        assertFalse(file.contains("LOOTED") || file.contains("DANGER"), "no Java names on disk");
    }

    @Test
    void aLayerTheFileNeverMentionedKeepsItsCodeDefault(@TempDir Path root) throws IOException {
        MapSettingsStorage storage = new MapSettingsStorage(root);
        write(storage.path(), "{\"formatVersion\": 1, \"layers\": {\"village\": false}}");
        MapPreferences loaded = storage.load().preferences();
        assertFalse(loaded.isLayerVisible("village", true));
        assertTrue(loaded.isLayerVisible("trial_chamber", true), "a layer new since the file was written");
        assertFalse(loaded.isLayerVisible("some_layer_off_by_default", false));
        assertTrue(loaded.filters().showsAllStatuses(), "missing filter sections are all visible");
    }

    @Test
    void unknownIdsAreIgnoredButWrittenBack(@TempDir Path root) throws IOException {
        MapSettingsStorage storage = new MapSettingsStorage(root);
        write(storage.path(), "{\"formatVersion\": 1,"
                + " \"layers\": {\"sky_castle\": false, \"village\": \"yes\"},"
                + " \"structureStatuses\": {\"haunted\": false, \"looted\": false, \"visited\": 3},"
                + " \"markerTypes\": {\"villager_hall\": false, \"stash\": false},"
                + " \"futureSection\": {\"x\": 1}}");
        MapPreferences loaded = storage.load().preferences();
        assertFalse(loaded.isLayerVisible("sky_castle", true));
        assertTrue(loaded.isLayerVisible("village", true), "a value that is not a boolean is ignored");
        assertFalse(loaded.filters().showsStatus(StructureStatus.LOOTED));
        assertTrue(loaded.filters().showsStatus(StructureStatus.VISITED));
        assertFalse(loaded.filters().showsMarkerType(MarkerType.STASH));
        assertTrue(loaded.filters().showsAllMarkerTypes() == false);

        assertTrue(storage.save(loaded));
        String file = read(storage.path());
        assertTrue(file.contains("\"haunted\": false"));
        assertTrue(file.contains("\"villager_hall\": false"));
        assertTrue(file.contains("\"sky_castle\": false"));
        assertFalse(file.contains("futureSection"));
    }

    @Test
    void aCorruptFileIsCopiedAsideAndTheDefaultsUsed(@TempDir Path root) throws IOException {
        MapSettingsStorage storage = new MapSettingsStorage(root);
        write(storage.path(), "{\"layers\": {\"village\": fal");
        MapSettingsStorage.LoadResult loaded = storage.load();
        assertTrue(loaded.isWritable());
        assertTrue(loaded.preferences().isLayerVisible("village", true));
        int aside = 0;
        DirectoryStream<Path> stream = Files.newDirectoryStream(root, "map-settings.json.corrupt-*");
        try {
            for (Path ignored : stream) {
                aside++;
            }
        } finally {
            stream.close();
        }
        assertEquals(1, aside);

        write(storage.path(), "[]");
        assertTrue(storage.load().preferences().filters().showsAllStatuses());
    }

    @Test
    void aSaveReplacesTheWholeFileAndLeavesNoTemporary(@TempDir Path root) throws IOException {
        MapSettingsStorage storage = new MapSettingsStorage(root);
        write(root.resolve("map-settings.json.tmp"), "{\"half\": ");
        MapPreferences preferences = new MapPreferences();
        preferences.setLayerVisible("biomes", false);
        assertTrue(storage.save(preferences));
        preferences.setLayerVisible("biomes", true);
        assertTrue(storage.save(preferences));
        assertFalse(Files.exists(root.resolve("map-settings.json.tmp")));
        assertTrue(storage.load().preferences().isLayerVisible("biomes", false));
    }

    @Test
    void aNewerFileIsReadButNeverRewritten(@TempDir Path root) throws IOException {
        MapSettingsStorage storage = new MapSettingsStorage(root);
        String newer = "{\"formatVersion\": 2, \"layers\": {\"mineshaft\": false}}";
        write(storage.path(), newer);
        MapSettingsManager manager = new MapSettingsManager(storage);
        assertFalse(manager.preferences().isLayerVisible("mineshaft", true));
        manager.layerChanged("mineshaft", true);
        assertTrue(manager.preferences().isLayerVisible("mineshaft", false), "applies for the session");
        assertEquals(newer, read(storage.path()));
    }

    @Test
    void theManagerSavesEveryExplicitChange(@TempDir Path root) {
        MapSettingsStorage storage = new MapSettingsStorage(root);
        MapSettingsManager manager = new MapSettingsManager(storage);
        manager.layerChanged("slime_chunks", false);
        manager.filters().setMarkerTypeVisible(MarkerType.PORTAL, false);
        assertTrue(manager.save());

        MapSettingsManager restarted = new MapSettingsManager(new MapSettingsStorage(root));
        assertFalse(restarted.preferences().isLayerVisible("slime_chunks", true));
        assertFalse(restarted.filters().showsMarkerType(MarkerType.PORTAL));
    }
}
