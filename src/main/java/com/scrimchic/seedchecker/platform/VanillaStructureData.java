package com.scrimchic.seedchecker.platform;

//? if >=1.18 {
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Stream;

import com.scrimchic.seedchecker.SeedChecker;
import com.scrimchic.seedchecker.core.util.LazyInit;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
//?}
//? if >=26.1 {
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.server.RegistryLayer;
import net.minecraft.tags.TagLoader;
//?} else if >=1.18 {
/*import net.minecraft.tags.TagLoader;*/
//?}

/**
 * The vanilla data pack, loaded as real registries, shared by every worker once loaded.
 *
 * <p>Exact jigsaw validation needs two things biome sampling never did: a genuine
 * {@code RegistryAccess} holding the template pools a jigsaw start pool is looked up in, and a
 * {@code StructureTemplateManager} to read the structure NBT its bounding box comes from. Neither
 * is part of {@code VanillaRegistries.createLookup()}, so the data pack inside the Minecraft jar is
 * loaded through {@code RegistryDataLoader} instead - no save, no server, no world.
 *
 * <h2>Lazy, once, never on the render thread</h2>
 *
 * <p>Nothing is loaded on world join. The first worker that validates a jigsaw candidate loads it,
 * inline, through {@link LazyInit}; concurrent workers are told it is still loading and move on,
 * and a failure is remembered rather than retried.
 *
 * <h2>What is shared, and what is not</h2>
 *
 * <p>Shared: the resource manager and the frozen registries. Both are read-only once built, and
 * everything reachable from a registry - {@code Structure}, {@code StructureTemplatePool},
 * {@code SinglePoolElement} - holds final fields.
 *
 * <p><strong>Not shared: the template manager.</strong> Its repository is a concurrent map, but the
 * templates it hands out are not safe to share: {@code StructureTemplate.Palette} caches blocks in
 * a plain {@code HashMap} through {@code computeIfAbsent}, on both 1.20.1 and 26.2, and a jigsaw
 * start reaches that cache while looking for its anchor. So every worker gets its own manager, and
 * with it its own templates, from {@link #newTemplateManager()}.
 *
 * <h2>Its own registry universe, deliberately contained</h2>
 *
 * <p>These registries are a second set of vanilla objects next to the ones biome tiles and the
 * desert pyramid use. Holders from the two are never compared: everything built from this class -
 * the jigsaw's noise, terrain, biome source and templates - is built from these registries alone,
 * and what leaves it is block coordinates and biome id strings. Keeping the lighter universe for
 * biome tiles is what keeps this load off world join.
 */
public final class VanillaStructureData {

    //? if >=1.18 {
    private static final LazyInit<VanillaStructureData> SHARED = new LazyInit<VanillaStructureData>(
            new LazyInit.Closer<VanillaStructureData>() {
                @Override
                public void close(VanillaStructureData data) {
                    data.resources.close();
                }
            });

    private static volatile long loadMillis = -1L;

    private final CloseableResourceManager resources;
    private final RegistryAccess.Frozen registries;
    private final RegistryAccess generationRegistries;
    private final Map<String, Structure> structuresById;

    private VanillaStructureData(CloseableResourceManager resources, RegistryAccess.Frozen registries,
                                 Map<String, Structure> structuresById) {
        this.resources = resources;
        this.registries = registries;
        this.generationRegistries = withStaticRegistries(registries);
        this.structuresById = structuresById;
    }

    /** Whether a worker is loading right now. A volatile read; safe from the render thread. */
    public static boolean isLoading() {
        return SHARED.state() == LazyInit.State.INITIALIZING;
    }

    public static LazyInit.State state() {
        return SHARED.state();
    }

    /** How long the load took, or {@code -1} before it has finished. */
    public static long loadMillis() {
        return loadMillis;
    }

