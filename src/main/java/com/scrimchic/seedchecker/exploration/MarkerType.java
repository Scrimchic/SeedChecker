package com.scrimchic.seedchecker.exploration;

/**
 * What a player-placed map marker stands for.
 *
 * <p>A deliberately short, fixed list. {@link #CUSTOM} is the one meant to carry a label of its
 * own; any marker may have one. Stored by {@link #id()}, never by constant name.
 */
public enum MarkerType {

    BASE("base", "Base"),
    STASH("stash", "Stash"),
    PORTAL("portal", "Portal"),
    FARM("farm", "Farm"),
    DANGER("danger", "Danger"),
    RESOURCE("resource", "Resource"),
    PLAYER_BASE("player_base", "Player base"),
    CUSTOM("custom", "Custom");

    private final String id;
    private final String displayName;

    MarkerType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** The canonical storage id, e.g. {@code player_base}. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** @return the type with that storage id, or {@code null} for an id this version does not know */
    public static MarkerType fromId(String id) {
        if (id != null) {
            for (MarkerType type : values()) {
                if (type.id.equals(id)) {
                    return type;
                }
            }
        }
        return null;
    }
}
