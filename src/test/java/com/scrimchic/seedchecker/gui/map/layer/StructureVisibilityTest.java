package com.scrimchic.seedchecker.gui.map.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.scrimchic.seedchecker.client.exploration.ExplorationManager;
import com.scrimchic.seedchecker.exploration.ExplorationFilters;
import com.scrimchic.seedchecker.exploration.StructureKey;
import com.scrimchic.seedchecker.exploration.StructureStatus;
import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.world.PlayMode;
import com.scrimchic.seedchecker.world.WorldContext;
import com.scrimchic.seedchecker.world.WorldIdentity;
import com.scrimchic.seedchecker.world.WorldProfile;
import com.scrimchic.seedchecker.worldgen.StructurePlacements;
import com.scrimchic.seedchecker.worldgen.StructureType;

/**
 * The structure filter rule, {@link StructureLayer#isStructureShown}, and the layers that ask it. It
 * takes no validation result at all, so an exact structure and a non-exact candidate are filtered
 * alike by construction.
 */
class StructureVisibilityTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String VERSION = "1.20.1";
    private static final WorldIdentity SERVER = WorldIdentity.multiplayer("filters.example.org", "Filters");

    private static ActiveWorld world(Long seed, String dimension) {
        WorldProfile profile = WorldProfile.createNew(SERVER, VERSION, 1L);
        if (seed != null) {
            profile.setManualSeed(seed.longValue());
        }
        return new ActiveWorld(WorldContext.withUnknownSeed(VERSION, PlayMode.MULTIPLAYER, dimension), profile);
    }

    private static boolean shown(ExplorationFilters filters, ExplorationManager exploration, ActiveWorld world,
                                 StructureType type, int chunkX, int chunkZ) {
        return StructureLayer.isStructureShown(filters, exploration, world, type, chunkX, chunkZ);
    }

    @Test
    void anUnannotatedStructureIsFilteredAsUnvisited(@TempDir Path root) {
        ExplorationManager exploration = CustomMarkerLayerTest.manager(root);
        ExplorationFilters filters = new ExplorationFilters();
        ActiveWorld world = world(7L, OVERWORLD);
        assertTrue(shown(filters, exploration, world, StructureType.VILLAGE, 3, 4));

        filters.showOnlyStatus(StructureStatus.UNVISITED);
        assertTrue(shown(filters, exploration, world, StructureType.VILLAGE, 3, 4), "no annotation is needed");
        filters.setStatusVisible(StructureStatus.UNVISITED, false);
        assertFalse(shown(filters, exploration, world, StructureType.VILLAGE, 3, 4));
    }

    @Test
    void hideLootedUnvisitedOnlyAndDestroyed(@TempDir Path root) {
        ExplorationManager exploration = CustomMarkerLayerTest.manager(root);
        ExplorationFilters filters = new ExplorationFilters();
        ActiveWorld world = world(7L, OVERWORLD);
        exploration.setStatus(StructureKey.predicted(7L, OVERWORLD, StructureType.DESERT_PYRAMID, 1, 1),
                StructureStatus.LOOTED);
        exploration.setStatus(StructureKey.predicted(7L, OVERWORLD, StructureType.DESERT_PYRAMID, 2, 2),
                StructureStatus.DESTROYED);

        filters.setStatusVisible(StructureStatus.LOOTED, false);
        assertFalse(shown(filters, exploration, world, StructureType.DESERT_PYRAMID, 1, 1));
        assertTrue(shown(filters, exploration, world, StructureType.DESERT_PYRAMID, 2, 2));
        assertTrue(shown(filters, exploration, world, StructureType.DESERT_PYRAMID, 9, 9));

        filters.showOnlyStatus(StructureStatus.UNVISITED);
        assertFalse(shown(filters, exploration, world, StructureType.DESERT_PYRAMID, 1, 1));
        assertFalse(shown(filters, exploration, world, StructureType.DESERT_PYRAMID, 2, 2));
        assertTrue(shown(filters, exploration, world, StructureType.DESERT_PYRAMID, 9, 9));

        filters.showOnlyStatus(StructureStatus.DESTROYED);
        assertTrue(shown(filters, exploration, world, StructureType.DESERT_PYRAMID, 2, 2));
        assertFalse(shown(filters, exploration, world, StructureType.DESERT_PYRAMID, 9, 9));
    }

    @Test
    void anAnnotationOnlyFiltersItsOwnSeedAndDimension(@TempDir Path root) {
        ExplorationManager exploration = CustomMarkerLayerTest.manager(root);
        ExplorationFilters filters = new ExplorationFilters();
        exploration.setStatus(StructureKey.predicted(7L, OVERWORLD, StructureType.RUINED_PORTAL, 5, -9),
                StructureStatus.LOOTED);
        filters.setStatusVisible(StructureStatus.LOOTED, false);

        assertFalse(shown(filters, exploration, world(7L, OVERWORLD), StructureType.RUINED_PORTAL, 5, -9));
        assertTrue(shown(filters, exploration, world(8L, OVERWORLD), StructureType.RUINED_PORTAL, 5, -9),
                "under another seed the chunk holds another structure");
        assertTrue(shown(filters, exploration, world(7L, NETHER), StructureType.RUINED_PORTAL, 5, -9),
                "the nether's portal in that chunk is another structure");
        assertTrue(shown(filters, exploration, world(null, OVERWORLD), StructureType.RUINED_PORTAL, 5, -9),
                "without a seed nothing is predicted, so nothing is recorded for it");
    }

    @Test
    void theLayersAskTheSameRule(@TempDir Path root) {
        ExplorationManager exploration = CustomMarkerLayerTest.manager(root);
        ExplorationFilters filters = new ExplorationFilters();
        StructureLayer villages = new StructureLayer(StructureType.VILLAGE, StructurePlacements.forThisVersion(),
                filters, exploration);
        StrongholdLayer strongholds = new StrongholdLayer(filters, exploration);
        ActiveWorld world = world(7L, OVERWORLD);
        exploration.setStatus(StructureKey.predicted(7L, OVERWORLD, StructureType.VILLAGE, 1, 1), StructureStatus.EMPTY);
        exploration.setStatus(StructureKey.predicted(7L, OVERWORLD, StructureType.STRONGHOLD, 1, 1),
                StructureStatus.EMPTY);

        assertTrue(villages.isShown(world, 1, 1) && strongholds.isShown(world, 1, 1));
        filters.setStatusVisible(StructureStatus.EMPTY, false);
        for (int[] chunk : new int[][] {{1, 1}, {2, 2}}) {
            assertEquals(shown(filters, exploration, world, StructureType.VILLAGE, chunk[0], chunk[1]),
                    villages.isShown(world, chunk[0], chunk[1]));
            assertEquals(shown(filters, exploration, world, StructureType.STRONGHOLD, chunk[0], chunk[1]),
                    strongholds.isShown(world, chunk[0], chunk[1]));
        }
        assertFalse(villages.isShown(world, 1, 1));
        assertTrue(villages.isShown(world, 2, 2));

        villages.setEnabled(false);
        assertFalse(villages.isShown(world, 2, 2), "a layer switched off shows nothing");
        assertFalse(strongholds.isShown(world(7L, NETHER), 2, 2), "and nothing outside its dimensions");
    }

    @Test
    void settingAStatusNeverChangesWhichStructureItIs(@TempDir Path root) {
        ExplorationManager exploration = CustomMarkerLayerTest.manager(root);
        StructureKey key = StructureKey.predicted(7L, OVERWORLD, StructureType.IGLOO, -4, 4);
        StructureKey before = StructureKey.predictedIn(world(7L, OVERWORLD), StructureType.IGLOO, -4, 4);
        exploration.setStatus(key, StructureStatus.VISITED);
        assertEquals(before, StructureKey.predictedIn(world(7L, OVERWORLD), StructureType.IGLOO, -4, 4));
        assertEquals(StructureStatus.VISITED,
                StructureLayer.explorationStatus(exploration, world(7L, OVERWORLD), StructureType.IGLOO, -4, 4));
    }
}
