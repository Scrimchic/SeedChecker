package com.scrimchic.seedchecker.gui.map.layer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.core.map.MapPreferences;
import com.scrimchic.seedchecker.core.map.MapViewport;
import com.scrimchic.seedchecker.exploration.ExplorationFilters;
import com.scrimchic.seedchecker.gui.map.MapCanvas;
import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.worldgen.StructurePlacements;
import com.scrimchic.seedchecker.worldgen.StructureType;

/** The ordered set of map layers, drawn bottom to top. */
public final class MapLayers {

    private final List<MapLayer> layers = new ArrayList<MapLayer>();

    /** Kept as a field: {@link #all()} is called once per frame while drawing the panel. */
    private final List<MapLayer> view = Collections.unmodifiableList(layers);

    private CustomMarkerLayer customMarkers;

    /** The layers with everything visible, for code that has no preferences of its own. */
    public static MapLayers createDefault() {
        return createDefault(new ExplorationFilters());
    }

    /**
     * The layers Seed Checker ships with today, sharing one set of exploration filters.
     *
     * <p>Structure layers are created from {@link StructurePlacements#forThisVersion()}, so a
     * structure the running version does not have simply never becomes a layer - there is no
     * permanently greyed-out row for ancient cities on 1.16.5.
     */
    public static MapLayers createDefault(ExplorationFilters filters) {
        MapLayers created = new MapLayers();
        // Biomes first, so the map paints under the grid overlays rather than over them.
        created.add(new BiomeLayer());
        created.add(new SlimeChunkLayer());

        StructurePlacements placements = StructurePlacements.forThisVersion();
        for (StructureType type : placements.types()) {
            created.add(new StructureLayer(type, placements, filters));
        }
        // Not grid placed, so not in StructurePlacements; every supported version has strongholds.
        created.add(new StrongholdLayer(filters));
        // Last, so the player's own markers are never drawn under a predicted structure.
        created.customMarkers = new CustomMarkerLayer(filters);
        created.add(created.customMarkers);
        return created;
    }

    public void add(MapLayer layer) {
        layers.add(layer);
    }

    public List<MapLayer> all() {
        return view;
    }

    /** The custom marker layer, or {@code null} for a set built without one. */
    public CustomMarkerLayer customMarkers() {
        return customMarkers;
    }

    /**
     * Switches every layer as the preferences say, and leaves a layer the preferences never mention at
     * its own default.
     */
    public void applyPreferences(MapPreferences preferences) {
        for (int i = 0; i < layers.size(); i++) {
            MapLayer layer = layers.get(i);
            layer.setEnabled(preferences.isLayerVisible(layer.id(), layer.isEnabled()));
        }
    }

    /** Draws every layer of this dimension that is switched on and currently able to draw. */
    public void renderAll(MapCanvas canvas, MapViewport viewport, ChunkRange visible,
                          ActiveWorld world) {
        String dimensionId = world.context().dimensionId();
        for (int i = 0; i < layers.size(); i++) {
            MapLayer layer = layers.get(i);
            if (isDrawing(layer, dimensionId, world, viewport, visible)) {
                layer.render(canvas, viewport, visible, world);
            }
        }
    }

    /** Whether {@link #renderAll} draws that layer this frame. */
    public static boolean isDrawing(MapLayer layer, String dimensionId, ActiveWorld world, MapViewport viewport,
                                    ChunkRange visible) {
        return layer.appliesTo(dimensionId) && layer.isEnabled()
                && layer.unavailableReason(world, viewport, visible) == null;
    }
}
