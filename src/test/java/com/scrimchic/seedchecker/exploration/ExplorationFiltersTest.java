package com.scrimchic.seedchecker.exploration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExplorationFiltersTest {

    @Test
    void everythingIsVisibleByDefault() {
        ExplorationFilters filters = new ExplorationFilters();
        assertTrue(filters.showsAllStatuses());
        assertTrue(filters.showsAllMarkerTypes());
        for (StructureStatus status : StructureStatus.values()) {
            assertTrue(filters.isStructureVisible(status));
        }
        assertTrue(filters.isStructureVisible(null));
    }

    @Test
    void aStructureWithoutAnAnnotationIsUnvisited() {
        ExplorationFilters filters = new ExplorationFilters();
        filters.showOnlyStatus(StructureStatus.UNVISITED);
        assertTrue(filters.isStructureVisible(null), "nothing recorded passes the unvisited filter");
        assertFalse(filters.isStructureVisible(StructureStatus.LOOTED));

        filters.setStatusVisible(StructureStatus.UNVISITED, false);
        filters.setStatusVisible(StructureStatus.DESTROYED, true);
        assertFalse(filters.isStructureVisible(null));
        assertTrue(filters.isStructureVisible(StructureStatus.DESTROYED));
    }

    @Test
    void theShortcutsAreOperationsOnTheSameState() {
        ExplorationFilters filters = new ExplorationFilters();
        filters.setStatusVisible(StructureStatus.LOOTED, false);
        assertFalse(filters.showsStatus(StructureStatus.LOOTED));
        assertTrue(filters.showsStatus(StructureStatus.VISITED), "hiding looted leaves the rest");
        filters.toggleStatus(StructureStatus.EMPTY);
        filters.showAllStatuses();
        assertTrue(filters.showsAllStatuses());

        int revision = filters.revision();
        filters.showAllStatuses();
        assertEquals(revision, filters.revision(), "no change, no revision");
        filters.toggleStatus(StructureStatus.VISITED);
        assertEquals(revision + 1, filters.revision());
    }

    @Test
    void everyMarkerTypeFiltersOnItsOwn() {
        ExplorationFilters filters = new ExplorationFilters();
        for (MarkerType hidden : MarkerType.values()) {
            filters.showAllMarkerTypes();
            filters.toggleMarkerType(hidden);
            for (MarkerType type : MarkerType.values()) {
                CustomMarker marker = CustomMarker.create("minecraft:overworld", 0, null, 0, type, null, null);
                assertEquals(type != hidden, filters.isMarkerVisible(marker), hidden + " hidden, " + type);
            }
            assertFalse(filters.showsAllMarkerTypes());
            filters.toggleMarkerType(hidden);
            assertTrue(filters.showsAllMarkerTypes(), "toggling back restores it");
        }
        assertFalse(filters.isMarkerVisible(null));
    }
}
