package com.scrimchic.seedchecker.gui.map.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.scrimchic.seedchecker.core.map.MapPreferences;
import com.scrimchic.seedchecker.worldgen.StructurePlacements;
import com.scrimchic.seedchecker.worldgen.StructureType;

/** Map preferences remember layers by these ids, so they must never move. */
class LayerIdTest {

    @Test
    void layerIdsArePinnedUniqueAndLowerCase() {
        List<String> expected = new ArrayList<String>();
        expected.add("biomes");
        expected.add("slime_chunks");
        for (StructureType type : StructurePlacements.forThisVersion().types()) {
            expected.add(type.id());
        }
        expected.add("stronghold");
        expected.add("custom_markers");

        List<String> ids = new ArrayList<String>();
        Set<String> unique = new HashSet<String>();
        for (MapLayer layer : MapLayers.createDefault().all()) {
            ids.add(layer.id());
            assertTrue(unique.add(layer.id()), "duplicate layer id " + layer.id());
            assertEquals(layer.id().toLowerCase(Locale.ROOT), layer.id());
        }
        assertEquals(expected, ids);
        assertTrue(ids.contains("village") && ids.contains("end_city") && ids.contains("mineshaft"));
    }

    @Test
    void preferencesSwitchLayersByIdAndLeaveTheRestAtTheirDefault() {
        MapLayers layers = MapLayers.createDefault();
        MapPreferences preferences = new MapPreferences();
        preferences.setLayerVisible("mineshaft", false);
        preferences.setLayerVisible("custom_markers", false);
        preferences.setLayerVisible("a_layer_from_the_future", false);
        layers.applyPreferences(preferences);
        for (MapLayer layer : layers.all()) {
            boolean off = layer.id().equals("mineshaft") || layer.id().equals("custom_markers");
            assertEquals(!off, layer.isEnabled(), layer.id());
        }
        assertFalse(layers.customMarkers().isEnabled());
    }
}
