package com.scrimchic.seedchecker.platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * <p>So the whole question is where the stub sits, and that splits the supported structures in two.
 *
 * <p><strong>Knowable position.</strong> {@code SinglePieceStructure} (desert pyramid) and
 * {@code ShipwreckStructure} both return {@code Structure.onTopOfChunkCenter}, which builds the
 * stub at {@code ChunkPos.getMiddleBlockX/Z} - the chunk's own centre column, fixed - with the
 * height from {@code ChunkGenerator.getFirstOccupiedHeight}. Only the height is unknown, and it is
 * bounded: {@code getBaseHeight} walks a column clamped to the dimension and falls back to its
 * minimum, so the stub height never leaves the range {@link BiomeWorldgenSession#lowestQuartY()}
 * to {@link BiomeWorldgenSession#highestQuartY()} covers. That range is enumerated, every quart row
 * of it. If no row in the column is accepted then no terrain can produce one, and the candidate is
 * rejected on proof rather than on a sample that happened to miss.
 *
 * <p><strong>Unknowable position.</strong> {@code JigsawStructure} (village, ancient city, trial
 * chamber) hands the stub to {@code JigsawPlacement.addPieces}, and the position that comes back is
 * the <em>centre of the start piece's bounding box</em>: {@code (bb.maxX + bb.minX) / 2} and the
 * same in Z. That box depends on which template the start pool drew, on the rotation drawn with it
 * and on that template's size in the structure NBT - so the sampled column is not the candidate
 * chunk's column, and can be tens of blocks outside it. Ancient city, whose start pool re-anchors
 * on a named {@code city_anchor} jigsaw, moves furthest of all.
 *
 * <p>Their <em>height</em> is another matter and is worth recording, because it is the exact part:
 * with {@code project_start_to_heightmap} absent the algebra in {@code addPieces} cancels out to
 * the raw {@code start_height} sample, which is a constant -27 for the ancient city and a uniform
 * draw over -40 to -20 for the trial chamber. Village projects onto {@code WORLD_SURFACE_WG} and so
 * is terrain-derived. But an exact height with an unknown column decides nothing, so all three
 * report {@code UNKNOWN} and are drawn. Computing the start piece geometry is Phase 3E-2 work,
 * alongside the heightmap.
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
            if (entries.get(i).undecidable != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Decides whether a grid candidate could pass vanilla's biome check.
     *
     * <p>Samples a whole biome column, so it must not be called on the render thread.
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

    /** One structure entry inside a structure set, with the biomes it accepts. */
    private static final class Entry {

        private final String name;
        private final String biomeTag;
        private final Set<String> biomeIds;

        /**
         * Why vanilla's sampled position cannot be reproduced for this entry, or {@code null} when
         * it can and the entry may therefore reject a candidate.
         */
        private final String undecidable;

        Entry(String name, String biomeTag, Set<String> biomeIds, String undecidable) {
            this.name = name;
            this.biomeTag = biomeTag;
            this.biomeIds = biomeIds;
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
     * Structure types whose {@code findGenerationPoint} was read in the bytecode and found to place
     * the stub on the candidate chunk's own centre column.
     *
     * <p>{@code SinglePieceStructure.findGenerationPoint} and
     * {@code ShipwreckStructure.findGenerationPoint} both end in
     * {@code Structure.onTopOfChunkCenter}, which is the only reason those two can be decided.
     * Anything absent from this set is treated as undecidable, so a structure type added by a later
     * version is shown rather than filtered on an assumption.
     */
    private static final Set<String> CHUNK_CENTRE_COLUMN = new HashSet<String>(java.util.Arrays.asList(
            "minecraft:desert_pyramid", "minecraft:shipwreck"));

    private static final String JIGSAW_REASON =
            "vanilla samples at the start piece's bounding box centre, not at the chunk";

    private static final String UNVERIFIED_REASON =
            "the position this structure type generates at has not been verified";

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

    private static String undecidableReason(String structureTypeId) {
        if (CHUNK_CENTRE_COLUMN.contains(structureTypeId)) {
            return null;
        }
        return "minecraft:jigsaw".equals(structureTypeId) ? JIGSAW_REASON : UNVERIFIED_REASON;
    }

    /**
     * Every biome vanilla could see for this candidate, tested against the entries that know where
     * vanilla looks.
     *
     * <p>The column is the chunk's centre - {@code ChunkPos.getMiddleBlockX} is
     * {@code (chunkX &lt;&lt; 4) + 8} and {@code QuartPos.fromBlock} shifts that right by two - and
     * every quart row the dimension allows is visited, so the rejection below is exhaustive rather
     * than sampled.
     */
    private static StructureValidation evaluate(BiomeWorldgenSession session, List<Entry> entries,
                                                int chunkX, int chunkZ) {
        String undecidable = null;
        boolean anyDecidable = false;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).undecidable == null) {
                anyDecidable = true;
            } else {
                undecidable = entries.get(i).undecidable;
            }
        }
        if (!anyDecidable) {
            return StructureValidation.unknown(undecidable);
        }

        int quartX = (chunkX << 2) + 2;
        int quartZ = (chunkZ << 2) + 2;

        Entry matched = null;
        boolean ambiguous = false;
        String matchedBiome = null;
        String lastSeen = null;
        // Adjacent quart rows repeat the same biome for long stretches, so the entry scan below
        // runs a handful of times per column rather than once per row.
        Set<String> seen = new HashSet<String>();

        for (int quartY = session.lowestQuartY(); quartY <= session.highestQuartY(); quartY++) {
            String biomeId = session.sampleBiomeIdAtQuart(quartX, quartY, quartZ);
            if (!seen.add(biomeId)) {
                continue;
            }
            lastSeen = biomeId;
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                if (entry.undecidable != null || !entry.biomeIds.contains(biomeId)) {
                    continue;
                }
                if (matched == null) {
                    matched = entry;
                    matchedBiome = biomeId;
                } else if (matched != entry) {
                    ambiguous = true;
                }
            }
        }

        if (matched != null) {
            // The variant is only reported when exactly one entry can explain the candidate. When
            // several could, vanilla picks between them with a seeded weighted draw that this phase
            // does not reproduce, so claiming one would be a guess.
            return StructureValidation.compatible(ambiguous ? null : matched.name, matchedBiome);
        }
        if (undecidable != null) {
            // Some entry of this set might still accept it somewhere this cannot look.
            return StructureValidation.unknown(undecidable);
        }
        return StructureValidation.incompatible(lastSeen);
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
                String structureId = selection.get("structure").getAsString();
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
                    entries.add(new Entry(shortName(structureId), tag,
                            Collections.unmodifiableSet(biomes),
                            undecidableReason(structureTypeId)));
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
                return StructureValidation.compatible(entry.name, biomeId);
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
            // exactly one entry and no variant to report. Nothing is undecidable here.
            loaded.put(entry.getKey(), Collections.singletonList(
                    new Entry(null, null, Collections.unmodifiableSet(entry.getValue()), null)));
        }
        return loaded;
    }
    *///?}
}
