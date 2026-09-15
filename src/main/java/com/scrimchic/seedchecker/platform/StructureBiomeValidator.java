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
import net.minecraft.world.level.levelgen.StructureSettings;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.StructureStart;*/
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
 * <p><strong>Exact position: the surface structures.</strong> The desert pyramid and the jungle
 * temple ({@code SinglePieceStructure}), the igloo and the swamp hut all anchor on
 * {@code Structure.onTopOfChunkCenter} over {@code WORLD_SURFACE_WG}, so the column is
 * {@code ChunkPos.getMiddleBlockX/Z} and the height is {@code getFirstOccupiedHeight}, which this
 * session can compute. The two single pieces' {@code findGenerationPoint} additionally refuses the
 * structure outright when the lowest of four footprint corner heights falls below sea level - a
 * condition no amount of biome enumeration models. Both halves are reproduced, so both
 * {@code COMPATIBLE} and {@code INCOMPATIBLE} are exact.
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
 * can produce one, so a rejection is still proof; an acceptance is a superset. The ocean ruins and
 * the buried treasure anchor on the same column over {@code OCEAN_FLOOR_WG} and are bounded the
 * same way.
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
 * <p>The mineshaft goes the same way though it is no jigsaw: its stub sits at the chunk's middle X,
 * minimum Z and a height its own pieces decide - moved below sea level at random, or for the
 * badlands variant onto the terrain - and its {@code findGenerationPoint} dereferences no biome tag,
 * so the loaded data pack answers it on 1.20.1 exactly as on 26.2. So do the woodland mansion
 * (a random rotation, then the lowest of four terrain corners, refused below y 60) and the seven
 * ruined portal entries (a weighted setup, a template, a rotation and mirror, and a height from the
 * terrain column), whose {@code findGenerationPoint} never refuses at all.
 *
 * <p><strong>Exact position with an area test: the ocean monument.</strong>
 * {@code OceanMonumentStructure.findGenerationPoint} first requires every biome
 * {@code BiomeSource.getBiomesWithin} returns around (chunk minimum + 9, sea level, chunk minimum +
 * 9) at radius 29 to be in {@code #required_ocean_monument_surrounding}, then anchors on the chunk
 * centre over {@code OCEAN_FLOOR_WG}. The area test reads the tag through {@code Holder.is}, which
 * 1.20.1's loaded registries cannot answer, so it is reproduced here - every quart of the box, over
 * the resolved tag - and the anchor too, as for the surface structures.
 *
 * <h2>The nether (Phase 3H-2)</h2>
 *
 * <p>The session carries its dimension, so every position model above runs on the nether's own
 * biome source, terrain and level height when the candidate is a nether one. What is specific to
 * the nether is which entries can start, and three of its structures:
 *
 * <p><strong>The fortress and the bastion remnant: one set, one draw.</strong> From 1.18 both are
 * entries of {@code nether_complexes} - fortress weight 2, bastion weight 3 - and
 * {@code createStructures} tries them in its weighted order, falling back to the other entry when
 * the first one's biome refuses. The fortress's {@code findGenerationPoint} is (chunk minimum, 64,
 * chunk minimum) and never refuses; the bastion is a jigsaw at a literal y 33. Both types load the
 * whole set and evaluate it identically, and each accepts only when the entry vanilla builds is its
 * own - so a chunk is never both. 1.16.5 has no such set: the shared grid is split by the two
 * {@code isFeatureChunk} overrides on the random {@code getPotentialFeatureChunk} has just used, the
 * fortress keeping {@code nextInt(5) < 2}, with no fallback, reproduced by
 * {@code featureChunkRefusal}.
 *
 * <p><strong>The nether fossil.</strong> Its {@code findGenerationPoint} draws a column inside the
 * chunk and a height, and walks the terrain column down to the first air above soul sand or a
 * sturdy top face, refusing at sea level: vanilla's own, through the loaded data pack, like the
 * mineshaft. Because the nether's biome does not depend on height, a chunk none of whose sixteen
 * quart columns carries an accepted biome is refused before that walk; see
 * {@link BiomeWorldgenSession#isBiomeColumnConstant()}.
 *
 * <p><strong>The ruined portal</strong> needs nothing of its own: the nether entry is the seventh of
 * the same set. An entry none of whose biomes the dimension's source can return is skipped, so the
 * nether evaluates one generation point per candidate, not seven, and the overworld evaluates none
 * for the nether entry.
 *
 * <h2>The End (Phase 3H-3)</h2>
 *
 * <p>One structure on every target: vanilla's registries let nothing but the end city start in an
 * End biome, which {@code EndStructureTest} derives from vanilla rather than assumes.
 *
 * <p>From 1.18 {@code EndCityStructure.findGenerationPoint} draws a rotation, takes the lowest of
 * four {@code WORLD_SURFACE_WG} heights of a 5 by 5 box at the chunk minimum plus 7 - which corners,
 * the rotation decides - refuses below y 60, and puts the stub on that lowest height at the chunk
 * minimum plus 7. Vanilla's own, through the loaded data pack, like the fossil: the class reads no
 * biome tag, and the End's terrain comes from a plain generator over the End's noise settings, so
 * no chunk and no generated feature enters it. Because the End's biome depends neither on height
 * nor on the column inside a chunk, a chunk whose biome refuses is refused before the four terrain
 * columns are walked, as for the fossil.
 *
 * <p>1.16.5 checks the same thing in another order and with another random: the biome at the
 * chunk's fixed quart must list the end city, and {@code EndCityFeature.isFeatureChunk} then draws
 * its rotation from {@code new Random(chunkX + chunkZ * 10387313)} and refuses below y 60.
 * {@code featureChunkRefusal} asks vanilla's own start.
 *
 * <h2>Placement comes first</h2>
 *
 * <p>Frequency reductions and exclusion zones are placement, not validation:
 * {@code StructurePlacementEngine} never offers a chunk they refuse, so this class is only ever
 * asked about chunks vanilla really lets the set try, and on 1.16.5 about chunks whose
 * {@code isFeatureChunk} override already said yes.
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
        return evaluate(session, type, entries, chunkX, chunkZ);
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
         * Exact, through vanilla's own {@code findValidGenerationPoint} over the loaded data pack:
         * every jigsaw, and the mineshaft.
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

        /**
         * For an {@link Position#EXACT} entry, the width and depth whose corners must reach sea
         * level, or an empty array when the structure has no such condition; {@code null} otherwise.
         */
        private final int[] footprint;

        /** Whether an {@link Position#EXACT} entry anchors on the ocean floor, not the surface. */
        private final boolean oceanFloor;

        /** The area every biome of which the entry requires first, or {@code null}. */
        private final Surrounding surrounding;

        /**
         * Whether a start of this entry is the structure type the set was loaded for. False only in
         * a set two types share - the fortress entry of {@code nether_complexes}, seen from the
         * bastion - where the entry still takes part in vanilla's draw but is not this type.
         */
        private final boolean owned;

        /**
         * Whether vanilla's stub for this entry always lies inside the candidate chunk's own
         * columns - the nether fossil's - so a biome no quart column of the chunk carries can
         * refuse it without computing the stub, where the biome does not depend on height.
         */
        private final boolean stubInsideChunk;

        Entry(String name, String structureId, String biomeTag, int weight, Set<String> biomeIds,
              Position position, String undecidable, int[] footprint, boolean oceanFloor,
              Surrounding surrounding, boolean owned, boolean stubInsideChunk) {
            this.name = name;
            this.structureId = structureId;
            this.biomeTag = biomeTag;
            this.weight = weight;
            this.biomeIds = biomeIds;
            this.position = position;
            this.undecidable = undecidable;
            this.footprint = footprint;
            this.oceanFloor = oceanFloor;
            this.surrounding = surrounding;
            this.owned = owned;
            this.stubInsideChunk = stubInsideChunk;
        }
    }

    /**
     * Whether a start of that entry is this type. False only for the other type's entry of a
     * shared set - the fortress in the bastion's {@code nether_complexes}, and the reverse.
     */
    public static boolean ownsVariant(StructureType type, String variantName) {
        Entry entry = entryOf(type, variantName);
        return entry != null && entry.owned;
    }

    /** A biome test over a whole box of quarts around the candidate, before its own position. */
    private static final class Surrounding {

        private final int radius;
        private final Set<String> biomeIds;

        Surrounding(int radius, Set<String> biomeIds) {
            this.radius = radius;
            this.biomeIds = biomeIds;
        }
    }

    /**
     * The biomes an entry requires all around it, e.g. the monument's ocean and river set; empty
     * for an entry with no such test.
     */
    public static Set<String> surroundingBiomes(StructureType type, String variantName) {
        Entry entry = entryOf(type, variantName);
        return entry == null || entry.surrounding == null
                ? Collections.<String>emptySet() : entry.surrounding.biomeIds;
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
     * The full id of an entry vanilla assembles through the jigsaw system - what geometry needs to
     * rebuild exactly the structure validation found - or {@code null} for any other entry.
     */
    static String jigsawStructureId(StructureType type, String variantName) {
        Entry entry = entryOf(type, variantName);
        return entry != null && entry.position == Position.JIGSAW ? entry.structureId : null;
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
    private static final int[] NO_FOOTPRINT = new int[0];

    /**
     * The structure types whose whole generation point is reproduced here, keyed to the footprint
     * whose corners must reach sea level, or to no footprint.
     *
     * <p>{@code SinglePieceStructure.findGenerationPoint} - the desert pyramid at 21 by 21 and the
     * jungle temple at 12 by 15, from their calls to super - rejects unless the lowest of four
     * corner heights reaches sea level, then anchors on the chunk centre at
     * {@code getFirstOccupiedHeight(WORLD_SURFACE_WG)}; {@code IglooStructure} and
     * {@code SwampHutStructure} do only the second half. Reproduced in {@link #exactlySampledBiome},
     * and checked against vanilla's own {@code findValidGenerationPoint} in
     * {@code StructureBiomeValidatorTest}.
     */
    private static final Map<String, int[]> SURFACE_ANCHORED = surfaceAnchored();

    private static Map<String, int[]> surfaceAnchored() {
        Map<String, int[]> anchored = new HashMap<String, int[]>();
        anchored.put("minecraft:desert_pyramid", new int[] {21, 21});
        anchored.put("minecraft:jungle_temple", new int[] {12, 15});
        anchored.put("minecraft:igloo", NO_FOOTPRINT);
        anchored.put("minecraft:swamp_hut", NO_FOOTPRINT);
        return Collections.unmodifiableMap(anchored);
    }

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
     *
     * <p>{@code OceanRuinStructure} and {@code BuriedTreasureStructure} anchor the same way on
     * {@code OCEAN_FLOOR_WG} and stay in the same superset, for the same reason.
     */
    private static final Set<String> CHUNK_CENTRE_COLUMN = new HashSet<String>(java.util.Arrays.asList(
            "minecraft:shipwreck", "minecraft:ocean_ruin", "minecraft:buried_treasure"));

    /**
     * Structure types that are not jigsaws but whose generation point is still vanilla's own,
     * through the same loaded data pack.
     *
     * <p>{@code MineshaftStructure.findGenerationPoint} assembles every piece to decide the stub's
     * height, and for the badlands variant samples the terrain as well.
     * {@code WoodlandMansionStructure} draws a rotation and refuses when the lowest of its terrain
     * corners is below y 60; {@code RuinedPortalStructure} draws a setup, a template, a rotation and a
     * mirror, and takes its height from the terrain column and {@code findSuitableY}. None of the
     * three reads a biome tag, which is what makes them safe on 1.20.1's unbound registries.
     */
    private static final Set<String> VANILLA_GENERATION_POINT = new HashSet<String>(
            java.util.Arrays.asList("minecraft:mineshaft", "minecraft:woodland_mansion",
                    "minecraft:ruined_portal", "minecraft:fortress", "minecraft:nether_fossil",
                    "minecraft:end_city"));

    /**
     * Structure types whose stub column is always one of the candidate chunk's own.
     *
     * <p>{@code NetherFossilStructure}: x and z are the chunk minimum plus {@code nextInt(16)}.
     * {@code EndCityStructure}: {@code getLowestYIn5by5BoxOffset7Blocks} puts the stub at the chunk
     * minimum plus 7 on both axes, whatever the rotation. Height and terrain are vanilla's for both.
     */
    private static final Set<String> STUB_INSIDE_CHUNK = new HashSet<String>(
            java.util.Arrays.asList("minecraft:nether_fossil", "minecraft:end_city"));

    private static final String NOT_IN_DIMENSION = "none of its biomes exist in this dimension";

    /**
     * {@code OceanMonumentStructure}: reproduced here, see the class comment. Radius and tag are
     * literals of its {@code findGenerationPoint}, and {@code StructureBiomeValidatorTest} holds
     * the reproduction to vanilla's own on 26.2, where the tag is bound.
     */
    private static final String OCEAN_MONUMENT = "minecraft:ocean_monument";
    private static final int MONUMENT_SURROUNDING_RADIUS = 29;
    private static final String MONUMENT_SURROUNDING_TAG =
            "minecraft:required_ocean_monument_surrounding";
    private static final String SURROUNDING_REFUSED =
            "a biome within 29 blocks is not ocean or river";

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
            case JUNGLE_TEMPLE:
                return "jungle_temples";
            case SWAMP_HUT:
                return "swamp_huts";
            case IGLOO:
                return "igloos";
            case PILLAGER_OUTPOST:
                return "pillager_outposts";
            case OCEAN_RUIN:
                return "ocean_ruins";
            case BURIED_TREASURE:
                return "buried_treasures";
            case MINESHAFT:
                return "mineshafts";
            case TRAIL_RUINS:
                return "trail_ruins";
            case OCEAN_MONUMENT:
                return "ocean_monuments";
            case WOODLAND_MANSION:
                return "woodland_mansions";
            case RUINED_PORTAL:
                return "ruined_portals";
            case NETHER_FORTRESS:
            case BASTION_REMNANT:
                return "nether_complexes";
            case NETHER_FOSSIL:
                return "nether_fossils";
            case END_CITY:
                return "end_cities";
            default:
                return null;
        }
    }

    /**
     * For a type that shares its structure set with another, the one entry that is this type;
     * {@code null} where every entry of the set is. {@code NetherStructureTest} holds the shared set
     * to exactly these two entries.
     */
    private static String ownedStructureId(StructureType type) {
        switch (type) {
            case NETHER_FORTRESS:
                return "minecraft:fortress";
            case BASTION_REMNANT:
                return "minecraft:bastion_remnant";
            default:
                return null;
        }
    }

    private static Position positionOf(String structureTypeId, int jigsawSize) {
        if (SURFACE_ANCHORED.containsKey(structureTypeId) || OCEAN_MONUMENT.equals(structureTypeId)) {
            return Position.EXACT;
        }
        if ("minecraft:jigsaw".equals(structureTypeId)) {
            return jigsawSize > 0 ? Position.JIGSAW : Position.UNKNOWN;
        }
        if (VANILLA_GENERATION_POINT.contains(structureTypeId)) {
            return Position.JIGSAW;
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
     * <p>An entry none of whose biomes the overworld's source can return - the ruined portal set's
     * nether entry - is not attempted. Vanilla does attempt it, since {@code createStructures} draws
     * over the whole list, but its biome test then refuses at whatever position comes back, the
     * attempt has a random of its own, and the draw that ordered the entries is already made; so
     * skipping it changes the cost and nothing else.
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

        Set<String> possibleBiomes = session.possibleBiomeIds();
        boolean attempted = false;
        String firstBiome = null;
        for (int i = 0; i < order.length; i++) {
            Entry entry = entries.get(order[i]);
            if (Collections.disjoint(entry.biomeIds, possibleBiomes)) {
                continue;
            }
            attempted = true;
            if (entry.stubInsideChunk && session.isBiomeColumnConstant()) {
                String refusedBiome = biomeNoChunkColumnAccepts(session, entry, chunkX, chunkZ);
                if (refusedBiome != null) {
                    // Wherever in the chunk the stub lands, its biome is one of these and refused:
                    // vanilla's attempt fails, exactly as if the stub had been computed.
                    if (firstBiome == null) {
                        firstBiome = refusedBiome;
                    }
                    continue;
                }
            }
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
                if (!entry.owned) {
                    // Vanilla stops here and builds the other type of the shared set.
                    return StructureValidation.incompatible(biomeId,
                            "vanilla builds " + entry.name + " here");
                }
                return StructureValidation.exactlyCompatible(entry.name, biomeId, point);
            }
        }
        if (!attempted) {
            return StructureValidation.incompatible(null, NOT_IN_DIMENSION);
        }
        return firstBiome == null
                ? StructureValidation.incompatible(null, NO_START_PIECE)
                : StructureValidation.incompatible(firstBiome);
    }

    /**
     * For an entry whose stub stays inside the chunk's columns, on a biome source that ignores
     * height: every quart column the chunk's blocks fall in, from {@code chunkX * 4} to
     * {@code chunkX * 4 + 3} on each axis.
     *
     * @return a refused biome of the chunk when none of the sixteen is accepted, or {@code null}
     *         when some column accepts and the stub has to be computed
     */
    private static String biomeNoChunkColumnAccepts(BiomeWorldgenSession session, Entry entry,
                                                    int chunkX, int chunkZ) {
        int quartY = session.seaLevel() >> 2;
        String seen = null;
        for (int dz = 0; dz < 4; dz++) {
            for (int dx = 0; dx < 4; dx++) {
                String biomeId = session.sampleBiomeIdAtQuart((chunkX << 2) + dx, quartY,
                        (chunkZ << 2) + dz);
                if (entry.biomeIds.contains(biomeId)) {
                    return null;
                }
                seen = biomeId;
            }
        }
        return seen;
    }

    /**
     * Runs whichever of the three position models each entry of the set supports, and combines
     * them.
     *
     * <p>A set may mix them: the shipwrecks set holds one entry this phase reproduces exactly and
     * one it only bounds. Vanilla tries a set's entries until one generates, so any entry
     * accepting is a yes, and only a set where <em>every</em> entry was decided and refused is a
     * no. The type is not needed here; 1.16.5's counterpart needs it.
     */
    private static StructureValidation evaluate(BiomeWorldgenSession session, StructureType type,
                                                List<Entry> entries, int chunkX, int chunkZ) {
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
     * Reproduces {@code SinglePieceStructure.findGenerationPoint}, or for a structure with no
     * footprint the {@code onTopOfChunkCenter} it ends in, followed by
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

        // Every condition is part of one conjunction, so they are tested cheapest first: one
        // biome sample of the area, then the anchor column, then the rest of the area.
        if (entry.surrounding != null
                && !surroundingSampleAccepted(session, entry.surrounding, chunkX, chunkZ, match,
                        true)) {
            return;
        }
        int stubY = entry.oceanFloor
                ? session.oceanFloorOccupiedHeight(middleX, middleZ)
                : session.surfaceOccupiedHeight(middleX, middleZ);
        String biomeId = session.sampleBiomeIdAtQuart(middleX >> 2, stubY >> 2, middleZ >> 2);
        match.lastSeen = biomeId;
        if (!entry.biomeIds.contains(biomeId)) {
            return;
        }

        if (entry.footprint != null && entry.footprint.length == 2) {
            // Structure.getLowestY: the corners of width by depth from the chunk's minimum corner.
            int width = entry.footprint[0];
            int depth = entry.footprint[1];
            int minX = chunkX << 4;
            int minZ = chunkZ << 4;
            int lowestCorner = Math.min(
                    Math.min(session.surfaceOccupiedHeight(minX, minZ),
                            session.surfaceOccupiedHeight(minX, minZ + depth)),
                    Math.min(session.surfaceOccupiedHeight(minX + width, minZ),
                            session.surfaceOccupiedHeight(minX + width, minZ + depth)));
            if (lowestCorner < session.seaLevel()) {
                match.rejectedBecause = BELOW_SEA_LEVEL;
                return;
            }
        }
        if (entry.surrounding != null
                && !surroundingSampleAccepted(session, entry.surrounding, chunkX, chunkZ, match,
                        false)) {
            return;
        }
        match.accept(entry, biomeId, new GenerationPoint(middleX, stubY, middleZ));
    }

    /**
     * {@code BiomeSource.getBiomesWithin(x, seaLevel, z, radius, sampler)} tested against a biome
     * set, where x and z are the chunk's minimum plus 9: every quart from
     * {@code QuartPos.fromBlock(c - radius)} to {@code QuartPos.fromBlock(c + radius)} inclusive,
     * on all three axes. Vanilla collects the set first; asking "all in the set" of each sample
     * with an early exit is the same answer.
     *
     * @param centreOnly test only the quart holding the centre - one of the box's own samples, so
     *                   a refusal there is already vanilla's refusal
     */
    private static boolean surroundingSampleAccepted(BiomeWorldgenSession session,
                                                     Surrounding surrounding, int chunkX,
                                                     int chunkZ, Match match, boolean centreOnly) {
        int x = (chunkX << 4) + 9;
        int y = session.seaLevel();
        int z = (chunkZ << 4) + 9;
        int radius = surrounding.radius;
        if (centreOnly) {
            return surroundingAccepts(surrounding, session.sampleBiomeIdAtQuart(x >> 2, y >> 2,
                    z >> 2), match);
        }
        for (int quartY = (y - radius) >> 2; quartY <= (y + radius) >> 2; quartY++) {
            for (int quartX = (x - radius) >> 2; quartX <= (x + radius) >> 2; quartX++) {
                for (int quartZ = (z - radius) >> 2; quartZ <= (z + radius) >> 2; quartZ++) {
                    if (!surroundingAccepts(surrounding,
                            session.sampleBiomeIdAtQuart(quartX, quartY, quartZ), match)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean surroundingAccepts(Surrounding surrounding, String biomeId, Match match) {
        if (surrounding.biomeIds.contains(biomeId)) {
            return true;
        }
        match.lastSeen = biomeId;
        match.rejectedBecause = SURROUNDING_REFUSED;
        return false;
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
            String owner = ownedStructureId(type);
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
                            undecidableReason(structureTypeId, size),
                            SURFACE_ANCHORED.get(structureTypeId),
                            OCEAN_MONUMENT.equals(structureTypeId),
                            OCEAN_MONUMENT.equals(structureTypeId)
                                    ? new Surrounding(MONUMENT_SURROUNDING_RADIUS, resolveTag(
                                            MONUMENT_SURROUNDING_TAG, tagCache, new HashSet<String>()))
                                    : null,
                            owner == null || owner.equals(structureId),
                            STUB_INSIDE_CHUNK.contains(structureTypeId)));
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

    /**
     * A file out of the vanilla data pack, for other structure data this package reads the same way
     * - the stronghold set's ring placement.
     *
     * @return the parsed object, or {@code null} when the file is absent or malformed
     */
    static JsonObject vanillaDataJson(String dataPath) {
        return readJson(dataPath);
    }

    /** The biome ids a data pack biome field names - a biome, a list, or a tag, recursively. */
    static Set<String> resolveBiomes(JsonElement element) {
        Set<String> biomes = new HashSet<String>();
        collectBiomes(element, biomes, new HashMap<String, Set<String>>(), new HashSet<String>());
        return Collections.unmodifiableSet(biomes);
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
            case JUNGLE_TEMPLE:
                return StructureFeature.JUNGLE_TEMPLE;
            case SWAMP_HUT:
                return StructureFeature.SWAMP_HUT;
            case IGLOO:
                return StructureFeature.IGLOO;
            case PILLAGER_OUTPOST:
                return StructureFeature.PILLAGER_OUTPOST;
            case OCEAN_RUIN:
                return StructureFeature.OCEAN_RUIN;
            case BURIED_TREASURE:
                return StructureFeature.BURIED_TREASURE;
            case MINESHAFT:
                return StructureFeature.MINESHAFT;
            case OCEAN_MONUMENT:
                return StructureFeature.OCEAN_MONUMENT;
            case WOODLAND_MANSION:
                return StructureFeature.WOODLAND_MANSION;
            case RUINED_PORTAL:
                return StructureFeature.RUINED_PORTAL;
            case NETHER_FORTRESS:
                return StructureFeature.NETHER_BRIDGE;
            case BASTION_REMNANT:
                return StructureFeature.BASTION_REMNANT;
            case NETHER_FOSSIL:
                return StructureFeature.NETHER_FOSSIL;
            case END_CITY:
                return StructureFeature.END_CITY;
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
    private static StructureValidation evaluate(BiomeWorldgenSession session, StructureType type,
                                                List<Entry> entries, int chunkX, int chunkZ) {
        String biomeId = session.sampleBiomeIdAtQuart((chunkX << 2) + 2, 0, (chunkZ << 2) + 2);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.biomeIds.contains(biomeId)) {
                // Exact in both directions here: Phase 3E-2b checked this answer against vanilla's
                // real generate() over 59,026 candidates without a single disagreement, and Phase
                // 3H-1 does the same for every structure added since.
                String refusal = featureChunkRefusal(session, type, entry, chunkX, chunkZ);
                if (refusal != null) {
                    return StructureValidation.incompatible(biomeId, refusal);
                }
                return StructureValidation.exactlyCompatible(entry.name, biomeId);
            }
        }
        return StructureValidation.incompatible(biomeId);
    }

    private static final String MONUMENT_BIOMES_REFUSED =
            "a biome within 16 blocks does not allow a monument";
    private static final String MONUMENT_SURROUNDING_REFUSED =
            "a biome within 29 blocks is not ocean or river";
    private static final String MANSION_BIOMES_REFUSED =
            "a biome within 32 blocks does not allow a mansion";
    private static final String MANSION_TERRAIN_REFUSED =
            "vanilla builds no start: the lowest corner is below y 60";

    /^*
     * The isFeatureChunk overrides that are not placement. OceanMonumentFeature and
     * WoodlandMansionFeature test every biome BiomeSource.getBiomesWithin returns around
     * (chunk * 16 + 9, sea level, chunk * 16 + 9): every quart from (c - radius) >> 2 to
     * (c + radius) >> 2 inclusive. This version's overworld source ignores the height it is
     * given, so the square alone is walked. The mansion's WoodlandMansionStart.generatePieces then
     * builds nothing when the lowest of four terrain corners, placed by a rotation drawn from the
     * start's own random, is below y 60 - asked of vanilla's own start rather than rebuilt, and only
     * for a candidate every biome test has already let through.
     *
     * @return why vanilla builds nothing here, or null when it builds the structure
     ^/
    private static String featureChunkRefusal(BiomeWorldgenSession session, StructureType type,
                                              Entry entry, int chunkX, int chunkZ) {
        if (type == StructureType.OCEAN_MONUMENT) {
            if (!allWithin(session, chunkX, chunkZ, 16, entry.biomeIds)) {
                return MONUMENT_BIOMES_REFUSED;
            }
            return allWithin(session, chunkX, chunkZ, 29, oceanOrRiver())
                    ? null : MONUMENT_SURROUNDING_REFUSED;
        }
        if (type == StructureType.WOODLAND_MANSION) {
            if (!allWithin(session, chunkX, chunkZ, 32, entry.biomeIds)) {
                return MANSION_BIOMES_REFUSED;
            }
            StructureStart<?> start = StructureGeometryGenerator.legacyStart(session,
                    StructureFeature.WOODLAND_MANSION, chunkX, chunkZ);
            return start != null && start.isValid() ? null : MANSION_TERRAIN_REFUSED;
        }
        if (type == StructureType.NETHER_FORTRESS || type == StructureType.BASTION_REMNANT) {
            boolean fortress = type == StructureType.NETHER_FORTRESS;
            return drawsFortress(session.seed(), featureOf(type), chunkX, chunkZ) == fortress
                    ? null : fortress ? DREW_BASTION : DREW_FORTRESS;
        }
        if (type == StructureType.NETHER_FOSSIL) {
            // NetherFossilFeature.FeatureStart walks the terrain column down from a random height
            // and adds no piece when it reaches sea level: vanilla's own start decides.
            StructureStart<?> start = StructureGeometryGenerator.legacyStart(session,
                    StructureFeature.NETHER_FOSSIL, chunkX, chunkZ);
            return start != null && start.isValid() ? null : FOSSIL_TERRAIN_REFUSED;
        }
        if (type == StructureType.END_CITY) {
            // EndCityFeature.isFeatureChunk: the lowest of four terrain corners, placed by a rotation
            // from its own chunk-seeded Random, must reach y 60; EndCityStart.generatePieces asks the
            // same again and adds no piece otherwise. Vanilla's own start decides.
            StructureStart<?> start = StructureGeometryGenerator.legacyStart(session,
                    StructureFeature.END_CITY, chunkX, chunkZ);
            return start != null && start.isValid() ? null : END_CITY_TERRAIN_REFUSED;
        }
        return null;
    }

    private static final String END_CITY_TERRAIN_REFUSED =
            "vanilla builds no start: the lowest corner is below y 60";

    private static final String DREW_BASTION = "vanilla drew a bastion remnant for this chunk";
    private static final String DREW_FORTRESS = "vanilla drew a fortress for this chunk";
    private static final String FOSSIL_TERRAIN_REFUSED =
            "vanilla builds no start: no floor above sea level in its column";

    /^*
     * The draw 1.16.5's NetherFortressFeature and BastionFeature split the shared grid with.
     * StructureFeature.generate hands isFeatureChunk the random getPotentialFeatureChunk has just
     * used - seeded with the region and the salt, then its two offset draws - and the fortress keeps
     * nextInt(5) &lt; 2, the bastion the rest. There is no reseed and no fallback: a bastion drawn
     * in basalt deltas, which does not list it, is simply nothing. The random is advanced by
     * vanilla's own getPotentialFeatureChunk rather than the offsets being recounted here. The
     * nether generator's grids for these two are the defaults, which NetherStructureTest checks.
     ^/
    private static boolean drawsFortress(long seed, StructureFeature<?> feature, int chunkX,
                                         int chunkZ) {
        WorldgenRandom random = new WorldgenRandom();
        feature.getPotentialFeatureChunk(StructureSettings.DEFAULTS.get(feature), seed, random,
                chunkX, chunkZ);
        return random.nextInt(5) < 2;
    }

    private static boolean allWithin(BiomeWorldgenSession session, int chunkX, int chunkZ,
                                     int radius, Set<String> accepted) {
        int blockX = chunkX * 16 + 9;
        int blockZ = chunkZ * 16 + 9;
        for (int quartX = (blockX - radius) >> 2; quartX <= (blockX + radius) >> 2; quartX++) {
            for (int quartZ = (blockZ - radius) >> 2; quartZ <= (blockZ + radius) >> 2; quartZ++) {
                if (!accepted.contains(session.sampleBiomeIdAtQuart(quartX, 0, quartZ))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Set<String> oceanOrRiver;

    /^* Every biome whose category is OCEAN or RIVER - the monument's test of the wider area. ^/
    private static synchronized Set<String> oceanOrRiver() {
        if (oceanOrRiver == null) {
            Set<String> ids = new HashSet<String>();
            Registry<Biome> registry = BuiltinRegistries.BIOME;
            for (Biome biome : registry) {
                ResourceLocation id = registry.getKey(biome);
                if (id != null && (biome.getBiomeCategory() == Biome.BiomeCategory.OCEAN
                        || biome.getBiomeCategory() == Biome.BiomeCategory.RIVER)) {
                    ids.add(id.toString());
                }
            }
            oceanOrRiver = Collections.unmodifiableSet(ids);
        }
        return oceanOrRiver;
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
            // the sampled position is a literal, and every generatePieces adds its first piece
            // unconditionally, so an accepted biome really does mean the structure generates.
            // isFeatureChunk is vanilla's "return true" for all but the pillager outpost, the
            // buried treasure and the mineshaft, whose overrides are pure placement and are applied
            // by StructurePlacementEngine before a chunk is ever offered here, and the monument and
            // the mansion, whose area tests featureChunkRefusal reproduces.
            loaded.put(entry.getKey(), Collections.singletonList(
                    new Entry(null, null, null, 1,
                            Collections.unmodifiableSet(entry.getValue()), Position.EXACT, null,
                            null, false, null, true, false)));
        }
        return loaded;
    }
    *///?}
}
