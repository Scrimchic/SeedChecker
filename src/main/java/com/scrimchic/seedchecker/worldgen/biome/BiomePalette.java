package com.scrimchic.seedchecker.worldgen.biome;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns a canonical biome id into a colour.
 *
 * <p>A prototype palette, not a finished design: the named entries are map-like colours for the
 * biomes a player is most likely to be looking for, and everything else - modded biomes, biomes
 * added by a later Minecraft version, anything a datapack invents - falls through to a
 * deterministic colour derived from the id itself. An unknown biome therefore always draws as
 * <em>something</em>, always the same something, and never breaks the renderer.
 *
 * <p>No Minecraft type appears here, which is what keeps the renderer free of them too: the layer
 * only ever sees ids and colours.
 */
public final class BiomePalette {

    private static final Map<String, Integer> NAMED = buildNamed();

    /** Saturation and value of generated colours, picked so they stay readable over the grid. */
    private static final float FALLBACK_SATURATION = 0.45f;
    private static final float FALLBACK_VALUE = 0.62f;

    private BiomePalette() {
    }

    /**
     * @param biomeId canonical id such as {@code minecraft:plains}
     * @return an opaque ARGB colour; the same id always yields the same colour
     */
    public static int colorOf(String biomeId) {
        if (biomeId == null) {
            return 0xFF404040;
        }
        Integer named = NAMED.get(biomeId);
        return named != null ? named.intValue() : fallbackColor(biomeId);
    }

    public static boolean isNamed(String biomeId) {
        return biomeId != null && NAMED.containsKey(biomeId);
    }

    /**
     * Spreads the id's hash around the hue circle at fixed saturation and value, so generated
     * colours are distinguishable from each other without any of them being unreadably dark or
     * blindingly bright. {@link String#hashCode()} is specified by the JDK, so this is stable
     * across machines and runs - it is only used for colour, never for identity or storage.
     */
    private static int fallbackColor(String biomeId) {
        int hash = biomeId.hashCode();
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        float hue = ((hash & 0x7FFFFFFF) % 3600) / 3600.0f;
        return hsvToArgb(hue, FALLBACK_SATURATION, FALLBACK_VALUE);
    }

    private static int hsvToArgb(float hue, float saturation, float value) {
        int sector = (int) (hue * 6.0f) % 6;
        float offset = hue * 6.0f - (int) (hue * 6.0f);
        float p = value * (1.0f - saturation);
        float q = value * (1.0f - saturation * offset);
        float t = value * (1.0f - saturation * (1.0f - offset));

        float red;
        float green;
        float blue;
        switch (sector) {
            case 0: red = value; green = t; blue = p; break;
            case 1: red = q; green = value; blue = p; break;
            case 2: red = p; green = value; blue = t; break;
            case 3: red = p; green = q; blue = value; break;
            case 4: red = t; green = p; blue = value; break;
            default: red = value; green = p; blue = q; break;
        }
        return 0xFF000000
                | (Math.round(red * 255.0f) << 16)
                | (Math.round(green * 255.0f) << 8)
                | Math.round(blue * 255.0f);
    }

