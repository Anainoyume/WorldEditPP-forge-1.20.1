package com.redawn.worldeditpp.bridge.minecraft.client.gizmo;

import com.redawn.common.selection.SelectionBox;
import com.redawn.common.selection.SelectionFace;
import com.redawn.common.selection.SelectionTransforms;

import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionMirrorForcefieldLayer;
import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayState;
import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionRotationRenderTransform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.redawn.worldeditpp.bridge.minecraft.network.CuboidSelectionPayload;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SelectionMirrorAnimationRenderTest {
    @Test
    void forcefieldUvMatchesVanillaWorldBorderScaleAndThreeSecondScroll() {
        assertEquals(0.0F, SelectionMirrorForcefieldLayer.phase(0), 0.00001F);
        assertEquals(0.5F, SelectionMirrorForcefieldLayer.phase(1500), 0.00001F);
        assertEquals(0.0F, SelectionMirrorForcefieldLayer.phase(3000), 0.00001F);
        assertEquals(0.75F, SelectionMirrorForcefieldLayer.phase(-750), 0.00001F);
        assertEquals(4.25F, SelectionMirrorForcefieldLayer.uv(8.0D, 0.25F), 0.00001F);
        assertEquals(-3.75F, SelectionMirrorForcefieldLayer.uv(-8.0D, 0.25F), 0.00001F);
    }

    @Test
    void sixFacesAnimateAcrossTheFixedPlaneAndFinishAtExactBounds() {
        SelectionBox source = new SelectionBox(-9, 11, -4, -7, 12, -1);
        Vec3 camera = new Vec3(-20, 8, 16);
        for (SelectionFace face : SelectionFace.values()) {
            SelectionOverlayState state = new SelectionOverlayState();
            state.replaceCuboidSelection(new CuboidSelectionPayload(true, -9, 11, -4, -7, 12, -1,
                    -9, 11, -4, -7, 12, -1));
            assertTrue(state.mirrorTo(face, 0));
            assertTrue(state.isMirrorAnimating());
            assertFalse(state.mirrorTo(face, 0), "A running transform cannot be replaced");
            SelectionBox target = SelectionTransforms.mirror(source, face);
            assertEquals(target, state.currentSelectionBox());
            float previousScale = 1.0F;
            for (int milliseconds = 0; milliseconds < 200; milliseconds++) {
                state.tickDisplayAnimation(milliseconds * 1_000_000L);
                assertTrue(state.isSelectionTransformInProgress());
                PoseStack poses = new PoseStack();
                SelectionRotationRenderTransform.apply(poses, state, camera);
                assertTrue(poses.last().pose().isFinite());
                assertTrue(poses.last().normal().isFinite());
                float scale = state.mirrorScale();
                assertTrue(scale <= previousScale);
                previousScale = scale;
                Vector3f pivot = new Vector3f((float) (state.rotationPivotX() - camera.x),
                        (float) (state.rotationPivotY() - camera.y), (float) (state.rotationPivotZ() - camera.z));
                Vector3f transformed = poses.last().pose().transformPosition(new Vector3f(pivot));
                assertEquals(0.0F, transformed.distance(pivot), 0.00001F, "Mirror plane must stay fixed");
            }
            state.tickDisplayAnimation(200_000_000L);
            assertFalse(state.isRotationAnimating());
            assertFalse(state.isMirrorAnimating());
            assertFalse(state.isSelectionTransformInProgress());
            assertEquals(target.minX(), state.cuboidSelectionBox().minX);
            assertEquals(target.maxX() + 1, state.cuboidSelectionBox().maxX);
            assertEquals(target.minY(), state.cuboidSelectionBox().minY);
            assertEquals(target.maxY() + 1, state.cuboidSelectionBox().maxY);
            assertEquals(target.minZ(), state.cuboidSelectionBox().minZ);
            assertEquals(target.maxZ() + 1, state.cuboidSelectionBox().maxZ);
        }
    }

    @Test
    void cancellationDiscardsThePendingMirrorTransform() {
        SelectionOverlayState state = new SelectionOverlayState();
        CuboidSelectionPayload original = new CuboidSelectionPayload(true, 0, 0, 0, 2, 3, 4,
                0, 0, 0, 2, 3, 4);
        state.replaceCuboidSelection(original);
        assertTrue(state.mirrorTo(SelectionFace.EAST, 0));
        state.tickDisplayAnimation(100_000_000L);
        state.replaceCuboidSelection(original);
        state.tickDisplayAnimation(300_000_000L);
        assertFalse(state.isRotationAnimating());
        assertEquals(new SelectionBox(0, 0, 0, 2, 3, 4), state.currentSelectionBox());
    }
}
