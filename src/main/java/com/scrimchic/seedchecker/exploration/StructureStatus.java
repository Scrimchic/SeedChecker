package com.scrimchic.seedchecker.exploration;

/**
 * What the player has recorded about one structure in the real world.
 *
 * <p>Set by hand only. Nothing in Phase 4 moves a structure from one state to another on its own -
 * not distance, not loaded chunks - so each value means exactly what the player said.
 *
 * <p>Stored by {@link #id()}, never by constant name, so renaming a constant cannot break a file.
 */
public enum StructureStatus {

    /** Nothing recorded. The default for every structure, and never written to disk on its own. */
    UNVISITED("unvisited", "Unvisited"),

    /** The player has been there, or otherwise checked it. */
    VISITED("visited", "Visited"),

    /** The main loot has been taken. */
    LOOTED("looted", "Looted"),

    /** Checked, and there was nothing useful or nothing the player was after. */
    EMPTY("empty", "Empty"),

    /** Destroyed or otherwise unusable. */
    DESTROYED("destroyed", "Destroyed");

    private final String id;
    private final String displayName;

    StructureStatus(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** The canonical storage id, e.g. {@code looted}. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** @return the status with that storage id, or {@code null} for an id this version does not know */
    public static StructureStatus fromId(String id) {
        if (id != null) {
            for (StructureStatus status : values()) {
                if (status.id.equals(id)) {
                    return status;
                }
            }
        }
        return null;
    }
}
