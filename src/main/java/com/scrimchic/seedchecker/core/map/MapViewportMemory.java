package com.scrimchic.seedchecker.core.map;

/**
 * Remembers where the map was left, per world, for as long as the client runs.
 *
 * <p>The rule it implements, kept deliberately simple and predictable:
 *
 * <ul>
 *   <li>the first time the map is opened in a world, there is nothing to restore, so the caller
 *       centres on the player;</li>
 *   <li>every later open - and every window resize, which re-runs screen setup - restores the
 *       position and zoom the map was last left at;</li>
 *   <li>switching save, server, seed or dimension changes the world key, so the map centres on
 *       the player again rather than showing the previous world's corner of the map.</li>
 * </ul>
 *
 * <p>Session only: nothing is written to disk.
 */
public final class MapViewportMemory {

    private String worldKey;
    private double centerBlockX;
    private double centerBlockZ;
    private double scale;
    private boolean followPlayer;

    /**
     * Applies the remembered position, if there is one for this world.
     *
     * @return whether anything was restored; {@code false} means the caller should choose a
     *         starting position itself
     */
    public boolean restore(String worldKey, MapViewport viewport) {
        if (worldKey == null || !worldKey.equals(this.worldKey)) {
            return false;
        }
        viewport.setCenter(centerBlockX, centerBlockZ);
        viewport.setScale(scale);
        return true;
    }

    /** Records the current position as the one to come back to. */
    public void remember(String worldKey, MapViewport viewport, boolean followPlayer) {
        this.worldKey = worldKey;
        this.centerBlockX = viewport.getCenterBlockX();
        this.centerBlockZ = viewport.getCenterBlockZ();
        this.scale = viewport.getScale();
        this.followPlayer = followPlayer;
    }

    /** Whether follow mode was on when the map was last left. */
    public boolean followPlayer() {
        return followPlayer;
    }

    /** @return whether a position is remembered for this world. */
    public boolean remembers(String worldKey) {
        return worldKey != null && worldKey.equals(this.worldKey);
    }

    public void forget() {
        worldKey = null;
        followPlayer = false;
    }
}
