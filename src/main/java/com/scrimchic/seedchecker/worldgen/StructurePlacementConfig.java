package com.scrimchic.seedchecker.worldgen;

/**
 * The three numbers plus spread mode that fully determine where a grid-placed structure can start.
 *
 * <p>Vanilla stores the same values as {@code StructureFeatureConfiguration} on 1.16.5 and as a
 * {@code RandomSpreadStructurePlacement} inside a datapack {@code structure_set} from 1.18 onwards.
 * This is Seed Checker's version-independent form of them.
 */
public final class StructurePlacementConfig {

    private final int spacing;
    private final int separation;
    private final int salt;
    private final SpreadType spreadType;

    /**
     * @param spacing    region size in chunks; one structure may start per region
     * @param separation minimum gap in chunks between two neighbouring regions' structures
     * @param salt       vanilla's per-structure seed salt
     * @param spreadType how the offset inside the region is drawn
     */
    public StructurePlacementConfig(int spacing, int separation, int salt, SpreadType spreadType) {
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
        this.spacing = spacing;
        this.separation = separation;
        this.salt = salt;
        this.spreadType = spreadType;
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
                + ", salt=" + salt + ", spread=" + spreadType;
    }
}
