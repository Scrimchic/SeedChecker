package com.scrimchic.seedchecker.exploration;

/**
 * Which structures and markers the map shows, by what the player recorded about them.
 *
 * <p>The one statement of those rules. Every path that draws, picks, lists, describes or keeps a
 * selection asks {@link #isStructureVisible} or {@link #isMarkerVisible}, so a hidden object cannot
 * still be clicked, and a visible one cannot be missing from the list.
 *
 * <p>A structure nothing was recorded about is {@link StructureStatus#UNVISITED}: pass {@code null}
 * for its status, and it is filtered as unvisited. The filter is about the player's record only; how
 * exactly a structure was predicted has no part in it.
 *
 * <p>Plain arrays by ordinal, so a check in a draw loop allocates nothing. Mutable, client thread
 * only, like the rest of the map state; {@link #revision()} counts changes.
 */
public final class ExplorationFilters {

    private final boolean[] statuses = new boolean[StructureStatus.values().length];
    private final boolean[] markerTypes = new boolean[MarkerType.values().length];
    private int revision;

    /** Everything visible. */
    public ExplorationFilters() {
        java.util.Arrays.fill(statuses, true);
        java.util.Arrays.fill(markerTypes, true);
    }

    // ------------------------------------------------------------ predicates

    /**
     * @param status what the player recorded, or {@code null} when nothing was - which is unvisited
     */
    public boolean isStructureVisible(StructureStatus status) {
        return statuses[(status == null ? StructureStatus.UNVISITED : status).ordinal()];
    }

    public boolean isMarkerVisible(CustomMarker marker) {
        return marker != null && markerTypes[marker.type().ordinal()];
    }

    // ------------------------------------------------------------ structure statuses

    public boolean showsStatus(StructureStatus status) {
        return statuses[status.ordinal()];
    }

    /** Whether no status is hidden, so drawing need not look a single status up. */
    public boolean showsAllStatuses() {
        for (boolean shown : statuses) {
            if (!shown) {
                return false;
            }
        }
        return true;
    }

    public void setStatusVisible(StructureStatus status, boolean visible) {
        if (statuses[status.ordinal()] != visible) {
            statuses[status.ordinal()] = visible;
            revision++;
        }
    }

    public void toggleStatus(StructureStatus status) {
        setStatusVisible(status, !showsStatus(status));
    }

    public void showAllStatuses() {
        for (StructureStatus status : StructureStatus.values()) {
            setStatusVisible(status, true);
        }
    }

    public void showOnlyStatus(StructureStatus only) {
        for (StructureStatus status : StructureStatus.values()) {
            setStatusVisible(status, status == only);
        }
    }

    // ------------------------------------------------------------ marker types

    public boolean showsMarkerType(MarkerType type) {
        return markerTypes[type.ordinal()];
    }

    public boolean showsAllMarkerTypes() {
        for (boolean shown : markerTypes) {
            if (!shown) {
                return false;
            }
        }
        return true;
    }

    public void setMarkerTypeVisible(MarkerType type, boolean visible) {
        if (markerTypes[type.ordinal()] != visible) {
            markerTypes[type.ordinal()] = visible;
            revision++;
        }
    }

    public void toggleMarkerType(MarkerType type) {
        setMarkerTypeVisible(type, !showsMarkerType(type));
    }

    public void showAllMarkerTypes() {
        for (MarkerType type : MarkerType.values()) {
            setMarkerTypeVisible(type, true);
        }
    }

    /** Increases with every change. */
    public int revision() {
        return revision;
    }
}
