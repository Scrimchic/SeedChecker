package com.scrimchic.seedchecker.gui.map.layer;

import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.core.map.MapViewport;
import com.scrimchic.seedchecker.gui.map.MapCanvas;
import com.scrimchic.seedchecker.world.WorldContext;
import com.scrimchic.seedchecker.worldgen.SlimeChunkCalculator;

/**
 * Shades every chunk where slimes may spawn below y=40.
 *
 * <p>Needs a known seed: there is no meaningful fallback, since a guessed seed would draw a
 * confidently wrong map. On a server with a hidden seed the layer reports itself unavailable and
 * draws nothing.
 */
public final class SlimeChunkLayer implements MapLayer {

    /** Translucent so the grid underneath stays readable. */
    private static final int COLOR_SLIME = 0x6636C15E;

    /** A chunk thinner than this is a speck; drawing it is noise rather than information. */
    private static final double MIN_CHUNK_PIXELS = 2.0;

    /**
     * Upper bound on the chunks scanned in one frame.
     *
     * <p>At the vanilla one-in-ten rate this is roughly 4,000 rectangles, and the calculator
     * measures around 10 ns per chunk, so a capped frame costs well under a millisecond. Beyond
     * the cap the layer switches itself off rather than degrade the whole screen.
     */
    private static final long MAX_VISIBLE_CHUNKS = 40_000L;

    private boolean enabled = true;

    @Override
    public String displayName() {
        return "Slime Chunks";
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
    public String unavailableReason(WorldContext context, MapViewport viewport, ChunkRange visible) {
        if (!context.hasSeed()) {
            return "needs a known seed";
        }
        if (viewport.getScale() * ChunkRange.CHUNK_SIZE < MIN_CHUNK_PIXELS
                || visible.chunkCount() > MAX_VISIBLE_CHUNKS) {
            return "zoom in";
        }
        return null;
    }

    @Override
    public void render(MapCanvas canvas, MapViewport viewport, ChunkRange visible,
                       WorldContext context) {
        long seed = context.seed();

        // Iterated as longs so a range that saturated at Integer.MAX_VALUE cannot wrap around.
        for (long z = visible.minChunkZ(); z <= visible.maxChunkZ(); z++) {
            int chunkZ = (int) z;
            int top = screenY(viewport, chunkZ);
            int bottom = screenY(viewport, chunkZ + 1.0);

            for (long x = visible.minChunkX(); x <= visible.maxChunkX(); x++) {
                int chunkX = (int) x;
                if (!SlimeChunkCalculator.isSlimeChunk(seed, chunkX, chunkZ)) {
                    continue;
                }
                // Edges are derived from the chunk grid, not from the rectangle before them, so
                // neighbouring chunks share an edge exactly and tile without seams.
                canvas.fill(screenX(viewport, chunkX), top, screenX(viewport, chunkX + 1.0), bottom,
                        COLOR_SLIME);
            }
        }
    }

    private static int screenX(MapViewport viewport, double chunkX) {
        return (int) Math.round(viewport.blockToScreenX(chunkX * ChunkRange.CHUNK_SIZE));
    }

    private static int screenY(MapViewport viewport, double chunkZ) {
        return (int) Math.round(viewport.blockToScreenY(chunkZ * ChunkRange.CHUNK_SIZE));
    }
}
