package com.scrimchic.seedchecker.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Saved exploration data refers to structure types by these ids, so they must never move. */
class StructureTypeIdTest {

    @Test
    void theIdsArePinned() {
        Map<StructureType, String> expected = new LinkedHashMap<StructureType, String>();
        expected.put(StructureType.VILLAGE, "village");
        expected.put(StructureType.DESERT_PYRAMID, "desert_pyramid");
        expected.put(StructureType.SHIPWRECK, "shipwreck");
        expected.put(StructureType.ANCIENT_CITY, "ancient_city");
        expected.put(StructureType.TRIAL_CHAMBER, "trial_chamber");
        expected.put(StructureType.JUNGLE_TEMPLE, "jungle_temple");
        expected.put(StructureType.SWAMP_HUT, "swamp_hut");
        expected.put(StructureType.IGLOO, "igloo");
        expected.put(StructureType.PILLAGER_OUTPOST, "pillager_outpost");
        expected.put(StructureType.OCEAN_RUIN, "ocean_ruin");
        expected.put(StructureType.BURIED_TREASURE, "buried_treasure");
        expected.put(StructureType.MINESHAFT, "mineshaft");
        expected.put(StructureType.TRAIL_RUINS, "trail_ruins");
        expected.put(StructureType.OCEAN_MONUMENT, "ocean_monument");
        expected.put(StructureType.WOODLAND_MANSION, "woodland_mansion");
        expected.put(StructureType.RUINED_PORTAL, "ruined_portal");
        expected.put(StructureType.NETHER_FORTRESS, "nether_fortress");
        expected.put(StructureType.BASTION_REMNANT, "bastion_remnant");
        expected.put(StructureType.NETHER_FOSSIL, "nether_fossil");
        expected.put(StructureType.END_CITY, "end_city");
        expected.put(StructureType.STRONGHOLD, "stronghold");
        assertEquals(StructureType.values().length, expected.size(), "a new type needs a pinned id here");
        for (StructureType type : StructureType.values()) {
            assertEquals(expected.get(type), type.id(), type.toString());
            assertEquals(type.id().toLowerCase(Locale.ROOT), type.id());
            assertSame(type, StructureType.fromId(type.id()));
        }
    }

    @Test
    void anUnknownIdIsNotAType() {
        assertNull(StructureType.fromId("sky_castle"));
        assertNull(StructureType.fromId(null));
        assertNull(StructureType.fromId("VILLAGE"));
        assertTrue(StructureType.fromId("village") == StructureType.VILLAGE);
    }
}