    /**
     * Loads the data on this thread if nobody has started to, and otherwise returns at once.
     *
     * <p>Worker threads only: the thread that wins spends most of a second in here.
     */
    static LazyInit.State loadIfNeeded() {
        return SHARED.initializeIfNeeded(new LazyInit.Loader<VanillaStructureData>() {
            @Override
            public VanillaStructureData load() throws Exception {
                long start = System.nanoTime();
                try {
                    VanillaStructureData data = open();
                    loadMillis = (System.nanoTime() - start) / 1_000_000L;
                    SeedChecker.LOGGER.info("Vanilla structure data loaded in " + loadMillis + " ms");
                    return data;
                } catch (Exception | Error failure) {
                    // Logged here, by the one thread that ran the load; everybody else only sees
                    // the remembered FAILED state, so this is the only log line it ever produces.
                    SeedChecker.LOGGER.log(Level.WARNING,
                            "Vanilla structure data could not be loaded; jigsaw structures will "
                                    + "stay unvalidated", failure);
                    throw failure;
                }
            }
        });
    }

    /** @return the loaded data, or {@code null} unless {@link #state()} is {@code READY}. */
    static VanillaStructureData get() {
        return SHARED.getIfReady();
    }

    /** Releases the data at client shutdown. Nothing is loaded again afterwards. */
    public static void shutdown() {
        SHARED.close();
    }

    private static VanillaStructureData open() throws Exception {
        CloseableResourceManager resources = openVanillaData();
        try {
            RegistryAccess.Frozen registries = loadWorldgenRegistries(resources);
            Map<String, Structure> structures = new HashMap<String, Structure>();
            registries.lookupOrThrow(Registries.STRUCTURE).listElements().forEach(holder ->
                    structures.put(keyName(holder.key()), holder.value()));
            return new VanillaStructureData(resources, registries,
                    Collections.unmodifiableMap(structures));
        } catch (Exception | Error failure) {
            resources.close();
            throw failure;
        }
    }

    RegistryAccess.Frozen registries() {
        return registries;
    }

    /**
     * The registries a structure is generated against: the loaded worldgen registries and, on 26.x,
     * the static ones beside them.
     *
     * <p>26.2's {@code RuinedPortalPiece} asks its generation context for the block registry and its
     * {@code features_cannot_replace} tag while building its processors - which decide blocks, not
     * where the piece is. The worldgen registries alone have no block registry, so the static
     * registries are added. Their tags are whatever the game has bound, which in a world is the
     * server's; nothing here binds or replaces them.
     */
    RegistryAccess generationRegistries() {
        return generationRegistries;
    }

    /** @return the structure with that id, e.g. {@code minecraft:ancient_city}, or {@code null}. */
    Structure structure(String structureId) {
        return structuresById.get(structureId);
    }

    /**
     * A template manager of the calling worker's own.
     *
     * <p>Its constructor insists on a {@code LevelStorageAccess}, and reads exactly one thing from
     * it - the path of the save's generated structures, which vanilla structures never live in. It
     * keeps the path, not the access, on both 1.20.1 and 26.2. So a throwaway access over a
     * temporary directory is opened, used for that one read, closed, and the directory deleted
     * before this method returns; a lookup that later tries that path finds nothing there and falls
     * through to the data pack, which is where the templates are.
     */
    StructureTemplateManager newTemplateManager() throws IOException {
        Path temporary = Files.createTempDirectory("seedchecker-templates");
        try {
            LevelStorageSource.LevelStorageAccess access =
                    LevelStorageSource.createDefault(temporary).createAccess("templates");
            try {
                return new StructureTemplateManager(resources, access, DataFixers.getDataFixer(),
                        blockLookup());
            } finally {
                access.close();
            }
        } finally {
            deleteRecursively(temporary);
        }
    }

