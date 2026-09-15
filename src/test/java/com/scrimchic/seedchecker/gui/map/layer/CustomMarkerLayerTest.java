package com.scrimchic.seedchecker.gui.map.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.scrimchic.seedchecker.client.exploration.ExplorationManager;
import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.core.map.MapViewport;
import com.scrimchic.seedchecker.exploration.CustomMarker;
import com.scrimchic.seedchecker.exploration.ExplorationFilters;
import com.scrimchic.seedchecker.exploration.MarkerType;
import com.scrimchic.seedchecker.gui.map.MapHitTest;
import com.scrimchic.seedchecker.storage.ExplorationStorage;
import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.world.PlayMode;
import com.scrimchic.seedchecker.world.WorldContext;
import com.scrimchic.seedchecker.world.WorldIdentity;
import com.scrimchic.seedchecker.world.WorldProfile;

class CustomMarkerLayerTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String VERSION = "1.20.1";
    private static final WorldIdentity SERVER = WorldIdentity.multiplayer("hidden-seed.example.org", "Hidden");

    static ExplorationManager manager(Path root) {
        ExplorationManager manager = new ExplorationManager(new ExplorationStorage(root), new ExplorationManager.Clock() {
            @Override
            public long millis() {
                return 0L;
            }
        });
        manager.activate(SERVER);
        return manager;
    }

    /** A server that never tells the client its seed, with no seed typed in either. */
    static ActiveWorld unknownSeedWorld(String dimension) {
        return new ActiveWorld(WorldContext.withUnknownSeed(VERSION, PlayMode.MULTIPLAYER, dimension),
                WorldProfile.createNew(SERVER, VERSION, 1L));
    }

    static MapViewport viewport(double scale, double centerX, double centerZ) {
        MapViewport viewport = new MapViewport();
        viewport.resize(320, 240);
        viewport.setScale(scale);
        viewport.setCenter(centerX, centerZ);
        return viewport;
    }

    @Test
    void markersWorkInAWorldWhoseSeedIsUnknown(@TempDir Path root) {
        ExplorationManager exploration = manager(root);
        CustomMarkerLayer layer = new CustomMarkerLayer(new ExplorationFilters(), exploration);
        ActiveWorld world = unknownSeedWorld(OVERWORLD);
        assertFalse(world.hasSeed());
        MapViewport view = viewport(1.0, 0, 0);

        assertNull(layer.unavailableReason(world, view, ChunkRange.visibleIn(view)), "no seed is needed");
        CustomMarker stash = exploration.createMarker(OVERWORLD, 10, null, -10, MarkerType.STASH, "chest", null);
        assertEquals(1, layer.visibleMarkers(world, view).size());
        assertEquals(stash, layer.visibleMarkers(world, view).get(0));

        exploration.deactivate();
        assertEquals("no world profile", layer.unavailableReason(world, view, ChunkRange.visibleIn(view)));
        assertTrue(layer.visibleMarkers(world, view).isEmpty());
    }

    @Test
    void onlyTheCurrentDimensionsMarkersAreDrawnOrPicked(@TempDir Path root) {
        ExplorationManager exploration = manager(root);
        CustomMarkerLayer layer = new CustomMarkerLayer(new ExplorationFilters(), exploration);
        CustomMarker overworld = exploration.createMarker(OVERWORLD, 0, null, 0, MarkerType.PORTAL, null, null);
        CustomMarker nether = exploration.createMarker(NETHER, 0, null, 0, MarkerType.PORTAL, null, null);
        MapViewport view = viewport(1.0, 0, 0);

        List<CustomMarker> drawnHere = layer.visibleMarkers(unknownSeedWorld(OVERWORLD), view);
        assertEquals(1, drawnHere.size());
        assertEquals(overworld.id(), drawnHere.get(0).id());
        List<MapHitTest.Candidate<Object>> pickedThere = layer.hitCandidates(unknownSeedWorld(NETHER), view);
        assertEquals(1, pickedThere.size());
        assertEquals(nether, pickedThere.get(0).target());

        assertTrue(layer.appliesTo(OVERWORLD) && layer.appliesTo("modded:mining_world"));
        assertFalse(layer.appliesTo(null));
        layer.setEnabled(false);
        assertTrue(layer.hitCandidates(unknownSeedWorld(NETHER), view).isEmpty(), "a hidden layer picks nothing");
    }

    @Test
    void offScreenMarkersAreCulledWithAMargin(@TempDir Path root) {
        ExplorationManager exploration = manager(root);
        CustomMarkerLayer layer = new CustomMarkerLayer(new ExplorationFilters(), exploration);
        // 320 by 240 pixels at one pixel a block, centred on 0,0: blocks -160..159 by -120..119.
        exploration.createMarker(OVERWORLD, 0, null, 0, MarkerType.BASE, "centre", null);
        exploration.createMarker(OVERWORLD, 165, null, 0, MarkerType.BASE, "just past the edge", null);
        exploration.createMarker(OVERWORLD, -175, null, 0, MarkerType.BASE, "past the margin", null);
        exploration.createMarker(OVERWORLD, -1_000_000, null, -1_000_000, MarkerType.BASE, "far", null);

        List<CustomMarker> drawn = layer.visibleMarkers(unknownSeedWorld(OVERWORLD), viewport(1.0, 0, 0));
        assertEquals(2, drawn.size());
        assertEquals("centre", drawn.get(0).label());
        assertEquals("just past the edge", drawn.get(1).label());

        // Far out, markers are never dropped for the zoom, only for being off screen.
        List<CustomMarker> zoomedOut = layer.visibleMarkers(unknownSeedWorld(OVERWORLD),
                viewport(MapViewport.MIN_SCALE, -1_000_000, -1_000_000));
        assertEquals(1, zoomedOut.size());
        assertEquals("far", zoomedOut.get(0).label());
    }

    @Test
    void aHiddenTypeIsNeitherDrawnNorPickedNorListedUntilToggledBack(@TempDir Path root) {
        ExplorationManager exploration = manager(root);
        ExplorationFilters filters = new ExplorationFilters();
        CustomMarkerLayer layer = new CustomMarkerLayer(filters, exploration);
        ActiveWorld world = unknownSeedWorld(OVERWORLD);
        MapViewport view = viewport(1.0, 0, 0);
        MarkerType[] types = MarkerType.values();
        for (int i = 0; i < types.length; i++) {
            exploration.createMarker(OVERWORLD, i * 20 - 80, null, 0, types[i], types[i].id(), null);
        }

        for (MarkerType hidden : types) {
            filters.setMarkerTypeVisible(hidden, false);
            List<CustomMarker> drawn = layer.visibleMarkers(world, view);
            List<CustomMarker> listed = layer.shownMarkers(world);
            List<MapHitTest.Candidate<Object>> pickable = layer.hitCandidates(world, view);
            assertEquals(types.length - 1, drawn.size(), hidden.toString());
            assertEquals(types.length - 1, listed.size());
            assertEquals(types.length - 1, pickable.size());
            for (CustomMarker marker : listed) {
                assertTrue(marker.type() != hidden);
            }
            CustomMarker hiddenMarker = null;
            for (CustomMarker marker : exploration.markersIn(OVERWORLD)) {
                if (marker.type() == hidden) {
                    hiddenMarker = marker;
                }
            }
            assertFalse(layer.isMarkerShown(world, hiddenMarker));
            assertNull(MapHitTest.pick(pickable, CustomMarkerLayer.screenX(view, hiddenMarker),
                    CustomMarkerLayer.screenY(view, hiddenMarker)), hidden + " must not be clickable");

            filters.setMarkerTypeVisible(hidden, true);
            assertTrue(layer.isMarkerShown(world, hiddenMarker));
            assertEquals(hiddenMarker, MapHitTest.pick(layer.hitCandidates(world, view),
                    CustomMarkerLayer.screenX(view, hiddenMarker), CustomMarkerLayer.screenY(view, hiddenMarker)).target());
        }
    }

    @Test
    void theClickRadiusIsTheSameInPixelsAtEveryZoom(@TempDir Path root) {
        ExplorationManager exploration = manager(root);
        CustomMarkerLayer layer = new CustomMarkerLayer(new ExplorationFilters(), exploration);
        CustomMarker marker = exploration.createMarker(OVERWORLD, -3000, 64, -3000, MarkerType.DANGER, null, null);
        for (double scale : new double[] {MapViewport.MIN_SCALE, 1.0 / 4, 1.0, 4.0, MapViewport.MAX_SCALE}) {
            MapViewport view = viewport(scale, -3000, -3000);
            List<MapHitTest.Candidate<Object>> candidates = layer.hitCandidates(unknownSeedWorld(OVERWORLD), view);
            double x = CustomMarkerLayer.screenX(view, marker);
            double y = CustomMarkerLayer.screenY(view, marker);
            assertEquals(marker, MapHitTest.pick(candidates, x + 6, y).target(), "scale " + scale);
            assertEquals(marker, MapHitTest.pick(candidates, x, y - CustomMarkerLayer.HIT_RADIUS).target());
            assertNull(MapHitTest.pick(candidates, x + CustomMarkerLayer.HIT_RADIUS + 1, y), "scale " + scale);
        }
    }
}
