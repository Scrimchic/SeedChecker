package com.scrimchic.seedchecker.gui.map.layer;

import java.util.List;

import com.scrimchic.seedchecker.client.structure.StrongholdManager;
import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.core.map.MapViewport;
import com.scrimchic.seedchecker.gui.map.MapCanvas;
import com.scrimchic.seedchecker.platform.BiomeWorldgenSession;
import com.scrimchic.seedchecker.platform.StrongholdLocator;
import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.worldgen.GenerationPoint;
import com.scrimchic.seedchecker.worldgen.StrongholdPosition;
import com.scrimchic.seedchecker.worldgen.StructureType;
import com.scrimchic.seedchecker.worldgen.StructureValidation;
import com.scrimchic.seedchecker.worldgen.StructureValidationKey;
import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;

/**
 * Marks every stronghold of the world, where vanilla really places it.
 *
 * <p>No candidate state: the list is drawn only once it has been computed, and every entry of it is
 * vanilla's final position - ring placement, biome search and all - so a drawn marker is always a
 * stronghold that generates. Until then nothing is drawn and the debug panel says it is locating.
 */
public final class StrongholdLayer implements StructureMarkerLayer {

    private static final int COLOR = 0xFFB07CE8;
    private static final int BORDER_COLOR = 0xFF0B0E11;
    private static final int MIN_MARKER_PIXELS = 7;

    private boolean enabled = true;

    @Override
    public String displayName() {
        return StructureType.STRONGHOLD.displayName();
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
    public StructureType type() {
        return StructureType.STRONGHOLD;
    }

    @Override
    public String unavailableReason(ActiveWorld world, MapViewport viewport, ChunkRange visible) {
        if (!world.hasSeed()) {
            return "needs a known seed";
        }
        if (!BiomeWorldgenSession.supportsDimension(world.context().dimensionId())) {
            return "overworld only";
        }
        String failure = StrongholdManager.get().failure();
        return failure == null ? null : "unavailable";
    }

    @Override
    public void render(MapCanvas canvas, MapViewport viewport, ChunkRange visible,
                       ActiveWorld world) {
        StrongholdManager manager = StrongholdManager.get();
        BiomeMapKey map = StructureValidationKey.mapKeyFor(world);
        manager.useMap(map);
        manager.request(map);
        List<StrongholdPosition> positions = manager.positionsIfReady();
        if (positions == null) {
            return;
        }
        int half = Math.max(MIN_MARKER_PIXELS,
                (int) Math.round(viewport.getScale() * ChunkRange.CHUNK_SIZE)) / 2;
        for (int i = 0; i < positions.size(); i++) {
            StrongholdPosition position = positions.get(i);
            if (position.chunkX() < visible.minChunkX() || position.chunkX() > visible.maxChunkX()
                    || position.chunkZ() < visible.minChunkZ()
                    || position.chunkZ() > visible.maxChunkZ()) {
                continue;
            }
            StructureValidation result = validationOf(position);
            int centerX = (int) Math.round(viewport.blockToScreenX(
                    StructureLayer.markerBlockX(result, position.chunkX()) + 0.5));
            int centerY = (int) Math.round(viewport.blockToScreenY(
                    StructureLayer.markerBlockZ(result, position.chunkZ()) + 0.5));
            canvas.fill(centerX - half - 1, centerY - half - 1, centerX + half + 1,
                    centerY + half + 1, BORDER_COLOR);
            canvas.fill(centerX - half, centerY - half, centerX + half, centerY + half, COLOR);
        }
    }

    @Override
    public boolean isMarkerAt(ActiveWorld world, int chunkX, int chunkZ) {
        return enabled && positionAt(world, chunkX, chunkZ) != null;
    }

    @Override
    public StructureValidation resultAt(ActiveWorld world, int chunkX, int chunkZ) {
        StrongholdPosition position = positionAt(world, chunkX, chunkZ);
        return position == null ? null : validationOf(position);
    }

    @Override
    public String describeAt(ActiveWorld world, int chunkX, int chunkZ) {
        StrongholdPosition position = positionAt(world, chunkX, chunkZ);
        return position == null
                ? null : "Stronghold #" + (position.index() + 1) + ", ring " + (position.ring() + 1);
    }

    @Override
    public String describeSelection(ActiveWorld world, int chunkX, int chunkZ) {
        StrongholdPosition position = positionAt(world, chunkX, chunkZ);
        if (position == null) {
            return null;
        }
        List<StrongholdPosition> all = StrongholdManager.get().positionsIfReady();
        return "#" + (position.index() + 1) + (all == null ? "" : " of " + all.size())
                + " in vanilla order, ring " + (position.ring() + 1)
                + (position.isBiomeAdjusted() ? ", moved by the biome search" : "");
    }

    private static StrongholdPosition positionAt(ActiveWorld world, int chunkX, int chunkZ) {
        if (!world.hasSeed()) {
            return null;
        }
        return StrongholdManager.get().positionAt(StructureValidationKey.mapKeyFor(world),
                chunkX, chunkZ);
    }

    /**
     * A stronghold in the list is vanilla's exact answer, so it reads as an exact, generating
     * structure - with a generation point where the version computes one.
     */
    private static StructureValidation validationOf(StrongholdPosition position) {
        GenerationPoint point = StrongholdLocator.generationPoint(position);
        return point != null
                ? StructureValidation.exactlyCompatible("stronghold", null, point)
                : StructureValidation.exactlyCompatible("stronghold", null);
    }
}
