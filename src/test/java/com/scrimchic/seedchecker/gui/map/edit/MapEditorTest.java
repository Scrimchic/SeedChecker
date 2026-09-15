package com.scrimchic.seedchecker.gui.map.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.scrimchic.seedchecker.client.exploration.ExplorationManager;
import com.scrimchic.seedchecker.exploration.CustomMarker;
import com.scrimchic.seedchecker.exploration.ExplorationText;
import com.scrimchic.seedchecker.exploration.MarkerType;
import com.scrimchic.seedchecker.exploration.StructureKey;
import com.scrimchic.seedchecker.exploration.StructureStatus;
import com.scrimchic.seedchecker.storage.ExplorationStorage;
import com.scrimchic.seedchecker.world.WorldIdentity;
import com.scrimchic.seedchecker.worldgen.StructureType;

class MapEditorTest {

    private static final WorldIdentity WORLD = WorldIdentity.multiplayer("play.example.net", "Server");
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final StructureKey VILLAGE =
            StructureKey.predicted(42L, OVERWORLD, StructureType.VILLAGE, -12, 30);

    private static ExplorationManager open(Path root) {
        ExplorationManager manager = new ExplorationManager(new ExplorationStorage(root), new ExplorationManager.Clock() {
            @Override
            public long millis() {
                return 0L;
            }
        });
        manager.activate(WORLD);
        return manager;
    }

