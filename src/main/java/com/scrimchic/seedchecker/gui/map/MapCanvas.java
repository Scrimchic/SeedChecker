package com.scrimchic.seedchecker.gui.map;

import net.minecraft.client.gui.Font;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else if >=1.20 {
/*import net.minecraft.client.gui.GuiGraphics;*/
//?} else {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Matrix4f;
import net.minecraft.client.gui.GuiComponent;*/
//?}

/**
 * The only place in Seed Checker that touches a Minecraft drawing API.
 *
 * <p>Minecraft changed how GUIs draw twice inside the supported range: 1.16.5 draws through a
 * static {@code GuiComponent} plus a {@code PoseStack}, 1.20.1 through {@code GuiGraphics}, and
 * 26.x records a render state through {@code GuiGraphicsExtractor}. Keeping that behind this thin
 * façade lets every map drawing routine stay version independent.
 *
 * <p>It also hides one large performance difference between those APIs. On 1.20.1 and 26.x a
 * {@code fill} appends four vertices to a buffer the screen flushes once, so thousands of fills
 * cost one draw call. On 1.16.5 every single {@code fill} sets nine pieces of render state, opens
 * its own {@code BufferBuilder} and ends with its own GL draw - verified in the bytecode of
 * {@code GuiComponent.fillGradient}. A biome map is several thousand rectangles per frame, so on
 * 1.16.5 that was several thousand draw calls, which is what made panning stutter.
 * {@link #beginBatch()} closes that gap: on 1.16.5 it opens one buffer and one set of render state
 * for the whole run, and on the newer versions it is a no-op because they already batch.
 */
public final class MapCanvas {

    private final Font font;

    //? if <1.20 {
    /*private static final int GL_QUADS = 7;
    private static final int GL_FLAT = 7424;
    private static final int GL_SMOOTH = 7425;

    private boolean batching;
    private boolean batchOpen;
    private Matrix4f batchPose;*/
    //?}

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

    /**
     * Starts a run of {@link #fill} calls that should share one draw call.
     *
     * <p>Must be closed with {@link #endBatch()}, and only {@code fill} may be called in between.
     * A no-op on 1.20.1 and 26.x, where the screen already owns one buffer for every fill.
     */
    public void beginBatch() {
        //? if <1.20 {
        /*if (batching) {
            return;
        }
        batching = true;
        batchPose = poseStack.last().pose();
        // The buffer and the render state are opened lazily by the first fill, so a frame where
        // every tile is still being generated touches no GL state at all.*/
        //?}
    }

    /** Ends the run started by {@link #beginBatch()} and hands the whole thing to the GPU once. */
    public void endBatch() {
        //? if <1.20 {
        /*if (!batching) {
            return;
        }
        batching = false;
        batchPose = null;
        if (!batchOpen) {
            return;
        }
        batchOpen = false;
        try {
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.end();
            BufferUploader.end(builder);
        } finally {
            // Restored in a finally so a failed upload cannot leave the rest of the GUI drawing
            // with blending on and no texture.
            RenderSystem.shadeModel(GL_FLAT);
            RenderSystem.disableBlend();
            RenderSystem.enableAlphaTest();
            RenderSystem.enableTexture();
        }*/
        //?}
    }

    /** Fills the half-open rectangle [x1, x2) x [y1, y2) with an ARGB colour. */
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        //? if >=26.1 {
        graphics.fill(x1, y1, x2, y2, argb);
        //?} else if >=1.20 {
        /*graphics.fill(x1, y1, x2, y2, argb);*/
        //?} else {
        /*if (!batching) {
            GuiComponent.fill(poseStack, x1, y1, x2, y2, argb);
            return;
        }
        if (!batchOpen) {
            batchOpen = true;
            // The same state GuiComponent.fillGradient sets per fill, set once for the whole run.
            RenderSystem.disableTexture();
            RenderSystem.enableBlend();
            RenderSystem.disableAlphaTest();
            RenderSystem.defaultBlendFunc();
            RenderSystem.shadeModel(GL_SMOOTH);
            Tesselator.getInstance().getBuilder()
                    .begin(GL_QUADS, DefaultVertexFormat.POSITION_COLOR);
        }
        // Winding, vertex format and colour order transcribed from
        // GuiComponent.fillGradient(Matrix4f, BufferBuilder, ...) so a batched quad is
        // indistinguishable from an unbatched one.
        float alpha = (argb >> 24 & 0xFF) / 255.0f;
        float red = (argb >> 16 & 0xFF) / 255.0f;
        float green = (argb >> 8 & 0xFF) / 255.0f;
        float blue = (argb & 0xFF) / 255.0f;

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.vertex(batchPose, x2, y1, 0.0F).color(red, green, blue, alpha).endVertex();
        builder.vertex(batchPose, x1, y1, 0.0F).color(red, green, blue, alpha).endVertex();
        builder.vertex(batchPose, x1, y2, 0.0F).color(red, green, blue, alpha).endVertex();
        builder.vertex(batchPose, x2, y2, 0.0F).color(red, green, blue, alpha).endVertex();*/
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
