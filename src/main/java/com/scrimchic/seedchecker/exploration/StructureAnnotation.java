package com.scrimchic.seedchecker.exploration;

/**
 * What the player recorded about one structure: a status and an optional note. Immutable.
 *
 * <p>Independent of how the structure was predicted: an exact structure and a non-exact candidate
 * can both be marked, because the player may well have checked either in person.
 */
public final class StructureAnnotation {

    private final StructureKey key;
    private final StructureStatus status;
    private final String note;

    private StructureAnnotation(StructureKey key, StructureStatus status, String note) {
        if (key == null) {
            throw new IllegalArgumentException("an annotation needs a structure key");
        }
        this.key = key;
        this.status = status == null ? StructureStatus.UNVISITED : status;
        this.note = ExplorationText.note(note);
    }

    /** @param note any text; normalised by {@link ExplorationText#note} */
    public static StructureAnnotation of(StructureKey key, StructureStatus status, String note) {
        return new StructureAnnotation(key, status, note);
    }

    public StructureKey key() {
        return key;
    }

    public StructureStatus status() {
        return status;
    }

    /** @return the note, or {@code null} when there is none */
    public String note() {
        return note;
    }

    /** Whether this says nothing beyond the default, and so need not be kept at all. */
    public boolean isEmpty() {
        return status == StructureStatus.UNVISITED && note == null;
    }

    public StructureAnnotation withStatus(StructureStatus newStatus) {
        return new StructureAnnotation(key, newStatus, note);
    }

    public StructureAnnotation withNote(String newNote) {
        return new StructureAnnotation(key, status, newNote);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StructureAnnotation)) {
            return false;
        }
        StructureAnnotation that = (StructureAnnotation) other;
        return key.equals(that.key) && status == that.status
                && (note == null ? that.note == null : note.equals(that.note));
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + status.hashCode();
        result = 31 * result + (note == null ? 0 : note.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "StructureAnnotation{" + key + ", " + status.id()
                + (note == null ? "" : ", note of " + note.length() + " chars") + "}";
    }
}
