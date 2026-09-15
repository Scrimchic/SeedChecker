package com.scrimchic.seedchecker.gui.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.scrimchic.seedchecker.client.exploration.ExplorationManager;
import com.scrimchic.seedchecker.exploration.CustomMarker;
import com.scrimchic.seedchecker.exploration.ExplorationFilters;
import com.scrimchic.seedchecker.exploration.MarkerType;
import com.scrimchic.seedchecker.gui.map.layer.CustomMarkerLayer;
import com.scrimchic.seedchecker.storage.ExplorationStorage;
import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.world.PlayMode;
import com.scrimchic.seedchecker.world.PlayerPosition;
import com.scrimchic.seedchecker.world.WorldContext;
import com.scrimchic.seedchecker.world.WorldIdentity;
import com.scrimchic.seedchecker.world.WorldProfile;

class MarkerListTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final WorldIdentity WORLD = WorldIdentity.multiplayer("list.example.org", "List");

    private static CustomMarker marker(String id, int x, int z, MarkerType type, String label) {
        return CustomMarker.restore(id, OVERWORLD, x, null, z, type, label, null);
    }

    private static List<String> ids(List<MarkerList.Entry> entries) {
        List<String> ids = new ArrayList<String>();
        for (MarkerList.Entry entry : entries) {
            ids.add(entry.marker().id());
        }
        return ids;
    }

    @Test
    void nearestFirstByHorizontalDistanceAcrossNegativeCoordinates() {
        List<CustomMarker> markers = Arrays.asList(
                marker("far", 1000, 1000, MarkerType.BASE, "far"),
                marker("west", -300, 0, MarkerType.BASE, "west"),
                marker("near", -10, -20, MarkerType.BASE, "near"),
                CustomMarker.restore("high", OVERWORLD, 0, 250, 50, MarkerType.BASE, "high", null));
        // Standing in the middle of block 0,0, below the marker's height.
        PlayerPosition player = new PlayerPosition(0.5, -40.0, 0.5);
        List<MarkerList.Entry> entries = MarkerList.of(markers, player);
        assertEquals(Arrays.asList("near", "high", "west", "far"), ids(entries));
        assertEquals(50.0, entries.get(1).distance(), 1e-9, "height is ignored");
        for (int i = 1; i < entries.size(); i++) {
            assertEquals(true, entries.get(i - 1).distance() <= entries.get(i).distance());
        }
    }

    @Test
    void equalDistancesKeepOneOrder() {
        PlayerPosition player = new PlayerPosition(0.5, 64, 0.5);
        List<CustomMarker> markers = new ArrayList<CustomMarker>(Arrays.asList(
                marker("b-id", 0, 10, MarkerType.STASH, "Same"),
                marker("a-id", 10, 0, MarkerType.STASH, "same"),
                marker("c-id", 0, -10, MarkerType.BASE, "alpha"),
                marker("d-id", -10, 0, MarkerType.BASE, null)));
        List<String> first = ids(MarkerList.of(markers, player));
        assertEquals(Arrays.asList("c-id", "a-id", "b-id", "d-id"), first,
                "label, then type, then id; no label last");
        java.util.Collections.reverse(markers);
        assertEquals(first, ids(MarkerList.of(markers, player)));
    }

    @Test
    void withoutAPlayerTheOrderIsTypeThenLabelThenId() {
        List<CustomMarker> markers = Arrays.asList(
                marker("3", 0, 0, MarkerType.CUSTOM, "zzz"),
                marker("2", 99, 99, MarkerType.BASE, "b"),
                marker("1", -99, 5, MarkerType.BASE, "a"),
                marker("0", 5, 5, MarkerType.PORTAL, null));
        List<MarkerList.Entry> entries = MarkerList.of(markers, null);
        assertEquals(Arrays.asList("1", "2", "0", "3"), ids(entries));
        assertNull(entries.get(0).distance());
        assertEquals("Base - a", entries.get(0).text(24));
    }

    @Test
    void distancesAreWordedDeterministically() {
        assertEquals("0 blocks", MarkerList.formatDistance(0.2));
        assertEquals("432 blocks", MarkerList.formatDistance(432.4));
        assertEquals("1.0k blocks", MarkerList.formatDistance(999.6));
        assertEquals("1.2k blocks", MarkerList.formatDistance(1234));
        assertEquals("1.3k blocks", MarkerList.formatDistance(1250));
        assertEquals("25.0k blocks", MarkerList.formatDistance(24999));
    }

    @Test
    void theListIsTheShownMarkersOfThisDimensionOnlyAndARowCentresOnItsMarker(@TempDir Path root) {
        ExplorationManager exploration = new ExplorationManager(new ExplorationStorage(root), new ExplorationManager.Clock() {
            @Override
            public long millis() {
                return 0L;
            }
        });
        exploration.activate(WORLD);
        ExplorationFilters filters = new ExplorationFilters();
        CustomMarkerLayer layer = new CustomMarkerLayer(filters, exploration);
        CustomMarker home = exploration.createMarker(OVERWORLD, -120, 64, 40, MarkerType.BASE, "home", null);
        CustomMarker danger = exploration.createMarker(OVERWORLD, 5, null, 5, MarkerType.DANGER, "lava", null);
        exploration.createMarker(NETHER, 0, null, 0, MarkerType.PORTAL, "hub", null);
        // A world whose seed is unknown: the list needs none.
        ActiveWorld world = new ActiveWorld(WorldContext.withUnknownSeed("1.16.5", PlayMode.MULTIPLAYER, OVERWORLD),
                WorldProfile.createNew(WORLD, "1.16.5", 1L));
        PlayerPosition player = new PlayerPosition(0, 70, 0);

        List<MarkerList.Entry> entries = MarkerList.of(layer.shownMarkers(world), player);
        assertEquals(Arrays.asList(danger.id(), home.id()), ids(entries));

        filters.setMarkerTypeVisible(MarkerType.DANGER, false);
        entries = MarkerList.of(layer.shownMarkers(world), player);
        assertEquals(Arrays.asList(home.id()), ids(entries), "a hidden type is not listed");
        assertEquals(-119.5, entries.get(0).centerX(), 1e-9);
        assertEquals(40.5, entries.get(0).centerZ(), 1e-9);

        filters.setMarkerTypeVisible(MarkerType.DANGER, true);
        assertEquals(2, MarkerList.of(layer.shownMarkers(world), player).size(), "shown again once toggled back");
    }
}
