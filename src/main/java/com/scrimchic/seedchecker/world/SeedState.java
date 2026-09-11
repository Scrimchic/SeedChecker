package com.scrimchic.seedchecker.world;

/** How much Seed Checker knows about the current world's seed. */
public enum SeedState {

    /** The seed is available and can be trusted, e.g. read from an integrated server. */
    KNOWN,

    /** The seed is not available and would have to be recovered from observed evidence. */
    UNKNOWN
}
