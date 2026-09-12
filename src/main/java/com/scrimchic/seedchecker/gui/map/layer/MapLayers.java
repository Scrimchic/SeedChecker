package com.scrimchic.seedchecker.gui.map.layer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.core.map.MapViewport;
import com.scrimchic.seedchecker.gui.map.MapCanvas;
import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.worldgen.StructurePlacements;
import com.scrimchic.seedchecker.worldgen.StructureType;

/** The ordered set of map layers, drawn bottom to top. */
public final class MapLayers {

    private final List<MapLayer> layers = new ArrayList<MapLayer>();

    /** Kept as a field: {@link #all()} is called once per frame while drawing the panel. */
    private final List<MapLayer> view = Collections.unmodifiableList(layers);

    /**
     * The layers Seed Checker ships with today.
     *
     * <p>Structure layers are created from {@link StructurePlacements#forThisVersion()}, so a
     * structure the running version does not have simply never becomes a layer - there is no
     * permanently greyed-out row for ancient cities on 1.16.5.
     */
    public static MapLayers createDefault() {
        MapLayers created = new MapLayers();
        // Biomes first, so the map paints under the grid overlays rather than over them.
        created.add(new BiomeLayer());
        created.add(new SlimeChunkLayer());

        StructurePlacements placements = StructurePlacements.forThisVersion();
        for (StructureType type : placements.types()) {
            created.add(new StructureLayer(type, placements.get(type)));
        }
        return created;
    }

    public void add(MapLayer layer) {
        layers.add(layer);
    }

    public List<MapLayer> all() {
        return view;
    }

    /** Draws every layer that is switched on and currently able to draw. */
    public void renderAll(MapCanvas canvas, MapViewport viewport, ChunkRange visible,
                          ActiveWorld world) {
        for (int i = 0; i < layers.size(); i++) {
            MapLayer layer = layers.get(i);
            if (layer.isEnabled() && layer.unavailableReason(world, viewport, visible) == null) {
                layer.render(canvas, viewport, visible, world);
            }
        }
    }
}
