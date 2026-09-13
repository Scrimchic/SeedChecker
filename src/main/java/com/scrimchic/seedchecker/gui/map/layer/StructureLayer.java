package com.scrimchic.seedchecker.gui.map.layer;

import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.core.map.MapViewport;
import com.scrimchic.seedchecker.client.structure.StructureValidationManager;
import com.scrimchic.seedchecker.gui.map.MapCanvas;
import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.worldgen.GenerationPoint;
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
 * <p>A candidate is hidden only when the check <em>proved</em> vanilla would reject it - a desert
 * pyramid in a jungle is gone from the map. A candidate the check could not decide is drawn like any
 * other, because the map is allowed to show a structure that will not generate and is not allowed
 * to hide one that will. Where the check reproduced vanilla exactly, the marker is drawn at the
 * exact generation point rather than at the chunk centre; a non-exact one (the shipwreck) is still
 * a candidate. Exclusion zones between structure sets are not modelled by either.
 *
 * <p>Validation is asynchronous, so a marker appears once its answer arrives rather than blocking
 * the frame. A candidate still waiting for its answer is invisible unless
 * {@link #setShowRawCandidates} is on, which shows every grid candidate and tints the rejected
 * ones - the developer view of what the filter is doing.
 *
 * <p>One instance per structure type; {@link MapLayers#createDefault()} only creates the types the
 * running version actually has, so an unsupported structure never appears in the UI at all.
 */
public final class StructureLayer implements StructureMarkerLayer {

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
                                draw(target, view, null, chunkX, chunkZ, half, PENDING_COLOR);
                            }
                            return true;
                        }
                        if (!result.isRejected()) {
                            draw(target, view, result, chunkX, chunkZ, half, color);
                        } else if (showRawCandidates) {
                            draw(target, view, result, chunkX, chunkZ, half, REJECTED_COLOR);
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
    @Override
    public String describeAt(ActiveWorld world, int chunkX, int chunkZ) {
        if (!world.hasSeed() || !isCandidate(world.seed(), chunkX, chunkZ)) {
            return null;
        }
        StructureValidation result = resultAt(world, chunkX, chunkZ);
        if (result == null) {
            return StructureValidationManager.get().isWaitingForStructureData(type)
                    ? type.displayName() + ": loading vanilla structure data"
                    : type.displayName() + ": checking";
        }
        if (result.isRejected()) {
            // A rejection that names a reason was not about the biome at all - the desert pyramid's
            // sea-level condition is the one that does this - so the biome must not be blamed.
            if (result.reason() != null) {
                return type.displayName() + ": rejected, " + result.reason();
            }
            return type.displayName() + ": rejected, biome "
                    + (result.sampledBiomeId() == null ? "incompatible" : result.sampledBiomeId());
        }
        if (!result.isCompatible()) {
            return type.displayName() + ": biome not checked - " + result.reason();
        }
        if (result.isExact()) {
            // Exact: vanilla generates it, so this is worded as a structure, not a candidate.
            return type.displayName() + ": generates"
                    + (result.variant() == null ? "" : " (" + result.variant() + ")")
                    + (result.generationPoint() == null ? "" : " at " + result.generationPoint());
        }
        return type.displayName() + ": compatible, non-exact"
                + (result.variant() == null ? "" : " (" + result.variant() + ")");
    }

    /**
     * Where a marker for that candidate sits, in blocks: vanilla's exact generation point when the
     * result carries one, the candidate chunk's centre otherwise.
     */
    public static int markerBlockX(StructureValidation result, int chunkX) {
        GenerationPoint point = result == null ? null : result.generationPoint();
        return point != null ? point.x() : (chunkX << 4) + 8;
    }

    /** @see #markerBlockX */
    public static int markerBlockZ(StructureValidation result, int chunkZ) {
        GenerationPoint point = result == null ? null : result.generationPoint();
        return point != null ? point.z() : (chunkZ << 4) + 8;
    }

    @Override
    public StructureType type() {
        return type;
    }

    @Override
    public String describeSelection(ActiveWorld world, int chunkX, int chunkZ) {
        return null;
    }

    /**
     * Whether this layer is drawing a marker in that chunk right now.
     *
     * <p>What a click can select, so it has to answer the same question {@link #render} does: a
     * candidate is on screen when it was not rejected, or when the developer view is showing the
     * rejected and still-pending ones too.
     */
    @Override
    public boolean isMarkerAt(ActiveWorld world, int chunkX, int chunkZ) {
        if (!enabled || !world.hasSeed() || !isCandidate(world.seed(), chunkX, chunkZ)) {
            return false;
        }
        StructureValidation result = resultAt(world, chunkX, chunkZ);
        if (result == null) {
            return showRawCandidates;
        }
        return !result.isRejected() || showRawCandidates;
    }

    /** @return the decision for that candidate, or {@code null} while it is still being made. */
    @Override
    public StructureValidation resultAt(ActiveWorld world, int chunkX, int chunkZ) {
        return StructureValidationManager.get().resultIfReady(new StructureValidationKey(
                StructureValidationKey.mapKeyFor(world), type, chunkX, chunkZ));
    }

    /** Whether grid placement picked exactly this chunk for its region. */
    private boolean isCandidate(long seed, int chunkX, int chunkZ) {
        int spacing = config.spacing();
        long packed = engine.candidateChunk(seed, config,
                Math.floorDiv(chunkX, spacing), Math.floorDiv(chunkZ, spacing));
        return StructurePlacementEngine.chunkX(packed) == chunkX
                && StructurePlacementEngine.chunkZ(packed) == chunkZ;
    }

    private void draw(MapCanvas canvas, MapViewport viewport, StructureValidation result,
                      int chunkX, int chunkZ, int half, int fillColor) {
        int centerX = (int) Math.round(viewport.blockToScreenX(markerBlockX(result, chunkX) + 0.5));
        int centerY = (int) Math.round(viewport.blockToScreenY(markerBlockZ(result, chunkZ) + 0.5));
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
