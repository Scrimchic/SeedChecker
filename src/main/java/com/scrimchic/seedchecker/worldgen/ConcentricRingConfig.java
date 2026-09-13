package com.scrimchic.seedchecker.worldgen;

/**
 * The three numbers vanilla's concentric ring placement is driven by.
 *
 * <p>Not a hardcoded table: the platform layer reads them from the running version -
 * {@code StructureSettings.DEFAULT_STRONGHOLD} on 1.16.5, the {@code strongholds} structure set on
 * 1.18 and later - and every value is checked against vanilla in a test.
 */
public final class ConcentricRingConfig {

    private final int distance;
    private final int spread;
    private final int count;

    /**
     * @param distance the ring spacing unit, in chunks
     * @param spread   how many structures the first ring holds
     * @param count    how many structures there are in total
     */
    public ConcentricRingConfig(int distance, int spread, int count) {
        if (distance < 0 || spread <= 0 || count < 0) {
            throw new IllegalArgumentException("invalid ring placement: distance " + distance
                    + ", spread " + spread + ", count " + count);
        }
        this.distance = distance;
        this.spread = spread;
        this.count = count;
    }

    public int distance() {
        return distance;
    }

    public int spread() {
        return spread;
    }

    public int count() {
        return count;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConcentricRingConfig)) {
            return false;
        }
        ConcentricRingConfig that = (ConcentricRingConfig) other;
        return distance == that.distance && spread == that.spread && count == that.count;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * distance + spread) + count;
    }

    @Override
    public String toString() {
        return "distance " + distance + ", spread " + spread + ", count " + count;
    }
}
