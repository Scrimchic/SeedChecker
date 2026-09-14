package com.scrimchic.seedchecker.worldgen;

import com.scrimchic.seedchecker.core.map.ChunkRange;

/**
 * Finds the chunks a grid-placed structure could start in, for any seed, with no Minecraft state.
 *
 * <h2>What a result means</h2>
 *
 * <p>A result is a <em>candidate</em>: a chunk vanilla's placement lets the structure set try. It
 * is not a promise that a structure generates there. Vanilla then checks the biome at the start
 * position, sometimes the terrain height, and for jigsaw structures consults a template manager -
 * none of which happens here. Every real structure of that type is at a candidate chunk, but not
 * every candidate chunk holds a structure.
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
 * <p>and then {@code StructurePlacement.isStructureChunk}'s two restrictions on that chunk, for the
 * sets that have them: the {@link FrequencyReduction} when the frequency is below 1, and the
 * {@link ExclusionZone}. A chunk that fails either is not a candidate at all - vanilla never lets
 * the set try it - so it is never reported, not even as a rejected one.
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
 * one thousandth of the work. A set of spacing 1 (buried treasure, mineshaft) has one region per
 * chunk, and every one of them pays for its frequency draw.
 *
 * <p>Instances carry a reusable generator and are <strong>not thread safe</strong>; give each
 * scanning thread its own.
 */
public final class StructurePlacementEngine {

    private final LegacyRandom random = new LegacyRandom();

    /**
     * The chunk grid placement picks for one structure region, before any restriction.
     *
     * @return the chunk packed as {@code (chunkX << 32) | (chunkZ & 0xFFFFFFFF)}; read it with
     *         {@link #chunkX(long)} and {@link #chunkZ(long)}. Packed rather than boxed so a scan
     *         allocates nothing.
     */
    public long candidateChunk(long worldSeed, StructurePlacementConfig config,
                               int regionX, int regionZ) {
        random.setLargeFeatureWithSalt(worldSeed, regionX, regionZ, config.salt());

        int range = config.offsetRange();
        SpreadType spread = config.spreadType();
        int chunkX = regionX * config.spacing() + spread.offset(random, range);
        int chunkZ = regionZ * config.spacing() + spread.offset(random, range);
        return pack(chunkX, chunkZ);
    }

    /**
     * {@code StructurePlacement.isStructureChunk}: whether vanilla lets this set try that chunk at
     * all - grid placement picked it, and no restriction refused it.
     */
    public boolean isStructureChunk(long worldSeed, StructurePlacementConfig config,
                                    int chunkX, int chunkZ) {
        int spacing = config.spacing();
        long candidate = candidateChunk(worldSeed, config,
                Math.floorDiv(chunkX, spacing), Math.floorDiv(chunkZ, spacing));
        return chunkX(candidate) == chunkX && chunkZ(candidate) == chunkZ
                && passesRestrictions(worldSeed, config, chunkX, chunkZ);
    }

    /**
     * The restrictions alone, for a chunk grid placement already picked: the frequency reduction,
     * then the exclusion zone, in vanilla's order. Both are pure conditions, so the order only
     * matters for cost.
     */
    public boolean passesRestrictions(long worldSeed, StructurePlacementConfig config,
                                      int chunkX, int chunkZ) {
        if (config.frequency() < 1.0F && !config.frequencyReduction().keeps(random, worldSeed,
                config.salt(), chunkX, chunkZ, config.frequency())) {
            return false;
        }
        ExclusionZone zone = config.exclusionZone();
        return zone == null
                || !hasStructureChunkInRange(worldSeed, zone.other(), chunkX, chunkZ,
                        zone.chunkCount());
    }

    /**
     * {@code ChunkGeneratorStructureState.hasStructureChunkInRange}: whether the set has a
     * structure chunk within {@code range} chunks of that one on both axes.
     *
     * <p>Vanilla asks {@code isStructureChunk} of every chunk in the square. Only the chunk grid
     * placement picked for its region can answer yes, so walking the regions that overlap the square
     * and testing their candidates is the same question at a fraction of the cost.
     */
    public boolean hasStructureChunkInRange(long worldSeed, StructurePlacementConfig config,
                                            int chunkX, int chunkZ, int range) {
        int minX = chunkX - range;
        int maxX = chunkX + range;
        int minZ = chunkZ - range;
        int maxZ = chunkZ + range;
        int spacing = config.spacing();
        for (int regionX = Math.floorDiv(minX, spacing); regionX <= Math.floorDiv(maxX, spacing);
                regionX++) {
            for (int regionZ = Math.floorDiv(minZ, spacing);
                    regionZ <= Math.floorDiv(maxZ, spacing); regionZ++) {
                long candidate = candidateChunk(worldSeed, config, regionX, regionZ);
                int x = chunkX(candidate);
                int z = chunkZ(candidate);
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ
                        && passesRestrictions(worldSeed, config, x, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Visits every candidate chunk inside {@code area}: every structure chunk, restrictions
     * applied.
     *
     * @param maxCandidates stop after this many, as a guard against absurd viewports
     * @return how many candidates were visited
     */
    public int forEachCandidate(long worldSeed, StructurePlacementConfig config, ChunkRange area,
                                int maxCandidates, StructureCandidateVisitor visitor) {
        int spacing = config.spacing();
        boolean restricted = config.hasRestrictions();
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
                if (restricted && !passesRestrictions(worldSeed, config, chunkX, chunkZ)) {
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
