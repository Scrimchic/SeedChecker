package com.scrimchic.seedchecker.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.scrimchic.seedchecker.world.SeedSource;
import com.scrimchic.seedchecker.world.SeedState;
import com.scrimchic.seedchecker.world.WorldIdentity;
import com.scrimchic.seedchecker.world.WorldProfile;

class WorldProfileStorageTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String VERSION = "1.20.1";
    private static final WorldIdentity SERVER = WorldIdentity.multiplayer("mc.example.com", "Example");

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(UTF_8));
    }

    @Test
    void aMissingFileYieldsAFreshProfile(@TempDir Path root) {
        WorldProfile profile = new WorldProfileStorage(root).loadOrCreate(SERVER, VERSION, 1000L);

        assertNotNull(profile);
        assertFalse(profile.hasSeed());
        assertEquals(1000L, profile.createdAt());
    }

    @Test
    void savingCreatesTheDirectoryTree(@TempDir Path root) {
        WorldProfileStorage storage = new WorldProfileStorage(root);
        WorldProfile profile = storage.loadOrCreate(SERVER, VERSION, 1000L);
        profile.setManualSeed(42L);

        assertTrue(storage.save(profile));
        assertTrue(Files.isRegularFile(storage.profilePath(SERVER)));
        assertTrue(storage.profilePath(SERVER).startsWith(root.resolve("worlds")));
    }

    @ParameterizedTest
    @ValueSource(longs = {
            0L,
            -1L,
            123456789L,
            -7407337299659424542L,
            Long.MAX_VALUE,
            Long.MIN_VALUE,
    })
    void seedsSurviveARoundTripExactly(long seed, @TempDir Path root) {
        WorldProfileStorage storage = new WorldProfileStorage(root);

        WorldProfile saved = storage.loadOrCreate(SERVER, VERSION, 1000L);
        saved.setManualSeed(seed);
        saved.markSeen(VERSION, 2000L);
        assertTrue(storage.save(saved));

        WorldProfile loaded = storage.loadOrCreate(SERVER, VERSION, 9999L);

        assertTrue(loaded.hasSeed());
        assertEquals(seed, loaded.seed());
        assertEquals(SeedSource.MANUAL, loaded.seedSource());
        assertEquals(SeedState.KNOWN, loaded.seedState());
        assertEquals(1000L, loaded.createdAt());
        assertEquals(2000L, loaded.lastSeenAt());
        assertEquals(SERVER, loaded.identity());
    }

    @Test
    void twoWorldsDoNotShareAFile(@TempDir Path root) {
        WorldProfileStorage storage = new WorldProfileStorage(root);
        WorldIdentity other = WorldIdentity.multiplayer("other.example.com", null);

        WorldProfile first = storage.loadOrCreate(SERVER, VERSION, 1L);
        first.setManualSeed(1L);
        storage.save(first);

        WorldProfile second = storage.loadOrCreate(other, VERSION, 1L);
        second.setManualSeed(2L);
        storage.save(second);

        assertEquals(1L, storage.loadOrCreate(SERVER, VERSION, 1L).seed());
        assertEquals(2L, storage.loadOrCreate(other, VERSION, 1L).seed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "{",
            "not json at all",
            "[1, 2, 3]",
            "{\"seed\": }",
            "{\"seed\": 1e999999}",
    })
    void brokenContentFallsBackToAFreshProfile(String content, @TempDir Path root) throws IOException {
        WorldProfileStorage storage = new WorldProfileStorage(root);
        write(storage.profilePath(SERVER), content);

        WorldProfile profile = storage.loadOrCreate(SERVER, VERSION, 1000L);

        assertNotNull(profile);
        assertFalse(profile.hasSeed());
        assertEquals(1000L, profile.createdAt());
    }

    @Test
    void fieldsFromAFutureFormatAreIgnoredRatherThanFatal(@TempDir Path root) throws IOException {
        WorldProfileStorage storage = new WorldProfileStorage(root);
        write(storage.profilePath(SERVER), "{"
                + "\"formatVersion\": 99,"
                + "\"seedState\": \"KNOWN\","
                + "\"seedSource\": \"MANUAL\","
                + "\"seed\": 777,"
                + "\"createdAt\": 5,"
                + "\"lastSeenAt\": 6,"
                + "\"markers\": [{\"x\": 1}],"
                + "\"notes\": \"something a later version added\""
                + "}");

        WorldProfile profile = storage.loadOrCreate(SERVER, VERSION, 1000L);

        assertEquals(777L, profile.seed());
        assertEquals(SeedSource.MANUAL, profile.seedSource());
        assertEquals(5L, profile.createdAt());
    }

    @Test
    void missingOptionalFieldsFallBackToDefaults(@TempDir Path root) throws IOException {
        WorldProfileStorage storage = new WorldProfileStorage(root);
        write(storage.profilePath(SERVER), "{\"formatVersion\": 1}");

        WorldProfile profile = storage.loadOrCreate(SERVER, VERSION, 1000L);

        assertFalse(profile.hasSeed());
        assertEquals(VERSION, profile.minecraftVersion());
        assertEquals(1000L, profile.createdAt());
        assertEquals(1000L, profile.lastSeenAt());
    }

    @Test
    void anUnknownEnumNameDegradesInsteadOfThrowing(@TempDir Path root) throws IOException {
        WorldProfileStorage storage = new WorldProfileStorage(root);
        write(storage.profilePath(SERVER), "{\"seedState\": \"TELEPATHIC\","
                + "\"seedSource\": \"DIVINED\", \"seed\": 5}");

        WorldProfile profile = storage.loadOrCreate(SERVER, VERSION, 1000L);

        assertFalse(profile.hasSeed());
        assertEquals(SeedSource.UNKNOWN, profile.seedSource());
    }

    @Test
    void clearingASeedIsPersisted(@TempDir Path root) {
        WorldProfileStorage storage = new WorldProfileStorage(root);

        WorldProfile profile = storage.loadOrCreate(SERVER, VERSION, 1L);
        profile.setManualSeed(42L);
        storage.save(profile);

        profile.clearManualSeed();
        storage.save(profile);

        assertFalse(storage.loadOrCreate(SERVER, VERSION, 1L).hasSeed());
    }

    @Test
    void theFileIsReadableJsonWithAFormatVersion(@TempDir Path root) throws IOException {
        WorldProfileStorage storage = new WorldProfileStorage(root);
        WorldProfile profile = storage.loadOrCreate(SERVER, VERSION, 1L);
        profile.setManualSeed(Long.MIN_VALUE);
        storage.save(profile);

        String json = new String(Files.readAllBytes(storage.profilePath(SERVER)), UTF_8);

        assertTrue(json.contains("\"formatVersion\""), json);
        assertTrue(json.contains("-9223372036854775808"), json);
        assertTrue(json.contains("\"MANUAL\""), json);
    }

    @Test
    void noTemporaryFileIsLeftBehind(@TempDir Path root) throws IOException {
        WorldProfileStorage storage = new WorldProfileStorage(root);
        WorldProfile profile = storage.loadOrCreate(SERVER, VERSION, 1L);
        profile.setManualSeed(42L);
        storage.save(profile);

        assertFalse(Files.exists(storage.profilePath(SERVER).resolveSibling("profile.json.tmp")));
    }
}
