package com.scrimchic.seedchecker.gui.map.layer;

import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.core.map.MapViewport;
import com.scrimchic.seedchecker.client.structure.StructureValidationManager;
import com.scrimchic.seedchecker.gui.map.MapCanvas;
import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.worldgen.StructureCandidateVisitor;
import com.scrimchic.seedchecker.worldgen.StructurePlacementConfig;
import com.scrimchic.seedchecker.worldgen.StructurePlacementEngine;
import com.scrimchic.seedchecker.worldgen.StructureType;
import com.scrimchic.seedchecker.worldgen.StructureValidation;
import com.scrimchic.seedchecker.worldgen.StructureValidationKey;
import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;

/**
 * Marks the chunks one grid-placed structure could start in.
 *
 * <p>A candidate is hidden only when the biome check <em>proved</em> vanilla would reject it - a
 * desert pyramid in a jungle is gone from the map. A candidate the check could not decide is drawn
 * like any other, because this phase is allowed to show a structure that will not generate and is
 * not allowed to hide one that will. They are all still <em>candidates</em>, not structures:
 * vanilla also checks terrain height, jigsaw fit and exclusion zones, none of which happens yet.
 *
 * <p>Validation is asynchronous, so a marker appears once its answer arrives rather than blocking
 * the frame. A candidate still waiting for its answer is invisible unless
 * {@link #setShowRawCandidates} is on, which shows every grid candidate and tints the rejected
 * ones - the developer view of what the filter is doing.
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

    /** Debug tint for a candidate the biome check threw out. */
    private static final int REJECTED_COLOR = 0x60808080;

    /** Debug tint for a candidate whose answer has not arrived yet. */
    private static final int PENDING_COLOR = 0x40C0C0C0;

    /** Validation requests started per frame per layer, so a fast pan cannot flood the queue. */
    private static final int MAX_REQUESTS_PER_FRAME = 64;

    /**
     * Shared by every structure layer: show raw grid candidates, including the rejected ones.
     *
     * <p>Off by default, because the whole point of this phase is that the map stops showing
     * obvious false positives. Session state, like the layer switches.
     */
    private static boolean showRawCandidates;

    private final StructureType type;
    private final StructurePlacementConfig config;
    private final int color;

    /** Reused across frames; the render thread is the only thread that touches it. */
    private final StructurePlacementEngine engine = new StructurePlacementEngine();

    private boolean enabled = true;

    public static boolean showRawCandidates() {
        return showRawCandidates;
    }

    public static void setShowRawCandidates(boolean show) {
        showRawCandidates = show;
    }

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

        final StructureValidationManager validation = StructureValidationManager.get();
        final BiomeMapKey map = StructureValidationKey.mapKeyFor(world);
        validation.useMap(map);

        final int[] requests = {0};
        engine.forEachCandidate(world.seed(), config, visible, MAX_MARKERS,
                new StructureCandidateVisitor() {
                    @Override
                    public boolean visit(int chunkX, int chunkZ) {
                        StructureValidationKey key =
                                new StructureValidationKey(map, type, chunkX, chunkZ);
                        StructureValidation result = validation.resultIfReady(key);

                        if (result == null) {
                            // Not decided yet. Asked for once, drawn only in the developer view,
                            // and never re-requested while it is in flight - that is what stops
                            // the marker flickering.
                            if (requests[0] < MAX_REQUESTS_PER_FRAME && validation.request(key)) {
                                requests[0]++;
                            }
                            if (showRawCandidates) {
                                draw(target, view, chunkX, chunkZ, half, PENDING_COLOR);
                            }
                            return true;
                        }
                        if (!result.isRejected()) {
                            draw(target, view, chunkX, chunkZ, half, color);
                        } else if (showRawCandidates) {
                            draw(target, view, chunkX, chunkZ, half, REJECTED_COLOR);
                        }
                        return true;
                    }
                });
    }

    /**
     * One line about this structure at that chunk, for the debug readout under the cursor.
     *
     * @return the description, or {@code null} when this chunk is not a candidate at all
     */
    public String describeAt(ActiveWorld world, int chunkX, int chunkZ) {
        if (!world.hasSeed() || !isCandidate(world.seed(), chunkX, chunkZ)) {
            return null;
        }
        StructureValidation result = StructureValidationManager.get().resultIfReady(
                new StructureValidationKey(
                        StructureValidationKey.mapKeyFor(world), type, chunkX, chunkZ));
        if (result == null) {
            return type.displayName() + ": checking biome";
        }
        if (result.isRejected()) {
            return type.displayName() + ": rejected, biome "
                    + (result.sampledBiomeId() == null ? "incompatible" : result.sampledBiomeId());
        }
        if (!result.isCompatible()) {
            return type.displayName() + ": biome not checked - " + result.reason();
        }
        return type.displayName() + ": biome-compatible"
                + (result.variant() == null ? "" : " (" + result.variant() + ")");
    }

    /** Whether grid placement picked exactly this chunk for its region. */
    private boolean isCandidate(long seed, int chunkX, int chunkZ) {
        int spacing = config.spacing();
        long packed = engine.candidateChunk(seed, config,
                Math.floorDiv(chunkX, spacing), Math.floorDiv(chunkZ, spacing));
        return StructurePlacementEngine.chunkX(packed) == chunkX
                && StructurePlacementEngine.chunkZ(packed) == chunkZ;
    }

    private void draw(MapCanvas canvas, MapViewport viewport, int chunkX, int chunkZ, int half,
                      int fillColor) {
        int centerX = (int) Math.round(viewport.blockToScreenX(
                (chunkX + 0.5) * ChunkRange.CHUNK_SIZE));
        int centerY = (int) Math.round(viewport.blockToScreenY(
                (chunkZ + 0.5) * ChunkRange.CHUNK_SIZE));
        drawMarker(canvas, centerX, centerY, half, fillColor);
    }

    private void drawMarker(MapCanvas canvas, int centerX, int centerY, int half, int fillColor) {
        int left = centerX - half;
        int top = centerY - half;
        int right = centerX + half;
        int bottom = centerY + half;

        canvas.fill(left - 1, top - 1, right + 1, top, BORDER_COLOR);
        canvas.fill(left - 1, bottom, right + 1, bottom + 1, BORDER_COLOR);
        canvas.fill(left - 1, top, left, bottom, BORDER_COLOR);
        canvas.fill(right, top, right + 1, bottom, BORDER_COLOR);
        canvas.fill(left, top, right, bottom, fillColor);
    }
}
