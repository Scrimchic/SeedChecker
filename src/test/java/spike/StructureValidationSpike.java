package spike;

import java.util.ArrayList;
import java.util.List;

import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.platform.BiomeWorldgenSession;
import com.scrimchic.seedchecker.platform.StructureBiomeValidator;
import com.scrimchic.seedchecker.worldgen.StructurePlacementConfig;
import com.scrimchic.seedchecker.worldgen.StructurePlacementEngine;
import com.scrimchic.seedchecker.worldgen.StructurePlacements;
import com.scrimchic.seedchecker.worldgen.StructureType;
import com.scrimchic.seedchecker.worldgen.StructureValidation;

/**
 * Measures what biome validation actually costs and how much of the raw grid it removes, for the
 * numbers quoted in the Phase 3E-1 summary. Throwaway, not part of {@code build}:
 * {@code ./gradlew :<target>:worldgenSpike -PspikeMain=spike.StructureValidationSpike}.
 */
public final class StructureValidationSpike {

    private static final long SEED = -7407337299659424542L;
    /** +-2000 chunks around the origin: far wider than a screenful, so biomes actually vary. */
    private static final int RADIUS_CHUNKS = 2000;

    private static String version() {
        return /*$ minecraft*/ "unknown";
    }

    public static void main(String[] args) {
        System.out.println("=== structure biome validation spike ===");
        System.out.println("minecraft   " + version());
        System.out.println("seed        " + SEED);
        System.out.println("area        chunks " + -RADIUS_CHUNKS + ".." + RADIUS_CHUNKS
                + " on both axes");

        //? if >=1.18 {
        net.minecraft.SharedConstants.tryDetectVersion();
        //?}
        net.minecraft.server.Bootstrap.bootStrap();

        long tableStart = System.nanoTime();
        StructureBiomeValidator.supports(StructureType.VILLAGE);
        System.out.printf("%nbiome table %.0f ms once per JVM (parse + resolve, seed independent)%n",
                (System.nanoTime() - tableStart) / 1e6);

        long sessionStart = System.nanoTime();
        BiomeWorldgenSession session =
                BiomeWorldgenSession.create(SEED, BiomeWorldgenSession.OVERWORLD);
        System.out.printf("session     %.0f ms for the first worker%n",
                (System.nanoTime() - sessionStart) / 1e6);

        System.out.println();
        System.out.printf("%-16s %10s %8s %9s %10s %8s %10s%n",
                "structure", "candidates", "shown", "rejected", "undecided", "shown %", "ms each");

        ChunkRange area = ChunkRange.of(-RADIUS_CHUNKS, -RADIUS_CHUNKS,
                RADIUS_CHUNKS, RADIUS_CHUNKS);
        StructurePlacements placements = StructurePlacements.forThisVersion();
        StructurePlacementEngine engine = new StructurePlacementEngine();

        int totalCandidates = 0;
        int totalKept = 0;
        for (StructureType type : StructureType.values()) {
            if (!placements.supports(type)) {
                continue;
            }
            StructurePlacementConfig config = placements.get(type);
            final List<int[]> chunks = new ArrayList<int[]>();
            engine.forEachCandidate(SEED, config, area, 100_000,
                    (chunkX, chunkZ) -> {
                        chunks.add(new int[] {chunkX, chunkZ});
                        return true;
                    });

            int rejected = 0;
            int undecided = 0;
            long start = System.nanoTime();
            for (int[] chunk : chunks) {
                StructureValidation result =
                        StructureBiomeValidator.validate(session, type, chunk[0], chunk[1]);
                if (result.isRejected()) {
                    rejected++;
                } else if (!result.isCompatible()) {
                    undecided++;
                }
            }
            double millisEach = (System.nanoTime() - start) / 1e6 / Math.max(1, chunks.size());
            int shown = chunks.size() - rejected;

            System.out.printf("%-16s %10d %8d %9d %10d %7.1f%% %10.2f%n",
                    type.name().toLowerCase(), chunks.size(), shown, rejected, undecided,
                    100.0 * shown / Math.max(1, chunks.size()), millisEach);
            totalCandidates += chunks.size();
            totalKept += shown;
        }

        System.out.println();
        System.out.println("decidable on this version (anything else is shown, never hidden):");
        for (StructureType type : StructureType.values()) {
            if (placements.supports(type)) {
                System.out.printf("  %-16s %s%n", type.name().toLowerCase(),
                        StructureBiomeValidator.canDecide(type) ? "yes" : "no");
            }
        }

        System.out.println();
        System.out.printf("overall     %d candidates, %d shown, %d rejected (%.1f%% filtered out)%n",
                totalCandidates, totalKept, totalCandidates - totalKept,
                100.0 * (totalCandidates - totalKept) / Math.max(1, totalCandidates));
    }
}
