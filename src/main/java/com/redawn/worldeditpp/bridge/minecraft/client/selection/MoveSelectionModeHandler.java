package com.redawn.worldeditpp.bridge.minecraft.client.selection;

import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayState;

import com.redawn.common.selection.SelectionBox;

import com.redawn.worldeditpp.mode.EditSubMode;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.redawn.worldeditpp.bridge.minecraft.api.client.ClientInput;
import org.lwjgl.glfw.GLFW;

/** Axis gizmo and whole-selection translation behavior. */
final class MoveSelectionModeHandler implements SelectionModeHandler {
    private final SelectionOverlayState state;
    private final GizmoInteractionManager gizmo;
    private final SelectionEditActions edits;
    private SelectionBox dragStartBox;

    MoveSelectionModeHandler(
            SelectionOverlayState state,
            GizmoInteractionManager gizmo,
            SelectionEditActions edits
    ) {
        this.state = state;
        this.gizmo = gizmo;
        this.edits = edits;
    }

    @Override
    public EditSubMode mode() {
        return EditSubMode.MOVE;
    }

    @Override
    public void prepareFrame(Minecraft minecraft, Camera camera, boolean emptyHand) {
        if (state.hasCuboidSelection()) {
            updateGizmoHover(camera);
            if (!emptyHand) {
                cancelDrag();
                gizmo.reset();
            }
        } else {
            cancelDrag();
            gizmo.reset();
        }

        if (gizmo.isHolded() && dragStartBox != null) {
            AABB box = state.cuboidSelectionBox();
            GizmoInteractionManager.DragMove move = gizmo.updateDrag(
                    camera,
                    (box.minX + box.maxX) * 0.5,
                    (box.minY + box.maxY) * 0.5,
                    (box.minZ + box.maxZ) * 0.5
            );
            if (move != null) {
                edits.move(dragStartBox, move);
            }
            if (gizmo.consumeDistanceCancel()) {
                cancelDrag();
                minecraft.player.displayClientMessage(
                        Component.translatable("message.worldeditpp.selection.drag_too_far")
                                .withStyle(ChatFormatting.RED),
                        true
                );
            }
        } else if (dragStartBox != null) {
            cancelDrag();
        }

        state.setSuppressDrawMode(gizmo.isHovered()
                || gizmo.isHolded()
                || gizmo.isOrientationTransitioning());
        state.setFaceMasksHidden(true);
        state.updateHover(Optional.empty());
    }

    @Override
    public boolean handleMouseButton(Minecraft minecraft, ClientInput.Button event) {
        if (gizmo.isOrientationTransitioning() && state.isEmptyHand()) {
            return event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        }
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return event.getAction() == GLFW.GLFW_PRESS && gizmo.isHolded();
        }
        if (event.getAction() == GLFW.GLFW_PRESS && gizmo.isHovered() && state.isEmptyHand()) {
            gizmo.pressRight(minecraft.gameRenderer.getMainCamera());
            if (gizmo.isHolded()) {
                dragStartBox = state.currentSelectionBox();
                return true;
            }
            cancelDrag();
            return false;
        }
        if (event.getAction() == GLFW.GLFW_RELEASE && gizmo.isHolded()) {
            gizmo.releaseRight(minecraft.gameRenderer.getMainCamera());
            cancelDrag();
            return true;
        }
        return event.getAction() == GLFW.GLFW_PRESS && gizmo.isHolded();
    }

    @Override
    public boolean handleBlockHighlight(Minecraft minecraft, Camera camera) {
        return gizmo.isHolded() || gizmo.isOrientationTransitioning();
    }

    @Override
    public void deactivate() {
        cancelDrag();
        gizmo.reset();
    }

    private void updateGizmoHover(Camera camera) {
        AABB box = state.cuboidSelectionBox();
        double centerX = (box.minX + box.maxX) * 0.5;
        double centerY = (box.minY + box.maxY) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;
        Vec3 cameraPosition = camera.getPosition();
        double dx = centerX - cameraPosition.x;
        double dy = centerY - cameraPosition.y;
        double dz = centerZ - cameraPosition.z;
        float scale = (float) (Math.sqrt(dx * dx + dy * dy + dz * dz) / 7.0);
        gizmo.updateHover(camera, centerX, centerY, centerZ, scale);
    }

    private void cancelDrag() {
        dragStartBox = null;
    }
}
