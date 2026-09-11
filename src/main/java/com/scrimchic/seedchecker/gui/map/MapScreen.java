package com.scrimchic.seedchecker.gui.map;

import com.scrimchic.seedchecker.core.map.MapViewport;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
//?} else if >=1.20 {
/*import net.minecraft.client.gui.GuiGraphics;*/
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.TranslatableComponent;*/
//?}

/**
 * The Seed Checker map screen: an empty, pannable and zoomable world map.
 *
 * <p>Everything except the render entry point and the mouse callbacks is shared between all
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

    /** Every eighth grid line is drawn brighter. */
    private static final int MAJOR_GRID_MULTIPLE = 8;

    private static final int PANEL_MARGIN = 6;
    private static final int PANEL_PADDING = 4;

    private final MapViewport viewport = new MapViewport();

    private boolean dragging;

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

    // ---------------------------------------------------------------- drawing

    private void draw(MapCanvas canvas, int mouseX, int mouseY) {
        viewport.resize(this.width, this.height);

        canvas.fill(0, 0, this.width, this.height, COLOR_BACKGROUND);
        drawGrid(canvas);
        drawInfoPanel(canvas, mouseX, mouseY);
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

    private void drawInfoPanel(MapCanvas canvas, int mouseX, int mouseY) {
        double blockX = viewport.screenToBlockX(mouseX);
        double blockZ = viewport.screenToBlockZ(mouseY);
        long flooredX = (long) Math.floor(blockX);
        long flooredZ = (long) Math.floor(blockZ);

        String[] lines = {
                this.getTitle().getString(),
                "Block   " + flooredX + ", " + flooredZ,
                "Chunk   " + (flooredX >> 4) + ", " + (flooredZ >> 4),
                "Screen  " + mouseX + ", " + mouseY,
                "Zoom    " + formatScale(viewport.getScale()),
                "Center  " + Math.round(viewport.getCenterBlockX()) + ", "
                        + Math.round(viewport.getCenterBlockZ()),
                "Grid    " + viewport.gridStepBlocks() + " blocks",
        };

        int lineHeight = canvas.lineHeight() + 1;
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, canvas.textWidth(line));
        }

        int left = PANEL_MARGIN;
        int top = PANEL_MARGIN;
        int right = left + textWidth + PANEL_PADDING * 2;
        int bottom = top + lines.length * lineHeight + PANEL_PADDING * 2;
        canvas.fill(left, top, right, bottom, COLOR_PANEL);

        int y = top + PANEL_PADDING;
        for (int i = 0; i < lines.length; i++) {
            canvas.text(lines[i], left + PANEL_PADDING, y, i == 0 ? COLOR_TEXT : COLOR_TEXT_DIM);
            y += lineHeight;
        }
    }

    private static String formatScale(double scale) {
        if (scale >= 1.0) {
            return String.format("%.2f px/block", scale);
        }
        return String.format("%.2f blocks/px", 1.0 / scale);
    }

    // ------------------------------------------------------------ interaction

    private boolean beginDrag(int button) {
        if (button == 0) {
            dragging = true;
            return true;
        }
        return false;
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
        return beginDrag(event.button()) || super.mouseClicked(event, doubleClick);
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
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return beginDrag(button) || super.mouseClicked(mouseX, mouseY, button);
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
    *///?}
}
