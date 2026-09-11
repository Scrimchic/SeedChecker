package com.scrimchic.seedchecker.gui.map.layer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.core.map.MapViewport;
import com.scrimchic.seedchecker.gui.map.MapCanvas;
import com.scrimchic.seedchecker.world.WorldContext;

/** The ordered set of map layers, drawn bottom to top. */
public final class MapLayers {

    private final List<MapLayer> layers = new ArrayList<MapLayer>();

    /** Kept as a field: {@link #all()} is called once per frame while drawing the panel. */
    private final List<MapLayer> view = Collections.unmodifiableList(layers);

    /** The layers Seed Checker ships with today. */
    public static MapLayers createDefault() {
        MapLayers created = new MapLayers();
        created.add(new SlimeChunkLayer());
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
                          WorldContext context) {
        for (int i = 0; i < layers.size(); i++) {
            MapLayer layer = layers.get(i);
            if (layer.isEnabled() && layer.unavailableReason(context, viewport, visible) == null) {
                layer.render(canvas, viewport, visible, context);
            }
        }
    }
}
