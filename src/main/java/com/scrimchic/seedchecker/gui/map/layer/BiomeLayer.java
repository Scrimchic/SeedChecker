package com.scrimchic.seedchecker.gui.map.layer;

import com.scrimchic.seedchecker.client.biome.BiomeTileManager;
import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.core.map.MapViewport;
import com.scrimchic.seedchecker.gui.map.MapCanvas;
import com.scrimchic.seedchecker.platform.BiomeWorldgenSession;
import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;
import com.scrimchic.seedchecker.worldgen.biome.BiomeSampleLevel;
import com.scrimchic.seedchecker.worldgen.biome.BiomeTile;
import com.scrimchic.seedchecker.worldgen.biome.BiomeTileGrid;
import com.scrimchic.seedchecker.worldgen.biome.BiomeTileKey;

/**
 * Paints the biome map underneath everything else.
 *
 * <p>Draws only tiles {@link BiomeTileManager} has already finished. A tile that is still being
 * generated is skipped, so the grid shows through and fills in a frame or two later; nothing here
 * ever waits on a worker.
 *
 * <p>What is drawn is a horizontal biome slice at {@link #sampleY()}, not the biome a player would
 * see standing on the surface. From 1.18 onwards biomes are three dimensional, so those differ -
 * a mountain range can be one biome at y=64 and another at its peak. Surface-resolved biomes need
 * terrain heights, which is a separate job.
 */
public final class BiomeLayer implements MapLayer {

    /**
     * The height the biome slice is taken at.
     *
     * <p>64 is sea level and the most neutral single choice: it is below the peaks and above the
     * cave biomes, so it matches what a surface map looks like for most of the world. Deliberately
     * a named concept rather than a constant buried in the sampler, so surface-aware sampling can
     * replace it later.
     */
    public static final int DEFAULT_SAMPLE_Y = 64;

    /**
     * The nether's slice height. Every version's nether biome source ignores height - 1.16.5's
     * multi-noise source is built with {@code useY} false, and the modern nether noise router feeds
     * the climate a temperature and vegetation noise with a {@code y_scale} of 0 and constant zero
     * continents, erosion, depth and ridges - so any height draws the same map, which
     * {@code NetherStructureTest} checks sample by sample. 32 is the nether's sea level, the lava
     * ocean, named rather than borrowing the overworld's 64.
     */
    public static final int NETHER_SAMPLE_Y = 32;

    /**
     * The End's slice height, and semantic only: the End's biome does not depend on height on any
     * target. 1.16.5's {@code TheEndBiomeSource.getNoiseBiome} never reads its y argument, and the
     * modern one reads only the router's erosion, which the End's noise settings define as
     * {@code cache_2d} over {@code end_islands}. {@code EndStructureTest} checks it sample by sample.
     * 0 is the End's own sea level and the floor of its level, named rather than borrowed.
     */
    public static final int END_SAMPLE_Y = 0;

    /** Per-frame submission budget, so a large jump in zoom does not enqueue the whole screen. */
    private static final int MAX_REQUESTS_PER_FRAME = 32;

    /**
     * Backstop on rectangles per frame. The measured worst case inside
     * {@link BiomeWorldgenSession#coarsestBlockStep()} is about 20,900, so this never clips in
     * practice; it exists so a pathological area cannot run away.
     */
    private static final int MAX_FILLS = 24000;

    private boolean enabled = true;
    private int sampleY = DEFAULT_SAMPLE_Y;

    @Override
    public String id() {
        return "biomes";
    }

