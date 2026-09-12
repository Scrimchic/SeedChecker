package com.scrimchic.seedchecker.worldgen;

import com.scrimchic.seedchecker.core.map.ChunkRange;

/**
 * Finds the chunks a grid-placed structure could start in, for any seed, with no Minecraft state.
 *
 * <h2>What a result means</h2>
 *
 * <p>A result is a <em>candidate</em>: the chunk vanilla's grid placement picked for that region.
 * It is not a promise that a structure generates there. Vanilla then checks the biome at the start
 * position, sometimes the terrain height, and for jigsaw structures consults a template manager -
 * none of which happens here. Roughly speaking every real structure of that type is at a candidate
 * chunk, but not every candidate chunk holds a structure.
 *
 * <h2>The algorithm</h2>
 *
 * <p>Transcribed from the bytecode of {@code RandomSpreadStructurePlacement
 * .getPotentialStructureChunk} (1.20.1, 26.2) and {@code StructureFeature.getPotentialFeatureChunk}
 * (1.16.5), which were verified to be the same computation:
 *
 * <pre>
 * regionX = floorDiv(chunkX, spacing)
 * regionZ = floorDiv(chunkZ, spacing)
 * rng.setSeed(regionX * 341873128712 + regionZ * 132897987541 + worldSeed + salt)
 * offsetX = spreadType.offset(rng, spacing - separation)
 * offsetZ = spreadType.offset(rng, spacing - separation)
 * candidate = (regionX * spacing + offsetX, regionZ * spacing + offsetZ)
 * </pre>
 *
 * <p>{@code floorDiv} rather than {@code /} is what makes negative coordinates work: integer
 * division truncates towards zero, which would fold chunk -1 and chunk 0 into the same region and
 * shift every structure west and north of the origin.
 *
 * <p>The two multiplications are <em>long</em> arithmetic that is expected to wrap for far-out
 * regions, and the sum is not normalised in any way. Rewriting it "more cleanly" changes results.
 *
 * <h2>Scanning</h2>
 *
 * <p>{@link #forEachCandidate} walks <em>regions</em>, not chunks, so its cost scales with the
 * number of structure regions on screen rather than the number of chunks - at spacing 32 that is
 * one thousandth of the work.
 *
 * <p>Instances carry a reusable generator and are <strong>not thread safe</strong>; give each
 * scanning thread its own.
 */
public final class StructurePlacementEngine {

    private static final long REGION_X_MULTIPLIER = 341873128712L;
    private static final long REGION_Z_MULTIPLIER = 132897987541L;

    private final LegacyRandom random = new LegacyRandom();

    /**
     * The candidate chunk for one structure region.
     *
     * @return the chunk packed as {@code (chunkX << 32) | (chunkZ & 0xFFFFFFFF)}; read it with
     *         {@link #chunkX(long)} and {@link #chunkZ(long)}. Packed rather than boxed so a scan
     *         allocates nothing.
     */
    public long candidateChunk(long worldSeed, StructurePlacementConfig config,
                               int regionX, int regionZ) {
        random.setSeed(regionX * REGION_X_MULTIPLIER
                + regionZ * REGION_Z_MULTIPLIER
                + worldSeed
                + config.salt());

        int range = config.offsetRange();
        SpreadType spread = config.spreadType();
        int chunkX = regionX * config.spacing() + spread.offset(random, range);
        int chunkZ = regionZ * config.spacing() + spread.offset(random, range);
        return pack(chunkX, chunkZ);
    }

    /**
     * Visits every candidate chunk inside {@code area}.
     *
     * @param maxCandidates stop after this many, as a guard against absurd viewports
     * @return how many candidates were visited
     */
    public int forEachCandidate(long worldSeed, StructurePlacementConfig config, ChunkRange area,
                                int maxCandidates, StructureCandidateVisitor visitor) {
        int spacing = config.spacing();
        // Every candidate sits inside its own region, so the regions overlapping the area are
        // exactly the regions that can contribute to it.
        long firstRegionX = Math.floorDiv((long) area.minChunkX(), (long) spacing);
        long lastRegionX = Math.floorDiv((long) area.maxChunkX(), (long) spacing);
        long firstRegionZ = Math.floorDiv((long) area.minChunkZ(), (long) spacing);
        long lastRegionZ = Math.floorDiv((long) area.maxChunkZ(), (long) spacing);

        int visited = 0;
        for (long regionZ = firstRegionZ; regionZ <= lastRegionZ; regionZ++) {
            for (long regionX = firstRegionX; regionX <= lastRegionX; regionX++) {
                if (visited >= maxCandidates) {
                    return visited;
                }
                long candidate = candidateChunk(worldSeed, config, (int) regionX, (int) regionZ);
                int chunkX = chunkX(candidate);
                int chunkZ = chunkZ(candidate);
                if (chunkX < area.minChunkX() || chunkX > area.maxChunkX()
                        || chunkZ < area.minChunkZ() || chunkZ > area.maxChunkZ()) {
                    continue;
                }
                visited++;
                if (!visitor.visit(chunkX, chunkZ)) {
                    return visited;
                }
            }
        }
        return visited;
    }

    /** How many regions {@link #forEachCandidate} would walk, saturating instead of overflowing. */
    public static long regionCount(StructurePlacementConfig config, ChunkRange area) {
        int spacing = config.spacing();
        long wide = Math.floorDiv((long) area.maxChunkX(), (long) spacing)
                - Math.floorDiv((long) area.minChunkX(), (long) spacing) + 1L;
        long tall = Math.floorDiv((long) area.maxChunkZ(), (long) spacing)
                - Math.floorDiv((long) area.minChunkZ(), (long) spacing) + 1L;
        if (wide <= 0L || tall <= 0L) {
            return 0L;
        }
        return wide > Long.MAX_VALUE / tall ? Long.MAX_VALUE : wide * tall;
    }

    public static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int chunkX(long packedChunk) {
        return (int) (packedChunk >> 32);
    }

    public static int chunkZ(long packedChunk) {
        return (int) packedChunk;
    }
}
