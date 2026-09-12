package com.scrimchic.seedchecker.worldgen;

/**
 * Receives candidate chunks as {@link StructurePlacementEngine} finds them.
 *
 * <p>A callback rather than a returned collection, so a viewport scan produces no objects at all:
 * the renderer draws straight from it.
 */
public interface StructureCandidateVisitor {

    /**
     * @param chunkX chunk X the structure would start in
     * @param chunkZ chunk Z the structure would start in
     * @return {@code false} to stop the scan early, for example once a marker budget is spent
     */
    boolean visit(int chunkX, int chunkZ);
}
