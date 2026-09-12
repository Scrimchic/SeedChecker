package com.scrimchic.seedchecker.gui.map.layer;

import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.core.map.MapViewport;
import com.scrimchic.seedchecker.gui.map.MapCanvas;
import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.worldgen.StructureCandidateVisitor;
import com.scrimchic.seedchecker.worldgen.StructurePlacementConfig;
import com.scrimchic.seedchecker.worldgen.StructurePlacementEngine;
import com.scrimchic.seedchecker.worldgen.StructureType;

/**
 * Marks the chunks one grid-placed structure could start in.
 *
 * <p>These are <em>candidates</em>, which is why the layer is named after the placement and not
 * after the building: vanilla still has to approve the biome, and sometimes the terrain, before a
 * structure really generates. The panel label says so too.
 *
 * <p>One instance per structure type; {@link MapLayers#createDefault()} only creates the types the
 * running version actually has, so an unsupported structure never appears in the UI at all.
 */
public final class StructureLayer implements MapLayer {

    /** Region scan budget per frame. At ~0.2 us per region this stays well under a millisecond. */
    private static final long MAX_REGIONS = 2048L;

    /** Marker budget per frame, so a very wide view cannot flood the draw calls. */
    private static final int MAX_MARKERS = 1024;

    /** Markers never shrink below this, so a candidate stays visible when zoomed out. */
    private static final int MIN_MARKER_PIXELS = 5;

    private static final int BORDER_COLOR = 0xFF0B0E11;

    private final StructureType type;
    private final StructurePlacementConfig config;
    private final int color;

    /** Reused across frames; the render thread is the only thread that touches it. */
    private final StructurePlacementEngine engine = new StructurePlacementEngine();

    private boolean enabled = true;

    public StructureLayer(StructureType type, StructurePlacementConfig config) {
        this.type = type;
        this.config = config;
        this.color = colorOf(type);
    }

    private static int colorOf(StructureType type) {
        switch (type) {
            case VILLAGE:
                return 0xFFD8A24A;
            case DESERT_PYRAMID:
                return 0xFFE8D98A;
            case SHIPWRECK:
                return 0xFF6FA8D6;
            case ANCIENT_CITY:
                return 0xFF4FB3A5;
            case TRIAL_CHAMBER:
                return 0xFFC8785A;
            default:
                return 0xFFCCCCCC;
        }
    }

    @Override
    public String displayName() {
        return type.displayName();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String unavailableReason(ActiveWorld world, MapViewport viewport, ChunkRange visible) {
        if (!world.hasSeed()) {
            return "needs a known seed";
        }
        if (StructurePlacementEngine.regionCount(config, visible) > MAX_REGIONS) {
            return "zoom in";
        }
        return null;
    }

    @Override
    public void render(MapCanvas canvas, MapViewport viewport, ChunkRange visible,
                       ActiveWorld world) {
        final MapCanvas target = canvas;
        final MapViewport view = viewport;
        // Half a chunk of markers is plenty; the marker is a fixed on-screen size below that.
        final int half = Math.max(MIN_MARKER_PIXELS,
                (int) Math.round(view.getScale() * ChunkRange.CHUNK_SIZE)) / 2;

        engine.forEachCandidate(world.seed(), config, visible, MAX_MARKERS,
                new StructureCandidateVisitor() {
                    @Override
                    public boolean visit(int chunkX, int chunkZ) {
                        int centerX = (int) Math.round(view.blockToScreenX(
                                (chunkX + 0.5) * ChunkRange.CHUNK_SIZE));
                        int centerY = (int) Math.round(view.blockToScreenY(
                                (chunkZ + 0.5) * ChunkRange.CHUNK_SIZE));
                        drawMarker(target, centerX, centerY, half);
                        return true;
                    }
                });
    }

    private void drawMarker(MapCanvas canvas, int centerX, int centerY, int half) {
        int left = centerX - half;
        int top = centerY - half;
        int right = centerX + half;
        int bottom = centerY + half;

        canvas.fill(left - 1, top - 1, right + 1, top, BORDER_COLOR);
        canvas.fill(left - 1, bottom, right + 1, bottom + 1, BORDER_COLOR);
        canvas.fill(left - 1, top, left, bottom, BORDER_COLOR);
        canvas.fill(right, top, right + 1, bottom, BORDER_COLOR);
        canvas.fill(left, top, right, bottom, color);
    }
}
