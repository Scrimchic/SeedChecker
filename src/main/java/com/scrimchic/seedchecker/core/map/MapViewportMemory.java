package com.scrimchic.seedchecker.core.map;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers where the map was left, per world and dimension, for as long as the client runs.
 *
 * <p>The rule it implements, kept deliberately simple and predictable:
 *
 * <ul>
 *   <li>the first time the map is opened in a world, there is nothing to restore, so the caller
 *       centres on the player;</li>
 *   <li>every later open - and every window resize, which re-runs screen setup - restores the
 *       position and zoom the map was last left at;</li>
 *   <li>switching save, server or dimension changes the key, so the other one's corner of the map
 *       is never shown; and each key keeps its own position, so going through a portal and back
 *       returns to where the map was left on this side. Overworld and nether coordinates are
 *       different spaces, so the two are never mixed.</li>
 * </ul>
 *
 * <p>Session only: nothing is written to disk. A handful of keys are kept, least recently used
 * dropped first.
 */
public final class MapViewportMemory {

    /** A few worlds' worth of dimensions. */
    private static final int MAX_KEYS = 16;

    private final Map<String, Remembered> byKey =
            new LinkedHashMap<String, Remembered>(MAX_KEYS, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Remembered> eldest) {
                    return size() > MAX_KEYS;
                }
            };

    /** The entry last restored or remembered, whose follow mode {@link #followPlayer} reports. */
    private Remembered last;

    /**
     * Applies the remembered position, if there is one for this key.
     *
     * @return whether anything was restored; {@code false} means the caller should choose a
     *         starting position itself
     */
    public boolean restore(String key, MapViewport viewport) {
        Remembered remembered = key == null ? null : byKey.get(key);
        if (remembered == null) {
            return false;
        }
        viewport.setCenter(remembered.centerBlockX, remembered.centerBlockZ);
        viewport.setScale(remembered.scale);
        last = remembered;
        return true;
    }

    /** Records the current position as the one to come back to under this key. */
    public void remember(String key, MapViewport viewport, boolean followPlayer) {
        if (key == null) {
            return;
        }
        Remembered remembered = new Remembered(viewport.getCenterBlockX(),
                viewport.getCenterBlockZ(), viewport.getScale(), followPlayer);
        byKey.put(key, remembered);
        last = remembered;
    }

    /** Whether follow mode was on when the map was last left, for the last key used. */
    public boolean followPlayer() {
        return last != null && last.followPlayer;
    }

    /** @return whether a position is remembered for this key. */
    public boolean remembers(String key) {
        return key != null && byKey.containsKey(key);
    }

    public void forget() {
        byKey.clear();
        last = null;
    }

    private static final class Remembered {
        private final double centerBlockX;
        private final double centerBlockZ;
        private final double scale;
        private final boolean followPlayer;

        Remembered(double centerBlockX, double centerBlockZ, double scale, boolean followPlayer) {
            this.centerBlockX = centerBlockX;
            this.centerBlockZ = centerBlockZ;
            this.scale = scale;
            this.followPlayer = followPlayer;
        }
    }
}
