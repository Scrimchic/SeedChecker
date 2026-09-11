package com.scrimchic.seedchecker.storage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import com.scrimchic.seedchecker.world.SeedSource;
import com.scrimchic.seedchecker.world.SeedState;
import com.scrimchic.seedchecker.world.WorldIdentity;
import com.scrimchic.seedchecker.world.WorldProfile;

/**
 * Reads and writes world profiles as JSON, one directory per world.
 *
 * <pre>
 * &lt;root&gt;/worlds/&lt;storage key&gt;/profile.json
 * </pre>
 *
 * <p>Deliberately free of Minecraft types: it is handed a root {@link Path} and works with
 * {@link WorldProfile} alone, which is what lets it be exercised without a running game.
 *
 * <p>Nothing here is allowed to take the game down with it. A missing directory, an empty file,
 * hand-edited nonsense or a field from a future format all end the same way - a warning in the log
 * and a fresh profile - because losing a remembered seed is an annoyance while crashing on world
 * join is not.
 */
public final class WorldProfileStorage {

    /** Written into every file so a future format change has something to branch on. */
    static final int FORMAT_VERSION = 1;

    private static final String WORLDS_DIRECTORY = "worlds";
    private static final String PROFILE_FILE = "profile.json";
    private static final String TEMP_SUFFIX = ".tmp";

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /**
     * Resolved by name rather than through {@code SeedChecker.MOD_ID} on purpose: that class
     * implements a Fabric interface, and this package is meant to stay loadable, and testable,
     * without Minecraft or Fabric on the classpath.
     */
    private static final Logger LOGGER = Logger.getLogger("seedchecker");

    private final Path root;
    private final Gson gson;

    /** @param root the Seed Checker config directory, e.g. {@code .minecraft/config/seedchecker} */
    public WorldProfileStorage(Path root) {
        this.root = root;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public Path profilePath(WorldIdentity identity) {
        return root.resolve(WORLDS_DIRECTORY).resolve(identity.storageKey()).resolve(PROFILE_FILE);
    }

    /**
     * Loads the profile for {@code identity}, or creates a new one if there is nothing usable on
     * disk. Never returns {@code null} and never throws.
     */
    public WorldProfile loadOrCreate(WorldIdentity identity, String minecraftVersion, long now) {
        Path path = profilePath(identity);
        if (!Files.isRegularFile(path)) {
            return WorldProfile.createNew(identity, minecraftVersion, now);
        }

        StoredProfile stored;
        try {
            BufferedReader reader = Files.newBufferedReader(path, UTF_8);
            try {
                stored = gson.fromJson(reader, StoredProfile.class);
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not read " + path + ", starting a fresh profile", e);
            return WorldProfile.createNew(identity, minecraftVersion, now);
        } catch (JsonParseException e) {
            LOGGER.log(Level.WARNING, "Malformed JSON in " + path + ", starting a fresh profile", e);
            return WorldProfile.createNew(identity, minecraftVersion, now);
        }

        if (stored == null) {
            // An empty file parses to null rather than failing.
            LOGGER.warning("Empty profile at " + path + ", starting a fresh profile");
            return WorldProfile.createNew(identity, minecraftVersion, now);
        }

        return WorldProfile.restore(
                identity,
                stored.minecraftVersion == null ? minecraftVersion : stored.minecraftVersion,
                parseSeedState(stored.seedState),
                stored.seed,
                parseSeedSource(stored.seedSource),
                stored.createdAt == 0L ? now : stored.createdAt,
                stored.lastSeenAt == 0L ? now : stored.lastSeenAt);
    }

    /**
     * Writes the profile out. Failures are logged rather than thrown - a profile that could not be
     * saved must not interrupt whatever the player was doing.
     *
     * @return whether the profile reached the disk
     */
    public boolean save(WorldProfile profile) {
        Path path = profilePath(profile.identity());
        Path temporary = path.resolveSibling(PROFILE_FILE + TEMP_SUFFIX);
        try {
            Files.createDirectories(path.getParent());

            BufferedWriter writer = Files.newBufferedWriter(temporary, UTF_8);
            try {
                gson.toJson(StoredProfile.of(profile), StoredProfile.class, writer);
            } finally {
                writer.close();
            }

            // Swapped into place so an interrupted write cannot leave a half-written profile.
            try {
                Files.move(temporary, path,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not save profile to " + path, e);
            deleteQuietly(temporary);
            return false;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Nothing useful to do; the next save overwrites it anyway.
        }
    }

    /** Unknown or missing names degrade to UNKNOWN instead of throwing. */
    private static SeedState parseSeedState(String name) {
        if (name != null) {
            for (SeedState candidate : SeedState.values()) {
                if (candidate.name().equals(name)) {
                    return candidate;
                }
            }
            LOGGER.warning("Unknown seed state '" + name + "' in a profile, treating it as unknown");
        }
        return SeedState.UNKNOWN;
    }

    private static SeedSource parseSeedSource(String name) {
        if (name != null) {
            for (SeedSource candidate : SeedSource.values()) {
                if (candidate.name().equals(name)) {
                    return candidate;
                }
            }
            LOGGER.warning("Unknown seed source '" + name + "' in a profile, treating it as unknown");
        }
        return SeedSource.UNKNOWN;
    }

    /**
     * The on-disk shape, kept separate from {@link WorldProfile} so the file format and the model
     * can move independently. Gson skips fields it does not recognise, which is what makes a file
     * written by a newer Seed Checker readable here.
     */
    private static final class StoredProfile {

        int formatVersion;
        String type;
        String stableId;
        String displayName;
        String minecraftVersion;
        String seedState;
        String seedSource;
        long seed;
        long createdAt;
        long lastSeenAt;

        static StoredProfile of(WorldProfile profile) {
            StoredProfile stored = new StoredProfile();
            stored.formatVersion = FORMAT_VERSION;
            stored.type = profile.identity().type().name();
            stored.stableId = profile.identity().stableId();
            stored.displayName = profile.identity().displayName();
            stored.minecraftVersion = profile.minecraftVersion();
            stored.seedState = profile.seedState().name();
            stored.seedSource = profile.seedSource().name();
            stored.seed = profile.hasSeed() ? profile.seed() : 0L;
            stored.createdAt = profile.createdAt();
            stored.lastSeenAt = profile.lastSeenAt();
            return stored;
        }
    }
}
