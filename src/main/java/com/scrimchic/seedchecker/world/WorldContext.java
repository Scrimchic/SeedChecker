package com.scrimchic.seedchecker.world;

/**
 * An immutable snapshot of what Seed Checker currently knows about the world the player is in.
 *
 * <p>This is a Seed Checker model, not a Minecraft one: it holds no Minecraft types and can be
 * built, stored and compared without a running client. Producing it from the live game is the job
 * of {@code com.scrimchic.seedchecker.platform.MinecraftBridge}.
 */
public final class WorldContext {

    private final String minecraftVersion;
    private final boolean inWorld;
    private final PlayMode playMode;
    private final String dimensionId;
    private final DimensionType dimension;
    private final SeedState seedState;
    private final long seed;

    private WorldContext(String minecraftVersion, boolean inWorld, PlayMode playMode,
                         String dimensionId, SeedState seedState, long seed) {
        this.minecraftVersion = minecraftVersion;
        this.inWorld = inWorld;
        this.playMode = playMode;
        this.dimensionId = dimensionId;
        this.dimension = DimensionType.fromId(dimensionId);
        this.seedState = seedState;
        this.seed = seed;
    }

    /** The player is in the main menu, or between worlds. */
    public static WorldContext outsideWorld(String minecraftVersion) {
        return new WorldContext(minecraftVersion, false, null, null, SeedState.UNKNOWN, 0L);
    }

    /** The player is in a world whose seed Seed Checker was able to read. */
    public static WorldContext withKnownSeed(String minecraftVersion, PlayMode playMode,
                                             String dimensionId, long seed) {
        return new WorldContext(minecraftVersion, true, playMode, dimensionId, SeedState.KNOWN, seed);
    }

    /** The player is in a world whose seed is hidden, as on most multiplayer servers. */
    public static WorldContext withUnknownSeed(String minecraftVersion, PlayMode playMode,
                                               String dimensionId) {
        return new WorldContext(minecraftVersion, true, playMode, dimensionId, SeedState.UNKNOWN, 0L);
    }

    /** The Minecraft version this build of Seed Checker is running on, e.g. {@code 1.20.1}. */
    public String minecraftVersion() {
        return minecraftVersion;
    }

    public boolean isInWorld() {
        return inWorld;
    }

    /** @return the play mode, or {@code null} when the player is not in a world. */
    public PlayMode playMode() {
        return playMode;
    }

    /** @return the raw dimension id, or {@code null} when the player is not in a world. */
    public String dimensionId() {
        return dimensionId;
    }

    /** @return the recognised dimension; {@link DimensionType#CUSTOM} for anything else. */
    public DimensionType dimension() {
        return dimension;
    }

    public SeedState seedState() {
        return seedState;
    }

    public boolean hasSeed() {
        return seedState == SeedState.KNOWN;
    }

    /**
     * @return the world seed.
     * @throws IllegalStateException if the seed is not {@link SeedState#KNOWN}; callers must
     *         check {@link #hasSeed()} first rather than treat an absent seed as zero.
     */
    public long seed() {
        if (seedState != SeedState.KNOWN) {
            throw new IllegalStateException("Seed is " + seedState + " for this world");
        }
        return seed;
    }

    @Override
    public String toString() {
        if (!inWorld) {
            return "WorldContext{outside world, minecraft=" + minecraftVersion + "}";
        }
        return "WorldContext{minecraft=" + minecraftVersion
                + ", mode=" + playMode
                + ", dimension=" + dimensionId
                + ", seedState=" + seedState
                + (hasSeed() ? ", seed=" + seed : "")
                + "}";
    }
}
