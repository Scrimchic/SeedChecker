package com.scrimchic.seedchecker.gui.map;

/**
 * Which drawn object a click on the map picks.
 *
 * <p>Measured on screen, in pixels, so what can be clicked is what is drawn at any zoom. Every
 * candidate has a radius of its own - the size of its marker plus a little slack - and only a click
 * inside it counts. Of those, the nearest centre wins; at exactly equal distance a custom marker
 * wins over a structure, the player's own mark being the more deliberate thing; and after that the
 * tie key decides, so the same click always picks the same object.
 */
public final class MapHitTest {

    /** Custom markers first on a tie. */
    public static final int PRIORITY_CUSTOM_MARKER = 0;

    public static final int PRIORITY_STRUCTURE = 1;

    /** The least a marker's click radius may be, whatever its drawn size. */
    public static final double MIN_RADIUS_PIXELS = 8.0;

    /** One object that could be picked. */
    public static final class Candidate<T> {

        private final T target;
        private final double screenX;
        private final double screenY;
        private final double radius;
        private final int priority;
        private final String tieKey;

        public Candidate(T target, double screenX, double screenY, double radius, int priority,
                         String tieKey) {
            this.target = target;
            this.screenX = screenX;
            this.screenY = screenY;
            this.radius = Math.max(MIN_RADIUS_PIXELS, radius);
            this.priority = priority;
            this.tieKey = tieKey == null ? "" : tieKey;
        }

        public T target() {
            return target;
        }

        public double screenX() {
            return screenX;
        }

        public double screenY() {
            return screenY;
        }

        public double radius() {
            return radius;
        }

        double distanceSquared(double x, double y) {
            double dx = screenX - x;
            double dy = screenY - y;
            return dx * dx + dy * dy;
        }
    }

    private MapHitTest() {
    }

    /** @return the candidate the click picks, or {@code null} when it hits none */
    public static <T> Candidate<T> pick(Iterable<Candidate<T>> candidates, double mouseX, double mouseY) {
        Candidate<T> best = null;
        double bestDistance = 0.0;
        for (Candidate<T> candidate : candidates) {
            double distance = candidate.distanceSquared(mouseX, mouseY);
            if (distance > candidate.radius * candidate.radius) {
                continue;
            }
            if (best == null || isBetter(candidate, distance, best, bestDistance)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean isBetter(Candidate<?> candidate, double distance, Candidate<?> best, double bestDistance) {
        if (distance != bestDistance) {
            return distance < bestDistance;
        }
        if (candidate.priority != best.priority) {
            return candidate.priority < best.priority;
        }
        return candidate.tieKey.compareTo(best.tieKey) < 0;
    }
}
