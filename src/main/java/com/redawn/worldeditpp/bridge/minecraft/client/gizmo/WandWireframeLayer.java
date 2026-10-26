package com.redawn.worldeditpp.bridge.minecraft.client.gizmo;

import com.redawn.worldeditpp.bridge.minecraft.client.selection.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.redawn.worldeditpp.bridge.minecraft.client.draw3d.DrawHelper;
import com.redawn.worldeditpp.bridge.minecraft.client.input.ClientKeys;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

public final class WandWireframeLayer implements SelectionOverlayLayer {
    private static final double BOX_HALF = 0.50D;
    private static final float EDGE_THICKNESS = 0.05F;
    private static final float VISIBLE_R = 0.88F, VISIBLE_G = 0.88F, VISIBLE_B = 0.92F, VISIBLE_A = 0.72F;
    private static final float OCCLUDED_R = 0.52F, OCCLUDED_G = 0.52F, OCCLUDED_B = 0.58F, OCCLUDED_A = 0.30F;

    @Override
    public void render(SelectionOverlayContext context, SelectionOverlayState state) {
        if (SelectionClientController.isMirrorSelecting()) return;
        WandInteractionState wand = SelectionClientController.wandState();
        if (!wand.isHoldingWand()) return;
        if (!isAltDown(context.minecraft())) return;

        wand.smoothTargetPos();

        MultiBufferSource.BufferSource bufferSource = context.minecraft().renderBuffers().bufferSource();
        Camera camera = context.camera();
        PoseStack poseStack = context.poseStack();

        Vec3 camPos = camera.getPosition();
        double cx = wand.smoothRenderX() - camPos.x;
        double cy = wand.smoothRenderY() - camPos.y;
        double cz = wand.smoothRenderZ() - camPos.z;

        VertexConsumer occluded = bufferSource.getBuffer(SelectionOverlayRenderTypes.selectionOccluded());
        renderWireframeCube(poseStack, occluded, cx, cy, cz, BOX_HALF, EDGE_THICKNESS,
                OCCLUDED_R, OCCLUDED_G, OCCLUDED_B, OCCLUDED_A);
        bufferSource.endBatch(SelectionOverlayRenderTypes.selectionOccluded());

        VertexConsumer visible = bufferSource.getBuffer(SelectionOverlayRenderTypes.selectionVisible());
        renderWireframeCube(poseStack, visible, cx, cy, cz, BOX_HALF, EDGE_THICKNESS,
                VISIBLE_R, VISIBLE_G, VISIBLE_B, VISIBLE_A);
        bufferSource.endBatch(SelectionOverlayRenderTypes.selectionVisible());
    }

    private static void renderWireframeCube(PoseStack poseStack, VertexConsumer consumer,
                                            double cx, double cy, double cz,
                                            double half, float thickness,
                                            float r, float g, float b, float a) {
        DrawHelper.renderBoxEdges(poseStack, consumer,
                cx - half, cy - half, cz - half, cx + half, cy + half, cz + half,
                thickness, r, g, b, a);
    }

    private static boolean isAltDown(Minecraft mc) {
        return ClientKeys.isAltDown(mc);
    }
}
