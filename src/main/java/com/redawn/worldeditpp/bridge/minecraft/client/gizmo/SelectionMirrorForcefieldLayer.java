package com.redawn.worldeditpp.bridge.minecraft.client.gizmo;

import com.redawn.worldeditpp.bridge.minecraft.client.selection.*;

import com.redawn.common.selection.SelectionFace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;

/** Vanilla-world-border-style animated mask for the hovered mirror plane. */
public final class SelectionMirrorForcefieldLayer implements SelectionOverlayLayer {
    static final long SCROLL_PERIOD_MILLIS = 3000L;
    static final float TEXTURE_SCALE = 0.5F;
    private static final double FACE_OFFSET = 0.006D;
    private static final float VISIBLE_ALPHA = 0.62F;
    private static final float OCCLUDED_ALPHA = 0.20F;

    @Override
    public void render(SelectionOverlayContext context, SelectionOverlayState state) {
        if (!SelectionClientController.isMirrorSelecting()
                || !state.shouldRenderFaceMasks()
                || state.isMirrorAnimating()) {
            return;
        }

        AABB worldBox = state.cuboidSelectionBox();
        AABB renderBox = worldBox.move(context.camera().getPosition().reverse());
        float phase = phase(Util.getMillis());
        MultiBufferSource.BufferSource buffers = context.minecraft().renderBuffers().bufferSource();
        PoseStack poses = context.poseStack();
        poses.pushPose();
        try {
            renderPass(buffers, SelectionOverlayRenderTypes.mirrorForcefieldOccluded(),
                    poses, renderBox, worldBox, state, phase, OCCLUDED_ALPHA);
            renderPass(buffers, SelectionOverlayRenderTypes.mirrorForcefieldVisible(),
                    poses, renderBox, worldBox, state, phase, VISIBLE_ALPHA);
        } finally {
            poses.popPose();
        }
    }

    static float phase(long millis) {
        return (float) Math.floorMod(millis, SCROLL_PERIOD_MILLIS) / SCROLL_PERIOD_MILLIS;
    }

    private static void renderPass(
            MultiBufferSource.BufferSource buffers,
            RenderType type,
            PoseStack poses,
            AABB renderBox,
            AABB worldBox,
            SelectionOverlayState state,
            float phase,
            float alpha
    ) {
        VertexConsumer consumer = buffers.getBuffer(type);
        for (SelectionFace face : SelectionFace.values()) {
            float progress = state.hoverProgress(face);
            if (progress > 0.0F) {
                renderFace(poses, consumer, renderBox, worldBox, face, phase, alpha * progress);
            }
        }
        buffers.endBatch(type);
    }

    private static void renderFace(
            PoseStack poses,
            VertexConsumer consumer,
            AABB box,
            AABB world,
            SelectionFace face,
            float phase,
            float alpha
    ) {
        float x0 = uv(world.minX, phase);
        float x1 = uv(world.maxX, phase);
        float y0 = uv(-world.minY, phase);
        float y1 = uv(-world.maxY, phase);
        float z0 = uv(world.minZ, phase);
        float z1 = uv(world.maxZ, phase);
        switch (face) {
            case WEST -> quadX(poses, consumer, box.minX - FACE_OFFSET,
                    box.minY, box.minZ, box.maxY, box.maxZ, z0, y0, z1, y1, alpha);
            case EAST -> quadX(poses, consumer, box.maxX + FACE_OFFSET,
                    box.minY, box.maxZ, box.maxY, box.minZ, -z1 + phase * 2, y0, -z0 + phase * 2, y1, alpha);
            case DOWN -> quadY(poses, consumer, box.minY - FACE_OFFSET,
                    box.minX, box.maxZ, box.maxX, box.minZ, x0, z1, x1, z0, alpha);
            case UP -> quadY(poses, consumer, box.maxY + FACE_OFFSET,
                    box.minX, box.minZ, box.maxX, box.maxZ, x0, -z0 + phase * 2, x1, -z1 + phase * 2, alpha);
            case NORTH -> quadZ(poses, consumer, box.minZ - FACE_OFFSET,
                    box.minX, box.minY, box.maxX, box.maxY, -x0 + phase * 2, y0, -x1 + phase * 2, y1, alpha);
            case SOUTH -> quadZ(poses, consumer, box.maxZ + FACE_OFFSET,
                    box.maxX, box.minY, box.minX, box.maxY, x1, y0, x0, y1, alpha);
        }
    }

    static float uv(double worldCoordinate, float phase) {
        return phase + (float) worldCoordinate * TEXTURE_SCALE;
    }

    private static void quadX(PoseStack poses, VertexConsumer out, double x,
                              double y0, double z0, double y1, double z1,
                              float u0, float v0, float u1, float v1, float alpha) {
        vertex(out, poses, x, y0, z0, u0, v0, alpha);
        vertex(out, poses, x, y0, z1, u1, v0, alpha);
        vertex(out, poses, x, y1, z1, u1, v1, alpha);
        vertex(out, poses, x, y1, z0, u0, v1, alpha);
    }

    private static void quadY(PoseStack poses, VertexConsumer out, double y,
                              double x0, double z0, double x1, double z1,
                              float u0, float v0, float u1, float v1, float alpha) {
        vertex(out, poses, x0, y, z0, u0, v0, alpha);
        vertex(out, poses, x1, y, z0, u1, v0, alpha);
        vertex(out, poses, x1, y, z1, u1, v1, alpha);
        vertex(out, poses, x0, y, z1, u0, v1, alpha);
    }

    private static void quadZ(PoseStack poses, VertexConsumer out, double z,
                              double x0, double y0, double x1, double y1,
                              float u0, float v0, float u1, float v1, float alpha) {
        vertex(out, poses, x0, y0, z, u0, v0, alpha);
        vertex(out, poses, x1, y0, z, u1, v0, alpha);
        vertex(out, poses, x1, y1, z, u1, v1, alpha);
        vertex(out, poses, x0, y1, z, u0, v1, alpha);
    }

    private static void vertex(VertexConsumer out, PoseStack poses, double x, double y, double z,
                               float u, float v, float alpha) {
        out.vertex(poses.last().pose(), (float) x, (float) y, (float) z)
                .uv(u, v)
                .color(1.0F, 1.0F, 1.0F, alpha).endVertex();
    }
}