    private static Map<String, Integer> buildNamed() {
        Map<String, Integer> colors = new HashMap<String, Integer>();

        // Water
        put(colors, 0xFF3A5FA8, "ocean", "deep_ocean");
        put(colors, 0xFF2E4C8C, "cold_ocean", "deep_cold_ocean");
        put(colors, 0xFF4A79C4, "lukewarm_ocean", "deep_lukewarm_ocean");
        put(colors, 0xFF4E93D0, "warm_ocean", "deep_warm_ocean");
        put(colors, 0xFF7FA8CC, "frozen_ocean", "deep_frozen_ocean");
        put(colors, 0xFF4F7FBF, "river");
        put(colors, 0xFF9FC0DC, "frozen_river");

        // Shores
        put(colors, 0xFFE4D7A4, "beach");
        put(colors, 0xFFD8E0E8, "snowy_beach");
        put(colors, 0xFF9A9A96, "stony_shore", "stone_shore");

        // Grass
        put(colors, 0xFF7FB25C, "plains", "sunflower_plains");
        put(colors, 0xFF8FC46A, "meadow");
        put(colors, 0xFF6E9E4E, "forest", "flower_forest", "wooded_hills", "flower_forest_hills");
        put(colors, 0xFF94B77A, "birch_forest", "tall_birch_forest", "birch_forest_hills",
                "old_growth_birch_forest", "tall_birch_hills");
        put(colors, 0xFF3F6B34, "dark_forest", "dark_forest_hills");
        put(colors, 0xFF4E7C52, "taiga", "taiga_hills", "giant_tree_taiga", "giant_tree_taiga_hills",
                "old_growth_pine_taiga", "giant_spruce_taiga", "giant_spruce_taiga_hills",
                "old_growth_spruce_taiga");
        put(colors, 0xFF8FA88C, "snowy_taiga", "snowy_taiga_hills", "snowy_taiga_mountains");
        put(colors, 0xFF4C6B3C, "swamp", "swamp_hills", "mangrove_swamp");
        put(colors, 0xFF3FA13F, "jungle", "jungle_hills", "jungle_edge", "modified_jungle",
                "modified_jungle_edge", "sparse_jungle", "bamboo_jungle", "bamboo_jungle_hills");
        put(colors, 0xFFB6B357, "savanna", "savanna_plateau", "shattered_savanna",
                "shattered_savanna_plateau", "windswept_savanna");
        put(colors, 0xFF7FAE6F, "grove");
        put(colors, 0xFFE2A8C4, "cherry_grove");

        // Dry
        put(colors, 0xFFE8D48A, "desert", "desert_hills", "desert_lakes");
        put(colors, 0xFFC4763F, "badlands", "badlands_plateau", "modified_badlands_plateau");
        put(colors, 0xFFA85C34, "eroded_badlands");
        put(colors, 0xFFB2874C, "wooded_badlands", "wooded_badlands_plateau",
                "modified_wooded_badlands_plateau");

        // Cold and high
        put(colors, 0xFFEDF2F5, "snowy_plains", "snowy_tundra", "snowy_mountains", "ice_spikes");
        put(colors, 0xFFDCE6EC, "snowy_slopes");
        put(colors, 0xFFF2F6F8, "frozen_peaks", "jagged_peaks");
        put(colors, 0xFF8E9296, "stony_peaks", "mountains", "mountain_edge", "gravelly_mountains",
                "modified_gravelly_mountains", "windswept_hills", "windswept_gravelly_hills");
        put(colors, 0xFF6F8A68, "wooded_mountains", "windswept_forest");

        // Odd ones out
        put(colors, 0xFFA47FC4, "mushroom_fields", "mushroom_field_shore");

        // Caves
        put(colors, 0xFF8E7A5E, "dripstone_caves");
        put(colors, 0xFF4F8E52, "lush_caves");
        put(colors, 0xFF1E2430, "deep_dark");

        // Nether, for when the dimension is supported
        put(colors, 0xFF8C2B21, "nether_wastes", "nether");
        put(colors, 0xFFB03A46, "crimson_forest");
        put(colors, 0xFF2A7C82, "warped_forest");
        put(colors, 0xFF6B4C3A, "soul_sand_valley");
        put(colors, 0xFF4A4448, "basalt_deltas");

        // End, likewise
        put(colors, 0xFF20203A, "the_end", "end_barrens", "small_end_islands");
        put(colors, 0xFFCEC894, "end_highlands", "end_midlands");

        return Collections.unmodifiableMap(colors);
    }

    /** Registers one colour under the {@code minecraft:} namespace for each given path. */
    private static void put(Map<String, Integer> colors, int argb, String... paths) {
        for (String path : paths) {
            colors.put("minecraft:" + path, Integer.valueOf(argb));
        }
    }
}
