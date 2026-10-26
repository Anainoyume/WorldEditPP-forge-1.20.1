package com.redawn.worldeditpp.bridge.minecraft.client.gizmo;

import com.redawn.worldeditpp.bridge.minecraft.client.selection.*;

import com.redawn.common.selection.SelectionFace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;

public final class SelectionFaceMaskLayer implements SelectionOverlayLayer {
    private static final double FACE_OFFSET = 0.004D;
    private static final float VISIBLE_HOVER_ALPHA = 0.24F;
    private static final float OCCLUDED_HOVER_ALPHA = 0.14F;
    private static final float VISIBLE_SELECTED_ALPHA = 0.30F;
    private static final float OCCLUDED_SELECTED_ALPHA = 0.17F;
    private static final float VISIBLE_PROJECTION_SELECTED_ALPHA = 0.62F;
    private static final float OCCLUDED_PROJECTION_SELECTED_ALPHA = 0.38F;

    @Override
    public void render(SelectionOverlayContext context, SelectionOverlayState state) {
        if (SelectionClientController.isMirrorSelecting() || !state.shouldRenderFaceMasks()) {
            return;
        }

        AABB box = state.cuboidSelectionBox(context.camera().getPosition());
        MultiBufferSource.BufferSource bufferSource = context.minecraft().renderBuffers().bufferSource();
        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        SelectionRotationRenderTransform.apply(poseStack, state, context.camera().getPosition());

        float blend = state.drawColorBlend();
        boolean drawMode = state.isDrawMode();

        VertexConsumer occluded = bufferSource.getBuffer(SelectionOverlayRenderTypes.selectionOccluded());
        renderMasks(poseStack, occluded, box, state, false, blend, drawMode);
        bufferSource.endBatch(SelectionOverlayRenderTypes.selectionOccluded());

        VertexConsumer visible = bufferSource.getBuffer(SelectionOverlayRenderTypes.selectionVisible());
        renderMasks(poseStack, visible, box, state, true, blend, drawMode);
        bufferSource.endBatch(SelectionOverlayRenderTypes.selectionVisible());
        poseStack.popPose();
    }

    private static void renderMasks(PoseStack poseStack, VertexConsumer consumer, AABB box, SelectionOverlayState state, boolean visiblePass, float blend, boolean drawMode) {
        for (SelectionFace face : SelectionFace.values()) {
            if (!drawMode) {
                float selected = state.selectedProgress(face);
                if (selected > 0.0F) {
                    boolean projection = SelectionClientController.isClipboardAdjustmentActive();
                    float sr = projection ? 0.88F : lerp(0.88F, 0.88F, blend);
                    float sg = projection ? 0.88F : lerp(0.82F, 0.46F, blend);
                    float sb = projection ? 0.92F : lerp(0.46F, 0.88F, blend);
                    float or = projection ? 0.52F : lerp(0.72F, 0.72F, blend);
                    float og = projection ? 0.52F : lerp(0.64F, 0.36F, blend);
                    float ob = projection ? 0.58F : lerp(0.30F, 0.72F, blend);
                    float selectedAlpha = projection
                            ? VISIBLE_PROJECTION_SELECTED_ALPHA : VISIBLE_SELECTED_ALPHA;
                    float occludedSelectedAlpha = projection
                            ? OCCLUDED_PROJECTION_SELECTED_ALPHA : OCCLUDED_SELECTED_ALPHA;
                    if (visiblePass) {
                        renderFace(poseStack, consumer, box, face, sr, sg, sb, selectedAlpha * selected);
                    } else {
                        renderFace(poseStack, consumer, box, face, or, og, ob, occludedSelectedAlpha * selected);
                    }
                }
            }

            if (!drawMode) {
                float hover = state.hoverProgress(face);
                if (hover > 0.0F) {
                    if (visiblePass) {
                        renderFace(poseStack, consumer, box, face, 1.0F, 1.0F, 1.0F, VISIBLE_HOVER_ALPHA * hover);
                    } else {
                        renderFace(poseStack, consumer, box, face, 0.84F, 0.84F, 0.84F, OCCLUDED_HOVER_ALPHA * hover);
                    }
                }
            }
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static void renderFace(PoseStack poseStack, VertexConsumer consumer, AABB box, SelectionFace face, float red, float green, float blue, float alpha) {
        switch (face) {
            case WEST -> quadX(poseStack, consumer, box.minX - FACE_OFFSET, box.minY, box.minZ, box.maxY, box.maxZ, red, green, blue, alpha);
            case EAST -> quadX(poseStack, consumer, box.maxX + FACE_OFFSET, box.minY, box.maxZ, box.maxY, box.minZ, red, green, blue, alpha);
            case DOWN -> quadY(poseStack, consumer, box.minY - FACE_OFFSET, box.minX, box.maxZ, box.maxX, box.minZ, red, green, blue, alpha);
            case UP -> quadY(poseStack, consumer, box.maxY + FACE_OFFSET, box.minX, box.minZ, box.maxX, box.maxZ, red, green, blue, alpha);
            case NORTH -> quadZ(poseStack, consumer, box.minZ - FACE_OFFSET, box.minX, box.minY, box.maxX, box.maxY, red, green, blue, alpha);
            case SOUTH -> quadZ(poseStack, consumer, box.maxZ + FACE_OFFSET, box.maxX, box.minY, box.minX, box.maxY, red, green, blue, alpha);
        }
    }

    private static void quadX(PoseStack poseStack, VertexConsumer consumer, double x, double minY, double firstZ, double maxY, double secondZ, float red, float green, float blue, float alpha) {
        vertex(consumer, poseStack, x, minY, firstZ, red, green, blue, alpha);
        vertex(consumer, poseStack, x, minY, secondZ, red, green, blue, alpha);
        vertex(consumer, poseStack, x, maxY, secondZ, red, green, blue, alpha);
        vertex(consumer, poseStack, x, maxY, firstZ, red, green, blue, alpha);
    }

    private static void quadY(PoseStack poseStack, VertexConsumer consumer, double y, double minX, double firstZ, double maxX, double secondZ, float red, float green, float blue, float alpha) {
        vertex(consumer, poseStack, minX, y, firstZ, red, green, blue, alpha);
        vertex(consumer, poseStack, maxX, y, firstZ, red, green, blue, alpha);
        vertex(consumer, poseStack, maxX, y, secondZ, red, green, blue, alpha);
        vertex(consumer, poseStack, minX, y, secondZ, red, green, blue, alpha);
    }

    private static void quadZ(PoseStack poseStack, VertexConsumer consumer, double z, double firstX, double minY, double secondX, double maxY, float red, float green, float blue, float alpha) {
        vertex(consumer, poseStack, firstX, minY, z, red, green, blue, alpha);
        vertex(consumer, poseStack, secondX, minY, z, red, green, blue, alpha);
        vertex(consumer, poseStack, secondX, maxY, z, red, green, blue, alpha);
        vertex(consumer, poseStack, firstX, maxY, z, red, green, blue, alpha);
    }

    private static void vertex(VertexConsumer consumer, PoseStack poseStack, double x, double y, double z, float red, float green, float blue, float alpha) {
        consumer.vertex(poseStack.last().pose(), (float)x, (float)y, (float)z).color(red, green, blue, alpha).endVertex();
    }
}
