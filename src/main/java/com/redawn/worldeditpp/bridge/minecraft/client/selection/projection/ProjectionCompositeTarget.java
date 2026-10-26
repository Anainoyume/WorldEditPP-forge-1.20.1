package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Offscreen colour + depth target for the opaque projection layer.
 *
 * <p>The world depth is copied into this target for one screen rectangle, the projection is drawn
 * opaque (nearest surface only), then the colour is composited once at 0.6 alpha. The main
 * target's own depth is never modified, so no restore copy is needed.</p>
 */
final class ProjectionCompositeTarget implements AutoCloseable {
    private OwnedTextureTarget target;

    /** Subclass keeps access to the protected colour texture id without reflection. */
    private static final class OwnedTextureTarget extends TextureTarget {
        OwnedTextureTarget(int width, int height) { super(width, height, true, false); }
        int colorTexture() { return this.colorTextureId; }
    }

    /** Copies the world depth rectangle and clears the colour rectangle inside the target. */
    boolean begin(RenderTarget main, int width, int height, int[] rect) {
        if (!main.useDepth || width <= 0 || height <= 0) return false;
        if (target == null) {
            target = new OwnedTextureTarget(width, height);
            target.setFilterMode(GL11.GL_NEAREST);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, false);
        }
        int x0 = rect[0], y0 = rect[1], x1 = rect[2], y1 = rect[3];
        int glY0 = height - y1;
        int glY1 = height - y0;
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
        GlStateManager._glBlitFrameBuffer(x0, glY0, x1, glY1,
                x0, glY0, x1, glY1, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        target.bindWrite(true);

        RenderSystem.enableScissor(x0, glY0, x1 - x0, y1 - y0);
        try {
            GlStateManager._clearColor(0.0F, 0.0F, 0.0F, 0.0F);
            GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT, false);
        } finally {
            RenderSystem.disableScissor();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.disableBlend();
        return true;
    }

    /**
     * Copies the opaque projection depth rectangle back to the main target. The caller has
     * already saved world depth and must restore it after drawing the translucent overlay.
     */
    void copyDepthTo(RenderTarget main, int[] rect) {
        if (target == null || main.frameBufferId == -1 || target.frameBufferId == -1) return;
        int x0 = rect[0], y0 = rect[1], x1 = rect[2], y1 = rect[3];
        int glY0 = main.height - y1;
        int glY1 = main.height - y0;
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBlitFrameBuffer(x0, glY0, x1, glY1,
                x0, glY0, x1, glY1, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        main.bindWrite(true);
    }

    /** Composites the target rectangle over the main colour buffer at the vertex alpha. */
    void composite(RenderTarget main, int width, int height, int[] rect) {
        main.bindWrite(true);
        int x0 = rect[0], y0 = rect[1], x1 = rect[2], y1 = rect[3];
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.colorMask(true, true, true, false);
        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        RenderSystem.disableCull();
        try {
            ShaderInstance shader = Minecraft.getInstance().gameRenderer.blitShader;
            shader.setSampler("DiffuseSampler", target.colorTexture());
            Matrix4f ortho = new Matrix4f().setOrtho(0.0F, (float) width, (float) height, 0.0F, 1000.0F, 3000.0F);
            if (shader.MODEL_VIEW_MATRIX != null) {
                shader.MODEL_VIEW_MATRIX.set(new Matrix4f().translation(0.0F, 0.0F, -2000.0F));
            }
            if (shader.PROJECTION_MATRIX != null) {
                shader.PROJECTION_MATRIX.set(ortho);
            }
            shader.apply();

            float u0 = x0 / (float) width, u1 = x1 / (float) width;
            float v0 = 1.0F - y1 / (float) height, v1 = 1.0F - y0 / (float) height;
            var buffer = RenderSystem.renderThreadTesselator().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            buffer.vertex(x0, y1, 0.0D).uv(u0, v0).color(255, 255, 255, 255).endVertex();
            buffer.vertex(x1, y1, 0.0D).uv(u1, v0).color(255, 255, 255, 255).endVertex();
            buffer.vertex(x1, y0, 0.0D).uv(u1, v1).color(255, 255, 255, 255).endVertex();
            buffer.vertex(x0, y0, 0.0D).uv(u0, v1).color(255, 255, 255, 255).endVertex();
            BufferUploader.draw(buffer.end());
            shader.clear();
        } finally {
            if (cullWasEnabled) RenderSystem.enableCull();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    @Override
    public void close() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}
