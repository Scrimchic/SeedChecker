package com.scrimchic.seedchecker.gui.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.scrimchic.seedchecker.client.exploration.ExplorationManager;
import com.scrimchic.seedchecker.exploration.CustomMarker;
import com.scrimchic.seedchecker.exploration.ExplorationFilters;
import com.scrimchic.seedchecker.exploration.MarkerType;
import com.scrimchic.seedchecker.exploration.StructureKey;
import com.scrimchic.seedchecker.exploration.StructureStatus;
import com.scrimchic.seedchecker.gui.map.layer.CustomMarkerLayer;
import com.scrimchic.seedchecker.gui.map.layer.StructureLayer;
import com.scrimchic.seedchecker.storage.ExplorationStorage;
import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.world.PlayMode;
import com.scrimchic.seedchecker.world.WorldContext;
import com.scrimchic.seedchecker.world.WorldIdentity;
import com.scrimchic.seedchecker.world.WorldProfile;
import com.scrimchic.seedchecker.worldgen.StructurePlacements;
import com.scrimchic.seedchecker.worldgen.StructureType;

/** A selection stays only while its object is shown, by the same rules that draw it. */
class MapSelectionTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String VERSION = "26.2";
    private static final WorldIdentity WORLD = WorldIdentity.singleplayer("Sel", "Sel");

    private static ExplorationManager manager(Path root) {
        ExplorationManager manager = new ExplorationManager(new ExplorationStorage(root), new ExplorationManager.Clock() {
            @Override
            public long millis() {
                return 0L;
            }
        });
        manager.activate(WORLD);
        return manager;
    }

    private static ActiveWorld world(Long seed) {
        WorldProfile profile = WorldProfile.createNew(WORLD, VERSION, 1L);
        if (seed != null) {
            profile.setManualSeed(seed.longValue());
        }
        return new ActiveWorld(WorldContext.withUnknownSeed(VERSION, PlayMode.MULTIPLAYER, OVERWORLD), profile);
    }

    @Test
    void aMarkerSelectionIsDroppedOnceItsTypeIsHiddenAndKeptOtherwise(@TempDir Path root) {
        ExplorationManager exploration = manager(root);
        ExplorationFilters filters = new ExplorationFilters();
        CustomMarkerLayer markers = new CustomMarkerLayer(filters, exploration);
        CustomMarker farm = exploration.createMarker(OVERWORLD, 1, null, 1, MarkerType.FARM, null, null);
        MapSelection selection = MapSelection.marker(farm.id());
        ActiveWorld world = world(null);

        assertTrue(selection.isShown(world, exploration, markers), "no seed is needed for a marker");
        filters.setMarkerTypeVisible(MarkerType.STASH, false);
        assertTrue(selection.isShown(world, exploration, markers), "still visible, still selected");
        filters.setMarkerTypeVisible(MarkerType.FARM, false);
        assertFalse(selection.isShown(world, exploration, markers));
        filters.showAllMarkerTypes();
        markers.setEnabled(false);
        assertFalse(selection.isShown(world, exploration, markers));
        markers.setEnabled(true);
        exploration.removeMarker(farm.id());
        assertFalse(selection.isShown(world, exploration, markers), "a deleted marker is not shown");
    }

    @Test
    void aStructureSelectionIsDroppedOnceItsStatusIsHidden(@TempDir Path root) {
        ExplorationManager exploration = manager(root);
        ExplorationFilters filters = new ExplorationFilters();
        StructureLayer villages = new StructureLayer(StructureType.VILLAGE, StructurePlacements.forThisVersion(),
                filters, exploration);
        MapSelection selection = MapSelection.structure(villages, 10, -20, null);
        ActiveWorld world = world(99L);

        filters.setStatusVisible(StructureStatus.LOOTED, false);
        assertTrue(selection.isShown(world, exploration, null), "unvisited, and unvisited is shown");
        exploration.setStatus(StructureKey.predicted(99L, OVERWORLD, StructureType.VILLAGE, 10, -20),
                StructureStatus.LOOTED);
        assertFalse(selection.isShown(world, exploration, null), "marked looted while looted is hidden");
        filters.showAllStatuses();
        assertTrue(selection.isShown(world, exploration, null));
    }

    @Test
    void theSameObjectHasTheSameTargetKey() {
        ExplorationFilters filters = new ExplorationFilters();
        StructureLayer villages = new StructureLayer(StructureType.VILLAGE, StructurePlacements.forThisVersion(), filters);
        assertEquals(MapSelection.structure(villages, -1, 2, null).targetKey(),
                MapSelection.structure(villages, -1, 2, null).targetKey());
        assertNotEquals(MapSelection.structure(villages, -1, 2, null).targetKey(),
                MapSelection.structure(villages, 2, -1, null).targetKey());
        assertEquals("marker:abc", MapSelection.marker("abc").targetKey());
    }
}
