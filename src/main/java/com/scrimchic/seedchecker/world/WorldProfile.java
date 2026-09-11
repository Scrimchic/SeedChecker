package com.scrimchic.seedchecker.world;

/**
 * What Seed Checker remembers about one world between sessions.
 *
 * <p>This is the persisted counterpart of {@link WorldContext}: the context is a snapshot of the
 * running game and is rebuilt every frame, while a profile outlives the session and is written to
 * disk. It holds no Minecraft types.
 *
 * <p>Mutation is deliberately narrow - there are no setters, only the four transitions the seed
 * rules allow - and every one of them reports whether anything actually changed, so the manager
 * can save only when there is something to save.
 */
public final class WorldProfile {

    private final WorldIdentity identity;
    private final long createdAt;

    private String minecraftVersion;
    private SeedState seedState;
    private long seed;
    private SeedSource seedSource;
    private long lastSeenAt;

    private WorldProfile(WorldIdentity identity, String minecraftVersion, SeedState seedState,
                         long seed, SeedSource seedSource, long createdAt, long lastSeenAt) {
        this.identity = identity;
        this.minecraftVersion = minecraftVersion;
        this.seedState = seedState;
        this.seed = seed;
        this.seedSource = seedSource;
        this.createdAt = createdAt;
        this.lastSeenAt = lastSeenAt;
    }

    /** A profile for a world Seed Checker has never seen before. */
    public static WorldProfile createNew(WorldIdentity identity, String minecraftVersion, long now) {
        return new WorldProfile(identity, minecraftVersion, SeedState.UNKNOWN, 0L,
                SeedSource.UNKNOWN, now, now);
    }

    /**
     * Rebuilds a profile that was read back from storage.
     *
     * <p>Inconsistent combinations are normalised rather than trusted, because the file on disk is
     * editable by hand: a profile that claims a known seed without a source, or a source without a
     * known seed, is reduced to something coherent.
     */
    public static WorldProfile restore(WorldIdentity identity, String minecraftVersion,
                                       SeedState seedState, long seed, SeedSource seedSource,
                                       long createdAt, long lastSeenAt) {
        SeedState state = seedState == null ? SeedState.UNKNOWN : seedState;
        SeedSource source = seedSource == null ? SeedSource.UNKNOWN : seedSource;
        if (state != SeedState.KNOWN || source == SeedSource.UNKNOWN) {
            state = SeedState.UNKNOWN;
            source = SeedSource.UNKNOWN;
            seed = 0L;
        }
        return new WorldProfile(identity, minecraftVersion, state, seed, source, createdAt, lastSeenAt);
    }

    public WorldIdentity identity() {
        return identity;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public SeedState seedState() {
        return seedState;
    }

    public boolean hasSeed() {
        return seedState == SeedState.KNOWN;
    }

    /**
     * @return the remembered seed
     * @throws IllegalStateException if no seed is known; check {@link #hasSeed()} first
     */
    public long seed() {
        if (seedState != SeedState.KNOWN) {
            throw new IllegalStateException("Profile " + identity + " has no known seed");
        }
        return seed;
    }

    public SeedSource seedSource() {
        return seedSource;
    }

    public long createdAt() {
        return createdAt;
    }

    public long lastSeenAt() {
        return lastSeenAt;
    }

    /**
     * Records the seed Minecraft itself reported. This always wins: a real integrated-server seed
     * is ground truth, so it overwrites anything that was typed in or recovered earlier.
     *
     * @return whether the profile changed
     */
    public boolean applyRuntimeSeed(long runtimeSeed) {
        if (seedState == SeedState.KNOWN && seedSource == SeedSource.RUNTIME && seed == runtimeSeed) {
            return false;
        }
        this.seed = runtimeSeed;
        this.seedState = SeedState.KNOWN;
        this.seedSource = SeedSource.RUNTIME;
        return true;
    }

    /**
     * Records a seed the player typed in.
     *
     * <p>Refused when the profile already holds a runtime seed: Minecraft is authoritative there,
     * and letting a typed number shadow it would silently produce a wrong map.
     *
     * @return whether the profile changed
     */
    public boolean setManualSeed(long manualSeed) {
        if (seedSource == SeedSource.RUNTIME) {
            return false;
        }
        if (seedState == SeedState.KNOWN && seedSource == SeedSource.MANUAL && seed == manualSeed) {
            return false;
        }
        this.seed = manualSeed;
        this.seedState = SeedState.KNOWN;
        this.seedSource = SeedSource.MANUAL;
        return true;
    }

    /**
     * Forgets a manually entered seed. A runtime seed cannot be cleared this way, since hiding
     * ground truth is never what the player means.
     *
     * @return whether the profile changed
     */
    public boolean clearManualSeed() {
        if (seedSource != SeedSource.MANUAL) {
            return false;
        }
        this.seed = 0L;
        this.seedState = SeedState.UNKNOWN;
        this.seedSource = SeedSource.UNKNOWN;
        return true;
    }

    /**
     * Marks the profile as seen now, on the given Minecraft version.
     *
     * @return whether the profile changed
     */
    public boolean markSeen(String currentMinecraftVersion, long now) {
        boolean changed = false;
        if (currentMinecraftVersion != null && !currentMinecraftVersion.equals(minecraftVersion)) {
            this.minecraftVersion = currentMinecraftVersion;
            changed = true;
        }
        if (now != lastSeenAt) {
            this.lastSeenAt = now;
            changed = true;
        }
        return changed;
    }

    @Override
    public String toString() {
        return "WorldProfile{" + identity
                + ", minecraft=" + minecraftVersion
                + ", seedState=" + seedState
                + ", source=" + seedSource
                + (hasSeed() ? ", seed=" + seed : "")
                + "}";
    }
}