    private static void deleteRecursively(Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                        throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException failure)
                        throws IOException {
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // A leftover empty temp directory is not worth failing a validation over.
        }
    }

    /** The whole vanilla data pack as a resource manager, with no world and no server. */
    private static CloseableResourceManager openVanillaData() {
        PackRepository repository = vanillaRepository();
        repository.reload();
        repository.setSelected(repository.getAvailableIds());
        return new MultiPackResourceManager(PackType.SERVER_DATA, repository.openAllSelected());
    }

    private static String keyName(ResourceKey<?> key) {
        //? if >=26.1 {
        return key.identifier().toString();
        //?} else {
        /*return key.location().toString();*/
        //?}
    }

    //? if >=26.1 {
    private static RegistryAccess withStaticRegistries(RegistryAccess.Frozen worldgen) {
        return new RegistryAccess.ImmutableRegistryAccess(Stream.concat(
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).registries(),
                worldgen.registries())).freeze();
    }

    /**
     * Tests only. Binds the vanilla data pack's tags onto the static registries, which joining a world
     * does in game and a bare test JVM never does. Production must not call this: in a world it
     * would replace the tags the server sent.
     */
    void bindStaticTagsLikeAWorldDoes() {
        TagLoader.loadTagsForExistingRegistries(resources,
                RegistryLayer.createRegistryAccess().getLayer(RegistryLayer.STATIC))
                .forEach(Registry.PendingTags::apply);
    }

    private static HolderGetter<Block> blockLookup() {
        // 26.x made Registry a HolderLookup.RegistryLookup itself.
        return BuiltInRegistries.BLOCK;
    }

    private static PackRepository vanillaRepository() {
        return ServerPacksSource.createVanillaTrustedRepository();
    }

    /**
     * 26.x validates block tags while it loads worldgen data, so the static tags are bound first -
     * without that the load dies on {@code Missing tag: 'minecraft:features_cannot_replace'}. This
     * is vanilla's own sequence from {@code WorldLoader}, minus the world, run on this thread.
     */
    private static RegistryAccess.Frozen loadWorldgenRegistries(CloseableResourceManager resources) {
        LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
        List<Registry.PendingTags<?>> pendingTags = TagLoader.loadTagsForExistingRegistries(
                resources, layers.getLayer(RegistryLayer.STATIC));
        List<HolderLookup.RegistryLookup<?>> lookups = TagLoader.buildUpdatedLookups(
                layers.getAccessForLoading(RegistryLayer.WORLDGEN), pendingTags);
        return RegistryDataLoader.load(resources, lookups, RegistryDataLoader.WORLDGEN_REGISTRIES,
                Runnable::run).join();
    }
    //?} else {
    /*private static RegistryAccess withStaticRegistries(RegistryAccess.Frozen worldgen) {
        // 1.20.1's structures never ask their context for a static registry.
        return worldgen;
    }

    /^*
     * Tests only. Binds the data pack's biome tags onto the loaded biome registry, as a server's
     * reload does and this loader does not, so a test can run vanilla's own createStructures - whose
     * biome predicate is the structure's tag - as an oracle. Production never reads a biome tag
     * through these registries, and must not call this.
     ^/
    void bindBiomeTagsLikeAWorldDoes() {
        net.minecraft.core.Registry<net.minecraft.world.level.biome.Biome> biomes =
                registries.registryOrThrow(Registries.BIOME);
        Map<net.minecraft.resources.ResourceLocation,
                java.util.Collection<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>> loaded =
                new TagLoader<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>(
                        id -> biomes.getHolder(ResourceKey.create(Registries.BIOME, id)),
                        net.minecraft.tags.TagManager.getTagDir(Registries.BIOME))
                        .loadAndBuild(resources);
        Map<net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome>,
                List<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>> bound =
                new HashMap<net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome>,
                        List<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>>();
        for (Map.Entry<net.minecraft.resources.ResourceLocation,
                java.util.Collection<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>> tag
                : loaded.entrySet()) {
            bound.put(net.minecraft.tags.TagKey.create(Registries.BIOME, tag.getKey()),
                    new java.util.ArrayList<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>(
                            tag.getValue()));
        }
        biomes.bindTags(bound);
    }

    private static HolderGetter<Block> blockLookup() {
        return BuiltInRegistries.BLOCK.asLookup();
    }

    private static PackRepository vanillaRepository() {
        return new PackRepository(new ServerPacksSource());
    }

    private static RegistryAccess.Frozen loadWorldgenRegistries(CloseableResourceManager resources) {
        return RegistryDataLoader.load(resources,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                RegistryDataLoader.WORLDGEN_REGISTRIES);
    }
    *///?}
    //?} else {
    /*private VanillaStructureData() {
    }

    /^* 1.16.5 needs none of this: its structure validity is exact without the data pack. ^/
    public static boolean isLoading() {
        return false;
    }

    public static long loadMillis() {
        return -1L;
    }

    public static void shutdown() {
    }
    *///?}
}
