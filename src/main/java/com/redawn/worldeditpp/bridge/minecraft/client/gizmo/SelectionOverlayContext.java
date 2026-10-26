package com.redawn.worldeditpp.bridge.minecraft.client.gizmo;

import com.redawn.worldeditpp.bridge.minecraft.client.selection.*;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

public record SelectionOverlayContext(Minecraft minecraft, Camera camera, PoseStack poseStack, Matrix4f projectionMatrix) {
}