    private static void type(MapEditor editor, String text) {
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (codePoint == '\n') {
                editor.handleKey(EditorKey.ENTER, false);
            } else {
                editor.typeCodePoint(codePoint);
            }
            i += Character.charCount(codePoint);
        }
    }

    // ------------------------------------------------------------ structure notes

    @Test
    void aNoteOpensWithWhatIsStoredAndSavesOnCtrlEnter(@TempDir Path root) {
        ExplorationManager exploration = open(root);
        exploration.setNote(VILLAGE, "old line");
        MapEditor editor = new MapEditor(exploration);

        assertTrue(editor.beginStructureNote(VILLAGE, "Village at chunk -12, 30"));
        assertEquals("old line", editor.note().text());
        assertTrue(editor.acceptsText());
        type(editor, "\nнова 🏰 ✓");
        assertEquals("old line", exploration.noteOf(VILLAGE), "nothing is written before save");

        assertTrue(editor.handleKey(EditorKey.ENTER, true));
        assertEquals(MapEditor.Result.SAVED, editor.takeResult());
        assertNull(editor.takeResult(), "a result is taken once");
        assertFalse(editor.isActive());
        assertEquals("old line\nнова 🏰 ✓", exploration.noteOf(VILLAGE));
    }

    @Test
    void escapeCancelsAndKeepsTheStoredNote(@TempDir Path root) {
        ExplorationManager exploration = open(root);
        exploration.setNote(VILLAGE, "keep me");
        MapEditor editor = new MapEditor(exploration);
        editor.beginStructureNote(VILLAGE, "village");
        type(editor, " and not this");
        assertTrue(editor.handleKey(EditorKey.ESCAPE, false));
        assertEquals(MapEditor.Result.CANCELLED, editor.takeResult());
        assertEquals("keep me", exploration.noteOf(VILLAGE));
        assertFalse(editor.handleKey(EditorKey.ESCAPE, false), "a closed editor leaves Escape to the screen");
        assertFalse(editor.typeCodePoint('x'));
    }

    @Test
    void anEmptiedNoteIsCleared(@TempDir Path root) {
        ExplorationManager exploration = open(root);
        exploration.setStatus(VILLAGE, StructureStatus.LOOTED);
        exploration.setNote(VILLAGE, "ab");
        MapEditor editor = new MapEditor(exploration);
        editor.beginStructureNote(VILLAGE, "village");
        editor.handleKey(EditorKey.BACKSPACE, false);
        editor.handleKey(EditorKey.BACKSPACE, false);
        type(editor, "   ");
        assertEquals(MapEditor.Result.SAVED, editor.save());
        assertNull(exploration.noteOf(VILLAGE));
        assertEquals(StructureStatus.LOOTED, exploration.statusOf(VILLAGE), "the status is the same structure's");
    }

    @Test
    void aNoteStopsAtTheModelsLimit(@TempDir Path root) {
        ExplorationManager exploration = open(root);
        MapEditor editor = new MapEditor(exploration);
        editor.beginStructureNote(VILLAGE, "village");
        for (int i = 0; i < ExplorationText.NOTE_MAX_CODE_POINTS + 50; i++) {
            editor.typeCodePoint('a' + i % 26);
        }
        assertEquals(ExplorationText.NOTE_MAX_CODE_POINTS, editor.note().codePointCount());
        editor.save();
        assertEquals(ExplorationText.NOTE_MAX_CODE_POINTS, exploration.noteOf(VILLAGE).length());
    }

    // ------------------------------------------------------------ paste

    @Test
    void pastingIntoANoteKeepsItsLinesAndUnicodeAtTheCursor(@TempDir Path root) {
        ExplorationManager exploration = open(root);
        MapEditor editor = new MapEditor(exploration);
        editor.beginStructureNote(VILLAGE, "village");
        type(editor, "head tail");
        for (int i = 0; i < 5; i++) {
            editor.handleKey(EditorKey.LEFT, false);
        }
        assertTrue(editor.paste("\r\nскриня 🏰\r\nспавнер ✓\n"));
        editor.save();
        assertEquals("head\nскриня 🏰\nспавнер ✓\n tail", exploration.noteOf(VILLAGE));
    }

    @Test
    void pastingIntoALabelKeepsItOneLine(@TempDir Path root) {
        ExplorationManager exploration = open(root);
        MapEditor editor = new MapEditor(exploration);
        editor.beginCreateMarker(OVERWORLD, 0, null, 0);
        editor.paste("Main\nbase\t🏠");
        assertEquals("Main base 🏠", editor.label().text());
        editor.handleKey(EditorKey.TAB, false);
        editor.paste("line one\nline two");
        editor.save();
        CustomMarker created = exploration.marker(editor.savedMarkerId());
        assertEquals("Main base 🏠", created.label());
        assertEquals("line one\nline two", created.note());
    }

    @Test
    void pastingNearTheLimitKeepsWhatFitsAndNeverHalfAnEmoji(@TempDir Path root) {
        ExplorationManager exploration = open(root);
        MapEditor editor = new MapEditor(exploration);
        editor.beginStructureNote(VILLAGE, "village");
        StringBuilder almost = new StringBuilder();
        for (int i = 0; i < ExplorationText.NOTE_MAX_CODE_POINTS - 2; i++) {
            almost.append('a');
        }
        editor.paste(almost.toString());
        editor.paste("🏰🏰🏰");
        assertEquals(ExplorationText.NOTE_MAX_CODE_POINTS, editor.note().codePointCount());
        editor.save();
        String note = exploration.noteOf(VILLAGE);
        assertTrue(note.endsWith("🏰🏰"));
        assertEquals(ExplorationText.NOTE_MAX_CODE_POINTS, ExplorationText.codePointLength(note));
    }

    @Test
    void pasteIsSwallowedByADeletionAndIgnoredWithoutAnEditor(@TempDir Path root) {
        ExplorationManager exploration = open(root);
        CustomMarker marker = exploration.createMarker(OVERWORLD, 0, null, 0, MarkerType.BASE, null, null);
        MapEditor editor = new MapEditor(exploration);
        assertFalse(editor.paste("x"), "no editor, the screen may use the key");
        editor.beginDeleteMarker(marker.id());
        assertTrue(editor.paste("confirm"));
        assertEquals(MapEditor.Mode.DELETE_MARKER, editor.mode());
    }

    // ------------------------------------------------------------ markers

    @Test
    void aMarkerDraftIsCreatedOnlyOnSave(@TempDir Path root) {
        ExplorationManager exploration = open(root);
        MapEditor editor = new MapEditor(exploration);

        assertTrue(editor.beginCreateMarker(OVERWORLD, 120, null, -40));
        assertEquals(MapEditor.Field.LABEL, editor.focus());
        editor.setType(MarkerType.STASH);
        type(editor, "shulkers");
        editor.handleKey(EditorKey.TAB, false);
        type(editor, "under the floor\nsecond line");
        assertTrue(exploration.markersIn(OVERWORLD).isEmpty(), "a draft is not a marker");

        editor.handleKey(EditorKey.ENTER, true);
        assertEquals(MapEditor.Result.SAVED, editor.takeResult());
        assertEquals(1, exploration.markersIn(OVERWORLD).size());
        CustomMarker created = exploration.marker(editor.savedMarkerId());
        assertNotNull(created);
        assertEquals(MarkerType.STASH, created.type());
        assertEquals("shulkers", created.label());
        assertEquals("under the floor\nsecond line", created.note());
        assertEquals(120, created.x());
        assertNull(created.y(), "a map column has no height");
        assertEquals(-40, created.z());
    }

    @Test
    void enterInTheLabelMovesToTheNoteInsteadOfBreakingTheLabel(@TempDir Path root) {
        MapEditor editor = new MapEditor(open(root));
        editor.beginCreateMarker(OVERWORLD, 0, 64, 0);
        type(editor, "farm");
        editor.handleKey(EditorKey.ENTER, false);
        assertEquals(MapEditor.Field.NOTE, editor.focus());
        assertEquals("farm", editor.label().text());
    }

    @Test
    void cancellingADraftLeavesNothingBehind(@TempDir Path root) {
        ExplorationStorage storage = new ExplorationStorage(root);
        ExplorationManager exploration = open(root);
        MapEditor editor = new MapEditor(exploration);
        editor.beginCreateMarker(NETHER, 1, 2, 3);
        type(editor, "temporary");
        editor.cancel();
        assertEquals(MapEditor.Result.CANCELLED, editor.takeResult());
        assertTrue(exploration.markersIn(NETHER).isEmpty());
        assertFalse(exploration.isDirty());
        exploration.deactivate();
        assertFalse(Files.exists(storage.explorationPath(WORLD)), "no file for a world where nothing was kept");
    }

    @Test
    void editingKeepsTheIdAndThePosition(@TempDir Path root) {
        ExplorationManager exploration = open(root);
        CustomMarker marker = exploration.createMarker(OVERWORLD, 5, 70, -5, MarkerType.BASE, "home", "note");
        MapEditor editor = new MapEditor(exploration);

        assertTrue(editor.beginEditMarker(marker.id()));
        assertEquals("home", editor.label().text());
        assertEquals("note", editor.note().text());
        editor.setType(MarkerType.PLAYER_BASE);
        type(editor, " base");
        assertEquals(MapEditor.Result.SAVED, editor.save());

        CustomMarker edited = exploration.marker(marker.id());
        assertEquals(marker.id(), edited.id());
        assertEquals(marker.id(), editor.savedMarkerId());
        assertEquals("home base", edited.label());
        assertEquals(MarkerType.PLAYER_BASE, edited.type());
        assertEquals(5, edited.x());
        assertEquals(Integer.valueOf(70), edited.y());
        assertEquals(-5, edited.z());
        assertEquals(1, exploration.markersIn(OVERWORLD).size());
    }

    @Test
    void movingToTheMapPointerForgetsTheHeightAndToThePlayerSetsIt(@TempDir Path root) {
        ExplorationManager exploration = open(root);
        CustomMarker marker = exploration.createMarker(OVERWORLD, 5, 70, -5, MarkerType.FARM, "wheat", null);
        MapEditor editor = new MapEditor(exploration);

        assertTrue(editor.moveMarker(marker.id(), OVERWORLD, -1000, null, 2000));
        CustomMarker moved = exploration.marker(marker.id());
        assertEquals(-1000, moved.x());
        assertNull(moved.y());
        assertEquals(2000, moved.z());
        assertEquals("wheat", moved.label());

        assertTrue(editor.moveMarker(marker.id(), OVERWORLD, 12, -59, 13));
        assertEquals(Integer.valueOf(-59), exploration.marker(marker.id()).y());
        assertFalse(editor.moveMarker("no-such-id", OVERWORLD, 0, null, 0));
    }

    @Test
    void deletingNeedsItsOwnConfirmation(@TempDir Path root) {
        ExplorationManager exploration = open(root);
        CustomMarker marker = exploration.createMarker(OVERWORLD, 0, null, 0, MarkerType.DANGER, "creeper hole", null);
        MapEditor editor = new MapEditor(exploration);

        assertTrue(editor.beginDeleteMarker(marker.id()));
        assertFalse(editor.acceptsText());
        assertTrue(editor.typeCodePoint('y'), "typing is swallowed, not confirmation");
        assertTrue(editor.handleKey(EditorKey.ENTER, false));
        assertTrue(editor.handleKey(EditorKey.ENTER, true));
        assertEquals(MapEditor.Mode.DELETE_MARKER, editor.mode(), "no key confirms a deletion");
        assertNotNull(exploration.marker(marker.id()));

        editor.handleKey(EditorKey.ESCAPE, false);
        assertEquals(MapEditor.Result.CANCELLED, editor.takeResult());
        assertNotNull(exploration.marker(marker.id()));

        editor.beginDeleteMarker(marker.id());
        assertEquals(MapEditor.Result.DELETED, editor.save());
        assertNull(exploration.marker(marker.id()));
        assertFalse(editor.beginDeleteMarker(marker.id()), "a marker is only deleted once");
    }

    @Test
    void aMarkerDeletedWhileBeingEditedIsReportedGone(@TempDir Path root) {
        ExplorationManager exploration = open(root);
        CustomMarker marker = exploration.createMarker(OVERWORLD, 0, null, 0, MarkerType.CUSTOM, null, null);
        MapEditor editor = new MapEditor(exploration);
        editor.beginEditMarker(marker.id());
        exploration.removeMarker(marker.id());
        assertEquals(MapEditor.Result.GONE, editor.save());
        assertTrue(exploration.markersIn(OVERWORLD).isEmpty(), "saving never resurrects it");
    }

    @Test
    void aReadOnlyExplorationCannotBeEditedButStillReads(@TempDir Path root) throws IOException {
        ExplorationStorage storage = new ExplorationStorage(root);
        Path file = storage.explorationPath(WORLD);
        Files.createDirectories(file.getParent());
        Files.write(file, ("{\"formatVersion\": 9, \"markers\": [{\"id\": \"m1\", \"dimension\": \"minecraft:overworld\","
                + " \"type\": \"base\", \"x\": 1, \"z\": 2}]}").getBytes(Charset.forName("UTF-8")));
        ExplorationManager exploration = open(root);
        assertFalse(exploration.isWritable());
        assertTrue(exploration.readOnlyReason().contains("newer"));
        MapEditor editor = new MapEditor(exploration);

        assertEquals(1, exploration.markersIn(OVERWORLD).size(), "markers are still shown");
        assertFalse(editor.beginStructureNote(VILLAGE, "village"));
        assertFalse(editor.beginCreateMarker(OVERWORLD, 0, null, 0));
        assertFalse(editor.beginEditMarker("m1"));
        assertFalse(editor.beginDeleteMarker("m1"));
        assertFalse(editor.moveMarker("m1", OVERWORLD, 9, null, 9));
        assertFalse(editor.isActive());
        assertEquals(1, exploration.marker("m1").x());
    }

    // ------------------------------------------------------------ lifecycle

    @Test
    void createEditAndDeleteSurviveReopening(@TempDir Path root) {
        ExplorationManager first = open(root);
        MapEditor editor = new MapEditor(first);
        editor.beginCreateMarker(OVERWORLD, 300, 64, 300);
        type(editor, "outpost");
        editor.save();
        String id = editor.savedMarkerId();
        first.deactivate();

        ExplorationManager second = open(root);
        assertEquals("outpost", second.marker(id).label());
        editor = new MapEditor(second);
        editor.beginEditMarker(id);
        editor.handleKey(EditorKey.TAB, false);
        type(editor, "raid farm next door");
        editor.save();
        editor.moveMarker(id, OVERWORLD, 310, null, 290);
        second.deactivate();

        ExplorationManager third = open(root);
        CustomMarker reopened = third.marker(id);
        assertEquals("raid farm next door", reopened.note());
        assertEquals(310, reopened.x());
        assertNull(reopened.y());
        editor = new MapEditor(third);
        editor.beginDeleteMarker(id);
        editor.save();
        third.deactivate();

        assertNull(open(root).marker(id));
    }
}
