package com.redawn.worldeditpp.bridge.minecraft.client.gizmo;

import com.redawn.worldeditpp.bridge.minecraft.client.selection.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.redawn.worldeditpp.bridge.minecraft.client.draw3d.DrawHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;

public final class SelectionCuboidFrameLayer implements SelectionOverlayLayer {
    private static final float EDGE_THICKNESS = 0.05F;
    private static final float DEEMPHASIZED_EDGE_THICKNESS = 0.015F;
    private static final float DEEMPHASIZED_VISIBLE_ALPHA = 0.17F;
    private static final float DEEMPHASIZED_OCCLUDED_ALPHA = 0.08F;

    @Override
    public void render(SelectionOverlayContext context, SelectionOverlayState state) {
        if (!state.hasCuboidSelection()) {
            return;
        }

        AABB box = state.cuboidSelectionBox(context.camera().getPosition());
        MultiBufferSource.BufferSource bufferSource = context.minecraft().renderBuffers().bufferSource();
        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        SelectionRotationRenderTransform.apply(poseStack, state, context.camera().getPosition());

        float blend = state.drawColorBlend();
        boolean projection = SelectionClientController.isClipboardAdjustmentActive();
        float emphasis = projection ? 1.0F : state.selectionStyleProgress();
        float thickness = lerp(DEEMPHASIZED_EDGE_THICKNESS, EDGE_THICKNESS, emphasis);
        float visibleAlpha = lerp(DEEMPHASIZED_VISIBLE_ALPHA, 0.90F, emphasis);
        float occludedAlpha = lerp(DEEMPHASIZED_OCCLUDED_ALPHA, 0.52F, emphasis);

        float vr = projection ? 0.88F : lerp(0.88F, 0.88F, blend);
        float vg = projection ? 0.88F : lerp(0.82F, 0.46F, blend);
        float vb = projection ? 0.92F : lerp(0.46F, 0.88F, blend);

        float or = projection ? 0.52F : lerp(0.62F, 0.62F, blend);
        float og = projection ? 0.52F : lerp(0.52F, 0.30F, blend);
        float ob = projection ? 0.58F : lerp(0.20F, 0.62F, blend);

        VertexConsumer occluded = bufferSource.getBuffer(SelectionOverlayRenderTypes.selectionOccluded());
        renderFrame(poseStack, occluded, box, thickness, or, og, ob, occludedAlpha);
        bufferSource.endBatch(SelectionOverlayRenderTypes.selectionOccluded());

        VertexConsumer visible = bufferSource.getBuffer(SelectionOverlayRenderTypes.selectionVisible());
        renderFrame(poseStack, visible, box, thickness, vr, vg, vb, visibleAlpha);
        bufferSource.endBatch(SelectionOverlayRenderTypes.selectionVisible());
        poseStack.popPose();
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static void renderFrame(PoseStack poseStack, VertexConsumer consumer, AABB box, float thickness, float red, float green, float blue, float alpha) {
        DrawHelper.renderBoxEdges(poseStack, consumer,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                thickness, red, green, blue, alpha);
    }


}
