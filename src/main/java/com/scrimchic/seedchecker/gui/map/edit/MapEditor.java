package com.scrimchic.seedchecker.gui.map.edit;

import com.scrimchic.seedchecker.client.exploration.ExplorationManager;
import com.scrimchic.seedchecker.exploration.CustomMarker;
import com.scrimchic.seedchecker.exploration.ExplorationText;
import com.scrimchic.seedchecker.exploration.MarkerType;
import com.scrimchic.seedchecker.exploration.StructureKey;

/**
 * What the map screen is editing, if anything, and every rule about it - without a single
 * Minecraft type, so it can be tested as it is.
 *
 * <p>One editor at a time: a structure's note, a marker being created, a marker being edited, or a
 * marker waiting for its deletion to be confirmed. While one is open the editor owns the keyboard -
 * {@link #handleKey} and {@link #typeCodePoint} report every key as consumed - so typing a letter
 * can never also act on the map, and Escape closes the editor rather than the screen.
 *
 * <p>Nothing reaches the exploration data until Save. A draft lives only here: cancelling, or
 * leaving the world, leaves no trace in {@code WorldExploration} and nothing on disk.
 */
public final class MapEditor {

    public enum Mode {
        NONE,
        STRUCTURE_NOTE,
        CREATE_MARKER,
        EDIT_MARKER,
        DELETE_MARKER
    }

    /** Which text field of a marker draft receives typing. */
    public enum Field {
        LABEL,
        NOTE
    }

    /** How the last editor closed, for the screen to react to once. */
    public enum Result {
        SAVED,
        DELETED,
        CANCELLED,
        /** Nothing could be written: the world's exploration is read-only or gone. */
        READ_ONLY,
        /** The marker being edited or deleted no longer exists. */
        GONE
    }

    private final ExplorationManager exploration;

    private Mode mode = Mode.NONE;
    private Result result;

    private StructureKey structureKey;
    private String subject;

    private String markerId;
    private String dimensionId;
    private int x;
    private Integer y;
    private int z;
    private MarkerType type = MarkerType.CUSTOM;
    private Field focus = Field.NOTE;
    private String savedMarkerId;

    private final TextBuffer label = new TextBuffer(ExplorationText.LABEL_MAX_LENGTH, false);
    private final TextBuffer note = new TextBuffer(ExplorationText.NOTE_MAX_LENGTH, true);

    public MapEditor(ExplorationManager exploration) {
        this.exploration = exploration;
    }

    // ------------------------------------------------------------ opening

    /**
     * Opens the note of a structure, preloaded with what is stored.
     *
     * @param subject a short description of the structure for the editor's title
     * @return whether it opened; not when the exploration cannot be edited
     */
    public boolean beginStructureNote(StructureKey key, String subject) {
        if (key == null || !exploration.isWritable()) {
            return false;
        }
        reset();
        mode = Mode.STRUCTURE_NOTE;
        structureKey = key;
        this.subject = subject;
        note.set(exploration.noteOf(key));
        focus = Field.NOTE;
        return true;
    }

    /**
     * Opens a draft for a new marker at a position; nothing is created until {@link #save()}.
     *
     * @param y the height, or {@code null} when the position is a map column
     */
    public boolean beginCreateMarker(String dimensionId, int x, Integer y, int z) {
        if (dimensionId == null || !exploration.isWritable()) {
            return false;
        }
        reset();
        mode = Mode.CREATE_MARKER;
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        type = MarkerType.CUSTOM;
        focus = Field.LABEL;
        return true;
    }

    /** Opens an existing marker's type, label and note; its id and position are not editable here. */
    public boolean beginEditMarker(String id) {
        CustomMarker marker = exploration.marker(id);
        if (marker == null || !exploration.isWritable()) {
            return false;
        }
        reset();
        mode = Mode.EDIT_MARKER;
        markerId = id;
        dimensionId = marker.dimensionId();
        x = marker.x();
        y = marker.y();
        z = marker.z();
        type = marker.type();
        label.set(marker.label());
        note.set(marker.note());
        focus = Field.LABEL;
        return true;
    }

    /** Asks for confirmation before a marker is deleted. */
    public boolean beginDeleteMarker(String id) {
        if (exploration.marker(id) == null || !exploration.isWritable()) {
            return false;
        }
        reset();
        mode = Mode.DELETE_MARKER;
        markerId = id;
        return true;
    }

    // ------------------------------------------------------------ input

    public Mode mode() {
        return mode;
    }

    public boolean isActive() {
        return mode != Mode.NONE;
    }

    /** Whether typed characters go into a field right now. */
    public boolean acceptsText() {
        return mode == Mode.STRUCTURE_NOTE || mode == Mode.CREATE_MARKER || mode == Mode.EDIT_MARKER;
    }

    /** @return whether the editor consumed it, which it does for any input while open */
    public boolean typeCodePoint(int codePoint) {
        if (!isActive()) {
            return false;
        }
        if (acceptsText()) {
            focusedBuffer().insert(codePoint);
        }
        return true;
    }

