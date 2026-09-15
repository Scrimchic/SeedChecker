package com.scrimchic.seedchecker.exploration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.scrimchic.seedchecker.worldgen.StructureType;

class WorldExplorationTest {

    private static final long SEED = 123456789L;
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    private static StructureKey village(int chunkX, int chunkZ) {
        return StructureKey.predicted(SEED, OVERWORLD, StructureType.VILLAGE, chunkX, chunkZ);
    }

    @Test
    void everyStructureStartsUnvisitedWithoutANote() {
        WorldExploration exploration = new WorldExploration();
        assertEquals(StructureStatus.UNVISITED, exploration.statusOf(village(0, 0)));
        assertNull(exploration.noteOf(village(0, 0)));
        assertNull(exploration.annotationOf(village(0, 0)));
        assertEquals(0, exploration.structureCount());
    }

    @Test
    void everyStatusCanBeSetAndReturningToTheDefaultRemovesTheEntry() {
        WorldExploration exploration = new WorldExploration();
        StructureKey key = village(-4, 7);
        for (StructureStatus status : StructureStatus.values()) {
            exploration.setStatus(key, status);
            assertEquals(status, exploration.statusOf(key));
            assertEquals(status == StructureStatus.UNVISITED ? 0 : 1, exploration.structureCount());
        }
        assertTrue(exploration.setStatus(key, StructureStatus.UNVISITED));
        assertEquals(0, exploration.structureCount(), "back to unvisited without a note, nothing is kept");

        int revision = exploration.revision();
        assertTrue(exploration.setStatus(key, StructureStatus.LOOTED));
        assertFalse(exploration.setStatus(key, StructureStatus.LOOTED), "no change, no revision");
        assertEquals(revision + 1, exploration.revision());
        assertTrue(exploration.setNote(key, "two chests"));
        assertTrue(exploration.setStatus(key, StructureStatus.UNVISITED));
        assertEquals(1, exploration.structureCount(), "the note keeps the entry");
        assertTrue(exploration.setNote(key, "   \n  "));
        assertEquals(0, exploration.structureCount(), "a blank note is no note");
    }

    @Test
    void notesArePlainTextWithNormalisedLineBreaks() {
        WorldExploration exploration = new WorldExploration();
        StructureKey key = village(1, 1);
        exploration.setNote(key, "перший рядок\r\nsecond ✓\rthird 🏰");
        assertEquals("перший рядок\nsecond ✓\nthird 🏰", exploration.noteOf(key));
        assertFalse(exploration.setNote(key, "перший рядок\nsecond ✓\nthird 🏰"));

        StringBuilder huge = new StringBuilder();
        while (huge.length() < ExplorationText.NOTE_MAX_LENGTH - 1) {
            huge.append('a');
        }
        huge.append("🏰🏰");
        exploration.setNote(key, huge.toString());
        String stored = exploration.noteOf(key);
        assertTrue(stored.length() <= ExplorationText.NOTE_MAX_LENGTH);
        assertFalse(Character.isHighSurrogate(stored.charAt(stored.length() - 1)), "never half a code point");
    }

    @Test
    void annotationsUnderAnotherSeedAreKeptAndCounted() {
        WorldExploration exploration = new WorldExploration();
        exploration.setStatus(village(0, 0), StructureStatus.VISITED);
        exploration.setStatus(StructureKey.predicted(42L, OVERWORLD, StructureType.VILLAGE, 0, 0),
                StructureStatus.LOOTED);
        exploration.setStatus(StructureKey.of(null, OVERWORLD, "village", 9, 9), StructureStatus.EMPTY);

        assertEquals(1, exploration.structuresNotPredictedFrom(SEED));
        assertEquals(1, exploration.structuresNotPredictedFrom(42L));
        assertEquals(2, exploration.structuresNotPredictedFrom(null), "no seed: every seeded one is detached");
        assertEquals(StructureStatus.VISITED, exploration.statusOf(village(0, 0)));
        assertEquals(StructureStatus.LOOTED,
                exploration.statusOf(StructureKey.predicted(42L, OVERWORLD, StructureType.VILLAGE, 0, 0)));
    }

