package com.scrimchic.seedchecker.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Vanilla's concentric ring placement, as used for strongholds, reproduced in pure Java.
 *
 * <p>Not a variant of {@link StructurePlacementEngine}: there is no grid and no per-region seed.
 * One generator is seeded with the world seed and walks all the structures in order, ring by ring,
 * and each position is then pulled towards a preferred biome by a randomised search. The whole
 * list exists at once or not at all, so this answers "where are all of them" rather than "is this
 * chunk one".
 *
 * <h2>The ring loop</h2>
 *
 * <p>Transcribed from {@code ChunkGenerator.generateStrongholds} on 1.16.5 and
 * {@code ChunkGeneratorStructureState.generateRingPositions} on 1.20.1 and 26.2, which are the same
 * arithmetic expression for expression - including the order of every floating point operation,
 * because a reordered multiplication is a different double:
 *
 * <pre>
 * angle = nextDouble() * PI * 2
 * for i in 0 until count:
 *     dist  = (4 * distance + distance * ring * 6) + (nextDouble() - 0.5) * (distance * 2.5)
 *     chunk = (round(cos(angle) * dist), round(sin(angle) * dist))
 *     chunk = biome search around (chunk * 16 + 8), radius 112 blocks, if it finds anything
 *     angle += 2 * PI / spread
 *     if ++inRing == spread:
 *         ring++, inRing = 0
 *         spread += 2 * spread / (ring + 1);  spread = min(spread, count - i)
 *         angle += nextDouble() * PI * 2
 * </pre>
 *
 * <p>The one real difference between the versions is which generator the biome search draws from,
 * captured by {@link RandomModel}.
 *
 * <h2>The biome search</h2>
 *
 * <p>{@code BiomeSource.findBiomeHorizontal} with a step of 1 and {@code findClosest} false, as both
 * callers use it: every quart column of the square within the radius is visited, z outer and x
 * inner, and among the accepted ones a single result is chosen by reservoir sampling - the first
 * match is taken outright, the n-th replaces it when {@code nextInt(n) == 0}. Only the biome test is
 * delegated, through {@link BiomeFilter}, because that is the part that needs Minecraft.
 *
 * <p>Verified candidate by candidate against vanilla's own list on all three targets, with every
 * number the generator draws in play.
 */
public final class StrongholdPlacementEngine {

    /** Which generator the biome search consumes. */
    public enum RandomModel {

        /**
         * 1.16.5: the search draws straight from the ring generator, so how many biome matches one
         * stronghold's search finds shifts every angle and distance after it.
         */
        SHARED,

        /**
         * 1.18 onwards: every stronghold forks its own generator - one {@code nextLong()} from the
         * ring generator seeds it - so the ring arithmetic no longer depends on the biomes.
         */
        FORKED
    }

    /** Decides whether a biome at a quart position is one the placement prefers. */
    public interface BiomeFilter {
        boolean accepts(int quartX, int quartY, int quartZ);
    }

    /** The search radius in blocks, a literal 112 at both vanilla call sites. */
    public static final int BIOME_SEARCH_RADIUS = 112;

    private static final long NOT_FOUND = Long.MIN_VALUE;

    private StrongholdPlacementEngine() {
    }

    /**
     * Every stronghold of a world, in vanilla's placement order.
     *
     * <p>Samples a biome square per stronghold - over three thousand samples each - so it belongs
     * on a worker thread.
     */
    public static List<StrongholdPosition> place(long seed, ConcentricRingConfig config,
                                                 RandomModel model, BiomeFilter filter) {
        int count = config.count();
        if (count == 0) {
            return Collections.emptyList();
        }
        int distance = config.distance();
        int spread = config.spread();

        LegacyRandom random = new LegacyRandom();
        random.setSeed(seed);
        double angle = random.nextDouble() * Math.PI * 2.0;
        int inRing = 0;
        int ring = 0;

        List<StrongholdPosition> positions = new ArrayList<StrongholdPosition>(count);
        for (int i = 0; i < count; i++) {
            double dist = (double) (4 * distance + distance * ring * 6)
                    + (random.nextDouble() - 0.5) * ((double) distance * 2.5);
            int ringChunkX = (int) Math.round(Math.cos(angle) * dist);
            int ringChunkZ = (int) Math.round(Math.sin(angle) * dist);

            LegacyRandom searchRandom;
            if (model == RandomModel.FORKED) {
                searchRandom = new LegacyRandom();
                searchRandom.setSeed(random.nextLong());
            } else {
                searchRandom = random;
            }
            long found = findBiome((ringChunkX << 4) + 8, 0, (ringChunkZ << 4) + 8,
                    BIOME_SEARCH_RADIUS, filter, searchRandom);
            int chunkX = found == NOT_FOUND ? ringChunkX : blockXOf(found) >> 4;
            int chunkZ = found == NOT_FOUND ? ringChunkZ : blockZOf(found) >> 4;
            positions.add(new StrongholdPosition(i, ring, chunkX, chunkZ, ringChunkX, ringChunkZ));

            angle += 6.283185307179586 / (double) spread;
            inRing++;
            if (inRing == spread) {
                ring++;
                inRing = 0;
                spread += 2 * spread / (ring + 1);
                spread = Math.min(spread, count - i);
                angle += random.nextDouble() * Math.PI * 2.0;
            }
        }
        return Collections.unmodifiableList(positions);
    }

    /**
     * The stronghold closest to a block position, by horizontal distance to its chunk centre.
     *
     * @return the nearest one, or {@code null} for an empty list
     */
    public static StrongholdPosition nearest(List<StrongholdPosition> positions, double blockX,
                                             double blockZ) {
        StrongholdPosition best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < positions.size(); i++) {
            StrongholdPosition position = positions.get(i);
            double dx = (position.chunkX() << 4) + 8 - blockX;
            double dz = (position.chunkZ() << 4) + 8 - blockZ;
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = position;
            }
        }
        return best;
    }

    /**
     * {@code BiomeSource.findBiomeHorizontal(x, y, z, radius, 1, predicate, random, false)}.
     *
     * @return the chosen block position packed by {@link #pack}, or {@link #NOT_FOUND}
     */
    static long findBiome(int blockX, int blockY, int blockZ, int radius, BiomeFilter filter,
                          LegacyRandom random) {
        int centreX = blockX >> 2;
        int centreZ = blockZ >> 2;
        int quartRadius = radius >> 2;
        int quartY = blockY >> 2;

        long result = NOT_FOUND;
        int matches = 0;
        // With findClosest false the shell loop starts at the full radius, so it runs once and
        // visits the whole square rather than just its edge.
        for (int offsetZ = -quartRadius; offsetZ <= quartRadius; offsetZ++) {
            for (int offsetX = -quartRadius; offsetX <= quartRadius; offsetX++) {
                int quartX = centreX + offsetX;
                int quartZ = centreZ + offsetZ;
                if (!filter.accepts(quartX, quartY, quartZ)) {
                    continue;
                }
                if (result == NOT_FOUND || random.nextInt(matches + 1) == 0) {
                    result = pack(quartX << 2, quartZ << 2);
                }
                matches++;
            }
        }
        return result;
    }

    static long pack(int blockX, int blockZ) {
        return ((long) blockX << 32) | (blockZ & 0xFFFFFFFFL);
    }

    static int blockXOf(long packed) {
        return (int) (packed >> 32);
    }

    static int blockZOf(long packed) {
        return (int) packed;
    }
}
