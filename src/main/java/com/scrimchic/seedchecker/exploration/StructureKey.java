package com.scrimchic.seedchecker.exploration;

import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.worldgen.StructureType;

/**
 * Which structure instance an annotation is about, inside one world profile.
 *
 * <h2>What identifies a structure</h2>
 *
 * <p>The dimension, the structure type's stable id, and the chunk its start is placed in - the chunk
 * grid placement picked, or the stronghold list gave. That chunk is what vanilla itself files a
 * structure start under on every supported version, and it is the one thing every production model
 * of a structure has: the generation point is absent on 1.16.5 and derived from terrain elsewhere,
 * bounds may be unavailable, and the drawn anchor moves when validation refines it. None of those
 * enter the key, so none of them can split or merge an annotation. Chunk coordinates are exact
 * integers, negative ones included, and the world profile is not part of the key because each
 * profile keeps its own exploration file.
 *
 * <h2>The seed</h2>
 *
 * <p>A structure predicted from a seed exists only under that seed: the same chunk under another
 * seed holds a different structure, or none. So a predicted structure's key carries the seed it was
 * predicted from - not its source, since a runtime seed and the same number typed in are the same
 * world. Changing the seed therefore never re-attaches an annotation to an unrelated structure, and
 * changing it back finds the annotation again.
 *
 * <p>A key may also have no seed at all, for a structure known without a prediction. Nothing
 * creates one in Phase 4; the file format already allows it, so a later unknown-seed mode can store
 * observations without a migration.
 */
public final class StructureKey {

    private final boolean hasSeed;
    private final long seed;
    private final String dimensionId;
    private final String structureTypeId;
    private final int chunkX;
    private final int chunkZ;

    private StructureKey(boolean hasSeed, long seed, String dimensionId, String structureTypeId,
                         int chunkX, int chunkZ) {
        if (dimensionId == null || dimensionId.trim().isEmpty()) {
            throw new IllegalArgumentException("a structure key needs a dimension");
        }
        if (structureTypeId == null || structureTypeId.trim().isEmpty()) {
            throw new IllegalArgumentException("a structure key needs a structure type");
        }
        this.hasSeed = hasSeed;
        this.seed = hasSeed ? seed : 0L;
        this.dimensionId = dimensionId;
        this.structureTypeId = structureTypeId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    /** A structure the map predicted from {@code seed}, starting in that chunk. */
    public static StructureKey predicted(long seed, String dimensionId, StructureType type,
                                         int chunkX, int chunkZ) {
        if (type == null) {
            throw new IllegalArgumentException("a structure key needs a structure type");
        }
        return new StructureKey(true, seed, dimensionId, type.id(), chunkX, chunkZ);
    }

    /**
     * The key of the structure the map shows for that type and chunk in the world as it is now: the
     * seed the map is drawn from - runtime or remembered, whichever {@link ActiveWorld} picks - and
     * the dimension the player is in. What validation found there is deliberately not an input.
     *
     * @return the key, or {@code null} when there is no seed or no dimension to predict from
     */
    public static StructureKey predictedIn(ActiveWorld world, StructureType type, int chunkX,
                                           int chunkZ) {
        if (world == null || !world.hasSeed() || world.context().dimensionId() == null) {
            return null;
        }
        return predicted(world.seed(), world.context().dimensionId(), type, chunkX, chunkZ);
    }

    /**
     * A key read back from storage, whose type id may belong to a newer Seed Checker.
     *
     * @param seed the seed it was predicted from, or {@code null} for a key without one
     */
    public static StructureKey of(Long seed, String dimensionId, String structureTypeId,
                                  int chunkX, int chunkZ) {
        return new StructureKey(seed != null, seed == null ? 0L : seed.longValue(), dimensionId,
                structureTypeId, chunkX, chunkZ);
    }

    public boolean hasSeed() {
        return hasSeed;
    }

    /**
     * @return the seed the structure was predicted from
     * @throws IllegalStateException for a key without one; check {@link #hasSeed()} first
     */
    public long seed() {
        if (!hasSeed) {
            throw new IllegalStateException("this structure key has no seed");
        }
        return seed;
    }

    /** Whether this key belongs to the structures predicted from that seed. */
    public boolean isPredictedFrom(long currentSeed) {
        return hasSeed && seed == currentSeed;
    }

    public String dimensionId() {
        return dimensionId;
    }

    public String structureTypeId() {
        return structureTypeId;
    }

    /** @return the type, or {@code null} when the id is not one this version knows */
    public StructureType structureType() {
        return StructureType.fromId(structureTypeId);
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StructureKey)) {
            return false;
        }
        StructureKey that = (StructureKey) other;
        return hasSeed == that.hasSeed && seed == that.seed && chunkX == that.chunkX
                && chunkZ == that.chunkZ && dimensionId.equals(that.dimensionId)
                && structureTypeId.equals(that.structureTypeId);
    }

    @Override
    public int hashCode() {
        int result = hasSeed ? (int) (seed ^ (seed >>> 32)) : 0x5EED;
        result = 31 * result + dimensionId.hashCode();
        result = 31 * result + structureTypeId.hashCode();
        result = 31 * result + chunkX;
        result = 31 * result + chunkZ;
        return result;
    }

    @Override
    public String toString() {
        return "StructureKey{" + structureTypeId + " " + dimensionId + " chunk " + chunkX + ","
                + chunkZ + (hasSeed ? ", seed " + seed : ", no seed") + "}";
    }
}
