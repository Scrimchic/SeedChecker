package com.scrimchic.seedchecker.platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.scrimchic.seedchecker.core.util.LazyInit;
import com.scrimchic.seedchecker.worldgen.GenerationPoint;
import com.scrimchic.seedchecker.worldgen.StructureType;
import com.scrimchic.seedchecker.worldgen.StructureValidation;

//? if >=1.18 {
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
//?} else {
/*import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.StructureFeature;*/
//?}

/**
 * Rejects structure candidates whose biome vanilla would never accept.
 *
 * <p>The one place that knows how a Minecraft version associates structures with biomes, and where
 * it samples the biome to decide. Everything above works on {@link StructureValidation} and biome
 * id strings.
 *
 * <h2>1.16.5: exact</h2>
 *
 * <p>1.16.5 inverts the relationship: a {@code Biome} lists the structures that may start in it,
 * through {@code BiomeGenerationSettings.isValidStart(StructureFeature)}. The position is fixed and
 * terrain independent - {@code ChunkGenerator.createStructures} samples
 * {@code biomeSource.getNoiseBiome((chunkX &lt;&lt; 2) + 2, 0, (chunkZ &lt;&lt; 2) + 2)} and then
 * walks that biome's structure list. One sample reproduces vanilla's whole answer, so on 1.16.5
 * this check is exact in both directions.
 *
 * <h2>1.18 onwards: exact only where the sampled position is knowable</h2>
 *
 * <p>From 1.18 a {@code Structure} carries a {@code HolderSet&lt;Biome&gt;} - in practice a
 * {@code #minecraft:has_structure/...} tag - and the check is
 * {@code Structure.findValidGenerationPoint}, which builds a {@code GenerationStub} and then asks
 * {@code isValidBiome}. The bytecode of {@code isValidBiome} is one expression: sample
 * {@code biomeSource.getNoiseBiome} at the stub position with every axis run through
 * {@code QuartPos.fromBlock}, and test it against {@code structure.biomes()::contains}. Nothing
 * else enters the decision - {@code ChunkGenerator.tryGenerateStructure} passes exactly that
 * predicate and no other.
 *
 * <p>So the whole question is where the stub sits, and that splits the supported structures in
 * three - {@link Position}.
 *
 * <p><strong>Exact position: the desert pyramid.</strong> {@code SinglePieceStructure} anchors on
 * {@code Structure.onTopOfChunkCenter}, so the column is {@code ChunkPos.getMiddleBlockX/Z} and the
 * height is {@code getFirstOccupiedHeight(WORLD_SURFACE_WG)}, which this session can compute.
 * {@code findGenerationPoint} additionally refuses the structure outright when the lowest of four
 * corner heights falls below sea level - a condition no amount of biome enumeration models. Both
 * halves are reproduced, so both {@code COMPATIBLE} and {@code INCOMPATIBLE} are exact.
 *
 * <p><strong>Known column, bounded height: the shipwreck.</strong>
 * {@code ShipwreckStructure.findGenerationPoint} also ends in {@code onTopOfChunkCenter}, so the
 * exact answer is equally within reach - and was deliberately not taken. Measured on the
 * representative seed, the exact path cost three to five times as much and rejected not one
 * additional candidate, because an ocean column carries an ocean biome at every height. Instead the
 * height is bounded: {@code getBaseHeight} walks a column clamped to the dimension and falls back to
 * its minimum, so the stub height never leaves the range
 * {@link BiomeWorldgenSession#lowestQuartY()} to {@link BiomeWorldgenSession#highestQuartY()}
 * covers. That range is enumerated, every quart row of it. If no row is accepted then no terrain
 * can produce one, so a rejection is still proof; an acceptance is a superset.
 *
 * <p><strong>Exact position through vanilla: the jigsaws.</strong> {@code JigsawStructure}
 * (village, ancient city, trial chamber) hands the stub to {@code JigsawPlacement.addPieces}, and
 * the position that comes back is the centre of the start piece's bounding box - which depends on
 * the template drawn from the start pool, its rotation, its NBT size, a named start jigsaw and, for
 * the village, the terrain. None of that is reimplemented. Vanilla's own
 * {@code findValidGenerationPoint} runs against the data pack loaded as real registries
 * ({@link VanillaStructureData}), and only the biome test at the point it returns is ours - the same
 * verified sets as everywhere else, so 1.20.1 and 26.2 answer alike. A multi-entry set is tried in
 * vanilla's own weighted order, so the variant and the point reported are the ones vanilla builds.
 *
 * <p>Two things this relies on, both read in the bytecode of 1.20.1 and 26.2 and both checked
 * against vanilla's {@code Structure.generate} in {@code StructureBiomeValidatorTest}: vanilla
 * defers piece assembly into the stub, and that deferred step always keeps the start piece -
 * unless the structure's {@code size} is zero, in which case it returns before handing any piece to
 * the builder and nothing generates. Such an entry is left {@code UNKNOWN} here rather than
 * modelled.
 *
 * <h2>Where the modern data comes from</h2>
 *
 * <p>Read out of the vanilla datapack that ships inside the Minecraft jar - the structure sets, the
 * structures they contain, and the biome tags those structures name, tags resolved recursively.
 * Nothing is hardcoded from memory, so a version that changes a structure's biomes is followed
 * automatically.
 *
 * <p>Loaded once, lazily, and cached for the life of the game.
 */
