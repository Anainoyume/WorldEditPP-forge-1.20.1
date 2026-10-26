package com.redawn.worldeditpp.bridge.minecraft.client.selection;

import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayState;

import com.redawn.common.selection.SelectionFace;
import com.redawn.common.selection.SelectionFaceHit;

import com.redawn.worldeditpp.mode.EditSubMode;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import com.redawn.worldeditpp.bridge.minecraft.api.client.ClientInput;
import com.redawn.worldeditpp.bridge.minecraft.client.input.ClientKeys;
import org.lwjgl.glfw.GLFW;

/** Face-targeted quarter-turn selection rotation. */
final class RotateSelectionModeHandler implements SelectionModeHandler {
    private final SelectionOverlayState state;
    private final GizmoInteractionManager gizmo;
    private final SelectionEditActions edits;

    RotateSelectionModeHandler(
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
        return EditSubMode.ROTATE;
    }

    @Override
    public void prepareFrame(Minecraft minecraft, Camera camera, boolean emptyHand) {
        gizmo.reset();
        state.setSuppressDrawMode(false);
        state.setFaceMasksHidden(true);
        if (state.isRotationAnimating() || !emptyHand || !state.hasCuboidSelection()) {
            state.updateHover(Optional.empty());
            state.updateRotationHandle(null, System.nanoTime());
            return;
        }
        SelectionInteractionController.updateHover(minecraft, camera, state, false);
        SelectionFace face = state.hoverHit().map(SelectionFaceHit::face).orElse(null);
        state.updateRotationHandle(face, System.nanoTime());
    }

    @Override
    public boolean handleMouseButton(Minecraft minecraft, ClientInput.Button event) {
        if (state.isRotationAnimating()) {
            return true;
        }
        if (event.getAction() != GLFW.GLFW_PRESS || !state.isEmptyHand()) {
            return false;
        }
        Optional<SelectionFaceHit> hover = state.hoverHit();
        if (hover.isEmpty()) {
            return false;
        }
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            edits.rotate(hover.get().face(), isAltDown(minecraft));
            return true;
        }
        return event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                || event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
    }

    @Override
    public boolean handleBlockHighlight(Minecraft minecraft, Camera camera) {
        if (state.isRotationAnimating()) {
            return true;
        }
        if (!state.isEmptyHand()) {
            return false;
        }
        SelectionInteractionController.updateHover(minecraft, camera, state, false);
        return state.hoverHit().isPresent();
    }

    @Override
    public void deactivate() {
        state.updateHover(Optional.empty());
        state.resetRotationHandles();
    }

    private static boolean isAltDown(Minecraft minecraft) {
        return ClientKeys.isAltDown(minecraft);
    }
}
