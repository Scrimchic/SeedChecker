package com.scrimchic.seedchecker.exploration;

import java.util.UUID;

/**
 * A point the player put on the map themselves. Immutable.
 *
 * <p>The {@link #id()} is the marker: moving it, renaming it or changing its type or note produces
 * a new value with the same id, and that is the same marker. It is a random UUID in its canonical
 * string form, so it is independent of position and of creation order, and survives any file
 * round trip unchanged.
 *
 * <p>No seed anywhere: a marker is an observation of the real world, so it exists the same whether
 * the world's seed is known or not.
 */
public final class CustomMarker {

    /** Generous for a UUID string, and short enough that a hand-edited file cannot run away. */
    static final int ID_MAX_LENGTH = 64;

    private final String id;
    private final String dimensionId;
    private final int x;
    private final Integer y;
    private final int z;
    private final MarkerType type;
    private final String label;
    private final String note;

    private CustomMarker(String id, String dimensionId, int x, Integer y, int z, MarkerType type,
                         String label, String note) {
        if (id == null || id.trim().isEmpty() || id.length() > ID_MAX_LENGTH) {
            throw new IllegalArgumentException("a marker needs an id of 1 to " + ID_MAX_LENGTH + " chars");
        }
        if (dimensionId == null || dimensionId.trim().isEmpty()) {
            throw new IllegalArgumentException("a marker needs a dimension");
        }
        if (type == null) {
            throw new IllegalArgumentException("a marker needs a type");
        }
        this.id = id;
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.label = ExplorationText.label(label);
        this.note = ExplorationText.note(note);
    }

    /** A new marker with a fresh id. */
    public static CustomMarker create(String dimensionId, int x, Integer y, int z, MarkerType type,
                                      String label, String note) {
        return new CustomMarker(newId(), dimensionId, x, y, z, type, label, note);
    }

    /** A marker with a known id, e.g. read back from storage. */
    public static CustomMarker restore(String id, String dimensionId, int x, Integer y, int z,
                                       MarkerType type, String label, String note) {
        return new CustomMarker(id, dimensionId, x, y, z, type, label, note);
    }

    static String newId() {
        return UUID.randomUUID().toString();
    }

    public String id() {
        return id;
    }

    public String dimensionId() {
        return dimensionId;
    }

    public int x() {
        return x;
    }

    /** @return the height, or {@code null} when the marker is a column rather than a point */
    public Integer y() {
        return y;
    }

    public int z() {
        return z;
    }

    public MarkerType type() {
        return type;
    }

    /** @return the player's label, or {@code null} */
    public String label() {
        return label;
    }

    /** @return the note, or {@code null} */
    public String note() {
        return note;
    }

    /** The label, or the type's name for a marker without one. */
    public String displayLabel() {
        return label != null ? label : type.displayName();
    }

    /** The same marker somewhere else, possibly in another dimension. */
    public CustomMarker movedTo(String newDimensionId, int newX, Integer newY, int newZ) {
        return new CustomMarker(id, newDimensionId, newX, newY, newZ, type, label, note);
    }

    public CustomMarker withType(MarkerType newType) {
        return new CustomMarker(id, dimensionId, x, y, z, newType, label, note);
    }

    public CustomMarker withLabel(String newLabel) {
        return new CustomMarker(id, dimensionId, x, y, z, type, newLabel, note);
    }

    public CustomMarker withNote(String newNote) {
        return new CustomMarker(id, dimensionId, x, y, z, type, label, newNote);
    }

    /** The same content under another id; storage uses it to keep a marker whose id was taken. */
    CustomMarker withId(String newId) {
        return new CustomMarker(newId, dimensionId, x, y, z, type, label, note);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CustomMarker)) {
            return false;
        }
        CustomMarker that = (CustomMarker) other;
        return x == that.x && z == that.z && id.equals(that.id)
                && dimensionId.equals(that.dimensionId) && type == that.type
                && (y == null ? that.y == null : y.equals(that.y))
                && (label == null ? that.label == null : label.equals(that.label))
                && (note == null ? that.note == null : note.equals(that.note));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "CustomMarker{" + id + " " + type.id() + " " + dimensionId + " " + x + ","
                + (y == null ? "~" : y.toString()) + "," + z
                + (label == null ? "" : " '" + label + "'") + "}";
    }
}
