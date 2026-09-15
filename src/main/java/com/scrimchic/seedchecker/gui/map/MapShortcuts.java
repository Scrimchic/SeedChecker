package com.scrimchic.seedchecker.gui.map;

/**
 * What Escape and Delete do on the map screen, decided in one place.
 *
 * <p>Escape undoes the innermost thing first: an open editor - a deletion waiting for confirmation
 * is one - then the seed field, then the selection and the pointer together, and only when there is
 * nothing left does it close the screen. Delete only ever opens a marker's delete confirmation; it
 * never deletes, and does nothing for a structure or while an editor is open.
 */
public final class MapShortcuts {

    public enum EscapeAction {
        CANCEL_EDITOR,
        CANCEL_SEED_EDIT,
        CLEAR_SELECTION,
        CLOSE_SCREEN
    }

    private MapShortcuts() {
    }

    public static EscapeAction escape(boolean editorActive, boolean seedEditing, boolean hasSelection,
                                      boolean hasPointer) {
        if (editorActive) {
            return EscapeAction.CANCEL_EDITOR;
        }
        if (seedEditing) {
            return EscapeAction.CANCEL_SEED_EDIT;
        }
        if (hasSelection || hasPointer) {
            return EscapeAction.CLEAR_SELECTION;
        }
        return EscapeAction.CLOSE_SCREEN;
    }

    /** Whether Delete should open the delete confirmation for the selection. */
    public static boolean deleteOpensConfirmation(boolean editorActive, boolean seedEditing,
                                                  MapSelection selection) {
        return !editorActive && !seedEditing && selection != null && selection.isMarker();
    }
}
