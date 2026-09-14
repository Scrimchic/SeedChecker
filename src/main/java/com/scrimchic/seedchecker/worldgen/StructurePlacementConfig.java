package com.scrimchic.seedchecker.worldgen;

/**
 * Everything that determines which chunks a grid-placed structure set may start in.
 *
 * <p>Vanilla stores the same values as {@code StructureFeatureConfiguration} plus
 * {@code isFeatureChunk} overrides on 1.16.5, and as a {@code RandomSpreadStructurePlacement} inside
 * a datapack {@code structure_set} from 1.18 onwards. This is Seed Checker's version-independent form
 * of them: the grid (spacing, separation, salt, spread) and the two restrictions vanilla applies to
 * the chunk the grid picked - a frequency reduction and an exclusion zone.
 */
public final class StructurePlacementConfig {

    private final int spacing;
    private final int separation;
    private final int salt;
    private final SpreadType spreadType;
    private final float frequency;
    private final FrequencyReduction frequencyReduction;
    private final ExclusionZone exclusionZone;

    /**
     * A set with no restrictions beyond its grid, which is every set but three.
     *
     * @param spacing    region size in chunks; one structure may start per region
     * @param separation minimum gap in chunks between two neighbouring regions' structures
     * @param salt       vanilla's per-structure seed salt
     * @param spreadType how the offset inside the region is drawn
     */
    public StructurePlacementConfig(int spacing, int separation, int salt, SpreadType spreadType) {
        this(spacing, separation, salt, spreadType, 1.0F, FrequencyReduction.DEFAULT, null);
    }

    /**
     * @param frequency          the share of placement chunks kept, in [0, 1]; 1 keeps every one and
     *                           never consults the reduction, as vanilla does
     * @param frequencyReduction how that share is drawn
     * @param exclusionZone      another set this one may not place near, or {@code null}
     */
    public StructurePlacementConfig(int spacing, int separation, int salt, SpreadType spreadType,
                                    float frequency, FrequencyReduction frequencyReduction,
                                    ExclusionZone exclusionZone) {
        if (spacing <= 0) {
            throw new IllegalArgumentException("spacing must be positive, was " + spacing);
        }
        if (separation < 0 || separation >= spacing) {
            throw new IllegalArgumentException(
                    "separation must be in [0, spacing), was " + separation + " for spacing " + spacing);
        }
        if (spreadType == null) {
            throw new IllegalArgumentException("spreadType is required");
        }
        if (!(frequency >= 0.0F && frequency <= 1.0F)) {
            throw new IllegalArgumentException("frequency must be in [0, 1], was " + frequency);
        }
        if (frequencyReduction == null) {
            throw new IllegalArgumentException("frequencyReduction is required");
        }
        this.spacing = spacing;
        this.separation = separation;
        this.salt = salt;
        this.spreadType = spreadType;
        this.frequency = frequency;
        this.frequencyReduction = frequencyReduction;
        this.exclusionZone = exclusionZone;
    }

    /** Region size in chunks along one axis. */
    public int spacing() {
        return spacing;
    }

    public int separation() {
        return separation;
    }

    public int salt() {
        return salt;
    }

    public SpreadType spreadType() {
        return spreadType;
    }

    public float frequency() {
        return frequency;
    }

    public FrequencyReduction frequencyReduction() {
        return frequencyReduction;
    }

    /** @return the exclusion zone, or {@code null} when the set has none. */
    public ExclusionZone exclusionZone() {
        return exclusionZone;
    }

    /** Whether anything beyond the grid decides a placement chunk. */
    public boolean hasRestrictions() {
        return frequency < 1.0F || exclusionZone != null;
    }

    /**
     * How far into its region a structure may sit. Always at least 1, because {@code separation}
     * is required to be smaller than {@code spacing}.
     */
    public int offsetRange() {
        return spacing - separation;
    }

    @Override
    public String toString() {
        return "spacing=" + spacing + ", separation=" + separation
                + ", salt=" + salt + ", spread=" + spreadType
                + (frequency < 1.0F ? ", frequency=" + frequency + " " + frequencyReduction : "")
                + (exclusionZone == null ? "" : ", " + exclusionZone);
    }
}
