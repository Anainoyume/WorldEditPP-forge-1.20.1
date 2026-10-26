package com.redawn.worldeditpp.bridge.minecraft.client.gizmo;

import com.redawn.worldeditpp.bridge.minecraft.client.selection.*;

import com.redawn.common.selection.SelectionFace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.redawn.worldeditpp.bridge.minecraft.client.input.ClientKeys;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.EditSubModeState;
import com.redawn.worldeditpp.mode.EditSubMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Textured face handle used by rotation mode. */
public final class SelectionRotationHandleLayer implements SelectionOverlayLayer {
    private static final RenderType ICON_RENDER_TYPE = SelectionOverlayRenderTypes.rotationHandle();
    private static final long SPIN_PERIOD_NANOS = 2_500_000_000L;
    private static final double FACE_OFFSET = 0.012D;
    private static final float MAX_ALPHA = 0.73F;
    private static final float SELECTION_RED = 0.88F;
    private static final float SELECTION_GREEN = 0.82F;
    private static final float SELECTION_BLUE = 0.46F;
    private static final float ADJUSTMENT_RED = 0.88F;
    private static final float ADJUSTMENT_GREEN = 0.88F;
    private static final float ADJUSTMENT_BLUE = 0.92F;

    @Override
    public void render(SelectionOverlayContext context, SelectionOverlayState state) {
        if (!EditSubModeState.supports(EditSubMode.Capability.ROTATE)
                || !state.hasCuboidSelection()
                || !state.isEmptyHand()
                || state.isDrawMode()) {
            return;
        }

        boolean altDown = isAltDown(context);
        long phaseNanos = Math.floorMod(System.nanoTime(), SPIN_PERIOD_NANOS);
        double phase = (double) phaseNanos / SPIN_PERIOD_NANOS * Math.PI * 2.0D;
        double angle = altDown ? phase : -phase;
        AABB box = state.cuboidSelectionBox(context.camera().getPosition());
        Vec3 camera = context.camera().getPosition();
        PoseStack poseStack = context.poseStack();
        MultiBufferSource.BufferSource bufferSource = context.minecraft().renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(ICON_RENDER_TYPE);
        boolean adjustment = SelectionClientController.isClipboardAdjustmentActive();
        float red = adjustment ? ADJUSTMENT_RED : SELECTION_RED;
        float green = adjustment ? ADJUSTMENT_GREEN : SELECTION_GREEN;
        float blue = adjustment ? ADJUSTMENT_BLUE : SELECTION_BLUE;

        poseStack.pushPose();
        try {
            SelectionRotationRenderTransform.apply(poseStack, state, camera);
            for (SelectionFace face : SelectionFace.values()) {
                float alpha = MAX_ALPHA * state.rotationHandleProgress(face);
                if (alpha > 0.0F) {
                    renderIcon(poseStack, consumer, box, face, angle, altDown, red, green, blue, alpha);
                }
            }
            bufferSource.endBatch(ICON_RENDER_TYPE);
        } finally {
            poseStack.popPose();
        }
    }

    private static void renderIcon(
            PoseStack poseStack,
            VertexConsumer consumer,
            AABB box,
            SelectionFace face,
            double angle,
            boolean flipU,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        Basis basis = basis(face);
        Vec3 center = faceCenter(box, face).add(
                face.normalX() * FACE_OFFSET,
                face.normalY() * FACE_OFFSET,
                face.normalZ() * FACE_OFFSET
        );
        double distance = center.length();
        double side = Math.min(faceLimit(box, face), Math.max(0.55D, distance * 0.13D));
        if (side <= 0.0D) {
            return;
        }

        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        Vec3 right = basis.right().scale(cosine).add(basis.up().scale(sine));
        Vec3 up = basis.right().scale(-sine).add(basis.up().scale(cosine));
        double half = side * 0.5D;
        Vec3 topLeft = center.add(right.scale(-half)).add(up.scale(half));
        Vec3 bottomLeft = center.add(right.scale(-half)).add(up.scale(-half));
        Vec3 bottomRight = center.add(right.scale(half)).add(up.scale(-half));
        Vec3 topRight = center.add(right.scale(half)).add(up.scale(half));
        float leftU = flipU ? 1.0F : 0.0F;
        float rightU = flipU ? 0.0F : 1.0F;

        vertex(poseStack, consumer, topLeft, leftU, 0.0F, red, green, blue, alpha);
        vertex(poseStack, consumer, bottomLeft, leftU, 1.0F, red, green, blue, alpha);
        vertex(poseStack, consumer, bottomRight, rightU, 1.0F, red, green, blue, alpha);
        vertex(poseStack, consumer, topRight, rightU, 0.0F, red, green, blue, alpha);
    }

    private static void vertex(
            PoseStack poseStack,
            VertexConsumer consumer,
            Vec3 position,
            float u,
            float v,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        consumer.vertex(poseStack.last().pose(), (float) position.x, (float) position.y, (float) position.z)
                .uv(u, v)
                .color(red, green, blue, alpha).endVertex();
    }

    private static Vec3 faceCenter(AABB box, SelectionFace face) {
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerY = (box.minY + box.maxY) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        return switch (face) {
            case WEST -> new Vec3(box.minX, centerY, centerZ);
            case EAST -> new Vec3(box.maxX, centerY, centerZ);
            case DOWN -> new Vec3(centerX, box.minY, centerZ);
            case UP -> new Vec3(centerX, box.maxY, centerZ);
            case NORTH -> new Vec3(centerX, centerY, box.minZ);
            case SOUTH -> new Vec3(centerX, centerY, box.maxZ);
        };
    }

    private static double faceLimit(AABB box, SelectionFace face) {
        double shortest = switch (face) {
            case WEST, EAST -> Math.min(box.getYsize(), box.getZsize());
            case DOWN, UP -> Math.min(box.getXsize(), box.getZsize());
            case NORTH, SOUTH -> Math.min(box.getXsize(), box.getYsize());
        };
        return shortest * 0.72D;
    }

    private static Basis basis(SelectionFace face) {
        return switch (face) {
            case EAST -> new Basis(new Vec3(0.0D, 0.0D, -1.0D), new Vec3(0.0D, 1.0D, 0.0D));
            case WEST -> new Basis(new Vec3(0.0D, 0.0D, 1.0D), new Vec3(0.0D, 1.0D, 0.0D));
            case UP -> new Basis(new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, -1.0D));
            case DOWN -> new Basis(new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D));
            case SOUTH -> new Basis(new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D));
            case NORTH -> new Basis(new Vec3(-1.0D, 0.0D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D));
        };
    }

    private static boolean isAltDown(SelectionOverlayContext context) {
        return ClientKeys.isAltDown(context.minecraft());
    }

    private record Basis(Vec3 right, Vec3 up) { }
}
