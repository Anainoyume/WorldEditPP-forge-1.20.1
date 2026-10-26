package com.redawn.worldeditpp.bridge.minecraft.client.gizmo;

import com.redawn.worldeditpp.bridge.minecraft.client.selection.*;

import com.redawn.common.gizmo.GizmoElement;
import com.redawn.common.gizmo.GizmoStyle;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.GizmoInteractionManager;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.redawn.worldeditpp.client.draw3d.DrawDirection;
import com.redawn.worldeditpp.bridge.minecraft.client.draw3d.DrawHelper;
import com.redawn.worldeditpp.client.draw3d.DrawPrimitive;
import com.redawn.worldeditpp.bridge.minecraft.client.draw3d.DrawRenderTypes;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.EditSubModeState;
import com.redawn.worldeditpp.mode.EditSubMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AxisGizmoLayer implements SelectionOverlayLayer {
    @Override
    public void render(SelectionOverlayContext context, SelectionOverlayState state) {
        if (!EditSubModeState.supports(EditSubMode.Capability.TRANSLATE)
                || !state.hasCuboidSelection()
                || !state.isEmptyHand()
                || state.isDrawMode()) {
            return;
        }

        AABB box = state.cuboidSelectionBox();
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;

        Vec3 cam = context.camera().getPosition();
        double dx = cx - cam.x;
        double dy = cy - cam.y;
        double dz = cz - cam.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float scale = (float) (dist / 7.0);

        MultiBufferSource.BufferSource bufferSource = context.minecraft().renderBuffers().bufferSource();
        PoseStack ps = context.poseStack();

        ps.pushPose();
        ps.translate(dx, dy, dz);
        ps.scale(scale, scale, scale);

        GizmoInteractionManager gizmo = SelectionClientController.gizmoManager();

        VertexConsumer occluded = bufferSource.getBuffer(DrawRenderTypes.drawOccluded());
        renderGizmo(ps, occluded, gizmo, GizmoStyle.OCCLUDED_EDGE_ALPHA, GizmoStyle.OCCLUDED_FILL_ALPHA);
        bufferSource.endBatch(DrawRenderTypes.drawOccluded());

        VertexConsumer visible = bufferSource.getBuffer(DrawRenderTypes.drawVisible());
        renderGizmo(ps, visible, gizmo, GizmoStyle.VISIBLE_EDGE_ALPHA, GizmoStyle.VISIBLE_FILL_ALPHA);
        bufferSource.endBatch(DrawRenderTypes.drawVisible());

        ps.popPose();
    }

    private static void renderGizmo(PoseStack ps, VertexConsumer vc,
                                    GizmoInteractionManager gizmo, float arrowAlpha, float fillAlpha) {
        float x = gizmo.axisOrientation(0);
        float y = gizmo.axisOrientation(1);
        float z = gizmo.axisOrientation(2);

        float[] xa = gizmo.getElementColor(GizmoElement.X_ARROW);
        DrawHelper.renderArrow(ps, vc, 0, 0, 0, gizmo.axisDirection(0), GizmoStyle.ARROW_LENGTH * Math.abs(x),
                xa[0], xa[1], xa[2], arrowAlpha);
        float[] xf = gizmo.getElementColor(GizmoElement.X_FACE);
        DrawHelper.renderSquareFace(ps, vc, 0, GizmoStyle.FACE_HALF * y, GizmoStyle.FACE_HALF * z, DrawDirection.EAST, GizmoStyle.FACE_SIZE,
                DrawPrimitive.DEFAULT_EDGE_THICKNESS, xf[0], xf[1], xf[2], arrowAlpha, fillAlpha);

        float[] ya = gizmo.getElementColor(GizmoElement.Y_ARROW);
        DrawHelper.renderArrow(ps, vc, 0, 0, 0, gizmo.axisDirection(1), GizmoStyle.ARROW_LENGTH * Math.abs(y),
                ya[0], ya[1], ya[2], arrowAlpha);
        float[] yf = gizmo.getElementColor(GizmoElement.Y_FACE);
        DrawHelper.renderSquareFace(ps, vc, GizmoStyle.FACE_HALF * x, 0, GizmoStyle.FACE_HALF * z, DrawDirection.UP, GizmoStyle.FACE_SIZE,
                DrawPrimitive.DEFAULT_EDGE_THICKNESS, yf[0], yf[1], yf[2], arrowAlpha, fillAlpha);

        float[] za = gizmo.getElementColor(GizmoElement.Z_ARROW);
        DrawHelper.renderArrow(ps, vc, 0, 0, 0, gizmo.axisDirection(2), GizmoStyle.ARROW_LENGTH * Math.abs(z),
                za[0], za[1], za[2], arrowAlpha);
        float[] zf = gizmo.getElementColor(GizmoElement.Z_FACE);
        DrawHelper.renderSquareFace(ps, vc, GizmoStyle.FACE_HALF * x, GizmoStyle.FACE_HALF * y, 0, DrawDirection.SOUTH, GizmoStyle.FACE_SIZE,
                DrawPrimitive.DEFAULT_EDGE_THICKNESS, zf[0], zf[1], zf[2], arrowAlpha, fillAlpha);
    }

}
