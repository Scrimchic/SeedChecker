package com.scrimchic.seedchecker.worldgen;

/**
 * How far a structure candidate has got through validation.
 *
 * <p>The full chain vanilla applies is longer than this, and the names say so deliberately:
 *
 * <pre>
 * grid candidate  -&gt;  biome compatible  -&gt;  (terrain, jigsaw, exclusion zones)  -&gt;  generated
 * </pre>
 *
 * <p>Seed Checker currently answers the first two steps only. Nothing here means "this structure
 * exists".
 *
 * <p>The three values exist because the biome question itself cannot always be answered. Vanilla
 * samples the biome once, at the position the structure would actually start from, and for some
 * structures that position is not computable without work this phase does not do. Where that is
 * the case the answer is {@link #UNKNOWN} rather than a guess in either direction.
 */
public enum StructureBiomeStatus {

    /**
     * Some position vanilla could sample for this candidate carries a biome it accepts.
     *
     * <p>A superset of vanilla's answer, never a claim of equality. Vanilla samples exactly one
     * height; where that height comes from the terrain this enumerates every height the dimension
     * allows and accepts if any of them works. So a real structure is always compatible, and a
     * compatible candidate may still be rejected by vanilla on height grounds.
     */
    COMPATIBLE,

    /**
     * No position vanilla could sample carries an accepted biome, so vanilla rejects this
     * candidate however the terrain turns out.
     *
     * <p>Only ever produced when the set of positions vanilla might sample was enumerated
     * exhaustively. This is the half of the answer the map is allowed to act on by hiding a
     * marker.
     */
    INCOMPATIBLE,

    /**
     * The biome cannot be decided yet, because the position vanilla samples is not known.
     *
     * <p>Not a failure and not a pending job - the check ran and its honest answer is "cannot
     * tell". Such a candidate is always drawn: this phase may show false positives, but it must
     * never hide a structure vanilla could generate.
     */
    UNKNOWN
}
