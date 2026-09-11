package com.scrimchic.seedchecker.world;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Stable identity of one world, independent of both Minecraft and the filesystem.
 *
 * <p>Identity deliberately is not the seed: two unrelated worlds can share a seed, and a server's
 * seed is usually unknown in the first place. It is also not a dimension - the Overworld, Nether
 * and End of one world share a single identity, because a dimension is runtime context.
 *
 * <p>For a singleplayer world the stable id is the save directory's <em>name</em>, which is unique
 * within {@code saves/} and survives restarts; the absolute path is deliberately not used, since
 * it changes as soon as the game directory moves. For a server it is the normalised connection
 * address, so reconnecting reopens the same profile.
 */
public final class WorldIdentity {

    public enum Type {
        SINGLEPLAYER,
        MULTIPLAYER
    }

    /** Minecraft's default port, dropped during normalisation so it cannot split a profile. */
    private static final String DEFAULT_PORT_SUFFIX = ":25565";

    /** 64 bits of SHA-256, far more than enough to keep one player's worlds apart. */
    private static final int HASH_HEX_LENGTH = 16;

    /** Keeps directory names readable without letting a long world name run away. */
    private static final int SLUG_MAX_LENGTH = 32;

    private final Type type;
    private final String stableId;
    private final String displayName;
    private final String storageKey;

    private WorldIdentity(Type type, String stableId, String displayName) {
        this.type = type;
        this.stableId = stableId;
        this.displayName = displayName;
        this.storageKey = slug(displayName) + "-" + hash(type, stableId);
    }

    /**
     * @param saveDirectoryName the name of the save folder, not its path
     * @param displayName       the world name shown to the player
     */
    public static WorldIdentity singleplayer(String saveDirectoryName, String displayName) {
        String id = saveDirectoryName == null ? "" : saveDirectoryName.trim();
        return new WorldIdentity(Type.SINGLEPLAYER, id, orFallback(displayName, id));
    }

    /**
     * @param serverAddress the address the player connected to
     * @param displayName   the server list entry's name, if there is one
     */
    public static WorldIdentity multiplayer(String serverAddress, String displayName) {
        String id = normalizeServerAddress(serverAddress);
        return new WorldIdentity(Type.MULTIPLAYER, id, orFallback(displayName, id));
    }

    /**
     * Folds the spellings of one server address together, so that {@code MC.Example.com},
     * {@code mc.example.com} and {@code mc.example.com:25565} share a single profile.
     */
    public static String normalizeServerAddress(String address) {
        if (address == null) {
            return "";
        }
        String normalized = address.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(DEFAULT_PORT_SUFFIX)) {
            normalized = normalized.substring(0, normalized.length() - DEFAULT_PORT_SUFFIX.length());
        }
        return normalized;
    }

    public Type type() {
        return type;
    }

    public String stableId() {
        return stableId;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * A directory name that is safe on every filesystem and maps one-to-one onto this identity.
     *
     * <p>The readable slug is only decoration; the trailing digest is what keeps two worlds apart,
     * which is why it is SHA-256 rather than {@link String#hashCode()}.
     */
    public String storageKey() {
        return storageKey;
    }

    private static String orFallback(String displayName, String fallback) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return fallback;
        }
        return displayName.trim();
    }

    /** Lower-cases and strips everything a filesystem might object to. */
    private static String slug(String text) {
        StringBuilder slug = new StringBuilder();
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length() && slug.length() < SLUG_MAX_LENGTH; i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                slug.append(c);
            } else if (slug.length() > 0 && slug.charAt(slug.length() - 1) != '-') {
                slug.append('-');
            }
        }
        while (slug.length() > 0 && slug.charAt(slug.length() - 1) == '-') {
            slug.setLength(slug.length() - 1);
        }
        return slug.length() == 0 ? "world" : slug.toString();
    }

    /**
     * Deterministic across machines and JVM versions, unlike {@link String#hashCode()}.
     *
     * <p>The id is length-prefixed so that no combination of type and id can be spelled two ways
     * and collide by construction rather than by chance.
     */
    private static String hash(Type type, String stableId) {
        String text = type.name() + "|" + stableId.length() + "|" + stableId;
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder(HASH_HEX_LENGTH);
            for (int i = 0; hex.length() < HASH_HEX_LENGTH; i++) {
                hex.append(Character.forDigit((bytes[i] >> 4) & 0xF, 16));
                hex.append(Character.forDigit(bytes[i] & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is required by every Java platform", e);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WorldIdentity)) {
            return false;
        }
        WorldIdentity that = (WorldIdentity) other;
        return type == that.type && stableId.equals(that.stableId);
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + stableId.hashCode();
    }

    @Override
    public String toString() {
        return type + ":" + stableId;
    }
}
