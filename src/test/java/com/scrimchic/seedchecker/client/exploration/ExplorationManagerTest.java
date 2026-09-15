package com.scrimchic.seedchecker.client.exploration;

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

import com.scrimchic.seedchecker.exploration.CustomMarker;
import com.scrimchic.seedchecker.exploration.MarkerType;
import com.scrimchic.seedchecker.exploration.StructureKey;
import com.scrimchic.seedchecker.exploration.StructureStatus;
import com.scrimchic.seedchecker.storage.ExplorationStorage;
import com.scrimchic.seedchecker.world.WorldIdentity;
import com.scrimchic.seedchecker.worldgen.StructureType;

/** The manager's lifecycle, driven as WorldProfileManager drives it, with a clock of the test's own. */
class ExplorationManagerTest {

    private static final WorldIdentity WORLD_A = WorldIdentity.singleplayer("A", "World A");
    private static final WorldIdentity WORLD_B = WorldIdentity.multiplayer("play.example.net", "Server B");
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final long SEED = 99L;

    private static final class FakeClock implements ExplorationManager.Clock {
        long now = 1_000_000L;

        @Override
        public long millis() {
            return now;
        }
    }

    private static StructureKey fortress(int chunkX, int chunkZ) {
        return StructureKey.predicted(SEED, NETHER, StructureType.NETHER_FORTRESS, chunkX, chunkZ);
    }

    @Test
    void eachWorldHasItsOwnExplorationAndComesBackAfterASwitch(@TempDir Path root) {
        FakeClock clock = new FakeClock();
        ExplorationManager manager = new ExplorationManager(new ExplorationStorage(root), clock);

        manager.activate(WORLD_A);
        assertTrue(manager.setStatus(fortress(1, 1), StructureStatus.LOOTED));
        CustomMarker home = manager.createMarker(OVERWORLD, 10, 70, 10, MarkerType.BASE, "home", null);
        assertNotNull(home);

        // Switching world writes A immediately, before the save delay.
        manager.activate(WORLD_B);
        assertEquals(WORLD_B, manager.activeIdentity());
        assertEquals(StructureStatus.UNVISITED, manager.statusOf(fortress(1, 1)));
        assertTrue(manager.markersIn(OVERWORLD).isEmpty());
        manager.setStatus(fortress(1, 1), StructureStatus.EMPTY);

        manager.activate(WORLD_A);
        assertEquals(StructureStatus.LOOTED, manager.statusOf(fortress(1, 1)));
        assertEquals(home, manager.marker(home.id()));

        // And a fresh manager, as after a restart, reads the same from disk.
        ExplorationManager restarted = new ExplorationManager(new ExplorationStorage(root), clock);
        restarted.activate(WORLD_B);
        assertEquals(StructureStatus.EMPTY, restarted.statusOf(fortress(1, 1)));
        restarted.activate(WORLD_A);
        assertEquals(StructureStatus.LOOTED, restarted.statusOf(fortress(1, 1)));
    }

    @Test
    void anOverworldMarkerIsNeverANetherMarker(@TempDir Path root) {
        ExplorationManager manager = new ExplorationManager(new ExplorationStorage(root), new FakeClock());
        manager.activate(WORLD_A);
        CustomMarker overworld = manager.createMarker(OVERWORLD, 800, null, 800, MarkerType.PORTAL, null, null);
        CustomMarker nether = manager.createMarker(NETHER, 100, null, 100, MarkerType.PORTAL, null, null);
        assertEquals(1, manager.markersIn(OVERWORLD).size());
        assertEquals(overworld, manager.markersIn(OVERWORLD).get(0));
        assertEquals(1, manager.markersIn(NETHER).size());
        assertEquals(nether, manager.markersIn(NETHER).get(0));
        assertTrue(manager.markersIn("minecraft:the_end").isEmpty());
    }

    @Test
    void leavingAWorldWritesAndClearsIt(@TempDir Path root) {
        ExplorationManager manager = new ExplorationManager(new ExplorationStorage(root), new FakeClock());
        manager.activate(WORLD_A);
        manager.setNote(fortress(0, 0), "blaze spawner at the east end");
        manager.deactivate();

        assertFalse(manager.isActive());
        assertFalse(manager.isWritable());
        assertNull(manager.activeIdentity());
        assertNull(manager.noteOf(fortress(0, 0)));
        assertTrue(manager.markersIn(OVERWORLD).isEmpty());
        assertFalse(manager.setStatus(fortress(0, 0), StructureStatus.VISITED), "nothing to edit outside a world");
        assertNull(manager.createMarker(OVERWORLD, 0, null, 0, MarkerType.BASE, null, null));

        manager.activate(WORLD_A);
        assertEquals("blaze spawner at the east end", manager.noteOf(fortress(0, 0)));
        manager.activate(null);
        assertFalse(manager.isActive(), "no identity is leaving");
    }