    @Test
    void markersBelongToExactlyOneDimension() {
        WorldExploration exploration = new WorldExploration();
        CustomMarker base = CustomMarker.create(OVERWORLD, 100, 64, -200, MarkerType.BASE, "home", null);
        CustomMarker portal = CustomMarker.create(NETHER, 12, null, -25, MarkerType.PORTAL, null, null);
        exploration.putMarker(base);
        exploration.putMarker(portal);

        List<CustomMarker> overworld = exploration.markersIn(OVERWORLD);
        assertEquals(1, overworld.size());
        assertEquals(base, overworld.get(0));
        assertEquals(1, exploration.markersIn(NETHER).size());
        assertEquals(portal, exploration.markersIn(NETHER).get(0));
        assertTrue(exploration.markersIn("minecraft:the_end").isEmpty());
        assertTrue(exploration.markersIn(null).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> exploration.markersIn(OVERWORLD).clear());
    }

    @Test
    void aMarkerKeepsItsIdWhenMovedRenamedOrRetyped() {
        WorldExploration exploration = new WorldExploration();
        CustomMarker marker = CustomMarker.create(OVERWORLD, 0, null, 0, MarkerType.CUSTOM, "spot", null);
        exploration.putMarker(marker);

        CustomMarker edited = marker.movedTo(NETHER, -8, 40, 8).withType(MarkerType.STASH)
                .withLabel("  shulkers\n here ").withNote("under the floor");
        assertEquals(marker.id(), edited.id());
        assertEquals("shulkers  here", edited.label());
        assertTrue(exploration.putMarker(edited));
        assertFalse(exploration.putMarker(edited));
        assertEquals(1, exploration.markerCount());
        assertTrue(exploration.markersIn(OVERWORLD).isEmpty(), "moved out of the overworld");
        assertEquals(edited, exploration.markersIn(NETHER).get(0));

        assertTrue(exploration.removeMarker(marker.id()));
        assertFalse(exploration.removeMarker(marker.id()));
        assertNull(exploration.marker(marker.id()));
        assertTrue(exploration.markersIn(NETHER).isEmpty());
    }

    @Test
    void twoMarkersNeverShareAnId() {
        CustomMarker first = CustomMarker.create(OVERWORLD, 1, null, 1, MarkerType.FARM, null, null);
        CustomMarker second = CustomMarker.create(OVERWORLD, 1, null, 1, MarkerType.FARM, null, null);
        assertNotEquals(first.id(), second.id());
        assertEquals("Farm", first.displayLabel());
    }

    @Test
    void aRestoredDuplicateIdIsKeptUnderANewId() {
        WorldExploration exploration = new WorldExploration();
        CustomMarker first = CustomMarker.restore("same", OVERWORLD, 1, null, 1, MarkerType.BASE, "a", null);
        CustomMarker clash = CustomMarker.restore("same", OVERWORLD, 2, null, 2, MarkerType.BASE, "b", null);
        assertEquals("same", exploration.restoreMarker(first).id());
        CustomMarker kept = exploration.restoreMarker(clash);
        assertNotNull(kept);
        assertNotEquals("same", kept.id());
        assertEquals("b", kept.label());
        assertNull(exploration.restoreMarker(first), "an exact duplicate adds nothing");
        assertEquals(2, exploration.markerCount());
    }

    @Test
    void aSnapshotIsACopy() {
        WorldExploration exploration = new WorldExploration();
        exploration.setStatus(village(0, 0), StructureStatus.VISITED);
        ExplorationSnapshot snapshot = exploration.snapshot();
        exploration.setStatus(village(0, 0), StructureStatus.DESTROYED);
        exploration.putMarker(CustomMarker.create(OVERWORLD, 0, null, 0, MarkerType.DANGER, null, null));
        assertEquals(StructureStatus.VISITED, snapshot.structures().get(0).status());
        assertTrue(snapshot.markers().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.structures().clear());
    }
}