public final class StructureBiomeValidator {

    private static Map<StructureType, List<Entry>> byType;

    private StructureBiomeValidator() {
    }

    /** @return whether this version has biome rules for that structure at all. */
    public static boolean supports(StructureType type) {
        return data().containsKey(type);
    }

    /**
     * Whether a candidate of this type can be rejected at all on this version.
     *
     * <p>False when vanilla's sampled position is not computable in this phase, in which case
     * {@link #validate} only ever answers {@code UNKNOWN} and no marker is ever hidden.
     */
    public static boolean canDecide(StructureType type) {
        List<Entry> entries = data().get(type);
        if (entries == null) {
            return false;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).position == Position.UNKNOWN) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether this version reproduces vanilla's answer for that structure exactly, rather than
     * bounding it.
     *
     * <p>{@link #canDecide} says a candidate can be rejected; this says the acceptances are exact
     * too, so {@code COMPATIBLE} means the structure really does generate as far as biome and the
     * structure's own conditions go. The map says which of the two it is showing.
     */
    public static boolean isExact(StructureType type) {
        List<Entry> entries = data().get(type);
        if (entries == null) {
            return false;
        }
        for (int i = 0; i < entries.size(); i++) {
            Position position = entries.get(i).position;
            if (position != Position.EXACT && position != Position.JIGSAW) {
                return false;
            }
        }
        return !entries.isEmpty();
    }

    /**
     * Decides whether vanilla would generate this structure at a grid candidate - exactly, or as a
     * safe superset, depending on the structure.
     *
     * <p>Samples terrain, biome columns or a jigsaw start, so it must not be called on the render
     * thread.
     *
     * @return the decision, or {@code null} when it needs vanilla structure data that another
     *         worker is still loading. Nothing about the candidate has been decided then, and it
     *         should be asked for again later rather than stored.
     */
    public static StructureValidation validate(BiomeWorldgenSession session, StructureType type,
                                               int chunkX, int chunkZ) {
        List<Entry> entries = data().get(type);
        if (entries == null) {
            return StructureValidation.unknown("this version declares no biomes for it");
        }
        return evaluate(session, entries, chunkX, chunkZ);
    }

    private static Map<StructureType, List<Entry>> data() {
        Map<StructureType, List<Entry>> loaded = byType;
        if (loaded == null) {
            synchronized (StructureBiomeValidator.class) {
                loaded = byType;
                if (loaded == null) {
                    loaded = Collections.unmodifiableMap(load());
                    byType = loaded;
                }
            }
        }
        return loaded;
    }

    /** How far vanilla's own sample position can be reproduced for one structure entry. */
    private enum Position {

        /**
         * Reproduced exactly, so the entry decides a candidate in both directions.
         *
         * <p>Reached by computing the terrain height vanilla would anchor on rather than bounding
         * it, which costs a noise column and is therefore only done where it buys something.
         */
        EXACT,

        /**
         * X and Z exact, height enumerated over the whole dimension.
         *
         * <p>A safe superset: a rejection still means no height would have worked, but an
         * acceptance may be a height vanilla never picks.
         */
        COLUMN,

        /**
         * Exact, through vanilla's own jigsaw assembly over the loaded data pack.
         *
         * <p>Decides both ways like {@link #EXACT}, but needs {@link VanillaStructureData}, so a
         * check can come back pending while that loads.
         */
        JIGSAW,

        /** Not computable in this phase. Such an entry can never reject a candidate. */
        UNKNOWN
    }

    /** One structure entry inside a structure set, with the biomes it accepts. */
    private static final class Entry {

        private final String name;

        /** The full structure id, e.g. {@code minecraft:village_plains}; {@code null} on 1.16.5. */
        private final String structureId;

        private final String biomeTag;

        /** The entry's weight in its structure set, which decides the order vanilla tries it in. */
        private final int weight;

        private final Set<String> biomeIds;
        private final Position position;

        /** Why the position is {@link Position#UNKNOWN}, or {@code null} for every other one. */
        private final String undecidable;

        Entry(String name, String structureId, String biomeTag, int weight, Set<String> biomeIds,
              Position position, String undecidable) {
            this.name = name;
            this.structureId = structureId;
            this.biomeTag = biomeTag;
            this.weight = weight;
            this.biomeIds = biomeIds;
            this.position = position;
            this.undecidable = undecidable;
        }
    }

    // ------------------------------------------------------------- inspection

    /**
     * The structure entries this version has for a type, e.g. the five village variants.
     *
     * <p>Exposed so the vanilla cross-check can compare what was parsed against what vanilla
     * declares, rather than against a list written from memory.
     */
    public static List<String> variantNames(StructureType type) {
        List<Entry> entries = data().get(type);
        if (entries == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<String>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            names.add(entries.get(i).name);
        }
        return names;
    }

    /** The biomes one entry accepts. */
    public static Set<String> acceptedBiomes(StructureType type, String variantName) {
        Entry entry = entryOf(type, variantName);
        return entry == null ? Collections.<String>emptySet() : entry.biomeIds;
    }

    /**
     * The weight an entry has in its structure set, which decides the order vanilla tries the
     * set's entries in. 1 on 1.16.5, which has no structure sets.
     */
    public static int variantWeight(StructureType type, String variantName) {
        Entry entry = entryOf(type, variantName);
        return entry == null ? 0 : entry.weight;
    }

    /**
     * The biome tag an entry named in the datapack, or {@code null} on a version that has no
     * tags - 1.16.5 keeps the association on the biome instead.
     */
    public static String declaredBiomeTag(StructureType type, String variantName) {
        Entry entry = entryOf(type, variantName);
        return entry == null ? null : entry.biomeTag;
    }

    private static Entry entryOf(StructureType type, String variantName) {
        List<Entry> entries = data().get(type);
        if (entries == null) {
            return null;
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (variantName == null ? entry.name == null : variantName.equals(entry.name)) {
                return entry;
            }
        }
        return null;
    }

    // ------------------------------------------------------- version-specific data

    //? if >=1.18 {
    /**
     * The one structure type whose whole generation point is reproduced exactly.
     *
     * <p>{@code SinglePieceStructure.findGenerationPoint} is short and entirely computable:
     * reject unless the lowest of four corner heights reaches sea level, then anchor on the chunk
     * centre at {@code getFirstOccupiedHeight(WORLD_SURFACE_WG)}. Both halves are reproduced in
     * {@link #exactlySampledBiome}, and the result is checked against vanilla's own
     * {@code findValidGenerationPoint} in {@code StructureBiomeValidatorTest}.
     */
    private static final String EXACT_SINGLE_PIECE = "minecraft:desert_pyramid";

    /**
     * Structure types whose stub sits on the candidate chunk's own centre column, but whose height
     * is not computed.
     *
     * <p>{@code ShipwreckStructure.findGenerationPoint} ends in
     * {@code Structure.onTopOfChunkCenter} like the desert pyramid does, so the exact answer is
     * within reach - it was measured and deliberately not taken. On the representative seed the
     * exact path cost three to five times as much and rejected not one additional candidate,
     * because an ocean column carries an ocean biome at every height the enumeration visits. The
     * cheaper superset is kept until a reusable heightmap makes the exact one free.
     */
    private static final Set<String> CHUNK_CENTRE_COLUMN =
            new HashSet<String>(java.util.Arrays.asList("minecraft:shipwreck"));

    private static final String EMPTY_JIGSAW_REASON =
            "a jigsaw of size 0 hands no pieces to its structure start";

    private static final String MIXED_SET_REASON =
            "this structure set mixes jigsaw and non-jigsaw entries";

    private static final String STRUCTURE_DATA_UNAVAILABLE =
            "vanilla structure data could not be loaded";

    private static final String NO_START_PIECE = "vanilla assembled no start piece";

    private static final int[] ONLY_ENTRY = {0};

    private static final String UNVERIFIED_REASON =
            "the position this structure type generates at has not been verified";

    /** The desert pyramid's footprint, from {@code DesertPyramidStructure}'s call to super. */
    private static final int SINGLE_PIECE_WIDTH = 21;
    private static final int SINGLE_PIECE_DEPTH = 21;

    private static final String BELOW_SEA_LEVEL = "lowest corner is below sea level";

    /** Structure set path in the vanilla datapack, per structure type. */
    private static String structureSetPath(StructureType type) {
        switch (type) {
            case VILLAGE:
                return "villages";
            case DESERT_PYRAMID:
                return "desert_pyramids";
            case SHIPWRECK:
                return "shipwrecks";
            case ANCIENT_CITY:
                return "ancient_cities";
            case TRIAL_CHAMBER:
                return "trial_chambers";
            default:
                return null;
        }
    }

    private static Position positionOf(String structureTypeId, int jigsawSize) {
        if (EXACT_SINGLE_PIECE.equals(structureTypeId)) {
            return Position.EXACT;
        }
        if ("minecraft:jigsaw".equals(structureTypeId)) {
            return jigsawSize > 0 ? Position.JIGSAW : Position.UNKNOWN;
        }
        return CHUNK_CENTRE_COLUMN.contains(structureTypeId) ? Position.COLUMN : Position.UNKNOWN;
    }

    private static String undecidableReason(String structureTypeId, int jigsawSize) {
        if (positionOf(structureTypeId, jigsawSize) != Position.UNKNOWN) {
            return null;
        }
        return "minecraft:jigsaw".equals(structureTypeId) ? EMPTY_JIGSAW_REASON : UNVERIFIED_REASON;
    }

    private static boolean isJigsawSet(List<Entry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).position != Position.JIGSAW) {
                return false;
            }
        }
        return !entries.isEmpty();
    }

    /**
     * Reproduces {@code ChunkGenerator.createStructures} for a set of jigsaw structures: the entries
     * in vanilla's weighted order, and for each, vanilla's own generation point followed by the
     * biome test at it. The first entry that passes is the one vanilla builds.
     *
     * @return the decision, or {@code null} while the vanilla structure data is still loading on
     *         another worker
     */
    private static StructureValidation evaluateJigsawSet(BiomeWorldgenSession session,
                                                         List<Entry> entries,
                                                         int chunkX, int chunkZ) {
        LazyInit.State state = session.prepareJigsaw();
        if (state == LazyInit.State.FAILED) {
            // Cached like any other answer, so a broken data pack costs one check per candidate
            // rather than one per frame - and the marker stays visible.
            return StructureValidation.unknown(STRUCTURE_DATA_UNAVAILABLE);
        }
        if (state != LazyInit.State.READY) {
            return null;
        }

        int[] order;
        if (entries.size() == 1) {
            order = ONLY_ENTRY;
        } else {
            int[] weights = new int[entries.size()];
            for (int i = 0; i < weights.length; i++) {
                weights[i] = entries.get(i).weight;
            }
            order = session.structureSelectionOrder(weights, chunkX, chunkZ);
        }

        String firstBiome = null;
        for (int i = 0; i < order.length; i++) {
            Entry entry = entries.get(order[i]);
            GenerationPoint point = session.jigsawGenerationPoint(entry.structureId, chunkX, chunkZ);
            if (point == null) {
                // No start piece: vanilla's attempt fails and it moves on to the next entry.
                continue;
            }
            String biomeId = session.jigsawBiomeIdAt(point);
            if (firstBiome == null) {
                firstBiome = biomeId;
            }
            if (entry.biomeIds.contains(biomeId)) {
                return StructureValidation.exactlyCompatible(entry.name, biomeId, point);
            }
        }
        return firstBiome == null
                ? StructureValidation.incompatible(null, NO_START_PIECE)
                : StructureValidation.incompatible(firstBiome);
    }

    /**
     * Runs whichever of the three position models each entry of the set supports, and combines
     * them.
     *
     * <p>A set may mix them: the shipwrecks set holds one entry this phase reproduces exactly and
     * one it only bounds. Vanilla tries a set's entries until one generates, so any entry
     * accepting is a yes, and only a set where <em>every</em> entry was decided and refused is a
     * no.
     */
    private static StructureValidation evaluate(BiomeWorldgenSession session, List<Entry> entries,
                                                int chunkX, int chunkZ) {
        if (isJigsawSet(entries)) {
            return evaluateJigsawSet(session, entries, chunkX, chunkZ);
        }

        String undecidable = null;
        List<Entry> columnEntries = null;
        List<Entry> exactEntries = null;

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.position == Position.UNKNOWN) {
                undecidable = entry.undecidable;
            } else if (entry.position == Position.JIGSAW) {
                // No vanilla set does this; were one to, its jigsaw half is not evaluated here.
                undecidable = MIXED_SET_REASON;
            } else if (entry.position == Position.EXACT) {
                if (exactEntries == null) {
                    exactEntries = new ArrayList<Entry>(entries.size());
                }
                exactEntries.add(entry);
            } else {
                if (columnEntries == null) {
                    columnEntries = new ArrayList<Entry>(entries.size());
                }
                columnEntries.add(entry);
            }
        }
        if (exactEntries == null && columnEntries == null) {
            return StructureValidation.unknown(undecidable);
        }

        Match match = new Match();

        // Exact entries first. They are the cheaper question for the structure they cover - one
        // terrain column rather than a biome column - and they answer it outright.
        if (exactEntries != null) {
            for (int i = 0; i < exactEntries.size(); i++) {
                exactlySampledBiome(session, exactEntries.get(i), chunkX, chunkZ, match);
            }
        }
        if (match.entry == null && columnEntries != null) {
            scanBiomeColumn(session, columnEntries, chunkX, chunkZ, match);
        }

        if (match.entry != null) {
            // The variant is only reported when exactly one entry can explain the candidate. When
            // several could, vanilla picks between them with a seeded weighted draw that this phase
            // does not reproduce, so claiming one would be a guess.
            String variant = match.ambiguous ? null : match.entry.name;
            return match.entry.position == Position.EXACT
                    ? StructureValidation.exactlyCompatible(variant, match.biomeId, match.point)
                    : StructureValidation.compatible(variant, match.biomeId);
        }
        if (undecidable != null) {
            // Some entry of this set might still accept it somewhere this cannot look.
            return StructureValidation.unknown(undecidable);
        }
        return match.rejectedBecause == null
                ? StructureValidation.incompatible(match.lastSeen)
                : StructureValidation.incompatible(match.lastSeen, match.rejectedBecause);
    }

    /** What the entry scan found, gathered in one place so both passes can fill it in. */
    private static final class Match {

        private Entry entry;
        private String biomeId;
        private boolean ambiguous;

        /** Where the accepted entry generates, when its position was reproduced exactly. */
        private GenerationPoint point;

        /** Some biome that was seen and not accepted, for the debug readout. */
        private String lastSeen;

        /** Set when an exact entry was rejected by something other than its biome. */
        private String rejectedBecause;

        void accept(Entry candidate, String biome) {
            if (entry == null) {
                entry = candidate;
                biomeId = biome;
            } else if (entry != candidate) {
                ambiguous = true;
            }
        }

        void accept(Entry candidate, String biome, GenerationPoint exactPoint) {
            if (entry == null) {
                point = exactPoint;
            }
            accept(candidate, biome);
        }
    }

    /**
     * Reproduces {@code SinglePieceStructure.findGenerationPoint} followed by
     * {@code Structure.isValidBiome}, exactly.
     *
     * <p>Vanilla's condition is a conjunction - the lowest of four corner heights must reach sea
     * level <em>and</em> the biome above the chunk centre must be accepted - so the two halves may
     * be tested in either order without changing the answer. The biome goes first because it
     * rejects the great majority of candidates for one terrain column instead of five, which
     * measured four times faster over a representative area.
     */
    private static void exactlySampledBiome(BiomeWorldgenSession session, Entry entry,
                                            int chunkX, int chunkZ, Match match) {
        // ChunkPos.getMiddleBlockX/Z, the column Structure.onTopOfChunkCenter anchors on.
        int middleX = (chunkX << 4) + 8;
        int middleZ = (chunkZ << 4) + 8;

        int stubY = session.surfaceOccupiedHeight(middleX, middleZ);
        String biomeId = session.sampleBiomeIdAtQuart(middleX >> 2, stubY >> 2, middleZ >> 2);
        match.lastSeen = biomeId;
        if (!entry.biomeIds.contains(biomeId)) {
            return;
        }

        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int lowestCorner = Math.min(
                Math.min(session.surfaceOccupiedHeight(minX, minZ),
                        session.surfaceOccupiedHeight(minX, minZ + SINGLE_PIECE_DEPTH)),
                Math.min(session.surfaceOccupiedHeight(minX + SINGLE_PIECE_WIDTH, minZ),
                        session.surfaceOccupiedHeight(minX + SINGLE_PIECE_WIDTH,
                                minZ + SINGLE_PIECE_DEPTH)));
        if (lowestCorner < session.seaLevel()) {
            match.rejectedBecause = BELOW_SEA_LEVEL;
            return;
        }
        match.accept(entry, biomeId, new GenerationPoint(middleX, stubY, middleZ));
    }

    /**
     * Every biome vanilla could see for this candidate, tested against the entries whose column is
     * known but whose height is not.
     *
     * <p>The column is the chunk's centre - {@code ChunkPos.getMiddleBlockX} is
     * {@code (chunkX &lt;&lt; 4) + 8} and {@code QuartPos.fromBlock} shifts that right by two - and
     * every quart row the dimension allows is visited, so a rejection here is exhaustive rather
     * than sampled.
     */
    private static void scanBiomeColumn(BiomeWorldgenSession session, List<Entry> entries,
                                        int chunkX, int chunkZ, Match match) {
        int quartX = (chunkX << 2) + 2;
        int quartZ = (chunkZ << 2) + 2;
        // Adjacent quart rows repeat the same biome for long stretches, so the entry scan below
        // runs a handful of times per column rather than once per row.
        Set<String> seen = new HashSet<String>();

        for (int quartY = session.lowestQuartY(); quartY <= session.highestQuartY(); quartY++) {
            String biomeId = session.sampleBiomeIdAtQuart(quartX, quartY, quartZ);
            if (!seen.add(biomeId)) {
                continue;
            }
            match.lastSeen = biomeId;
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                if (entry.biomeIds.contains(biomeId)) {
                    match.accept(entry, biomeId);
                }
            }
        }
    }

    private static Map<StructureType, List<Entry>> load() {
        Map<StructureType, List<Entry>> loaded =
                new LinkedHashMap<StructureType, List<Entry>>();
        Map<String, Set<String>> tagCache = new HashMap<String, Set<String>>();

        for (StructureType type : StructureType.values()) {
            String setPath = structureSetPath(type);
            if (setPath == null) {
                continue;
            }
            JsonObject set = readJson("worldgen/structure_set/" + setPath);
            if (set == null || !set.has("structures")) {
                // Not in this version's datapack, e.g. trial chambers before 1.21.
                continue;
            }

            List<Entry> entries = new ArrayList<Entry>();
            JsonArray structures = set.getAsJsonArray("structures");
            for (int i = 0; i < structures.size(); i++) {
                JsonObject selection = structures.get(i).getAsJsonObject();
                String structureId = normalizeId(selection.get("structure").getAsString());
                int weight = selection.has("weight") ? selection.get("weight").getAsInt() : 1;
                JsonObject structure = readJson("worldgen/structure/" + pathOf(structureId));
                if (structure == null || !structure.has("biomes")) {
                    continue;
                }
                JsonElement biomeField = structure.get("biomes");
                String tag = biomeField.isJsonPrimitive()
                        && biomeField.getAsString().startsWith("#")
                        ? normalizeId(biomeField.getAsString().substring(1))
                        : null;
                Set<String> biomes = new HashSet<String>();
                collectBiomes(biomeField, biomes, tagCache, new HashSet<String>());
                if (!biomes.isEmpty()) {
                    String structureTypeId = structure.has("type")
                            ? normalizeId(structure.get("type").getAsString())
                            : null;
                    int size = structure.has("size") ? structure.get("size").getAsInt() : 0;
                    entries.add(new Entry(shortName(structureId), structureId, tag, weight,
                            Collections.unmodifiableSet(biomes),
                            positionOf(structureTypeId, size),
                            undecidableReason(structureTypeId, size)));
                }
            }
            if (!entries.isEmpty()) {
                loaded.put(type, Collections.unmodifiableList(entries));
            }
        }
        return loaded;
    }

    /** A biome set is a single entry, a list of entries, and each entry may be a nested tag. */
    private static void collectBiomes(JsonElement element, Set<String> into,
                                      Map<String, Set<String>> tagCache, Set<String> visiting) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                collectBiomes(array.get(i), into, tagCache, visiting);
            }
            return;
        }
        if (element.isJsonObject()) {
            // The long form, {"id": "...", "required": false}.
            JsonObject object = element.getAsJsonObject();
            if (object.has("id")) {
                collectBiomes(object.get("id"), into, tagCache, visiting);
            }
            return;
        }

        String value = element.getAsString();
        if (!value.startsWith("#")) {
            into.add(normalizeId(value));
            return;
        }
        into.addAll(resolveTag(value.substring(1), tagCache, visiting));
    }

    private static Set<String> resolveTag(String tagId, Map<String, Set<String>> tagCache,
                                          Set<String> visiting) {
        Set<String> cached = tagCache.get(tagId);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(tagId)) {
            // Vanilla data has no cycles, but a datapack could; refusing to recurse is cheaper
            // than a stack overflow in a worker thread.
            return Collections.emptySet();
        }

        Set<String> resolved = new HashSet<String>();
        JsonObject tag = readJson("tags/worldgen/biome/" + pathOf(tagId));
        if (tag != null && tag.has("values")) {
            collectBiomes(tag.get("values"), resolved, tagCache, visiting);
        }
        visiting.remove(tagId);

        Set<String> immutable = Collections.unmodifiableSet(resolved);
        tagCache.put(tagId, immutable);
        return immutable;
    }

    /** Reads one file out of the vanilla datapack inside the Minecraft jar. */
    private static JsonObject readJson(String dataPath) {
        java.io.InputStream in = StructureBiomeValidator.class
                .getResourceAsStream("/data/minecraft/" + dataPath + ".json");
        if (in == null) {
            return null;
        }
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            JsonElement parsed = JsonParser.parseString(new String(out.toByteArray(), "UTF-8"));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception malformed) {
            return null;
        } finally {
            try {
                in.close();
            } catch (java.io.IOException ignored) {
                // Nothing useful to do.
            }
        }
    }

    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    private static String normalizeId(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    private static String shortName(String structureId) {
        return pathOf(structureId);
    }
    //?} else {
    /*private static StructureFeature<?> featureOf(StructureType type) {
        switch (type) {
            case VILLAGE:
                return StructureFeature.VILLAGE;
            case DESERT_PYRAMID:
                return StructureFeature.DESERT_PYRAMID;
            case SHIPWRECK:
                return StructureFeature.SHIPWRECK;
            default:
                return null;
        }
    }

    /^*
     * Exactly what ChunkGenerator.createStructures does: one biome at quart coordinates
     * ((chunkX &lt;&lt; 2) + 2, 0, (chunkZ &lt;&lt; 2) + 2), then that biome's structure list. The
     * position is fixed and the height is a literal zero, so this reproduces vanilla's answer
     * rather than bounding it, and every candidate is decidable.
     ^/
    private static StructureValidation evaluate(BiomeWorldgenSession session, List<Entry> entries,
                                                int chunkX, int chunkZ) {
        String biomeId = session.sampleBiomeIdAtQuart((chunkX << 2) + 2, 0, (chunkZ << 2) + 2);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.biomeIds.contains(biomeId)) {
                // Exact in both directions here: Phase 3E-2b checked this answer against vanilla's
                // real generate() over 59,026 candidates without a single disagreement.
                return StructureValidation.exactlyCompatible(entry.name, biomeId);
            }
        }
        return StructureValidation.incompatible(biomeId);
    }

    /^*
     * Inverts 1.16.5's model once: every biome is asked which of our structures it allows, and the
     * answer is kept as biome ids so the check above can work on ids like the modern path does.
     ^/
    private static Map<StructureType, List<Entry>> load() {
        Map<StructureType, Set<String>> biomesByType =
                new LinkedHashMap<StructureType, Set<String>>();
        Registry<Biome> registry = BuiltinRegistries.BIOME;

        for (Biome biome : registry) {
            ResourceLocation id = registry.getKey(biome);
            if (id == null) {
                continue;
            }
            for (StructureType type : StructureType.values()) {
                StructureFeature<?> feature = featureOf(type);
                if (feature == null || !biome.getGenerationSettings().isValidStart(feature)) {
                    continue;
                }
                Set<String> biomes = biomesByType.get(type);
                if (biomes == null) {
                    biomes = new HashSet<String>();
                    biomesByType.put(type, biomes);
                }
                biomes.add(id.toString());
            }
        }

        Map<StructureType, List<Entry>> loaded = new LinkedHashMap<StructureType, List<Entry>>();
        for (Map.Entry<StructureType, Set<String>> entry : biomesByType.entrySet()) {
            // 1.16.5 has one StructureFeature per type - village variants are chosen inside the
            // jigsaw pool at generation time, not by a separate structure entry - so there is
            // exactly one entry and no variant to report.
            //
            // The position is EXACT for all of them, and Phase 3E-2b proved that claim end to end:
            // the sampled position is a literal, isFeatureChunk is vanilla's "return true" for
            // each of the three, and every generatePieces adds its first piece unconditionally, so
            // an accepted biome really does mean the structure generates.
            loaded.put(entry.getKey(), Collections.singletonList(
                    new Entry(null, null, null, 1,
                            Collections.unmodifiableSet(entry.getValue()), Position.EXACT, null)));
        }
        return loaded;
    }
    *///?}
}
