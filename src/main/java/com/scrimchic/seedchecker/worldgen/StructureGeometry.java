package com.scrimchic.seedchecker.worldgen;

/**
 * The geometry of one exactly validated structure, or why there is none.
 *
 * <p>Computed on request rather than with validation: assembling a structure's pieces costs from a
 * few hundred microseconds to several seconds for a village, against a fraction of that to decide
 * whether it exists at all.
 */
public final class StructureGeometry {

    private final GenerationPoint generationPoint;
    private final StructureBounds bounds;
    private final String unavailableReason;

    private StructureGeometry(GenerationPoint generationPoint, StructureBounds bounds,
                              String unavailableReason) {
        this.generationPoint = generationPoint;
        this.bounds = bounds;
        this.unavailableReason = unavailableReason;
    }

    /**
     * @param generationPoint the stub position the pieces were assembled from, or {@code null} on
     *                        a version where it is not computed
     * @param bounds          the extent of every piece, never {@code null}
     */
    public static StructureGeometry of(GenerationPoint generationPoint, StructureBounds bounds) {
        if (bounds == null) {
            throw new IllegalArgumentException("available geometry requires bounds");
        }
        return new StructureGeometry(generationPoint, bounds, null);
    }

    /** No geometry, for a reason that will not change - so the answer is cached like any other. */
    public static StructureGeometry unavailable(String reason) {
        return new StructureGeometry(null, null, reason == null ? "unavailable" : reason);
    }

    public boolean isAvailable() {
        return bounds != null;
    }

    /** @return the bounds, or {@code null} when {@link #isAvailable()} is false. */
    public StructureBounds bounds() {
        return bounds;
    }

    /** @return the generation point the pieces came from, or {@code null}. */
    public GenerationPoint generationPoint() {
        return generationPoint;
    }

    /** @return why there is no geometry, or {@code null} when there is. */
    public String unavailableReason() {
        return unavailableReason;
    }

    @Override
    public String toString() {
        return bounds != null ? "bounds " + bounds + (generationPoint == null ? ""
                : " from " + generationPoint) : "unavailable: " + unavailableReason;
    }
}
