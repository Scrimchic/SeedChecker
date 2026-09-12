package com.scrimchic.seedchecker.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BiomePaletteTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "minecraft:ocean", "minecraft:plains", "minecraft:desert", "minecraft:forest",
            "minecraft:taiga", "minecraft:swamp", "minecraft:jungle", "minecraft:snowy_plains",
            "minecraft:badlands", "minecraft:meadow", "minecraft:nether_wastes", "minecraft:the_end",
    })
    void theBiomesWorthRecognisingHaveDeliberateColours(String biomeId) {
        assertTrue(BiomePalette.isNamed(biomeId), biomeId + " should have a chosen colour");
        assertEquals(0xFF000000, BiomePalette.colorOf(biomeId) & 0xFF000000);
    }

    @Test
    void legacyAndModernNamesBothResolve() {
        // 1.16.5 calls them snowy_tundra and mountains; 1.20.1 calls them snowy_plains and
        // windswept_hills. The palette answers for both.
        assertTrue(BiomePalette.isNamed("minecraft:snowy_tundra"));
        assertTrue(BiomePalette.isNamed("minecraft:snowy_plains"));
        assertTrue(BiomePalette.isNamed("minecraft:mountains"));
        assertTrue(BiomePalette.isNamed("minecraft:windswept_hills"));
    }

    @Test
    void unknownBiomesGetAStableColourRatherThanBreaking() {
        String modded = "somemod:crystal_wastes";
        assertFalse(BiomePalette.isNamed(modded));

        int first = BiomePalette.colorOf(modded);
        assertEquals(first, BiomePalette.colorOf(modded));
        assertEquals(0xFF000000, first & 0xFF000000);
    }

    @Test
    void nullDoesNotBreakTheRenderer() {
        assertEquals(0xFF000000, BiomePalette.colorOf(null) & 0xFF000000);
    }

    @Test
    void generatedColoursAreSpreadOutRatherThanAllAlike() {
        Set<Integer> colors = new HashSet<Integer>();
        for (int i = 0; i < 200; i++) {
            colors.add(Integer.valueOf(BiomePalette.colorOf("somemod:biome_" + i)));
        }
        // Collisions are acceptable, all-the-same is not.
        assertTrue(colors.size() > 150, "only " + colors.size() + " distinct colours from 200 ids");
    }

    @Test
    void generatedColoursStayInAReadableRange() {
        for (int i = 0; i < 500; i++) {
            int color = BiomePalette.colorOf("somemod:biome_" + i);
            int red = (color >> 16) & 0xFF;
            int green = (color >> 8) & 0xFF;
            int blue = color & 0xFF;
            int brightest = Math.max(red, Math.max(green, blue));
            assertTrue(brightest >= 80 && brightest <= 200,
                    "biome_" + i + " brightest channel " + brightest);
        }
    }

    @Test
    void differentIdsUsuallyDifferInColour() {
        assertNotEquals(BiomePalette.colorOf("minecraft:ocean"),
                BiomePalette.colorOf("minecraft:desert"));
        assertNotEquals(BiomePalette.colorOf("minecraft:plains"),
                BiomePalette.colorOf("minecraft:jungle"));
    }
}
