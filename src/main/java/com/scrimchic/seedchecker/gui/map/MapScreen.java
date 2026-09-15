package com.scrimchic.seedchecker.gui.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.scrimchic.seedchecker.client.biome.BiomeTileManager;
import com.scrimchic.seedchecker.client.exploration.ExplorationManager;
import com.scrimchic.seedchecker.client.structure.StrongholdManager;
import com.scrimchic.seedchecker.client.structure.StructureGeometryManager;
import com.scrimchic.seedchecker.client.structure.StructureValidationManager;
import com.scrimchic.seedchecker.client.world.WorldProfileManager;
import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.core.map.MapViewport;
import com.scrimchic.seedchecker.core.map.MapViewportMemory;
import com.scrimchic.seedchecker.exploration.CustomMarker;
import com.scrimchic.seedchecker.exploration.MarkerType;
import com.scrimchic.seedchecker.exploration.StructureKey;
import com.scrimchic.seedchecker.exploration.StructureStatus;
import com.scrimchic.seedchecker.gui.map.edit.EditorKey;
import com.scrimchic.seedchecker.gui.map.edit.MapEditor;
import com.scrimchic.seedchecker.gui.map.edit.TextBuffer;
import com.scrimchic.seedchecker.gui.map.edit.WrappedText;
import com.scrimchic.seedchecker.gui.map.layer.CustomMarkerLayer;
import com.scrimchic.seedchecker.gui.map.layer.MapLayer;
import com.scrimchic.seedchecker.gui.map.layer.MapLayers;
import com.scrimchic.seedchecker.gui.map.layer.StrongholdLayer;
import com.scrimchic.seedchecker.gui.map.layer.StructureLayer;
import com.scrimchic.seedchecker.gui.map.layer.StructureMarkerLayer;
import com.scrimchic.seedchecker.platform.MinecraftBridge;
import com.scrimchic.seedchecker.platform.PlayerNavigator;
import com.scrimchic.seedchecker.platform.VanillaStructureData;
import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.world.DimensionType;
import com.scrimchic.seedchecker.world.PlayerPosition;
import com.scrimchic.seedchecker.world.SeedParser;
import com.scrimchic.seedchecker.world.TeleportCommand;
import com.scrimchic.seedchecker.world.WorldContext;
import com.scrimchic.seedchecker.world.WorldProfile;
import com.scrimchic.seedchecker.worldgen.GenerationPoint;
import com.scrimchic.seedchecker.worldgen.StrongholdPlacementEngine;
import com.scrimchic.seedchecker.worldgen.StrongholdPosition;
import com.scrimchic.seedchecker.worldgen.StructureBounds;
import com.scrimchic.seedchecker.worldgen.StructureGeometry;
import com.scrimchic.seedchecker.worldgen.StructureValidation;
import com.scrimchic.seedchecker.worldgen.StructureValidationKey;
import com.scrimchic.seedchecker.worldgen.StructureValidationStore;
import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;
import com.scrimchic.seedchecker.worldgen.biome.BiomeTileStore;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?} else if >=1.20 {
/*import net.minecraft.client.gui.GuiGraphics;*/
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.TranslatableComponent;*/
//?}

/**
 * The Seed Checker map screen: a pannable and zoomable world map with the map layers on top.
 *
 * <p>Everything except the render entry point and the input callbacks is shared between all
 * supported Minecraft versions; those signatures changed in 26.x and are isolated below.
 *
 * <h2>Interaction</h2>
 *
 * <p>Left click runs a panel action, or selects the object under the cursor - a predicted structure
 * or a custom marker, whichever {@link MapHitTest} picks - and starts panning. Right click on the map
 * places the pointer, the position "Add marker" and "Move" use: a button is clicked on a panel, so
 * the cursor itself can never be the target.
 *
 * <p>What is being edited, and every rule about it, lives in {@link MapEditor}; the screen only draws
 * it and forwards input. While an editor is open it owns the keyboard, clicks on the map pan but do
 * not change the selection, and Escape closes the editor rather than the screen.
 */
public final class MapScreen extends Screen {

    private static final String TITLE_KEY = "screen.seedchecker.map";

    private static final int COLOR_BACKGROUND = 0xFF0E1216;
    private static final int COLOR_GRID_MINOR = 0xFF1B232C;
    private static final int COLOR_GRID_MAJOR = 0xFF2B3845;
    private static final int COLOR_AXIS = 0xFF4C7BA8;
    private static final int COLOR_PANEL = 0xB0000000;
    private static final int COLOR_EDITOR_PANEL = 0xE0101418;
    private static final int COLOR_TEXT = 0xFFDCE3EA;
    private static final int COLOR_TEXT_DIM = 0xFF8C98A4;
    private static final int COLOR_TEXT_HOVER = 0xFFFFD479;
    private static final int COLOR_TEXT_ERROR = 0xFFE86A6A;
    private static final int COLOR_SECTION = 0xFF66727E;

    /** Near-black outline plus a white core, so the marker reads over any biome colour. */
    private static final int COLOR_PLAYER = 0xFFFFFFFF;
    private static final int COLOR_PLAYER_OUTLINE = 0xFF0B0E11;

    /** A selected structure's bounds: thin and translucent, so biomes and markers still read. */
    private static final int COLOR_BOUNDS = 0xC0FFD479;

    private static final int COLOR_POINTER = 0xFFFFD479;

    /** Every eighth grid line is drawn brighter. */
    private static final int MAJOR_GRID_MULTIPLE = 8;

    private static final int PANEL_MARGIN = 6;

    /** Rows one wheel notch scrolls a panel by. */
    private static final int PANEL_SCROLL_ROWS = 3;

    /** Half the player marker's size, used to cull it when it is off screen. */
    private static final int MARKER_REACH = 8;

    /** {@code -9223372036854775808} is the longest seed that can be typed. */
    private static final int MAX_SEED_LENGTH = 20;

    /** The editor panel's width, narrowed to the screen on a small window. */
    private static final int EDITOR_WIDTH = 260;

    /** How much of a note a side panel shows: lines, and characters per line. */
    private static final int NOTE_PREVIEW_LINES = 3;
    private static final int NOTE_PREVIEW_CHARS = 38;

    /** How much of a label a side panel shows. */
    private static final int LABEL_PREVIEW_CHARS = 32;

    private static final int ACTION_EDIT_SEED = 1;
    private static final int ACTION_CLEAR_SEED = 2;
    private static final int ACTION_CENTER_PLAYER = 3;
    private static final int ACTION_FOLLOW_PLAYER = 4;
    private static final int ACTION_RAW_CANDIDATES = 5;
    private static final int ACTION_SELECTION_CENTER = 6;
    private static final int ACTION_SELECTION_TELEPORT = 7;
    private static final int ACTION_SELECTION_COPY_COORDS = 8;
    private static final int ACTION_SELECTION_COPY_COMMAND = 9;
    private static final int ACTION_SELECTION_CLEAR = 10;

    /** One action per exploration status, from here upwards in declaration order. */
    private static final int ACTION_STATUS_BASE = 20;

    private static final int ACTION_EDIT_NOTE = 30;
    private static final int ACTION_ADD_MARKER_POINTER = 31;
    private static final int ACTION_ADD_MARKER_PLAYER = 32;
    private static final int ACTION_CLEAR_POINTER = 33;

    private static final int ACTION_MARKER_EDIT = 40;
    private static final int ACTION_MARKER_MOVE_POINTER = 41;
    private static final int ACTION_MARKER_MOVE_PLAYER = 42;
    private static final int ACTION_MARKER_DELETE = 43;

    private static final int ACTION_EDITOR_SAVE = 50;
    private static final int ACTION_EDITOR_CANCEL = 51;
    private static final int ACTION_EDITOR_FOCUS_LABEL = 52;
    private static final int ACTION_EDITOR_FOCUS_NOTE = 53;

    /** One action per marker type in the editor, from here upwards in declaration order. */
    private static final int ACTION_EDITOR_TYPE_BASE = 60;

    /**
     * How far a click may miss a structure's chunk and still hit its marker, in chunks.
     *
     * <p>Zoomed out, a marker is drawn at a fixed pixel size that covers several chunks, so the
     * chunk directly under the cursor is often not the one the player was aiming at.
     */
    private static final int MAX_SELECT_CHUNK_RADIUS = 4;

    /** Layer toggles occupy the action ids from here upwards, one per layer. */
    private static final int ACTION_LAYER_BASE = 100;

    /**
     * Layer switches live for the whole client session rather than per screen, so reopening the
     * map does not undo them. Saving them to disk comes with the rest of the storage work.
     */
    private static final MapLayers LAYERS = MapLayers.createDefault();

    /**
     * Where the map was left, per world, for the life of the client. Like the layer switches it is
     * session state rather than a saved setting.
     */
    private static final MapViewportMemory MEMORY = new MapViewportMemory();

    private final MapViewport viewport = new MapViewport();

    private boolean dragging;

    /** The panels as they were last drawn, kept so a click can be matched against their rows. */
    private TextPanel contextPanel;
    private TextPanel rightPanel;
    private TextPanel debugPanel;

    /** How far each panel is scrolled, in rows; clamped by the panel every frame. */
    private int contextScroll;
    private int selectionScroll;
    private int editorScroll;
    private int debugScroll;

    /**
     * What is selected, if anything.
     *
     * <p>Per screen rather than per session: reopening the map with a stale selection would be more
     * confusing than helpful.
     */
    private MapSelection selection;

    /** One line of feedback for the last selection action, e.g. that a copy happened. */
    private String selectionNotice;

    /** Created on first use: the exploration manager exists once the client has started. */
    private MapEditor editor;
    private String editorNotice;

    /** Where the editor's text cursor was drawn last frame, and what it was drawn for. */
    private int editorCursorRow = -1;
    private int editorCursorX;
    private String editorCursorState = "";

    /** The last wrapping, kept while the text and width stay the same. */
    private String wrapText;
    private int wrapWidth;
    private List<WrappedText.Line> wrapLines;

    /** The block right-clicked on the map, for adding or moving a marker; one per world and dimension. */
    private boolean pointerSet;
    private int pointerX;
    private int pointerZ;

    /** The world and dimension the selection, pointer and editor belong to. */
    private String interactionKey;

    private boolean followPlayer;

    private boolean editingSeed;
    private String seedInput = "";
    private String seedError;

    public MapScreen() {
        super(title());
    }

    private static Component title() {
        //? if >=1.19 {
        return Component.translatable(TITLE_KEY);
        //?} else {
        /*return new TranslatableComponent(TITLE_KEY);*/
        //?}
    }

    /**
     * Chooses where the map starts.
     *
     * <p>Runs on open and on resize. The first time the map is opened in a world there is nothing
     * remembered, so it centres on the player; after that it comes back to wherever it was left.
     */
    @Override
    protected void init() {
        super.init();
        viewport.resize(this.width, this.height);

        ActiveWorld world = WorldProfileManager.get().currentWorld();
        if (MEMORY.restore(memoryKeyOf(world), viewport)) {
            followPlayer = MEMORY.followPlayer();
            return;
        }
        followPlayer = false;
        centerOnPlayer();
    }

    private MapEditor editor() {
        if (editor == null) {
            editor = new MapEditor(ExplorationManager.get());
        }
        return editor;
    }

    /**
     * Which world the remembered position belongs to.
     *
     * <p>Profile and dimension, deliberately not the seed: typing a seed in changes what the map
     * draws, not where the player is standing, so it should not move the view.
     */
    private static String memoryKeyOf(ActiveWorld world) {
        WorldProfile profile = world.profile();
        String base = profile != null ? profile.identity().storageKey() : "no-profile";
        return base + "|" + world.context().dimensionId();
    }

    private boolean centerOnPlayer() {
        PlayerPosition player = MinecraftBridge.currentPlayerPosition();
        if (player == null) {
            return false;
        }
        viewport.setCenter(player.x(), player.z());
        return true;
    }

    // ---------------------------------------------------------------- drawing

    private void draw(MapCanvas canvas, int mouseX, int mouseY) {
        viewport.resize(this.width, this.height);

        // Re-read every frame so the panel keeps up with world loads and dimension changes. The
        // profile behind it is already in memory; nothing here touches the filesystem.
        ActiveWorld world = WorldProfileManager.get().currentWorld();
        PlayerPosition player = MinecraftBridge.currentPlayerPosition();

        if (followPlayer && player != null) {
            viewport.setCenter(player.x(), player.z());
        }
        MEMORY.remember(memoryKeyOf(world), viewport, followPlayer);

        ChunkRange visible = ChunkRange.visibleIn(viewport);
        BiomeMapKey structureMap = world.hasSeed() ? StructureValidationKey.mapKeyFor(world) : null;
        dropStaleInteraction(world, structureMap);
        if (structureMap != null) {
            StructureGeometryManager.get().useMap(structureMap);
            // Even with the stronghold layer switched off, so a seed or world change always drops
            // the old list.
            StrongholdManager.get().useMap(structureMap);
        }

        canvas.fill(0, 0, this.width, this.height, COLOR_BACKGROUND);
        drawGrid(canvas);
        LAYERS.renderAll(canvas, viewport, visible, world);
        drawSelectedBounds(canvas, world);
        drawSelectedMarker(canvas);
        drawPointer(canvas);

        // After every layer, so the player is never hidden behind a structure marker.
        if (player != null) {
            drawPlayerMarker(canvas, player);
        }

        // Both left panels share the screen's height. The debug readout is capped at a third of it
        // and the context panel takes the rest; either scrolls rather than running off a small
        // window, which the layer list alone would do below a GUI height of about 400.
        debugPanel = buildDebugPanel(mouseX, mouseY);
        debugPanel.limitHeight((this.height - PANEL_MARGIN * 3) / 3, debugScroll);
        int debugHeight = debugPanel.height(canvas);

        contextPanel = buildContextPanel(world, player, visible);
        contextPanel.limitHeight(this.height - PANEL_MARGIN * 3 - debugHeight, contextScroll);
        contextPanel.draw(canvas, PANEL_MARGIN, PANEL_MARGIN, mouseX, mouseY);
        contextScroll = contextPanel.firstRow();

        debugPanel.draw(canvas, PANEL_MARGIN, this.height - PANEL_MARGIN - debugHeight, mouseX,
                mouseY);
        debugScroll = debugPanel.firstRow();

        drawRightPanel(canvas, world, player, mouseX, mouseY);
    }

    /**
     * A selection, the pointer and an open editor belong to one world and dimension; a structure
     * selection also to one predicted map, and a marker selection to a marker that still exists.
     */
    private void dropStaleInteraction(ActiveWorld world, BiomeMapKey structureMap) {
        String key = memoryKeyOf(world);
        if (!key.equals(interactionKey)) {
            interactionKey = key;
            editor().cancel();
            editor().takeResult();
            editorNotice = null;
            pointerSet = false;
            clearSelection();
            return;
        }
        if (selection == null) {
            return;
        }
        if (selection.isStructure() && (structureMap == null || !structureMap.equals(selection.map()))) {
            // A note open for the structure would be saved under a seed the map no longer shows.
            if (editor().mode() == MapEditor.Mode.STRUCTURE_NOTE) {
                editor().cancel();
                editor().takeResult();
            }
            clearSelection();
        } else if (selection.isMarker() && selectedMarker() == null) {
            clearSelection();
        }
    }

    private void drawRightPanel(MapCanvas canvas, ActiveWorld world, PlayerPosition player, int mouseX,
                                int mouseY) {
        int maxHeight = this.height - PANEL_MARGIN * 2;
        if (editor().isActive()) {
            TextPanel panel = buildEditorPanel(canvas);
            panel.limitHeight(maxHeight, editorScroll);
            keepEditorCursorVisible(panel.visibleRowCount(canvas));
            panel.limitHeight(maxHeight, editorScroll);
            int left = Math.max(PANEL_MARGIN, this.width - PANEL_MARGIN - panel.width(canvas));
            panel.draw(canvas, left, PANEL_MARGIN, mouseX, mouseY);
            editorScroll = panel.firstRow();
            drawEditorCursor(canvas, panel);
            rightPanel = panel;
            return;
        }
        if (selection == null) {
            rightPanel = null;
            return;
        }
        TextPanel panel = selection.isMarker()
                ? buildMarkerPanel(world, player, selectedMarker())
                : buildStructurePanel(world);
        panel.limitHeight(maxHeight, selectionScroll);
        panel.draw(canvas, Math.max(PANEL_MARGIN, this.width - PANEL_MARGIN - panel.width(canvas)),
                PANEL_MARGIN, mouseX, mouseY);
        selectionScroll = panel.firstRow();
        rightPanel = panel;
    }

    /**
     * What the selected structure candidate is, and what can be done with it.
     *
     * <p>Deliberately says <em>candidate anchor</em> rather than anything resembling a structure
     * position. For everything except an exactly validated structure the anchor is only the middle
     * of the chunk grid placement picked; vanilla's real generation point can be tens of blocks
     * away.
     */
    private TextPanel buildStructurePanel(ActiveWorld world) {
        StructureMarkerLayer layer = selection.layer();
        int chunkX = selection.chunkX();
        int chunkZ = selection.chunkZ();
        int anchorX = (chunkX << 4) + 8;
        int anchorZ = (chunkZ << 4) + 8;

        TextPanel panel = new TextPanel(COLOR_PANEL, COLOR_TEXT_HOVER);
        panel.line("SELECTED", COLOR_SECTION);
        panel.line(layer.displayName(), COLOR_TEXT);
        String detail = layer.describeSelection(world, chunkX, chunkZ);
        if (detail != null) {
            panel.line(detail, COLOR_TEXT_DIM);
        }

        StructureValidation result = layer.resultAt(world, chunkX, chunkZ);
        GenerationPoint point = result == null ? null : result.generationPoint();
        panel.blank();
        panel.line("Status    " + statusLabel(result), COLOR_TEXT);
        if (result == null && StructureValidationManager.get().isWaitingForStructureData(layer.type())) {
            panel.line("          loading vanilla structure data", COLOR_TEXT_DIM);
        }
        if (result != null && result.reason() != null) {
            panel.line("          " + result.reason(), COLOR_TEXT_DIM);
        }
        if (result != null && result.sampledBiomeId() != null) {
            panel.line("Biome     " + result.sampledBiomeId(), COLOR_TEXT_DIM);
        }
        if (result != null && result.variant() != null) {
            panel.line("Variant   " + result.variant(), COLOR_TEXT_DIM);
        }

        panel.blank();
        panel.line("Candidate chunk  " + chunkX + ", " + chunkZ, COLOR_TEXT_DIM);
        if (point != null) {
            // The stub position vanilla assembles from - not the middle of the structure, which
            // for a jigsaw can be a long way off. The bounds below carry their own centre.
            panel.line("Generation position (exact)", COLOR_TEXT);
            panel.line("X " + point.x() + "   Y " + point.y() + "   Z " + point.z(), COLOR_TEXT);
        } else if (result != null && result.isExact() && result.isCompatible()) {
            // Exact, on a version that computes no generation point: the chunk is certain, the
            // block the start is built from is not.
            panel.line("Chunk centre " + anchorX + ", " + anchorZ, COLOR_TEXT);
            panel.line("exact chunk; no generation point on this version", COLOR_TEXT_DIM);
        } else {
            panel.line("Candidate anchor " + anchorX + ", " + anchorZ, COLOR_TEXT);
            panel.line("the candidate chunk centre, not a structure position", COLOR_TEXT_DIM);
        }
        appendBoundsRows(panel, result);
        appendStructureExplorationRows(panel, world, result);
        appendNavigationRows(panel, "keeps your Y");
        panel.action(ACTION_SELECTION_CLEAR, "[Close]", COLOR_TEXT_DIM);
        if (selectionNotice != null) {
            panel.line(selectionNotice, COLOR_TEXT_DIM);
        }
        return panel;
    }

    /**
     * The player's record of the selected structure: its status as one row of buttons, and its note.
     *
     * <p>Offered for any structure the map is showing, exact or not - the player may have checked a
     * non-exact one in person - but not for a rejected candidate, which is not a structure.
     */
    private void appendStructureExplorationRows(TextPanel panel, ActiveWorld world, StructureValidation result) {
        panel.blank();
        panel.line("EXPLORATION", COLOR_SECTION);
        ExplorationManager exploration = ExplorationManager.get();
        StructureKey key = selectedStructureKey(world);
        if (key == null || !exploration.isActive()) {
            panel.line("no world profile to record it in", COLOR_TEXT_DIM);
            return;
        }
        if (result == null || result.isRejected()) {
            panel.line(result == null ? "available once checked" : "not a structure", COLOR_TEXT_DIM);
            return;
        }
        StructureStatus current = exploration.statusOf(key);
        panel.line("Status    " + current.displayName(), COLOR_TEXT);
        if (exploration.isWritable()) {
            StructureStatus[] statuses = StructureStatus.values();
            appendStatusButtons(panel, statuses, 0, 3, current);
            appendStatusButtons(panel, statuses, 3, statuses.length, current);
        }
        String note = exploration.noteOf(key);
        panel.line("Note", COLOR_TEXT);
        appendNotePreview(panel, note);
        if (exploration.isWritable()) {
            panel.action(ACTION_EDIT_NOTE, note == null ? "[Add note]" : "[Edit note]", COLOR_TEXT);
        } else {
            panel.line("read-only: " + exploration.readOnlyReason(), COLOR_TEXT_DIM);
        }
    }

    private static void appendStatusButtons(TextPanel panel, StructureStatus[] statuses, int from, int to,
                                             StructureStatus current) {
        int[] actions = new int[to - from];
        String[] labels = new String[to - from];
        int[] colors = new int[to - from];
        for (int i = from; i < to; i++) {
            boolean selected = statuses[i] == current;
            actions[i - from] = ACTION_STATUS_BASE + i;
            labels[i - from] = selected ? "[" + statuses[i].displayName() + "]" : statuses[i].displayName();
            colors[i - from] = selected ? COLOR_TEXT : COLOR_TEXT_DIM;
        }
        panel.buttons(actions, labels, colors);
    }

    private static void appendNotePreview(TextPanel panel, String note) {
        NotePreview preview = NotePreview.of(note, NOTE_PREVIEW_LINES, NOTE_PREVIEW_CHARS);
        if (preview.isEmpty()) {
            panel.line("  (none)", COLOR_TEXT_DIM);
            return;
        }
        for (String line : preview.lines()) {
            panel.line("  " + line, COLOR_TEXT_DIM);
        }
        if (preview.hiddenLines() > 0) {
            panel.line("  (+" + preview.hiddenLines() + " more lines)", COLOR_TEXT_DIM);
        }
    }

    /** A custom marker, where it is, its note, and what can be done with it. */
    private TextPanel buildMarkerPanel(ActiveWorld world, PlayerPosition player, CustomMarker marker) {
        ExplorationManager exploration = ExplorationManager.get();
        TextPanel panel = new TextPanel(COLOR_PANEL, COLOR_TEXT_HOVER);
        panel.line("CUSTOM MARKER", COLOR_SECTION);
        panel.line("Type      " + marker.type().displayName(), COLOR_TEXT);
        panel.line("Label     " + (marker.label() == null ? "(none)" : shortLabel(marker.label())),
                marker.label() == null ? COLOR_TEXT_DIM : COLOR_TEXT);
        panel.line("Dimension " + dimensionName(marker.dimensionId()), COLOR_TEXT_DIM);

        panel.blank();
        panel.line("Position", COLOR_TEXT);
        panel.line("X " + marker.x() + "   Y " + (marker.y() == null ? "unknown" : marker.y().toString())
                + "   Z " + marker.z(), COLOR_TEXT);

        panel.blank();
        panel.line("Note", COLOR_TEXT);
        appendNotePreview(panel, marker.note());

        appendNavigationRows(panel, marker.y() == null ? "keeps your Y" : "copies the exact Y");

        if (exploration.isWritable()) {
            panel.blank();
            panel.buttons(new int[] {ACTION_MARKER_EDIT, ACTION_MARKER_DELETE},
                    new String[] {"[Edit]", "[Delete]"}, new int[] {COLOR_TEXT, COLOR_TEXT});
            if (pointerSet) {
                panel.action(ACTION_MARKER_MOVE_POINTER,
                        "[Move to pointer] " + pointerX + ", " + pointerZ, COLOR_TEXT);
            } else {
                panel.line("right-click the map to set a pointer to move it to", COLOR_TEXT_DIM);
            }
            if (player != null) {
                panel.action(ACTION_MARKER_MOVE_PLAYER, "[Move to player]", COLOR_TEXT);
            }
        } else {
            panel.blank();
            panel.line("read-only: " + exploration.readOnlyReason(), COLOR_TEXT_DIM);
        }
        panel.action(ACTION_SELECTION_CLEAR, "[Close]", COLOR_TEXT_DIM);
        if (selectionNotice != null) {
            panel.line(selectionNotice, COLOR_TEXT_DIM);
        }
        return panel;
    }

    /** Center, Teleport and the two copies, shared by every kind of selection. */
    private static void appendNavigationRows(TextPanel panel, String copyCommandHint) {
        panel.blank();
        panel.action(ACTION_SELECTION_CENTER, "[Center]", COLOR_TEXT);
        boolean canTeleport = PlayerNavigator.canTeleport();
        panel.action(ACTION_SELECTION_TELEPORT,
                canTeleport ? "[Teleport] keeps your Y" : "[Teleport] needs command permission",
                canTeleport ? COLOR_TEXT : COLOR_TEXT_DIM);
        panel.action(ACTION_SELECTION_COPY_COORDS, "[Copy coords]", COLOR_TEXT);
        panel.action(ACTION_SELECTION_COPY_COMMAND, "[Copy /tp] " + copyCommandHint, COLOR_TEXT);
    }

    // ------------------------------------------------------------------ editor

    /**
     * The open editor: a structure note, a marker draft, or a deletion to confirm. Text wraps to the
     * panel's width, which is at most {@link #EDITOR_WIDTH} and never more than the screen allows,
     * and the panel scrolls to keep the cursor in view.
     */
    private TextPanel buildEditorPanel(MapCanvas canvas) {
        MapEditor editor = editor();
        int panelWidth = Math.min(EDITOR_WIDTH, this.width - PANEL_MARGIN * 2);
        int textWidth = Math.max(40, panelWidth - TextPanel.PADDING * 2 - 4);
        TextPanel panel = new TextPanel(COLOR_EDITOR_PANEL, COLOR_TEXT_HOVER).minWidth(panelWidth);
        editorCursorRow = -1;

        if (editor.mode() == MapEditor.Mode.DELETE_MARKER) {
            CustomMarker marker = ExplorationManager.get().marker(editor.markerId());
            panel.line("DELETE MARKER", COLOR_SECTION);
            if (marker != null) {
                panel.line(marker.type().displayName() + (marker.label() == null ? ""
                        : "  " + shortLabel(marker.label())), COLOR_TEXT);
                panel.line("X " + marker.x() + "   Z " + marker.z(), COLOR_TEXT_DIM);
            }
            panel.line("This cannot be undone.", COLOR_TEXT_DIM);
            panel.blank();
            panel.buttons(new int[] {ACTION_EDITOR_SAVE, ACTION_EDITOR_CANCEL},
                    new String[] {"[Confirm delete]", "[Cancel]"}, new int[] {COLOR_TEXT_ERROR, COLOR_TEXT});
            panel.line("Esc cancels", COLOR_TEXT_DIM);
            appendEditorNotice(panel);
            return panel;
        }

        if (editor.mode() == MapEditor.Mode.STRUCTURE_NOTE) {
            panel.line("NOTE", COLOR_SECTION);
            panel.line(editor.subject(), COLOR_TEXT_DIM);
            panel.blank();
            appendTextArea(panel, canvas, editor.note(), textWidth, true);
        } else {
            panel.line(editor.mode() == MapEditor.Mode.CREATE_MARKER ? "NEW MARKER" : "EDIT MARKER",
                    COLOR_SECTION);
            panel.line("Dimension " + dimensionName(editor.dimensionId()), COLOR_TEXT_DIM);
            panel.line("X " + editor.x() + "   Y " + (editor.y() == null ? "unknown" : editor.y().toString())
                    + "   Z " + editor.z(), COLOR_TEXT_DIM);
            panel.blank();
            panel.line("Type", COLOR_TEXT);
            MarkerType[] types = MarkerType.values();
            appendTypeButtons(panel, types, 0, 4, editor.type());
            appendTypeButtons(panel, types, 4, types.length, editor.type());
            panel.blank();
            appendLabelRow(panel, canvas, editor.label(), textWidth,
                    editor.focus() == MapEditor.Field.LABEL);
            boolean noteFocus = editor.focus() == MapEditor.Field.NOTE;
            panel.action(ACTION_EDITOR_FOCUS_NOTE, noteFocus ? "Note" : "Note  (click to type)",
                    noteFocus ? COLOR_TEXT : COLOR_TEXT_DIM);
            appendTextArea(panel, canvas, editor.note(), textWidth, noteFocus);
        }
        panel.line(editor.note().length() + " / " + editor.note().maxLength(), COLOR_TEXT_DIM);
        panel.blank();
        panel.buttons(new int[] {ACTION_EDITOR_SAVE, ACTION_EDITOR_CANCEL}, new String[] {"[Save]", "[Cancel]"},
                new int[] {COLOR_TEXT, COLOR_TEXT});
        panel.line("Ctrl+Enter saves, Esc cancels", COLOR_TEXT_DIM);
        if (editor.mode() != MapEditor.Mode.STRUCTURE_NOTE) {
            panel.line("Tab switches label and note", COLOR_TEXT_DIM);
        }
        appendEditorNotice(panel);
        return panel;
    }

    private void appendEditorNotice(TextPanel panel) {
        if (editorNotice != null) {
            panel.line(editorNotice, COLOR_TEXT_ERROR);
        }
    }

    private static void appendTypeButtons(TextPanel panel, MarkerType[] types, int from, int to, MarkerType current) {
        int[] actions = new int[to - from];
        String[] labels = new String[to - from];
        int[] colors = new int[to - from];
        for (int i = from; i < to; i++) {
            boolean selected = types[i] == current;
            actions[i - from] = ACTION_EDITOR_TYPE_BASE + i;
            labels[i - from] = selected ? "[" + types[i].displayName() + "]" : types[i].displayName();
            colors[i - from] = selected ? COLOR_TEXT : COLOR_TEXT_DIM;
        }
        panel.buttons(actions, labels, colors);
    }

    /**
     * The one-line label field. A label longer than the panel is shown as the part around the
     * cursor, starting with "..." when its beginning is cut off.
     */
    private void appendLabelRow(TextPanel panel, MapCanvas canvas, TextBuffer label, int textWidth,
                                boolean focused) {
        String prefix = "Label  ";
        String text = label.text();
        int available = textWidth - canvas.textWidth(prefix) - canvas.textWidth("...");
        int start = 0;
        while (start < label.cursor() && canvas.textWidth(text.substring(start, label.cursor())) > available) {
            start += Character.charCount(text.codePointAt(start));
        }
        int end = text.length();
        while (end > label.cursor() && canvas.textWidth(text.substring(start, end)) > available) {
            end = text.offsetByCodePoints(end, -1);
        }
        String shownPrefix = prefix + (start > 0 ? "..." : "");
        int row = panel.rowCount();
        panel.action(ACTION_EDITOR_FOCUS_LABEL, shownPrefix + text.substring(start, end),
                focused ? COLOR_TEXT : COLOR_TEXT_DIM);
        if (focused) {
            editorCursorRow = row;
            editorCursorX = canvas.textWidth(shownPrefix + text.substring(start, label.cursor()));
        }
    }

    private void appendTextArea(TextPanel panel, MapCanvas canvas, TextBuffer buffer, int textWidth,
                                boolean focused) {
        final MapCanvas measureCanvas = canvas;
        String text = buffer.text();
        if (wrapLines == null || wrapWidth != textWidth || !text.equals(wrapText)) {
            wrapLines = WrappedText.wrap(text, textWidth, new WrappedText.Measure() {
                @Override
                public int width(String part) {
                    return measureCanvas.textWidth(part);
                }
            });
            wrapText = text;
            wrapWidth = textWidth;
        }
        int cursorLine = WrappedText.lineOf(wrapLines, buffer.cursor());
        for (int i = 0; i < wrapLines.size(); i++) {
            WrappedText.Line line = wrapLines.get(i);
            int row = panel.rowCount();
            panel.line(text.substring(line.start(), line.end()), COLOR_TEXT);
            if (focused && i == cursorLine) {
                int cursor = Math.max(line.start(), Math.min(line.end(), buffer.cursor()));
                editorCursorRow = row;
                editorCursorX = canvas.textWidth(text.substring(line.start(), cursor));
            }
        }
    }

    /** Scrolls the editor to the cursor, but only when the cursor moved - the wheel still scrolls. */
    private void keepEditorCursorVisible(int visibleRows) {
        MapEditor editor = editor();
        String state = editor.mode() + "|" + editor.focus() + "|" + editor.label().cursor() + "|"
                + editor.note().cursor() + "|" + editor.note().length() + "|" + editor.label().length();
        if (editorCursorRow < 0 || state.equals(editorCursorState)) {
            return;
        }
        editorCursorState = state;
        if (editorCursorRow < editorScroll) {
            editorScroll = editorCursorRow;
        } else if (editorCursorRow >= editorScroll + visibleRows) {
            editorScroll = editorCursorRow - visibleRows + 1;
        }
    }

    private void drawEditorCursor(MapCanvas canvas, TextPanel panel) {
        if (editorCursorRow < 0) {
            return;
        }
        int top = panel.rowTop(editorCursorRow);
        if (top == Integer.MIN_VALUE) {
            return;
        }
        int x = panel.textLeft() + editorCursorX;
        canvas.fill(x, top - 1, x + 1, top + canvas.lineHeight(), COLOR_TEXT_HOVER);
    }

    /** Reacts once to how the editor closed: select what was saved, drop what was deleted. */
    private void afterEditorInput() {
        MapEditor.Result result = editor().takeResult();
        if (result == null) {
            return;
        }
        switch (result) {
            case SAVED:
                editorNotice = null;
                if (editor().savedMarkerId() != null) {
                    selection = MapSelection.marker(editor().savedMarkerId());
                    selectionScroll = 0;
                }
                selectionNotice = "saved";
                break;
            case DELETED:
                editorNotice = null;
                clearSelection();
                break;
            case GONE:
                editorNotice = null;
                clearSelection();
                break;
            case READ_ONLY:
                String reason = ExplorationManager.get().readOnlyReason();
                editorNotice = "cannot save: " + (reason == null ? "no world profile" : reason);
                break;
            default:
                editorNotice = null;
                break;
        }
    }

    // ----------------------------------------------------------- map overlays

    private void clearSelection() {
        selection = null;
        rightPanel = null;
        selectionNotice = null;
    }

    /** @return the selected marker, or {@code null} when none is selected or it is gone or elsewhere */
    private CustomMarker selectedMarker() {
        if (selection == null || !selection.isMarker()) {
            return null;
        }
        CustomMarker marker = ExplorationManager.get().marker(selection.markerId());
        String dimensionId = WorldProfileManager.get().currentWorld().context().dimensionId();
        return marker != null && marker.dimensionId().equals(dimensionId) ? marker : null;
    }

    /**
     * The exploration key of the selected structure: the seed the map is drawn from, the dimension,
     * the layer's type and the candidate chunk.
     *
     * @return the key, or {@code null} when no structure is selected or no seed is known
     */
    private StructureKey selectedStructureKey(ActiveWorld world) {
        return selection == null || !selection.isStructure() ? null
                : StructureKey.predictedIn(world, selection.layer().type(), selection.chunkX(),
                        selection.chunkZ());
    }

    /**
     * The selected structure's geometry, asked for if it is not known yet.
     *
     * <p>Only for an exactly validated structure that generates: nothing else has geometry worth
     * assembling, and a non-exact one must not be given bounds it does not have.
     *
     * @return the geometry, or {@code null} while it is being assembled or when there is none
     */
    private StructureGeometry selectedGeometry(StructureValidation result) {
        if (selection == null || !selection.isStructure() || result == null || !result.isExact()
                || !result.isCompatible()) {
            return null;
        }
        StructureValidationKey key = new StructureValidationKey(selection.map(), selection.layer().type(),
                selection.chunkX(), selection.chunkZ());
        StructureGeometryManager geometry = StructureGeometryManager.get();
        StructureGeometry known = geometry.resultIfReady(key);
        if (known == null) {
            // Deduplicated by the store, so asking every frame until it arrives queues one job.
            geometry.request(key, result.variant());
        }
        return known;
    }

    private void appendBoundsRows(TextPanel panel, StructureValidation result) {
        panel.blank();
        if (result == null || !result.isCompatible()) {
            panel.line("Bounds    unavailable", COLOR_TEXT_DIM);
            return;
        }
        if (!result.isExact()) {
            panel.line("Bounds    not computed for a non-exact structure", COLOR_TEXT_DIM);
            return;
        }
        StructureGeometry geometry = selectedGeometry(result);
        if (geometry == null) {
            panel.line("Bounds    loading...", COLOR_TEXT_DIM);
            return;
        }
        if (!geometry.isAvailable()) {
            panel.line("Bounds    unavailable", COLOR_TEXT_DIM);
            panel.line("          " + geometry.unavailableReason(), COLOR_TEXT_DIM);
            return;
        }
        StructureBounds bounds = geometry.bounds();
        panel.line("Bounds (all pieces)", COLOR_TEXT);
        panel.line("X " + bounds.minX() + ".." + bounds.maxX(), COLOR_TEXT);
        panel.line("Y " + bounds.minY() + ".." + bounds.maxY(), COLOR_TEXT);
        panel.line("Z " + bounds.minZ() + ".." + bounds.maxZ(), COLOR_TEXT);
        panel.line("Bounds center " + bounds.centerX() + ", " + bounds.centerY() + ", "
                + bounds.centerZ(), COLOR_TEXT_DIM);
    }

    /** A thin outline of the selected structure's bounds, drawn only once they are known. */
    private void drawSelectedBounds(MapCanvas canvas, ActiveWorld world) {
        if (selection == null || !selection.isStructure()) {
            return;
        }
        StructureGeometry geometry = selectedGeometry(
                selection.layer().resultAt(world, selection.chunkX(), selection.chunkZ()));
        if (geometry == null || !geometry.isAvailable()) {
            return;
        }
        StructureBounds bounds = geometry.bounds();
        double left = viewport.blockToScreenX(bounds.minX());
        double right = viewport.blockToScreenX(bounds.maxX() + 1.0);
        double top = viewport.blockToScreenY(bounds.minZ());
        double bottom = viewport.blockToScreenY(bounds.maxZ() + 1.0);
        if (right < 0 || bottom < 0 || left > this.width || top > this.height) {
            return;
        }
        int x0 = clampToScreen(left, this.width);
        int y0 = clampToScreen(top, this.height);
        int x1 = Math.max(x0 + 1, clampToScreen(right, this.width));
        int y1 = Math.max(y0 + 1, clampToScreen(bottom, this.height));
        drawOutline(canvas, x0, y0, x1, y1, COLOR_BOUNDS);
    }

    /** A ring around the selected custom marker. */
    private void drawSelectedMarker(MapCanvas canvas) {
        CustomMarker marker = selectedMarker();
        if (marker == null) {
            return;
        }
        int x = (int) Math.round(CustomMarkerLayer.screenX(viewport, marker));
        int y = (int) Math.round(CustomMarkerLayer.screenY(viewport, marker));
        drawOutline(canvas, x - 8, y - 8, x + 9, y + 9, COLOR_TEXT_HOVER);
    }

    /** The right-click pointer: a small open cross, so the block under it stays visible. */
    private void drawPointer(MapCanvas canvas) {
        if (!pointerSet) {
            return;
        }
        int x = (int) Math.round(viewport.blockToScreenX(pointerX + 0.5));
        int y = (int) Math.round(viewport.blockToScreenY(pointerZ + 0.5));
        if (x < -MARKER_REACH || y < -MARKER_REACH || x > this.width + MARKER_REACH
                || y > this.height + MARKER_REACH) {
            return;
        }
        canvas.fill(x - 6, y - 1, x - 1, y + 2, COLOR_PLAYER_OUTLINE);
        canvas.fill(x + 2, y - 1, x + 7, y + 2, COLOR_PLAYER_OUTLINE);
        canvas.fill(x - 1, y - 6, x + 2, y - 1, COLOR_PLAYER_OUTLINE);
        canvas.fill(x - 1, y + 2, x + 2, y + 7, COLOR_PLAYER_OUTLINE);
        canvas.fill(x - 5, y, x - 1, y + 1, COLOR_POINTER);
        canvas.fill(x + 2, y, x + 6, y + 1, COLOR_POINTER);
        canvas.fill(x, y - 5, x + 1, y - 1, COLOR_POINTER);
        canvas.fill(x, y + 2, x + 1, y + 6, COLOR_POINTER);
    }

    private static void drawOutline(MapCanvas canvas, int x0, int y0, int x1, int y1, int color) {
        canvas.fill(x0, y0, x1, y0 + 1, color);
        canvas.fill(x0, y1 - 1, x1, y1, color);
        canvas.fill(x0, y0, x0 + 1, y1, color);
        canvas.fill(x1 - 1, y0, x1, y1, color);
    }

    /** Pins an off-screen edge just outside the screen, so a huge box cannot overflow an int. */
    private static int clampToScreen(double coordinate, int size) {
        return (int) Math.round(Math.max(-2.0, Math.min(size + 2.0, coordinate)));
    }

    private static String statusLabel(StructureValidation result) {
        if (result == null) {
            return "checking";
        }
        if (result.isRejected()) {
            return "INCOMPATIBLE";
        }
        if (!result.isCompatible()) {
            return "UNKNOWN";
        }
        // The distinction the exact paths bought: whether this is "vanilla generates one here" or
        // "vanilla might". Read off the result, because asking the validator would load the
        // version's structure data on the render thread.
        return result.isExact() ? "COMPATIBLE (exact)" : "COMPATIBLE (non-exact)";
    }

    /**
     * A crosshair with a bright core and a dark outline, at a fixed size in pixels.
     *
     * <p>Fixed rather than scaled: a marker sized in blocks would vanish when zoomed out and swamp
     * the map when zoomed in.
     */
    private void drawPlayerMarker(MapCanvas canvas, PlayerPosition player) {
        int centerX = (int) Math.round(viewport.blockToScreenX(player.x()));
        int centerY = (int) Math.round(viewport.blockToScreenY(player.z()));
        if (centerX < -MARKER_REACH || centerY < -MARKER_REACH
                || centerX > this.width + MARKER_REACH || centerY > this.height + MARKER_REACH) {
            return;
        }

        canvas.fill(centerX - 7, centerY - 1, centerX + 8, centerY + 2, COLOR_PLAYER_OUTLINE);
        canvas.fill(centerX - 1, centerY - 7, centerX + 2, centerY + 8, COLOR_PLAYER_OUTLINE);
        canvas.fill(centerX - 6, centerY, centerX + 7, centerY + 1, COLOR_PLAYER);
        canvas.fill(centerX, centerY - 6, centerX + 1, centerY + 7, COLOR_PLAYER);
        canvas.fill(centerX - 3, centerY - 3, centerX + 4, centerY + 4, COLOR_PLAYER_OUTLINE);
        canvas.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, COLOR_PLAYER);
    }

    private void drawGrid(MapCanvas canvas) {
        int step = viewport.gridStepBlocks();
        long major = (long) step * MAJOR_GRID_MULTIPLE;

        long firstX = floorToStep(viewport.screenToBlockX(0.0), step);
        double lastX = viewport.screenToBlockX(this.width);
        for (long blockX = firstX; blockX <= lastX; blockX += step) {
            int screenX = (int) Math.round(viewport.blockToScreenX(blockX));
            if (screenX < 0 || screenX >= this.width) {
                continue;
            }
            canvas.fill(screenX, 0, screenX + 1, this.height, gridColor(blockX, major));
        }

        long firstZ = floorToStep(viewport.screenToBlockZ(0.0), step);
        double lastZ = viewport.screenToBlockZ(this.height);
        for (long blockZ = firstZ; blockZ <= lastZ; blockZ += step) {
            int screenY = (int) Math.round(viewport.blockToScreenY(blockZ));
            if (screenY < 0 || screenY >= this.height) {
                continue;
            }
            canvas.fill(0, screenY, this.width, screenY + 1, gridColor(blockZ, major));
        }
    }

    private static long floorToStep(double value, int step) {
        return Math.floorDiv((long) Math.floor(value), (long) step) * step;
    }

    private static int gridColor(long block, long major) {
        if (block == 0L) {
            return COLOR_AXIS;
        }
        return block % major == 0L ? COLOR_GRID_MAJOR : COLOR_GRID_MINOR;
    }

    // ----------------------------------------------------------- left panels

    /** World, player, exploration and layers, top left. Grouped so the interesting part is findable. */
    private TextPanel buildContextPanel(ActiveWorld world, PlayerPosition player, ChunkRange visible) {
        WorldContext context = world.context();
        TextPanel panel = new TextPanel(COLOR_PANEL, COLOR_TEXT_HOVER);

        panel.line(this.getTitle().getString(), COLOR_TEXT);

        panel.blank();
        panel.line("WORLD", COLOR_SECTION);
        panel.line("Profile   " + profileLabel(world), COLOR_TEXT_DIM);
        panel.line("Version   " + context.minecraftVersion(), COLOR_TEXT_DIM);
        panel.line("Dimension " + dimensionLabel(context), COLOR_TEXT_DIM);
        panel.line("Mode      " + modeLabel(context), COLOR_TEXT_DIM);
        if (editingSeed) {
            appendSeedEditor(panel);
        } else {
            appendSeedRows(panel, world);
        }

        panel.blank();
        panel.line("PLAYER", COLOR_SECTION);
        appendPlayerRows(panel, player);

        panel.blank();
        panel.line("EXPLORATION", COLOR_SECTION);
        appendExplorationRows(panel, world);

        panel.blank();
        panel.line("LAYERS (click to toggle)", COLOR_SECTION);
        // Says it out loud: exact structures are vanilla's answer, the rest are still candidates.
        panel.line("exact where vanilla is reproduced, else candidates", COLOR_TEXT_DIM);
        panel.action(ACTION_RAW_CANDIDATES,
                "Raw candidates: " + (StructureLayer.showRawCandidates() ? "ON" : "OFF"),
                StructureLayer.showRawCandidates() ? COLOR_TEXT : COLOR_TEXT_DIM);
        List<MapLayer> layers = LAYERS.all();
        String dimensionId = world.context().dimensionId();
        for (int i = 0; i < layers.size(); i++) {
            MapLayer layer = layers.get(i);
            if (!layer.appliesTo(dimensionId)) {
                // Not listed at all; the action id stays the layer's index, so clicks still land.
                continue;
            }
            String reason = layer.unavailableReason(world, viewport, visible);
            panel.action(ACTION_LAYER_BASE + i,
                    layer.displayName() + ": " + layerState(layer, reason),
                    layer.isEnabled() && reason == null ? COLOR_TEXT : COLOR_TEXT_DIM);
        }
        return panel;
    }

    private void appendPlayerRows(TextPanel panel, PlayerPosition player) {
        if (player == null) {
            panel.line("not in a world", COLOR_TEXT_DIM);
            return;
        }
        panel.line(String.format("X %.1f   Y %.1f   Z %.1f", player.x(), player.y(), player.z()),
                COLOR_TEXT);
        panel.line("Chunk     " + player.chunkX() + ", " + player.chunkZ(), COLOR_TEXT_DIM);
        panel.action(ACTION_CENTER_PLAYER, "[Center on player]", COLOR_TEXT);
        panel.action(ACTION_FOLLOW_PLAYER, "Follow: " + (followPlayer ? "ON" : "OFF"),
                followPlayer ? COLOR_TEXT : COLOR_TEXT_DIM);
        if (ExplorationManager.get().isWritable()) {
            panel.action(ACTION_ADD_MARKER_PLAYER, "[Add marker at player]", COLOR_TEXT);
        }
    }

    /**
     * Markers in this dimension, the pointer, and annotations kept for another seed. Works with or
     * without a seed: nothing here is predicted.
     */
    private void appendExplorationRows(TextPanel panel, ActiveWorld world) {
        ExplorationManager exploration = ExplorationManager.get();
        if (!exploration.isActive()) {
            panel.line("no world profile to keep markers in", COLOR_TEXT_DIM);
            return;
        }
        panel.line("Markers here " + exploration.markersIn(world.context().dimensionId()).size(),
                COLOR_TEXT_DIM);
        if (pointerSet) {
            panel.line("Pointer   " + pointerX + ", " + pointerZ, COLOR_TEXT);
            if (exploration.isWritable()) {
                panel.buttons(new int[] {ACTION_ADD_MARKER_POINTER, ACTION_CLEAR_POINTER},
                        new String[] {"[Add marker at pointer]", "[Clear]"}, new int[] {COLOR_TEXT, COLOR_TEXT_DIM});
            } else {
                panel.action(ACTION_CLEAR_POINTER, "[Clear pointer]", COLOR_TEXT_DIM);
            }
        } else {
            panel.line("right-click the map to place a pointer", COLOR_TEXT_DIM);
        }
        int otherSeeds = exploration.structuresNotPredictedFrom(world.hasSeed() ? Long.valueOf(world.seed()) : null);
        if (otherSeeds > 0) {
            panel.line("Annotations from other seeds: " + otherSeeds, COLOR_TEXT_DIM);
        }
        if (!exploration.isWritable()) {
            panel.line("read-only: " + exploration.readOnlyReason(), COLOR_TEXT_DIM);
        }
    }

    private void appendSeedRows(TextPanel panel, ActiveWorld world) {
        panel.line("Seed      " + (world.hasSeed() ? Long.toString(world.seed()) : "Unknown"),
                world.hasSeed() ? COLOR_TEXT : COLOR_TEXT_DIM);
        panel.line("Source    " + world.seedSource().displayName(), COLOR_TEXT_DIM);

        // Only worlds Minecraft refuses to tell us about can be edited; a real integrated-server
        // seed is ground truth and must not be shadowed by a typed one.
        if (world.acceptsManualSeed()) {
            panel.blank();
            panel.action(ACTION_EDIT_SEED, world.hasSeed() ? "[Change seed]" : "[Set seed]", COLOR_TEXT);
            if (world.hasManualSeed()) {
                panel.action(ACTION_CLEAR_SEED, "[Clear seed]", COLOR_TEXT);
            }
        }
    }

    private void appendSeedEditor(TextPanel panel) {
        panel.line("Seed      " + seedInput + "_", COLOR_TEXT);
        panel.line("Enter applies, Esc cancels", COLOR_TEXT_DIM);
        if (seedError != null) {
            panel.line(seedError, COLOR_TEXT_ERROR);
        }
    }

    /**
     * Cursor position and engine instrumentation, bottom left.
     *
     * <p>Kept in its own dim block so the tile counters do not compete with the world and player
     * information the map is actually for.
     */
    private TextPanel buildDebugPanel(int mouseX, int mouseY) {
        long blockX = (long) Math.floor(viewport.screenToBlockX(mouseX));
        long blockZ = (long) Math.floor(viewport.screenToBlockZ(mouseY));

        TextPanel panel = new TextPanel(COLOR_PANEL, COLOR_TEXT_HOVER);
        panel.line("DEBUG", COLOR_SECTION);
        panel.line("Cursor  " + blockX + ", " + blockZ
                + "   chunk " + (blockX >> 4) + ", " + (blockZ >> 4), COLOR_TEXT_DIM);
        panel.line("Zoom    " + formatScale(viewport.getScale())
                + "   grid " + viewport.gridStepBlocks() + " blocks", COLOR_TEXT_DIM);
        panel.line("Center  " + Math.round(viewport.getCenterBlockX()) + ", "
                + Math.round(viewport.getCenterBlockZ()), COLOR_TEXT_DIM);

        // Biome engine instrumentation. Read from a snapshot, never logged per sample.
        BiomeTileStore.Metrics tiles = BiomeTileManager.get().metrics();
        panel.line("Tiles   " + tiles.cachedTiles() + " cached, " + tiles.pendingTiles()
                + " pending, " + tiles.completedTiles() + " built"
                + (tiles.rejectedTiles() > 0 ? ", " + tiles.rejectedTiles() + " dropped" : "")
                + (tiles.failedTiles() > 0 ? ", " + tiles.failedTiles() + " failed" : ""),
                COLOR_TEXT_DIM);
        panel.line("Tile ms " + String.format("%.1f last, %.1f avg",
                tiles.lastMillis(), tiles.averageMillis()), COLOR_TEXT_DIM);

        StructureValidationStore.Metrics checks = StructureValidationManager.get().metrics();
        panel.line("Checks  " + checks.accepted() + " kept, " + checks.rejected() + " rejected, "
                + checks.undecided() + " undecided, " + checks.pendingResults() + " pending"
                + (checks.discarded() > 0 ? ", " + checks.discarded() + " dropped" : "")
                + (checks.failed() > 0 ? ", " + checks.failed() + " failed" : ""),
                COLOR_TEXT_DIM);
        panel.line("Check ms " + String.format("%.1f avg", checks.averageMillis()), COLOR_TEXT_DIM);
        appendStrongholdRows(panel);
        long dataMillis = VanillaStructureData.loadMillis();
        if (VanillaStructureData.isLoading() || dataMillis >= 0 || checks.deferred() > 0) {
            panel.line("Struct data " + (VanillaStructureData.isLoading() ? "loading"
                    : dataMillis >= 0 ? "loaded in " + dataMillis + " ms" : "not loaded")
                    + (checks.waitingTypes() > 0 ? ", " + checks.waitingTypes() + " types waiting"
                            : "")
                    + (checks.deferred() > 0 ? ", " + checks.deferred() + " deferred" : ""),
                    COLOR_TEXT_DIM);
        }

        appendCursorStructures(panel, (int) (blockX >> 4), (int) (blockZ >> 4));
        return panel;
    }

    /** How far the stronghold list has got, and the one nearest the player once it is known. */
    private static void appendStrongholdRows(TextPanel panel) {
        StrongholdManager strongholds = StrongholdManager.get();
        List<StrongholdPosition> positions = strongholds.positionsIfReady();
        if (positions == null) {
            if (strongholds.isComputing()) {
                panel.line("Strongholds locating...", COLOR_TEXT_DIM);
            } else if (strongholds.failure() != null) {
                panel.line("Strongholds unavailable: " + strongholds.failure(), COLOR_TEXT_DIM);
            }
            return;
        }
        String line = "Strongholds " + positions.size() + " in " + strongholds.lastMillis() + " ms";
        PlayerPosition player = MinecraftBridge.currentPlayerPosition();
        StrongholdPosition nearest = player == null
                ? null : StrongholdPlacementEngine.nearest(positions, player.x(), player.z());
        if (nearest != null) {
            line += ", nearest #" + (nearest.index() + 1) + " at "
                    + ((nearest.chunkX() << 4) + 8) + ", " + ((nearest.chunkZ() << 4) + 8);
        }
        panel.line(line, COLOR_TEXT_DIM);
    }

    /**
     * Whatever structure candidate sits in the chunk under the cursor, and what the biome check
     * made of it. The sanity check for "why is this marker here, or missing".
     */
    private void appendCursorStructures(TextPanel panel, int chunkX, int chunkZ) {
        ActiveWorld world = WorldProfileManager.get().currentWorld();
        List<MapLayer> layers = LAYERS.all();
        String dimensionId = world.context().dimensionId();
        for (int i = 0; i < layers.size(); i++) {
            if (!(layers.get(i) instanceof StructureMarkerLayer)
                    || !layers.get(i).appliesTo(dimensionId)) {
                continue;
            }
            String description =
                    ((StructureMarkerLayer) layers.get(i)).describeAt(world, chunkX, chunkZ);
            if (description != null) {
                panel.line("        " + description, COLOR_TEXT);
            }
        }
    }

    private static String layerState(MapLayer layer, String unavailableReason) {
        if (!layer.isEnabled()) {
            return "OFF";
        }
        return unavailableReason == null ? "ON" : "ON (" + unavailableReason + ")";
    }

    private static String dimensionLabel(WorldContext context) {
        if (!context.isInWorld()) {
            return "-";
        }
        return dimensionName(context.dimensionId());
    }

    private static String dimensionName(String dimensionId) {
        DimensionType dimension = DimensionType.fromId(dimensionId);
        return dimension == DimensionType.CUSTOM ? String.valueOf(dimensionId) : dimension.displayName();
    }

    private static String shortLabel(String label) {
        return NotePreview.of(label, 1, LABEL_PREVIEW_CHARS).lines().get(0);
    }

    private static String modeLabel(WorldContext context) {
        return context.isInWorld() ? context.playMode().displayName() : "Not in a world";
    }

    private static String profileLabel(ActiveWorld world) {
        if (world.hasProfile()) {
            return world.profile().identity().displayName();
        }
        return world.context().isInWorld() ? "none for this connection" : "-";
    }

    private static String formatScale(double scale) {
        if (scale >= 1.0) {
            return String.format("%.2f px/block", scale);
        }
        return String.format("%.2f blocks/px", 1.0 / scale);
    }

    // ------------------------------------------------------------ interaction

    /**
     * Left click runs a panel action, or selects what is under the cursor and starts panning; right
     * click places the pointer. A click on a panel never reaches the map.
     */
    private boolean onPress(double mouseX, double mouseY, int button) {
        if (button == 1) {
            if (!isOverPanel(mouseX, mouseY)) {
                pointerX = (int) Math.floor(viewport.screenToBlockX(mouseX));
                pointerZ = (int) Math.floor(viewport.screenToBlockZ(mouseY));
                pointerSet = true;
            }
            return true;
        }
        if (button != 0) {
            return false;
        }
        int action = panelActionAt(mouseX, mouseY);
        if (action != TextPanel.NO_ACTION) {
            runAction(action);
            return true;
        }
        if (isOverPanel(mouseX, mouseY)) {
            return true;
        }
        // An open editor keeps its subject: the map still pans, but the selection does not move.
        if (!editor().isActive()) {
            selectAt(mouseX, mouseY);
        }
        // Selecting and panning share the press: a drag only becomes one once the mouse moves, so
        // picking a marker never costs the ability to pan away from it.
        dragging = true;
        return true;
    }

    private boolean isOverPanel(double mouseX, double mouseY) {
        return (rightPanel != null && rightPanel.contains(mouseX, mouseY))
                || (contextPanel != null && contextPanel.contains(mouseX, mouseY))
                || (debugPanel != null && debugPanel.contains(mouseX, mouseY));
    }

    private int panelActionAt(double mouseX, double mouseY) {
        // The right panel is drawn last, over the context panel on a narrow window, so it is asked first.
        if (rightPanel != null && rightPanel.contains(mouseX, mouseY)) {
            return rightPanel.actionAt(mouseX, mouseY);
        }
        return contextPanel == null ? TextPanel.NO_ACTION : contextPanel.actionAt(mouseX, mouseY);
    }

    /**
     * Selects the object {@link MapHitTest} picks among every drawn structure marker and custom marker
     * near the click, or clears the selection when there is none.
     *
     * <p>Structures are gathered from a few chunks around the cursor, because a marker has a minimum
     * on-screen size and may sit at a generation point off its chunk's centre; every test is seed
     * arithmetic. Custom markers come from the markers on screen in this dimension.
     */
    private void selectAt(double mouseX, double mouseY) {
        ActiveWorld world = WorldProfileManager.get().currentWorld();
        List<MapHitTest.Candidate<Object>> candidates = new ArrayList<MapHitTest.Candidate<Object>>();
        double blockX = viewport.screenToBlockX(mouseX);
        double blockZ = viewport.screenToBlockZ(mouseY);
        int centreChunkX = (int) Math.floor(blockX) >> 4;
        int centreChunkZ = (int) Math.floor(blockZ) >> 4;
        int radius = selectChunkRadius();
        BiomeMapKey map = world.hasSeed() ? StructureValidationKey.mapKeyFor(world) : null;

        List<MapLayer> layers = LAYERS.all();
        for (int i = 0; i < layers.size(); i++) {
            MapLayer layer = layers.get(i);
            if (layer instanceof CustomMarkerLayer) {
                candidates.addAll(((CustomMarkerLayer) layer).hitCandidates(world, viewport));
                continue;
            }
            if (!(layer instanceof StructureMarkerLayer) || map == null) {
                continue;
            }
            StructureMarkerLayer structures = (StructureMarkerLayer) layer;
            double markerRadius = (structures instanceof StrongholdLayer
                    ? StrongholdLayer.markerHalfPixels(viewport.getScale())
                    : StructureLayer.markerHalfPixels(viewport.getScale())) + 2;
            for (int chunkZ = centreChunkZ - radius; chunkZ <= centreChunkZ + radius; chunkZ++) {
                for (int chunkX = centreChunkX - radius; chunkX <= centreChunkX + radius; chunkX++) {
                    if (!structures.isMarkerAt(world, chunkX, chunkZ)) {
                        continue;
                    }
                    // Measured to where the marker is drawn, which for an exact result is the
                    // generation point rather than the chunk centre.
                    StructureValidation result = structures.resultAt(world, chunkX, chunkZ);
                    candidates.add(new MapHitTest.Candidate<Object>(
                            MapSelection.structure(structures, chunkX, chunkZ, map),
                            viewport.blockToScreenX(StructureLayer.markerBlockX(result, chunkX) + 0.5),
                            viewport.blockToScreenY(StructureLayer.markerBlockZ(result, chunkZ) + 0.5),
                            markerRadius, MapHitTest.PRIORITY_STRUCTURE,
                            String.format(Locale.ROOT, "%03d:%d:%d", i, chunkX, chunkZ)));
                }
            }
        }

        MapHitTest.Candidate<Object> picked = MapHitTest.pick(candidates, mouseX, mouseY);
        selectionScroll = 0;
        selectionNotice = null;
        if (picked == null) {
            clearSelection();
        } else if (picked.target() instanceof CustomMarker) {
            selection = MapSelection.marker(((CustomMarker) picked.target()).id());
        } else {
            selection = (MapSelection) picked.target();
        }
    }

    /** How many chunks around the cursor a drawn structure marker may be picked from. */
    private int selectChunkRadius() {
        double markerBlocks = Math.max(ChunkRange.CHUNK_SIZE,
                Math.max(MapHitTest.MIN_RADIUS_PIXELS, StructureLayer.markerHalfPixels(viewport.getScale()) + 2)
                        / viewport.getScale());
        int radius = (int) Math.ceil(markerBlocks / ChunkRange.CHUNK_SIZE) + 1;
        return Math.min(MAX_SELECT_CHUNK_RADIUS, Math.max(1, radius));
    }

    /** Where Center, Teleport and the copies go for the current selection. */
    private static final class NavigationTarget {

        final int x;
        final Integer y;
        final int z;
        final double centerX;
        final double centerZ;
        final String copyCommand;

        NavigationTarget(int x, Integer y, int z, double centerX, double centerZ, String copyCommand) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.copyCommand = copyCommand;
        }
    }

    /** @return the target, or {@code null} when nothing selected has a position */
    private NavigationTarget navigationTarget(ActiveWorld world) {
        if (selection == null) {
            return null;
        }
        if (selection.isMarker()) {
            CustomMarker marker = selectedMarker();
            if (marker == null) {
                return null;
            }
            // A marker's height is the player's own record, so the copied command may use it; the
            // sent teleport still keeps the player's height.
            return new NavigationTarget(marker.x(), marker.y(), marker.z(), marker.x() + 0.5, marker.z() + 0.5,
                    PlayerNavigator.teleportCommand(marker.x(), marker.y(), marker.z()));
        }
        // Exact generation position when there is one, candidate chunk centre otherwise.
        StructureValidation result = selection.layer().resultAt(world, selection.chunkX(), selection.chunkZ());
        GenerationPoint point = result == null ? null : result.generationPoint();
        int anchorX = StructureLayer.markerBlockX(result, selection.chunkX());
        int anchorZ = StructureLayer.markerBlockZ(result, selection.chunkZ());
        double centerX = anchorX + 0.5;
        double centerZ = anchorZ + 0.5;
        StructureGeometry geometry = selectedGeometry(result);
        if (geometry != null && geometry.isAvailable()) {
            centerX = geometry.bounds().centerX() + 0.5;
            centerZ = geometry.bounds().centerZ() + 0.5;
        }
        return new NavigationTarget(anchorX, point == null ? null : Integer.valueOf(point.y()), anchorZ, centerX,
                centerZ, PlayerNavigator.teleportCommand(anchorX, anchorZ));
    }

    private void runSelectionAction(int action) {
        if (action == ACTION_SELECTION_CLEAR) {
            clearSelection();
            return;
        }
        NavigationTarget target = navigationTarget(WorldProfileManager.get().currentWorld());
        if (target == null) {
            return;
        }
        if (action == ACTION_SELECTION_CENTER) {
            followPlayer = false;
            viewport.setCenter(target.centerX, target.centerZ);
            selectionNotice = null;
            return;
        }
        if (action == ACTION_SELECTION_TELEPORT) {
            PlayerNavigator.TeleportResult outcome = PlayerNavigator.teleportToColumn(target.x, target.z);
            selectionNotice = teleportNotice(outcome);
            if (outcome == PlayerNavigator.TeleportResult.SENT) {
                // Nothing else to look at on the map while the game moves the player.
                this.onClose();
            }
            return;
        }
        if (action == ACTION_SELECTION_COPY_COORDS) {
            String coordinates = TeleportCommand.coordinates(target.x, target.y, target.z);
            PlayerNavigator.copyToClipboard(coordinates);
            selectionNotice = "copied " + coordinates;
            return;
        }
        if (action == ACTION_SELECTION_COPY_COMMAND) {
            PlayerNavigator.copyToClipboard(target.copyCommand);
            selectionNotice = "copied " + target.copyCommand;
        }
    }

    private static String teleportNotice(PlayerNavigator.TeleportResult result) {
        if (result == PlayerNavigator.TeleportResult.NO_PERMISSION) {
            return "no command permission here - copy the coordinates instead";
        }
        if (result == PlayerNavigator.TeleportResult.NO_PLAYER) {
            return "no player to move";
        }
        return null;
    }

    private void runAction(int action) {
        ActiveWorld world = WorldProfileManager.get().currentWorld();
        MapEditor editor = editor();
        StructureStatus[] statuses = StructureStatus.values();
        MarkerType[] types = MarkerType.values();

        if (action >= ACTION_STATUS_BASE && action < ACTION_STATUS_BASE + statuses.length) {
            StructureKey key = selectedStructureKey(world);
            StructureStatus status = statuses[action - ACTION_STATUS_BASE];
            if (key != null && ExplorationManager.get().setStatus(key, status)) {
                selectionNotice = "marked " + status.displayName().toLowerCase(Locale.ROOT);
            }
            return;
        }
        if (action >= ACTION_EDITOR_TYPE_BASE && action < ACTION_EDITOR_TYPE_BASE + types.length) {
            editor.setType(types[action - ACTION_EDITOR_TYPE_BASE]);
            return;
        }
        if (action >= ACTION_SELECTION_CENTER && action <= ACTION_SELECTION_CLEAR) {
            runSelectionAction(action);
            return;
        }
        switch (action) {
            case ACTION_EDIT_NOTE: {
                StructureKey key = selectedStructureKey(world);
                if (key != null) {
                    openEditor(editor.beginStructureNote(key, selection.layer().displayName() + " at chunk "
                            + selection.chunkX() + ", " + selection.chunkZ()));
                }
                return;
            }
            case ACTION_ADD_MARKER_POINTER:
                if (pointerSet) {
                    openEditor(editor.beginCreateMarker(world.context().dimensionId(), pointerX, null, pointerZ));
                }
                return;
            case ACTION_ADD_MARKER_PLAYER: {
                PlayerPosition player = MinecraftBridge.currentPlayerPosition();
                if (player != null) {
                    openEditor(editor.beginCreateMarker(world.context().dimensionId(), player.blockX(),
                            Integer.valueOf(player.blockY()), player.blockZ()));
                }
                return;
            }
            case ACTION_CLEAR_POINTER:
                pointerSet = false;
                return;
            case ACTION_MARKER_EDIT:
                if (selectedMarker() != null) {
                    openEditor(editor.beginEditMarker(selection.markerId()));
                }
                return;
            case ACTION_MARKER_DELETE:
                if (selectedMarker() != null) {
                    openEditor(editor.beginDeleteMarker(selection.markerId()));
                }
                return;
            case ACTION_MARKER_MOVE_POINTER:
                // A map column: the old height means nothing at a new place.
                if (pointerSet && selectedMarker() != null
                        && editor.moveMarker(selection.markerId(), world.context().dimensionId(), pointerX, null,
                                pointerZ)) {
                    selectionNotice = "moved to the pointer";
                }
                return;
            case ACTION_MARKER_MOVE_PLAYER: {
                PlayerPosition player = MinecraftBridge.currentPlayerPosition();
                if (player != null && selectedMarker() != null
                        && editor.moveMarker(selection.markerId(), world.context().dimensionId(), player.blockX(),
                                Integer.valueOf(player.blockY()), player.blockZ())) {
                    selectionNotice = "moved to the player";
                }
                return;
            }
            case ACTION_EDITOR_SAVE:
                editor.save();
                afterEditorInput();
                return;
            case ACTION_EDITOR_CANCEL:
                editor.cancel();
                afterEditorInput();
                return;
            case ACTION_EDITOR_FOCUS_LABEL:
                editor.focus(MapEditor.Field.LABEL);
                return;
            case ACTION_EDITOR_FOCUS_NOTE:
                editor.focus(MapEditor.Field.NOTE);
                return;
            case ACTION_EDIT_SEED:
                beginSeedEdit();
                return;
            case ACTION_CLEAR_SEED:
                WorldProfileManager.get().clearManualSeed();
                return;
            case ACTION_CENTER_PLAYER:
                centerOnPlayer();
                return;
            case ACTION_RAW_CANDIDATES:
                StructureLayer.setShowRawCandidates(!StructureLayer.showRawCandidates());
                return;
            case ACTION_FOLLOW_PLAYER:
                followPlayer = !followPlayer;
                if (followPlayer) {
                    centerOnPlayer();
                }
                return;
            default:
                break;
        }
        List<MapLayer> layers = LAYERS.all();
        int layerIndex = action - ACTION_LAYER_BASE;
        // Only rows that were listed can be clicked, and a row is listed only for its dimension.
        if (layerIndex >= 0 && layerIndex < layers.size()) {
            MapLayer layer = layers.get(layerIndex);
            layer.setEnabled(!layer.isEnabled());
        }
    }

    /** Only one text field at a time: an opened editor closes the seed field. */
    private void openEditor(boolean opened) {
        if (opened) {
            cancelSeedEdit();
            editorNotice = null;
            editorScroll = 0;
            editorCursorState = "";
        } else {
            String reason = ExplorationManager.get().readOnlyReason();
            selectionNotice = "cannot edit: " + (reason == null ? "no world profile" : reason);
        }
    }

    private void beginSeedEdit() {
        ActiveWorld world = WorldProfileManager.get().currentWorld();
        editor().cancel();
        editor().takeResult();
        editingSeed = true;
        seedInput = world.hasSeed() ? Long.toString(world.seed()) : "";
        seedError = null;
    }

    private void confirmSeedEdit() {
        if (!SeedParser.isValid(seedInput)) {
            seedError = "Not a valid seed";
            return;
        }
        if (!WorldProfileManager.get().setManualSeed(SeedParser.parse(seedInput))) {
            seedError = "This world's seed cannot be set by hand";
            return;
        }
        cancelSeedEdit();
    }

    private void cancelSeedEdit() {
        editingSeed = false;
        seedInput = "";
        seedError = null;
    }

    /**
     * Feeds one typed character to whichever field is open.
     *
     * @return whether the screen consumed the input; while a field is open it consumes everything,
     *         so stray keys cannot leak through to the rest of the game
     */
    private boolean onCharTyped(int codePoint) {
        if (editor().isActive()) {
            return editor().typeCodePoint(codePoint);
        }
        if (!editingSeed) {
            return false;
        }
        boolean digit = codePoint >= '0' && codePoint <= '9';
        boolean leadingMinus = codePoint == '-' && seedInput.isEmpty();
        if ((digit || leadingMinus) && seedInput.length() < MAX_SEED_LENGTH) {
            seedInput += (char) codePoint;
            seedError = null;
        }
        return true;
    }

    private boolean onKeyPressed(int keyCode, int modifiers) {
        if (editor().isActive()) {
            EditorKey key = editorKeyOf(keyCode);
            if (key != null) {
                editor().handleKey(key, (modifiers & GLFW.GLFW_MOD_CONTROL) != 0);
                afterEditorInput();
            }
            // Every key is the editor's while it is open, Escape included.
            return true;
        }
        if (!editingSeed) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirmSeedEdit();
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Swallowed so cancelling the field does not also close the whole screen.
            cancelSeedEdit();
        } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !seedInput.isEmpty()) {
            seedInput = seedInput.substring(0, seedInput.length() - 1);
            seedError = null;
        }
        return true;
    }

    private static EditorKey editorKeyOf(int keyCode) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                return EditorKey.ENTER;
            case GLFW.GLFW_KEY_ESCAPE:
                return EditorKey.ESCAPE;
            case GLFW.GLFW_KEY_TAB:
                return EditorKey.TAB;
            case GLFW.GLFW_KEY_BACKSPACE:
                return EditorKey.BACKSPACE;
            case GLFW.GLFW_KEY_DELETE:
                return EditorKey.DELETE;
            case GLFW.GLFW_KEY_LEFT:
                return EditorKey.LEFT;
            case GLFW.GLFW_KEY_RIGHT:
                return EditorKey.RIGHT;
            case GLFW.GLFW_KEY_UP:
                return EditorKey.UP;
            case GLFW.GLFW_KEY_DOWN:
                return EditorKey.DOWN;
            case GLFW.GLFW_KEY_HOME:
                return EditorKey.HOME;
            case GLFW.GLFW_KEY_END:
                return EditorKey.END;
            default:
                return null;
        }
    }

    private boolean endDrag(int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    private boolean continueDrag(int button, double deltaX, double deltaY) {
        if (button != 0 || !dragging) {
            return false;
        }
        // Panning by hand is an explicit "look here", so it takes the map off the player.
        followPlayer = false;
        viewport.panByPixels(deltaX, deltaY);
        return true;
    }

    /** The wheel scrolls a panel it is over, and zooms the map everywhere else. */
    private boolean onScroll(double mouseX, double mouseY, double steps) {
        if (steps == 0.0) {
            return false;
        }
        int rows = steps > 0.0 ? -PANEL_SCROLL_ROWS : PANEL_SCROLL_ROWS;
        if (rightPanel != null && rightPanel.contains(mouseX, mouseY)) {
            if (editor().isActive()) {
                editorScroll = Math.max(0, editorScroll + rows);
            } else {
                selectionScroll = Math.max(0, selectionScroll + rows);
            }
            return true;
        }
        if (contextPanel != null && contextPanel.contains(mouseX, mouseY)) {
            contextScroll = Math.max(0, contextScroll + rows);
            return true;
        }
        if (debugPanel != null && debugPanel.contains(mouseX, mouseY)) {
            debugScroll = Math.max(0, debugScroll + rows);
            return true;
        }
        return zoom(steps, mouseX, mouseY);
    }

    private boolean zoom(double steps, double anchorX, double anchorY) {
        if (steps == 0.0) {
            return false;
        }
        viewport.resize(this.width, this.height);
        viewport.zoomAt(steps, anchorX, anchorY);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------ version-specific bridge

    //? if >=26.1 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        draw(new MapCanvas(graphics, this.font), mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    //?} else if >=1.20 {
    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        draw(new MapCanvas(graphics, this.font), mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    *///?} else {
    /*@Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        draw(new MapCanvas(poseStack, this.font), mouseX, mouseY);
        super.render(poseStack, mouseX, mouseY, partialTick);
    }
    *///?}

    //? if >=26.1 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return onPress(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return endDrag(event.button()) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        return continueDrag(event.button(), deltaX, deltaY) || super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return onScroll(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return onCharTyped(event.codepoint()) || super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return onKeyPressed(event.key(), event.modifiers()) || super.keyPressed(event);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return onPress(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return endDrag(button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return continueDrag(button, deltaX, deltaY) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return onScroll(mouseX, mouseY, amount) || super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return onCharTyped(chr) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return onKeyPressed(keyCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }
    *///?}
}
