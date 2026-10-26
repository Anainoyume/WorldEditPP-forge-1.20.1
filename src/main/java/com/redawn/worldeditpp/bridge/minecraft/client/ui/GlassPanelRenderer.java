package com.redawn.worldeditpp.bridge.minecraft.client.ui;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL30;

/** Samples a separate scene copy; the blur touches only the rounded panel's pixels. */
public final class GlassPanelRenderer implements AutoCloseable {
    private static ShaderInstance shader;
    private RenderTarget scene;

    public static void installShader(ShaderInstance value) {
        shader = value;
    }

    public void render(GuiGraphics graphics, int x, int y, int width, int height, float opacity) {
        if (opacity <= 0.01F) return;
        if (shader == null) {
            rounded(graphics, x + 1, y + 1, width - 2, height - 2, 9, color(0x64121418, opacity));
            roundedOutline(graphics, x, y, width, height, 10, color(0xFFFFFFFF, opacity));
            return;
        }
        graphics.flush();
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        if (scene == null || scene.width != main.width || scene.height != main.height) {
            close();
            scene = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            scene.setFilterMode(GL30.GL_LINEAR);
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, scene.width, scene.height,
                GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
        main.bindWrite(false);
        float scale = (float) minecraft.getWindow().getGuiScale();
        shader.safeGetUniform("ScreenSize").set((float) main.width, (float) main.height);
        shader.safeGetUniform("PanelRect").set(x * scale, main.height - (y + height) * scale,
                width * scale, height * scale);
        shader.safeGetUniform("CornerRadius").set(10 * scale);
        shader.safeGetUniform("PixelSize").set(scale);
        shader.safeGetUniform("BlurRadius").set(12.0F);
        shader.safeGetUniform("Opacity").set(opacity);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, scene.getColorTextureId());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        var matrix = graphics.pose().last().pose();
        BufferBuilder vertices = Tesselator.getInstance().getBuilder();
        vertices.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        vertices.vertex(matrix, x, y, 0).uv(0, 0).color(-1).endVertex();
        vertices.vertex(matrix, x, y + height, 0).uv(0, 1).color(-1).endVertex();
        vertices.vertex(matrix, x + width, y + height, 0).uv(1, 1).color(-1).endVertex();
        vertices.vertex(matrix, x + width, y, 0).uv(1, 0).color(-1).endVertex();
        BufferUploader.drawWithShader(vertices.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public static int color(int argb, float opacity) {
        return ((int) ((argb >>> 24) * opacity) << 24) | (argb & 0xFFFFFF);
    }

    public static void rounded(GuiGraphics graphics, int x, int y, int width, int height, int radius, int argb) {
        int r = Math.min(radius, Math.min(width, height) / 2);
        graphics.fill(x, y + r, x + width, y + height - r, argb);
        for (int row = 0; row < r; row++) {
            int inset = cornerInset(r, row);
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, argb);
            graphics.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, argb);
        }
    }

    /** Draw only the one-pixel ring, leaving the glass interior untouched. */
    public static void roundedOutline(GuiGraphics graphics, int x, int y, int width, int height, int radius, int argb) {
        int r = Math.min(radius, Math.min(width, height) / 2);
        for (int row = 0; row < height; row++) {
            int edgeRow = Math.min(row, height - row - 1);
            int outer = cornerInset(r, edgeRow);
            if (edgeRow == 0) {
                graphics.fill(x + outer, y + row, x + width - outer, y + row + 1, argb);
            } else {
                int inner = 1 + cornerInset(Math.max(0, r - 1), edgeRow - 1);
                graphics.fill(x + outer, y + row, x + inner, y + row + 1, argb);
                graphics.fill(x + width - inner, y + row, x + width - outer, y + row + 1, argb);
            }
        }
    }

    public static int cornerInset(int radius, int row) {
        if (row >= radius) return 0;
        double dy = radius - row - 0.5;
        // Test GUI pixel centers against the same circle as glass_panel.fsh.
        return Math.max(0, (int) Math.ceil(radius - Math.sqrt(radius * radius - dy * dy) - 0.5));
    }

    @Override public void close() {
        if (scene != null) {
            scene.destroyBuffers();
            scene = null;
        }
    }
}
