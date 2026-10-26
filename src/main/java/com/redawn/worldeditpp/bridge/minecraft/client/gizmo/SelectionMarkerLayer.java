package com.redawn.worldeditpp.bridge.minecraft.client.gizmo;

import com.redawn.worldeditpp.bridge.minecraft.client.selection.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.redawn.worldeditpp.bridge.minecraft.client.draw3d.DrawHelper;
import net.minecraft.client.renderer.MultiBufferSource;

public final class SelectionMarkerLayer implements SelectionOverlayLayer {
    private static final double MARKER_HALF = 0.50D;
    private static final float EDGE_THICKNESS = 0.05F;
    private static final float DEEMPHASIZED_EDGE_THICKNESS = 0.015F;
    private static final float MARKER_VISIBLE_ALPHA = 0.88F;
    private static final float MARKER_OCCLUDED_ALPHA = 0.42F;
    private static final float DEEMPHASIZED_VISIBLE_ALPHA = 0.17F;
    private static final float DEEMPHASIZED_OCCLUDED_ALPHA = 0.08F;

    @Override
    public void render(SelectionOverlayContext context, SelectionOverlayState state) {
        if (!state.hasCuboidSelection() || SelectionClientController.isClipboardAdjustmentActive()) {
            return;
        }

        if (state.pos1X() == state.pos2X() && state.pos1Y() == state.pos2Y() && state.pos1Z() == state.pos2Z()) {
            return;
        }

        MultiBufferSource.BufferSource bufferSource = context.minecraft().renderBuffers().bufferSource();
        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        SelectionRotationRenderTransform.apply(poseStack, state, context.camera().getPosition());

        VertexConsumer occluded = bufferSource.getBuffer(SelectionOverlayRenderTypes.selectionOccluded());
        renderMarkers(poseStack, occluded, state, context, false);
        bufferSource.endBatch(SelectionOverlayRenderTypes.selectionOccluded());

        VertexConsumer visible = bufferSource.getBuffer(SelectionOverlayRenderTypes.selectionVisible());
        renderMarkers(poseStack, visible, state, context, true);
        bufferSource.endBatch(SelectionOverlayRenderTypes.selectionVisible());
        poseStack.popPose();
    }

    private static void renderMarkers(PoseStack poseStack, VertexConsumer consumer, SelectionOverlayState state, SelectionOverlayContext context, boolean visiblePass) {
        double camX = context.camera().getPosition().x;
        double camY = context.camera().getPosition().y;
        double camZ = context.camera().getPosition().z;
        float emphasis = state.selectionStyleProgress();
        float thickness = lerp(DEEMPHASIZED_EDGE_THICKNESS, EDGE_THICKNESS, emphasis);
        float visibleAlpha = lerp(DEEMPHASIZED_VISIBLE_ALPHA, MARKER_VISIBLE_ALPHA, emphasis);
        float occludedAlpha = lerp(DEEMPHASIZED_OCCLUDED_ALPHA, MARKER_OCCLUDED_ALPHA, emphasis);

        double cx1 = state.smoothPos1X() + 0.5D - camX;
        double cy1 = state.smoothPos1Y() + 0.5D - camY;
        double cz1 = state.smoothPos1Z() + 0.5D - camZ;
        if (visiblePass) {
            renderWireframeCube(poseStack, consumer, cx1, cy1, cz1, MARKER_HALF, thickness, 1.0F, 0.48F, 0.48F, visibleAlpha);
        } else {
            renderWireframeCube(poseStack, consumer, cx1, cy1, cz1, MARKER_HALF, thickness, 0.72F, 0.30F, 0.30F, occludedAlpha);
        }

        double cx2 = state.smoothPos2X() + 0.5D - camX;
        double cy2 = state.smoothPos2Y() + 0.5D - camY;
        double cz2 = state.smoothPos2Z() + 0.5D - camZ;
        if (visiblePass) {
            renderWireframeCube(poseStack, consumer, cx2, cy2, cz2, MARKER_HALF, thickness, 0.48F, 0.48F, 1.0F, visibleAlpha);
        } else {
            renderWireframeCube(poseStack, consumer, cx2, cy2, cz2, MARKER_HALF, thickness, 0.30F, 0.30F, 0.72F, occludedAlpha);
        }
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static void renderWireframeCube(PoseStack poseStack, VertexConsumer consumer,
                                            double cx, double cy, double cz,
                                            double half, float thickness,
                                            float red, float green, float blue, float alpha) {
        DrawHelper.renderBoxEdges(poseStack, consumer,
                cx - half, cy - half, cz - half, cx + half, cy + half, cz + half,
                thickness, red, green, blue, alpha);
    }
}
