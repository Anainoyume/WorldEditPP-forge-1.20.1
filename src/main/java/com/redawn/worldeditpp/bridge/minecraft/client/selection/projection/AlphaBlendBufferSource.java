package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;

/** Projection-only entity buffers: depth prepass, then one blended colour pass. */
public final class AlphaBlendBufferSource implements MultiBufferSource, AutoCloseable {
    private static final int MAX_RETAINED_TYPES = 32;
    private final Map<RenderType, OwnedBufferBuilder> buffers = new LinkedHashMap<>();
    private final Map<RenderType, VertexBuffer> captured = new LinkedHashMap<>();
    private final Map<RenderType, VertexBuffer> streaming = new LinkedHashMap<>();
    private boolean mirroring, capture;
    public void setMirroring(boolean value) { mirroring = value; }
    void beginCapture() { cancelCapture(); capture = true; }
    void cancelCapture() {
        buffers.values().forEach(OwnedBufferBuilder::reset);
        captured.values().forEach(VertexBuffer::close);
        captured.clear();
        capture = false;
    }
    Map<RenderType, VertexBuffer> finishCapture() {
        // The producer only ever fills BufferBuilders; without this flush the captured map is
        // always empty and every block entity silently falls back to per-frame rendering.
        endBatch();
        capture = false;
        Map<RenderType, VertexBuffer> result = new LinkedHashMap<>(captured);
        captured.clear();
        return result;
    }
    @Override public VertexConsumer getBuffer(RenderType type) {
        var owner = buffers.get(type);
        if (owner == null) {
            owner = new OwnedBufferBuilder(4096);
            buffers.put(type, owner);
        }
        BufferBuilder buffer = owner.buffer();
        if (buffer.building() && !type.canConsolidateConsecutiveGeometry()) endBatch(type);
        if (!buffer.building()) buffer.begin(type.mode(), type.format());
        return buffer;
    }
    public void endBatch() {
        for (RenderType type : buffers.keySet()) endBatch(type);
        // Evict only after the producer has finished using its VertexConsumers.
        var iterator = buffers.entrySet().iterator();
        while (buffers.size() > MAX_RETAINED_TYPES && iterator.hasNext()) {
            var entry = iterator.next();
            entry.getValue().close();
            VertexBuffer vbo = streaming.remove(entry.getKey());
            if (vbo != null) vbo.close();
            iterator.remove();
        }
    }
    public void endBatch(RenderType type) {
        var owner = buffers.get(type);
        if (owner == null || !owner.buffer().building()) return;
        BufferBuilder buffer = owner.buffer();
        // No per-quad sorting: the depth prepass makes the colour order irrelevant, and sorting
        // thousands of block-entity quads every frame was a major projection-only cost.
        BufferBuilder.RenderedBuffer mesh = buffer.end();
        if (capture) {
            if (mesh.drawState().vertexCount() > 0) {
                VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
                try {
                    vbo.bind();
                    vbo.upload(mesh);
                    captured.put(type, vbo);
                } catch (RuntimeException exception) {
                    vbo.close();
                    throw exception;
                } finally {
                    VertexBuffer.unbind();
                }
            } else {
                mesh.release();
            }
            return;
        }
        boolean culling = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        VertexBuffer vbo = streaming.computeIfAbsent(type, ignored -> new VertexBuffer(VertexBuffer.Usage.DYNAMIC));
        try {
            vbo.bind();
            vbo.upload(mesh);
            Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            type.setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            if (mirroring) RenderSystem.disableCull();

            // Pass 1: depth only. Cutout textures still discard, so transparent texels keep holes.
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            vbo.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);

            // Pass 2: colour, one fragment per pixel at the prepass depth, blended once over the
            // world. Without this, a translucent lid would blend over a previously drawn base.
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            vbo.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
        } finally {
            VertexBuffer.unbind();
            if (mirroring && culling) RenderSystem.enableCull();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            type.clearRenderState();
            Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        }
    }
    @Override public void close() {
        buffers.values().forEach(OwnedBufferBuilder::close);
        buffers.clear();
        captured.values().forEach(VertexBuffer::close);
        captured.clear();
        streaming.values().forEach(VertexBuffer::close);
        streaming.clear();
    }

    long bytes() { return buffers.values().stream().mapToLong(OwnedBufferBuilder::bytes).sum(); }
}
