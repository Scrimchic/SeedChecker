package com.scrimchic.seedchecker.gui.map;

import java.util.List;

import com.scrimchic.seedchecker.client.biome.BiomeTileManager;
import com.scrimchic.seedchecker.client.structure.StructureValidationManager;
import com.scrimchic.seedchecker.client.world.WorldProfileManager;
import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.core.map.MapViewport;
import com.scrimchic.seedchecker.core.map.MapViewportMemory;
import com.scrimchic.seedchecker.gui.map.layer.MapLayer;
import com.scrimchic.seedchecker.gui.map.layer.MapLayers;
import com.scrimchic.seedchecker.gui.map.layer.StructureLayer;
import com.scrimchic.seedchecker.platform.MinecraftBridge;
import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.world.DimensionType;
import com.scrimchic.seedchecker.world.PlayerPosition;
import com.scrimchic.seedchecker.world.SeedParser;
import com.scrimchic.seedchecker.world.WorldContext;
import com.scrimchic.seedchecker.world.WorldProfile;
import com.scrimchic.seedchecker.worldgen.StructureValidationStore;
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
 */
public final class MapScreen extends Screen {

    private static final String TITLE_KEY = "screen.seedchecker.map";

    private static final int COLOR_BACKGROUND = 0xFF0E1216;
    private static final int COLOR_GRID_MINOR = 0xFF1B232C;
    private static final int COLOR_GRID_MAJOR = 0xFF2B3845;
    private static final int COLOR_AXIS = 0xFF4C7BA8;
    private static final int COLOR_PANEL = 0xB0000000;
    private static final int COLOR_TEXT = 0xFFDCE3EA;
    private static final int COLOR_TEXT_DIM = 0xFF8C98A4;
    private static final int COLOR_TEXT_HOVER = 0xFFFFD479;
    private static final int COLOR_TEXT_ERROR = 0xFFE86A6A;
    private static final int COLOR_SECTION = 0xFF66727E;

    /** Near-black outline plus a white core, so the marker reads over any biome colour. */
    private static final int COLOR_PLAYER = 0xFFFFFFFF;
    private static final int COLOR_PLAYER_OUTLINE = 0xFF0B0E11;

    /** Every eighth grid line is drawn brighter. */
    private static final int MAJOR_GRID_MULTIPLE = 8;

    private static final int PANEL_MARGIN = 6;

    /** Half the player marker's size, used to cull it when it is off screen. */
    private static final int MARKER_REACH = 8;

    /** {@code -9223372036854775808} is the longest seed that can be typed. */
    private static final int MAX_SEED_LENGTH = 20;

    private static final int ACTION_EDIT_SEED = 1;
    private static final int ACTION_CLEAR_SEED = 2;
    private static final int ACTION_CENTER_PLAYER = 3;
    private static final int ACTION_FOLLOW_PLAYER = 4;
    private static final int ACTION_RAW_CANDIDATES = 5;

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

    /** The panel as it was last drawn, kept so a click can be matched against its rows. */
    private TextPanel contextPanel;

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

        canvas.fill(0, 0, this.width, this.height, COLOR_BACKGROUND);
        drawGrid(canvas);
        LAYERS.renderAll(canvas, viewport, visible, world);

        // After every layer, so the player is never hidden behind a structure marker.
        if (player != null) {
            drawPlayerMarker(canvas, player);
        }

        contextPanel = buildContextPanel(world, player, visible);
        contextPanel.draw(canvas, PANEL_MARGIN, PANEL_MARGIN, mouseX, mouseY);

        TextPanel debugPanel = buildDebugPanel(mouseX, mouseY);
        debugPanel.draw(canvas, PANEL_MARGIN,
                this.height - PANEL_MARGIN - debugPanel.height(canvas), mouseX, mouseY);
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

    /** World, player and layers, top left. Grouped so the interesting part is findable. */
    private TextPanel buildContextPanel(ActiveWorld world, PlayerPosition player,
                                        ChunkRange visible) {
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
        panel.line("LAYERS (click to toggle)", COLOR_SECTION);
        // Says it out loud: grid placement picked these chunks, vanilla has not approved them.
        panel.line("structures are biome-checked candidates", COLOR_TEXT_DIM);
        panel.action(ACTION_RAW_CANDIDATES,
                "Raw candidates: " + (StructureLayer.showRawCandidates() ? "ON" : "OFF"),
                StructureLayer.showRawCandidates() ? COLOR_TEXT : COLOR_TEXT_DIM);
        List<MapLayer> layers = LAYERS.all();
        for (int i = 0; i < layers.size(); i++) {
            MapLayer layer = layers.get(i);
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

        appendCursorStructures(panel, (int) (blockX >> 4), (int) (blockZ >> 4));
        return panel;
    }

    /**
     * Whatever structure candidate sits in the chunk under the cursor, and what the biome check
     * made of it. The sanity check for "why is this marker here, or missing".
     */
    private void appendCursorStructures(TextPanel panel, int chunkX, int chunkZ) {
        ActiveWorld world = WorldProfileManager.get().currentWorld();
        List<MapLayer> layers = LAYERS.all();
        for (int i = 0; i < layers.size(); i++) {
            if (!(layers.get(i) instanceof StructureLayer)) {
                continue;
            }
            String description =
                    ((StructureLayer) layers.get(i)).describeAt(world, chunkX, chunkZ);
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
        return context.dimension() == DimensionType.CUSTOM
                ? context.dimensionId()
                : context.dimension().displayName();
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

    /** Left click either triggers a panel action or starts panning the map. */
    private boolean onPress(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        int action = contextPanel == null
                ? TextPanel.NO_ACTION
                : contextPanel.actionAt(mouseX, mouseY);
        if (action != TextPanel.NO_ACTION) {
            runAction(action);
            return true;
        }
        dragging = true;
        return true;
    }

    private void runAction(int action) {
        if (action == ACTION_EDIT_SEED) {
            beginSeedEdit();
            return;
        }
        if (action == ACTION_CLEAR_SEED) {
            WorldProfileManager.get().clearManualSeed();
            return;
        }
        if (action == ACTION_CENTER_PLAYER) {
            centerOnPlayer();
            return;
        }
        if (action == ACTION_RAW_CANDIDATES) {
            StructureLayer.setShowRawCandidates(!StructureLayer.showRawCandidates());
            return;
        }
        if (action == ACTION_FOLLOW_PLAYER) {
            followPlayer = !followPlayer;
            if (followPlayer) {
                centerOnPlayer();
            }
            return;
        }
        List<MapLayer> layers = LAYERS.all();
        int layerIndex = action - ACTION_LAYER_BASE;
        if (layerIndex >= 0 && layerIndex < layers.size()) {
            MapLayer layer = layers.get(layerIndex);
            layer.setEnabled(!layer.isEnabled());
        }
    }

    private void beginSeedEdit() {
        ActiveWorld world = WorldProfileManager.get().currentWorld();
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
     * Feeds one typed character into the seed field.
     *
     * @return whether the screen consumed the input; while editing it consumes everything, so
     *         stray keys cannot leak through to the rest of the game
     */
    private boolean onCharTyped(int codePoint) {
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

    private boolean onKeyPressed(int keyCode) {
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
        return zoom(scrollY, mouseX, mouseY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return onCharTyped(event.codepoint()) || super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return onKeyPressed(event.key()) || super.keyPressed(event);
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
        return zoom(amount, mouseX, mouseY) || super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return onCharTyped(chr) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return onKeyPressed(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }
    *///?}
}
