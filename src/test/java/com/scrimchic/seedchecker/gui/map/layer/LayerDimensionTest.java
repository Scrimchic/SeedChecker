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

        List<String> expectedNether = new ArrayList<String>();
        expectedNether.add("Biomes");
        for (StructureType type : StructurePlacements.forThisVersion().types()) {
            if (type.generatesIn("minecraft:the_nether")) {
                expectedNether.add(type.displayName());
            }
        }
        assertEquals(expectedNether, nether);
        assertEquals(5, nether.size(), "biomes, ruined portal, fortress, bastion and fossil: " + nether);
        assertFalse(nether.contains("Slime Chunks"));
        assertFalse(nether.contains("Stronghold"));

        // Neither a dimension this phase does not cover nor no world at all gets a structure layer.
        List<String> end = new ArrayList<String>();
        end.add("Biomes");
        assertEquals(end, namesIn(layers, "minecraft:the_end"));
        assertEquals(end, namesIn(layers, null));
    }
}
