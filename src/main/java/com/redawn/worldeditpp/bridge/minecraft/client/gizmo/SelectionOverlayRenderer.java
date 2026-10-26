package com.redawn.worldeditpp.bridge.minecraft.client.gizmo;

import com.redawn.worldeditpp.bridge.minecraft.client.selection.*;

import com.redawn.worldeditpp.bridge.minecraft.client.mode.ClientEditModeController;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionLayer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

/** Ordered render layers; input and selection mutations live in the controller. */
public final class SelectionOverlayRenderer {
    private static final SelectionBlockProjectionLayer PROJECTION = new SelectionBlockProjectionLayer();
    private static final List<SelectionOverlayLayer> LAYERS = List.of(
            new SelectionMirrorForcefieldLayer(),
            new SelectionFaceMaskLayer(),
            new SelectionCuboidFrameLayer(),
            new SelectionMarkerLayer(),
            new WandWireframeLayer(),
            PROJECTION,
            new SelectionRotationHandleLayer(),
            new AxisGizmoLayer()
    );

    public static void render(Camera camera, PoseStack poseStack, Matrix4f projectionMatrix) {
        if (!ClientEditModeController.isEditing()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        SelectionClientController.prepareFrame(minecraft, camera);
        SelectionOverlayContext context = new SelectionOverlayContext(minecraft, camera, poseStack, projectionMatrix);
        for (SelectionOverlayLayer layer : LAYERS) {
            layer.render(context, SelectionClientController.state());
        }
    }

    public static void clearProjectionRenderResources() {
        PROJECTION.close();
    }

    /** Called after vanilla weather/clouds so terrain depth is restored before debug/GUI. */
    public static void restoreDeferredProjectionDepth() {
        PROJECTION.restoreDeferredDepth();
    }

    private SelectionOverlayRenderer() { }
}
