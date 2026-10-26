package com.redawn.worldeditpp.bridge.minecraft.client.gizmo;

import com.redawn.worldeditpp.bridge.minecraft.client.selection.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;

public final class SelectionRotationRenderTransform {
    public static void apply(PoseStack poseStack, SelectionOverlayState state, Vec3 cameraPosition) {
        if (!state.isRotationAnimating()) {
            return;
        }
        double x = state.rotationPivotX() - cameraPosition.x;
        double y = state.rotationPivotY() - cameraPosition.y;
        double z = state.rotationPivotZ() - cameraPosition.z;
        poseStack.translate(
                x + state.rotationTranslationX(),
                y + state.rotationTranslationY(),
                z + state.rotationTranslationZ());
        if (state.isMirrorAnimating()) {
            float scale = state.mirrorScale();
            poseStack.scale(state.rotationAxis() == 0 ? scale : 1.0F,
                    state.rotationAxis() == 1 ? scale : 1.0F,
                    state.rotationAxis() == 2 ? scale : 1.0F);
        } else switch (state.rotationAxis()) {
            case 0 -> poseStack.mulPose(Axis.XP.rotationDegrees(state.rotationAngleDegrees()));
            case 1 -> poseStack.mulPose(Axis.YP.rotationDegrees(state.rotationAngleDegrees()));
            case 2 -> poseStack.mulPose(Axis.ZP.rotationDegrees(state.rotationAngleDegrees()));
            default -> throw new IllegalStateException("Unknown rotation axis: " + state.rotationAxis());
        }
        poseStack.translate(-x, -y, -z);
    }

    private SelectionRotationRenderTransform() { }
}