    @Override
    public String displayName() {
        return "Biomes";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** The block height the biome slice is sampled at. */
    public int sampleY() {
        return sampleY;
    }

    public void setSampleY(int sampleY) {
        this.sampleY = sampleY;
    }

    /** The height the slice is taken at in that dimension. */
    public int sampleYFor(String dimensionId) {
        if (BiomeWorldgenSession.NETHER.equals(dimensionId)) {
            return NETHER_SAMPLE_Y;
        }
        return BiomeWorldgenSession.END.equals(dimensionId) ? END_SAMPLE_Y : sampleY;
    }

    @Override
    public String unavailableReason(ActiveWorld world, MapViewport viewport, ChunkRange visible) {
        if (!world.hasSeed()) {
            return "needs a known seed";
        }
        String dimensionId = world.context().dimensionId();
        if (dimensionId == null) {
            return "not in a world";
        }
        if (!BiomeWorldgenSession.supportsDimension(dimensionId)) {
            return "vanilla dimensions only";
        }
        if (stepFor(viewport) > BiomeWorldgenSession.coarsestBlockStep()) {
            // Measured per version: how coarsely it can sample, and how many rectangles the map
            // then reduces to. 1.16.5 stops sooner on both counts.
            return "zoom in";
        }
        return null;
    }

    @Override
    public void render(MapCanvas canvas, MapViewport viewport, ChunkRange visible,
                       ActiveWorld world) {
        BiomeTileManager manager = BiomeTileManager.get();
        BiomeMapKey map = BiomeMapKey.of(world, sampleYFor(world.context().dimensionId()));
        manager.useMap(map);

        int step = stepFor(viewport);

        long minBlockX = (long) visible.minChunkX() * ChunkRange.CHUNK_SIZE;
        long maxBlockX = ((long) visible.maxChunkX() + 1L) * ChunkRange.CHUNK_SIZE - 1L;
        long minBlockZ = (long) visible.minChunkZ() * ChunkRange.CHUNK_SIZE;
        long maxBlockZ = ((long) visible.maxChunkZ() + 1L) * ChunkRange.CHUNK_SIZE - 1L;

        int minTileX = BiomeTileGrid.tileOfSaturating(minBlockX, step);
        int maxTileX = BiomeTileGrid.tileOfSaturating(maxBlockX, step);
        int minTileZ = BiomeTileGrid.tileOfSaturating(minBlockZ, step);
        int maxTileZ = BiomeTileGrid.tileOfSaturating(maxBlockZ, step);

        int fills = 0;
        int requests = 0;
        // One batch around every tile: on 1.16.5 this is the difference between thousands of GL
        // draw calls per frame and one.
        canvas.beginBatch();
        try {
            for (long tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
                for (long tileX = minTileX; tileX <= maxTileX; tileX++) {
                    BiomeTileKey key = new BiomeTileKey(map, step, (int) tileX, (int) tileZ);
                    BiomeTile tile = manager.tileIfReady(key);
                    if (tile == null) {
                        if (requests < MAX_REQUESTS_PER_FRAME && manager.request(key)) {
                            requests++;
                        }
                        continue;
                    }
                    fills += drawTile(canvas, viewport, tile, MAX_FILLS - fills);
                    if (fills >= MAX_FILLS) {
                        return;
                    }
                }
            }
        } finally {
            canvas.endBatch();
        }
    }

    private static int stepFor(MapViewport viewport) {
        return BiomeSampleLevel.forPixelsPerBlock(viewport.getScale()).blockStep();
    }

    /**
     * Draws one tile from the rectangles it was already reduced to when it was generated.
     *
     * <p>The render thread does no scanning: the greedy merge ran once on a worker, so this is a
     * straight walk over a packed int array.
     *
     * @return how many fills were issued
     */
    private int drawTile(MapCanvas canvas, MapViewport viewport, BiomeTile tile, int fillBudget) {
        int step = tile.key().blockStep();
        int originX = tile.key().originBlockX();
        int originZ = tile.key().originBlockZ();
        int screenWidth = viewport.getWidth();
        int screenHeight = viewport.getHeight();

        int fills = 0;
        int rectCount = tile.rectCount();
        for (int i = 0; i < rectCount && fills < fillBudget; i++) {
            int rect = tile.rect(i);
            int sampleX = BiomeTile.rectSampleX(rect);
            int sampleZ = BiomeTile.rectSampleZ(rect);

            int left = screenX(viewport, originX + sampleX * step);
            int top = screenY(viewport, originZ + sampleZ * step);
            int right = screenX(viewport, originX + (sampleX + BiomeTile.rectWidth(rect)) * step);
            int bottom = screenY(viewport, originZ + (sampleZ + BiomeTile.rectHeight(rect)) * step);

            if (right <= left || bottom <= top) {
                // Cannot happen while a sample covers four pixels or more, but a degenerate
                // viewport must not silently drop the map.
                right = Math.max(right, left + 1);
                bottom = Math.max(bottom, top + 1);
            }
            // Edge tiles hang off the screen; skipping those rectangles is free.
            if (right <= 0 || bottom <= 0 || left >= screenWidth || top >= screenHeight) {
                continue;
            }

            canvas.fill(left, top, right, bottom,
                    tile.paletteColor(BiomeTile.rectPaletteIndex(rect)));
            fills++;
        }
        return fills;
    }

    private static int screenX(MapViewport viewport, int blockX) {
        return (int) Math.round(viewport.blockToScreenX(blockX));
    }

    private static int screenY(MapViewport viewport, int blockZ) {
        return (int) Math.round(viewport.blockToScreenY(blockZ));
    }
}
