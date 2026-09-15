package com.scrimchic.seedchecker.gui.map.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.scrimchic.seedchecker.worldgen.StructurePlacements;
import com.scrimchic.seedchecker.worldgen.StructureType;

/**
 * Which layers the map screen lists, draws and lets a click select in each dimension: the rule is
 * {@link MapLayer#appliesTo}, and every one of those paths asks it.
 */
class LayerDimensionTest {

    private static final String CUSTOM_MARKERS = "Custom Markers";

    private static List<String> namesIn(MapLayers layers, String dimensionId) {
        List<String> names = new ArrayList<String>();
        for (MapLayer layer : layers.all()) {
            if (layer.appliesTo(dimensionId)) {
                names.add(layer.displayName());
            }
        }
        return names;
    }

    @Test
    void eachDimensionListsOnlyItsOwnLayers() {
        MapLayers layers = MapLayers.createDefault();
        List<String> overworld = namesIn(layers, "minecraft:overworld");
        List<String> nether = namesIn(layers, "minecraft:the_nether");

        assertTrue(overworld.contains("Biomes"));
        assertTrue(overworld.contains("Slime Chunks"));
        assertTrue(overworld.contains("Stronghold"));
        assertTrue(overworld.contains("Village"));
        assertTrue(overworld.contains("Ruined Portal"));
        assertFalse(overworld.contains("Nether Fortress"));
        assertFalse(overworld.contains("Bastion Remnant"));
        assertFalse(overworld.contains("Nether Fossil"));
        assertEquals(CUSTOM_MARKERS, overworld.get(overworld.size() - 1), "drawn last, over every structure");

        List<String> expectedNether = new ArrayList<String>();
        expectedNether.add("Biomes");
        for (StructureType type : StructurePlacements.forThisVersion().types()) {
            if (type.generatesIn("minecraft:the_nether")) {
                expectedNether.add(type.displayName());
            }
        }
        expectedNether.add(CUSTOM_MARKERS);
        assertEquals(expectedNether, nether);
        assertEquals(6, nether.size(), "biomes, ruined portal, fortress, bastion, fossil and markers: " + nether);
        assertFalse(nether.contains("Slime Chunks"));
        assertFalse(nether.contains("Stronghold"));

        // The End: biomes, its one structure and the player's markers. No slime chunks, stronghold,
        // overworld or nether structure, and no ruined portal - no portal entry accepts an End biome.
        List<String> expectedEnd = new ArrayList<String>();
        expectedEnd.add("Biomes");
        expectedEnd.add("End City");
        expectedEnd.add(CUSTOM_MARKERS);
        assertEquals(expectedEnd, namesIn(layers, "minecraft:the_end"));
        assertFalse(overworld.contains("End City"));
        assertFalse(nether.contains("End City"));

        // A dimension Seed Checker does not know still takes the player's markers, but no structure;
        // no world at all takes neither.
        List<String> custom = new ArrayList<String>();
        custom.add("Biomes");
        custom.add(CUSTOM_MARKERS);
        assertEquals(custom, namesIn(layers, "seedchecker:custom"));
        List<String> outside = new ArrayList<String>();
        outside.add("Biomes");
        assertEquals(outside, namesIn(layers, null));
    }
}