    @Test
    void editsAreWrittenOnceTheyHaveSettledNotPerEdit(@TempDir Path root) {
        FakeClock clock = new FakeClock();
        ExplorationStorage storage = new ExplorationStorage(root);
        ExplorationManager manager = new ExplorationManager(storage, clock);
        Path file = storage.explorationPath(WORLD_A);
        manager.activate(WORLD_A);

        manager.setStatus(fortress(2, 2), StructureStatus.VISITED);
        clock.now += 400;
        manager.setStatus(fortress(2, 2), StructureStatus.LOOTED);
        manager.tick();
        assertTrue(manager.isDirty());
        assertFalse(Files.exists(file), "nothing is written while edits keep coming");

        clock.now += ExplorationManager.SAVE_DELAY_MILLIS - 1;
        manager.tick();
        assertFalse(Files.exists(file));
        clock.now += 1;
        manager.tick();
        assertTrue(Files.exists(file));
        assertFalse(manager.isDirty());

        assertFalse(manager.setStatus(fortress(2, 2), StructureStatus.LOOTED), "no change");
        assertFalse(manager.isDirty());
        assertEquals(StructureStatus.LOOTED, storage.load(WORLD_A).exploration().statusOf(fortress(2, 2)));
    }

    @Test
    void markersAreUpdatedInPlaceByIdOnly(@TempDir Path root) {
        ExplorationManager manager = new ExplorationManager(new ExplorationStorage(root), new FakeClock());
        manager.activate(WORLD_A);
        CustomMarker marker = manager.createMarker(OVERWORLD, 0, null, 0, MarkerType.CUSTOM, "x", null);
        assertTrue(manager.updateMarker(marker.movedTo(OVERWORLD, 50, 12, -50).withLabel("y")));
        assertEquals(50, manager.marker(marker.id()).x());
        assertFalse(manager.updateMarker(CustomMarker.create(OVERWORLD, 1, null, 1, MarkerType.CUSTOM, null, null)),
                "an update never creates");
        assertTrue(manager.removeMarker(marker.id()));
        assertFalse(manager.removeMarker(marker.id()));
        manager.deactivate();
        manager.activate(WORLD_A);
        assertTrue(manager.markersIn(OVERWORLD).isEmpty());
    }

    @Test
    void aFileOfANewerFormatIsShownButNotEdited(@TempDir Path root) throws IOException {
        ExplorationStorage storage = new ExplorationStorage(root);
        Path file = storage.explorationPath(WORLD_A);
        Files.createDirectories(file.getParent());
        String newer = "{\"formatVersion\": 7, \"structures\": [{\"dimension\": \"minecraft:the_nether\","
                + " \"type\": \"nether_fortress\", \"chunkX\": 3, \"chunkZ\": 3, \"seed\": \"99\", \"status\": \"looted\"}]}";
        Files.write(file, newer.getBytes(Charset.forName("UTF-8")));

        ExplorationManager manager = new ExplorationManager(storage, new FakeClock());
        manager.activate(WORLD_A);
        assertTrue(manager.isActive());
        assertFalse(manager.isWritable());
        assertEquals("exploration.json is from a newer Seed Checker", manager.readOnlyReason());
        assertEquals(StructureStatus.LOOTED, manager.statusOf(fortress(3, 3)));
        assertFalse(manager.setStatus(fortress(3, 3), StructureStatus.EMPTY));
        manager.deactivate();
        assertEquals(newer, new String(Files.readAllBytes(file), Charset.forName("UTF-8")));
    }

    @Test
    void changingTheSeedHidesAnnotationsWithoutLosingThem(@TempDir Path root) {
        // The profile's seed is not the manager's business; keys carry it. Under another seed the
        // same chunk is another key, and the first seed's annotation is still there afterwards.
        ExplorationManager manager = new ExplorationManager(new ExplorationStorage(root), new FakeClock());
        manager.activate(WORLD_B);
        StructureKey underFirst = StructureKey.predicted(SEED, OVERWORLD, StructureType.VILLAGE, 4, 4);
        StructureKey underSecond = StructureKey.predicted(SEED + 1, OVERWORLD, StructureType.VILLAGE, 4, 4);
        manager.setStatus(underFirst, StructureStatus.LOOTED);

        assertEquals(StructureStatus.UNVISITED, manager.statusOf(underSecond));
        assertEquals(1, manager.structuresNotPredictedFrom(SEED + 1));
        assertEquals(1, manager.structuresNotPredictedFrom(null));
        assertEquals(0, manager.structuresNotPredictedFrom(SEED));
        assertEquals(StructureStatus.LOOTED, manager.statusOf(underFirst));
    }
}
