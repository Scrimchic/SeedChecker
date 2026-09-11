package com.scrimchic.seedchecker.gui.map.layer;

import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.core.map.MapViewport;
import com.scrimchic.seedchecker.gui.map.MapCanvas;
import com.scrimchic.seedchecker.world.ActiveWorld;

/**
 * One switchable overlay on the map, such as slime chunks, and later biomes or structures.
 *
 * <p>A layer is asked to draw only the chunks that are on screen, and only after it has said it
 * is able to draw at all, so implementations do not have to repeat those checks. It is handed an
 * {@link ActiveWorld}, which has already resolved runtime and remembered seeds into one answer, so
 * no layer has to know where a seed came from.
 */
public interface MapLayer {

    /** Name shown in the Seed Checker panel. */
    String displayName();

    boolean isEnabled();

    void setEnabled(boolean enabled);

    /**
     * Why this layer cannot draw right now.
     *
     * <p>Returning a reason is not an error: a layer that needs a seed says so, and the screen
     * shows it next to the toggle instead of silently drawing nothing.
     *
     * @return a short lower-case reason, or {@code null} when the layer is ready to draw
     */
    String unavailableReason(ActiveWorld world, MapViewport viewport, ChunkRange visible);

    /**
     * Draws the layer. Only called when the layer is enabled and
     * {@link #unavailableReason} returned {@code null}.
     */
    void render(MapCanvas canvas, MapViewport viewport, ChunkRange visible, ActiveWorld world);
}
