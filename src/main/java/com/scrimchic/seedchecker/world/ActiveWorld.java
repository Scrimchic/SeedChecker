package com.scrimchic.seedchecker.world;

/**
 * The world Seed Checker is currently working with: the live {@link WorldContext} paired with the
 * stored {@link WorldProfile}, if there is one.
 *
 * <p>This is the single place seed priority is decided, so no screen and no map layer has to
 * repeat the rule:
 *
 * <pre>
 * runtime seed  &gt;  remembered seed  &gt;  unknown
 * </pre>
 *
 * <p>A runtime seed comes from a local integrated server and is ground truth, so it wins outright.
 * Only when Minecraft does not offer one does the remembered seed - typed in by the player, or
 * later recovered by the cracker - come into play.
 */
public final class ActiveWorld {

    private final WorldContext context;
    private final WorldProfile profile;

    /** @param profile the stored profile, or {@code null} when there is none yet */
    public ActiveWorld(WorldContext context, WorldProfile profile) {
        this.context = context;
        this.profile = profile;
    }

    public WorldContext context() {
        return context;
    }

    /** @return the stored profile, or {@code null} when the player is not in a known world */
    public WorldProfile profile() {
        return profile;
    }

    public boolean hasProfile() {
        return profile != null;
    }

    public SeedState seedState() {
        return hasSeed() ? SeedState.KNOWN : SeedState.UNKNOWN;
    }

    public boolean hasSeed() {
        return context.hasSeed() || (profile != null && profile.hasSeed());
    }

    /**
     * @return the seed the map should be drawn from
     * @throws IllegalStateException if no seed is known; check {@link #hasSeed()} first
     */
    public long seed() {
        if (context.hasSeed()) {
            return context.seed();
        }
        if (profile != null && profile.hasSeed()) {
            return profile.seed();
        }
        throw new IllegalStateException("No seed is known for this world");
    }

    public SeedSource seedSource() {
        if (context.hasSeed()) {
            return SeedSource.RUNTIME;
        }
        if (profile != null && profile.hasSeed()) {
            return profile.seedSource();
        }
        return SeedSource.UNKNOWN;
    }

    /** Whether the player may type a seed in: only worlds Minecraft will not tell us about. */
    public boolean acceptsManualSeed() {
        return profile != null && !context.hasSeed();
    }

    /** Whether there is a manually entered seed that could be forgotten again. */
    public boolean hasManualSeed() {
        return acceptsManualSeed() && profile.hasSeed() && profile.seedSource() == SeedSource.MANUAL;
    }
}
