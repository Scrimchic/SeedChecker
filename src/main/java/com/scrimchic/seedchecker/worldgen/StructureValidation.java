package com.scrimchic.seedchecker.worldgen;

/** The outcome of checking one structure candidate against vanilla's rules for generating it. */
public final class StructureValidation {

    private final StructureBiomeStatus status;
    private final String variant;
    private final String sampledBiomeId;
    private final String reason;
    private final boolean exact;
    private final GenerationPoint generationPoint;

    private StructureValidation(StructureBiomeStatus status, String variant, String sampledBiomeId,
                                String reason, boolean exact, GenerationPoint generationPoint) {
        this.status = status;
        this.variant = variant;
        this.sampledBiomeId = sampledBiomeId;
        this.reason = reason;
        this.exact = exact;
        this.generationPoint = generationPoint;
    }

    /**
     * A candidate some position vanilla could sample would accept - a superset answer.
     *
     * @param variant        the structure entry that matched, e.g. {@code village_plains}, or
     *                       {@code null} when several matched and vanilla's choice between them is
     *                       not determined here
     * @param sampledBiomeId the biome that matched
     */
    public static StructureValidation compatible(String variant, String sampledBiomeId) {
        return new StructureValidation(StructureBiomeStatus.COMPATIBLE, variant, sampledBiomeId,
                null, false, null);
    }

    /**
     * A candidate whose generation was reproduced exactly, where the decision is exact but the
     * position vanilla builds from is not computed - 1.16.5, whose check is position independent.
     *
     * <p>Carried on the result rather than asked of the structure type, so the map can say which
     * kind of answer it is showing without loading the version's structure data on the render
     * thread.
     */
    public static StructureValidation exactlyCompatible(String variant, String sampledBiomeId) {
        return new StructureValidation(StructureBiomeStatus.COMPATIBLE, variant, sampledBiomeId,
                null, true, null);
    }

    /**
     * A candidate vanilla's own generation point was reproduced for, and accepted: the structure
     * generates, from exactly this point.
     *
     * @param variant         the structure entry vanilla would build, which is exact here
     * @param generationPoint the stub position, never {@code null}
     */
    public static StructureValidation exactlyCompatible(String variant, String sampledBiomeId,
                                                        GenerationPoint generationPoint) {
        if (generationPoint == null) {
            throw new IllegalArgumentException("an exact position requires the position");
        }
        return new StructureValidation(StructureBiomeStatus.COMPATIBLE, variant, sampledBiomeId,
                null, true, generationPoint);
    }

    /**
     * Only for a candidate whose whole set of possible sample positions was enumerated and none of
     * them accepted, or whose exact generation point was reproduced and refused.
     *
     * @param sampledBiomeId a biome that was seen and rejected, for the debug readout
     */
    public static StructureValidation incompatible(String sampledBiomeId) {
        return new StructureValidation(StructureBiomeStatus.INCOMPATIBLE, null, sampledBiomeId,
                null, false, null);
    }

    /**
     * A rejection that was not about the biome.
     *
     * <p>An exactly reproduced structure can fail a condition of its own - a desert pyramid whose
     * lowest corner sits below sea level never generates, whatever grows there - and saying so
     * beats reporting a biome that was in fact acceptable.
     *
     * @param sampledBiomeId the biome at the exact position, which may well have been accepted, or
     *                       {@code null} when no position was reached at all
     * @param reason         what actually rejected it, in a few words
     */
    public static StructureValidation incompatible(String sampledBiomeId, String reason) {
        return new StructureValidation(StructureBiomeStatus.INCOMPATIBLE, null, sampledBiomeId,
                reason, false, null);
    }

    /**
     * The check could not be made at all.
     *
     * @param reason why, in a few words, for the debug readout
     */
    public static StructureValidation unknown(String reason) {
        return new StructureValidation(StructureBiomeStatus.UNKNOWN, null, null, reason, false,
                null);
    }

    public StructureBiomeStatus status() {
        return status;
    }

    /** @return whether vanilla is known to reject this candidate. The only reason to hide one. */
    public boolean isRejected() {
        return status == StructureBiomeStatus.INCOMPATIBLE;
    }

    /** @return whether some position vanilla could sample carries an accepted biome. */
    public boolean isCompatible() {
        return status == StructureBiomeStatus.COMPATIBLE;
    }

    /**
     * Qualifies an acceptance, and only an acceptance.
     *
     * <p>A rejection needs no such qualifier: every model only ever rejects on proof, so
     * {@link #isRejected} always means vanilla rejects. An acceptance is the half that differs -
     * exact means the structure really does generate, while a non-exact acceptance means only that
     * some height vanilla might have picked would have worked.
     *
     * @return whether this answer reproduces vanilla's own decision rather than bounding it
     */
    public boolean isExact() {
        return exact;
    }

    /**
     * @return the exact position vanilla generates this structure from, or {@code null} when that
     *         position was not computed - every non-exact result, every rejection, and 1.16.5
     */
    public GenerationPoint generationPoint() {
        return generationPoint;
    }

    /** @return the matching structure entry, or {@code null} when it is not unambiguous. */
    public String variant() {
        return variant;
    }

    /** @return a biome id from the sampled positions, for the debug readout; may be {@code null}. */
    public String sampledBiomeId() {
        return sampledBiomeId;
    }

    /**
     * @return why the answer is what it is, in a few words, or {@code null} when the biome alone
     *         explains it. Set for every {@code UNKNOWN}, and for a rejection that was decided by
     *         something other than the biome.
     */
    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return status + (exact ? " exact" : "") + (variant == null ? "" : " (" + variant + ")")
                + (sampledBiomeId == null ? "" : " at " + sampledBiomeId)
                + (generationPoint == null ? "" : " from " + generationPoint)
                + (reason == null ? "" : ": " + reason);
    }
}
