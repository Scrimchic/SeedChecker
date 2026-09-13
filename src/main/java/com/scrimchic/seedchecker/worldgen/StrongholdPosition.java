package com.scrimchic.seedchecker.worldgen;

/**
 * One stronghold, where vanilla finally places it.
 *
 * <p>{@link #index()} is vanilla's own placement order - the order of its ring loop, which is also
 * the order of the list vanilla keeps - and is the stable identity of a stronghold. It is not a
 * distance ranking; sort a copy for that.
 */
public final class StrongholdPosition {

    private final int index;
    private final int ring;
    private final int chunkX;
    private final int chunkZ;
    private final int ringChunkX;
    private final int ringChunkZ;

    public StrongholdPosition(int index, int ring, int chunkX, int chunkZ, int ringChunkX,
                              int ringChunkZ) {
        this.index = index;
        this.ring = ring;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.ringChunkX = ringChunkX;
        this.ringChunkZ = ringChunkZ;
    }

    /** Zero based position in vanilla's placement order. */
    public int index() {
        return index;
    }

    /** Zero based ring, counted outwards. */
    public int ring() {
        return ring;
    }

    /** The chunk the stronghold starts in, after the biome adjustment. */
    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    /** The chunk the ring maths produced, before the biome search moved it. */
    public int ringChunkX() {
        return ringChunkX;
    }

    public int ringChunkZ() {
        return ringChunkZ;
    }

    /** @return whether the biome search moved the stronghold off its ring chunk. */
    public boolean isBiomeAdjusted() {
        return chunkX != ringChunkX || chunkZ != ringChunkZ;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StrongholdPosition)) {
            return false;
        }
        StrongholdPosition that = (StrongholdPosition) other;
        return index == that.index && ring == that.ring && chunkX == that.chunkX
                && chunkZ == that.chunkZ && ringChunkX == that.ringChunkX
                && ringChunkZ == that.ringChunkZ;
    }

    @Override
    public int hashCode() {
        int result = index;
        result = 31 * result + ring;
        result = 31 * result + chunkX;
        result = 31 * result + chunkZ;
        result = 31 * result + ringChunkX;
        return 31 * result + ringChunkZ;
    }

    @Override
    public String toString() {
        return "#" + index + " ring " + ring + " chunk " + chunkX + "," + chunkZ
                + (isBiomeAdjusted() ? " (ring chunk " + ringChunkX + "," + ringChunkZ + ")" : "");
    }
}
