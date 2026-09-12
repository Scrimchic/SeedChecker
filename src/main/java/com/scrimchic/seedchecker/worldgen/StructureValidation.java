package com.scrimchic.seedchecker.worldgen;

/** The outcome of checking one structure candidate against vanilla's biome rules. */
public final class StructureValidation {

    private final StructureBiomeStatus status;
    private final String variant;
    private final String sampledBiomeId;
    private final String reason;

    private StructureValidation(StructureBiomeStatus status, String variant, String sampledBiomeId,
                                String reason) {
        this.status = status;
        this.variant = variant;
        this.sampledBiomeId = sampledBiomeId;
        this.reason = reason;
    }

    /**
     * @param variant        the structure entry that matched, e.g. {@code village_plains}, or
     *                       {@code null} when several matched and vanilla's choice between them is
     *                       not determined here
     * @param sampledBiomeId the biome that matched
     */
    public static StructureValidation compatible(String variant, String sampledBiomeId) {
        return new StructureValidation(StructureBiomeStatus.COMPATIBLE, variant, sampledBiomeId,
                null);
    }

    /**
     * Only for a candidate whose whole set of possible sample positions was enumerated and none of
     * them accepted.
     *
     * @param sampledBiomeId a biome that was seen and rejected, for the debug readout
     */
    public static StructureValidation incompatible(String sampledBiomeId) {
        return new StructureValidation(StructureBiomeStatus.INCOMPATIBLE, null, sampledBiomeId,
                null);
    }

    /**
     * The check could not be made at all.
     *
     * @param reason why, in a few words, for the debug readout
     */
    public static StructureValidation unknown(String reason) {
        return new StructureValidation(StructureBiomeStatus.UNKNOWN, null, null, reason);
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

    /** @return the matching structure entry, or {@code null} when it is not unambiguous. */
    public String variant() {
        return variant;
    }

    /** @return a biome id from the sampled positions, for the debug readout; may be {@code null}. */
    public String sampledBiomeId() {
        return sampledBiomeId;
    }

    /** @return why the answer is {@code UNKNOWN}, or {@code null} for a decided candidate. */
    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return status + (variant == null ? "" : " (" + variant + ")")
                + (sampledBiomeId == null ? "" : " at " + sampledBiomeId)
                + (reason == null ? "" : ": " + reason);
    }
}