    /**
     * Escape cancels, Ctrl+Enter saves, Enter moves from the label to the note and breaks a line in
     * the note, Tab switches field, and the rest move or delete in the focused field.
     *
     * @return whether the editor consumed the key, which it does for every key while open
     */
    public boolean handleKey(EditorKey key, boolean control) {
        if (!isActive()) {
            return false;
        }
        if (key == EditorKey.ESCAPE) {
            cancel();
            return true;
        }
        if (!acceptsText()) {
            // A deletion is confirmed by its button only, never by a key held a moment too long.
            return true;
        }
        TextBuffer field = focusedBuffer();
        switch (key) {
            case ENTER:
                if (control) {
                    save();
                } else if (field == label) {
                    focus = Field.NOTE;
                } else {
                    field.newline();
                }
                break;
            case TAB:
                if (hasLabel()) {
                    focus = focus == Field.LABEL ? Field.NOTE : Field.LABEL;
                }
                break;
            case BACKSPACE:
                field.backspace();
                break;
            case DELETE:
                field.delete();
                break;
            case LEFT:
                field.left();
                break;
            case RIGHT:
                field.right();
                break;
            case UP:
                field.up();
                break;
            case DOWN:
                field.down();
                break;
            case HOME:
                field.home();
                break;
            case END:
                field.end();
                break;
            default:
                break;
        }
        return true;
    }

    public void setType(MarkerType newType) {
        if ((mode == Mode.CREATE_MARKER || mode == Mode.EDIT_MARKER) && newType != null) {
            type = newType;
        }
    }

    public void focus(Field field) {
        if (field == Field.NOTE || hasLabel()) {
            focus = field;
        }
    }

    // ------------------------------------------------------------ closing

    /**
     * Writes the draft, or performs the confirmed deletion, and closes the editor.
     *
     * @return what happened; {@link Result#READ_ONLY} leaves the editor open with the draft intact
     */
    public Result save() {
        switch (mode) {
            case STRUCTURE_NOTE:
                if (!exploration.isWritable()) {
                    return remember(Result.READ_ONLY);
                }
                exploration.setNote(structureKey, note.committedText());
                return close(Result.SAVED);
            case CREATE_MARKER: {
                CustomMarker created = exploration.createMarker(dimensionId, x, y, z, type,
                        label.committedText(), note.committedText());
                if (created == null) {
                    return remember(Result.READ_ONLY);
                }
                String id = created.id();
                Result closed = close(Result.SAVED);
                savedMarkerId = id;
                return closed;
            }
            case EDIT_MARKER: {
                if (!exploration.isWritable()) {
                    return remember(Result.READ_ONLY);
                }
                CustomMarker current = exploration.marker(markerId);
                if (current == null) {
                    return close(Result.GONE);
                }
                exploration.updateMarker(current.withType(type).withLabel(label.committedText())
                        .withNote(note.committedText()));
                String id = markerId;
                Result closed = close(Result.SAVED);
                savedMarkerId = id;
                return closed;
            }
            case DELETE_MARKER:
                if (!exploration.isWritable()) {
                    return remember(Result.READ_ONLY);
                }
                return close(exploration.removeMarker(markerId) ? Result.DELETED : Result.GONE);
            default:
                return null;
        }
    }

    /** Closes the editor and drops the draft. */
    public void cancel() {
        if (isActive()) {
            close(Result.CANCELLED);
        }
    }

    /** How the editor last closed or failed, once; {@code null} when nothing happened since. */
    public Result takeResult() {
        Result taken = result;
        result = null;
        return taken;
    }

    /** The id of the marker the last save created or edited, or {@code null}. */
    public String savedMarkerId() {
        return savedMarkerId;
    }

    // ------------------------------------------------------------ direct actions

    /**
     * Moves a marker, keeping its id, type, label and note. Not an editor: it applies at once.
     *
     * @param newY the new height, or {@code null} for a map column - a moved marker never keeps its
     *             old height for a new place
     */
    public boolean moveMarker(String id, String newDimensionId, int newX, Integer newY, int newZ) {
        CustomMarker marker = exploration.marker(id);
        return marker != null && newDimensionId != null
                && exploration.updateMarker(marker.movedTo(newDimensionId, newX, newY, newZ));
    }

    // ------------------------------------------------------------ draft

    /** The structure whose note is open, or {@code null}. */
    public StructureKey structureKey() {
        return structureKey;
    }

    public String subject() {
        return subject;
    }

    /** The marker being edited or deleted, or {@code null} for a new one. */
    public String markerId() {
        return markerId;
    }

    public String dimensionId() {
        return dimensionId;
    }

    public int x() {
        return x;
    }

    public Integer y() {
        return y;
    }

    public int z() {
        return z;
    }

    public MarkerType type() {
        return type;
    }

    public Field focus() {
        return focus;
    }

    public TextBuffer label() {
        return label;
    }

    public TextBuffer note() {
        return note;
    }

    private boolean hasLabel() {
        return mode == Mode.CREATE_MARKER || mode == Mode.EDIT_MARKER;
    }

    private TextBuffer focusedBuffer() {
        return focus == Field.LABEL && hasLabel() ? label : note;
    }

    private Result remember(Result outcome) {
        result = outcome;
        return outcome;
    }

    private Result close(Result outcome) {
        reset();
        return remember(outcome);
    }

    private void reset() {
        mode = Mode.NONE;
        structureKey = null;
        subject = null;
        markerId = null;
        dimensionId = null;
        x = 0;
        y = null;
        z = 0;
        type = MarkerType.CUSTOM;
        focus = Field.NOTE;
        savedMarkerId = null;
        label.set(null);
        note.set(null);
    }
}
