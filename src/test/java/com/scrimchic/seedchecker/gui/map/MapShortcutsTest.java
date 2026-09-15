package com.scrimchic.seedchecker.gui.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.scrimchic.seedchecker.exploration.ExplorationFilters;
import com.scrimchic.seedchecker.gui.map.layer.StructureLayer;
import com.scrimchic.seedchecker.worldgen.StructurePlacements;
import com.scrimchic.seedchecker.worldgen.StructureType;

class MapShortcutsTest {

    @Test
    void escapeUndoesTheInnermostThingFirst() {
        // An open editor - a pending deletion is one - beats everything.
        assertEquals(MapShortcuts.EscapeAction.CANCEL_EDITOR, MapShortcuts.escape(true, true, true, true));
        assertEquals(MapShortcuts.EscapeAction.CANCEL_SEED_EDIT, MapShortcuts.escape(false, true, true, true));
        assertEquals(MapShortcuts.EscapeAction.CLEAR_SELECTION, MapShortcuts.escape(false, false, true, false));
        assertEquals(MapShortcuts.EscapeAction.CLEAR_SELECTION, MapShortcuts.escape(false, false, false, true));
        assertEquals(MapShortcuts.EscapeAction.CLOSE_SCREEN, MapShortcuts.escape(false, false, false, false));
    }

    @Test
    void deleteOnlyAsksAboutASelectedMarker() {
        MapSelection marker = MapSelection.marker("m");
        MapSelection structure = MapSelection.structure(
                new StructureLayer(StructureType.VILLAGE, StructurePlacements.forThisVersion(), new ExplorationFilters()),
                0, 0, null);
        assertTrue(MapShortcuts.deleteOpensConfirmation(false, false, marker));
        assertFalse(MapShortcuts.deleteOpensConfirmation(true, false, marker), "not over an open editor");
        assertFalse(MapShortcuts.deleteOpensConfirmation(false, true, marker), "not while typing a seed");
        assertFalse(MapShortcuts.deleteOpensConfirmation(false, false, structure), "a structure is never deleted");
        assertFalse(MapShortcuts.deleteOpensConfirmation(false, false, null));
    }
}
