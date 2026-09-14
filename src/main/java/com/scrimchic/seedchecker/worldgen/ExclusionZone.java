package com.scrimchic.seedchecker.worldgen;

/**
 * A structure set that may not place near another one.
 *
 * <p>Vanilla's {@code StructurePlacement.ExclusionZone}: a placement chunk is refused when the other
 * set has a structure chunk - its own grid placement, frequency and exclusion all applied - within
 * {@code chunkCount} chunks on both axes ({@code ChunkGeneratorStructureState
 * .hasStructureChunkInRange}). Neither the other structure's biome nor whether it really generates
 * enters that test, so it is pure placement and needs no worldgen.
 *
 * <p>The other set is held as its configuration, not looked up by name, so the dependency is
 * explicit and a cycle cannot be built: a configuration can only refer to one that already exists.
 */
public final class ExclusionZone {

    private final StructureType otherType;
    private final StructurePlacementConfig other;
    private final int chunkCount;

    /**
     * @param otherType  which structure the other set places, for tests and the debug readout
     * @param other      the other set's placement
     * @param chunkCount the reach in chunks, 1 to 16 as vanilla's codec allows
     */
    public ExclusionZone(StructureType otherType, StructurePlacementConfig other, int chunkCount) {
        if (otherType == null || other == null) {
            throw new IllegalArgumentException("an exclusion zone needs the other set");
        }
        if (chunkCount < 1 || chunkCount > 16) {
            throw new IllegalArgumentException("chunk count must be in [1, 16], was " + chunkCount);
        }
        this.otherType = otherType;
        this.other = other;
        this.chunkCount = chunkCount;
    }

    public StructureType otherType() {
        return otherType;
    }

    public StructurePlacementConfig other() {
        return other;
    }

    public int chunkCount() {
        return chunkCount;
    }

    @Override
    public String toString() {
        return "not within " + chunkCount + " chunks of " + otherType;
    }
}
