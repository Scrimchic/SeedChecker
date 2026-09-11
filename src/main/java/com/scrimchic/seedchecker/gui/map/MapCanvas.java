package com.scrimchic.seedchecker.gui.map;

import net.minecraft.client.gui.Font;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else if >=1.20 {
/*import net.minecraft.client.gui.GuiGraphics;*/
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;*/
//?}

/**
 * The only place in Seed Checker that touches a Minecraft drawing API.
 *
 * <p>Minecraft changed how GUIs draw twice inside the supported range: 1.16.5 draws through a
 * static {@code GuiComponent} plus a {@code PoseStack}, 1.20.1 through {@code GuiGraphics}, and
 * 26.x records a render state through {@code GuiGraphicsExtractor}. Keeping that behind this thin
 * façade lets every map drawing routine stay version independent.
 */
public final class MapCanvas {

    private final Font font;

    //? if >=26.1 {
    private final GuiGraphicsExtractor graphics;

    public MapCanvas(GuiGraphicsExtractor graphics, Font font) {
        this.graphics = graphics;
        this.font = font;
    }
    //?} else if >=1.20 {
    /*private final GuiGraphics graphics;

    public MapCanvas(GuiGraphics graphics, Font font) {
        this.graphics = graphics;
        this.font = font;
    }
    *///?} else {
    /*private final PoseStack poseStack;

    public MapCanvas(PoseStack poseStack, Font font) {
        this.poseStack = poseStack;
        this.font = font;
    }
    *///?}

    /** Fills the half-open rectangle [x1, x2) x [y1, y2) with an ARGB colour. */
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        //? if >=26.1 {
        graphics.fill(x1, y1, x2, y2, argb);
        //?} else if >=1.20 {
        /*graphics.fill(x1, y1, x2, y2, argb);*/
        //?} else {
        /*GuiComponent.fill(poseStack, x1, y1, x2, y2, argb);*/
        //?}
    }

    /** Draws a single line of shadowed text with its top-left corner at (x, y). */
    public void text(String text, int x, int y, int argb) {
        //? if >=26.1 {
        graphics.text(font, text, x, y, argb);
        //?} else if >=1.20 {
        /*graphics.drawString(font, text, x, y, argb);*/
        //?} else {
        /*font.drawShadow(poseStack, text, x, y, argb);*/
        //?}
    }

    public int textWidth(String text) {
        return font.width(text);
    }

    public int lineHeight() {
        return font.lineHeight;
    }
}
