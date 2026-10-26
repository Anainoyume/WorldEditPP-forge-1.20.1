package com.redawn.worldeditpp.bridge.minecraft.client.selection;

import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayState;

import com.redawn.worldeditpp.mode.EditSubMode;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import com.redawn.worldeditpp.bridge.minecraft.api.client.ClientInput;
import com.redawn.worldeditpp.bridge.minecraft.client.input.ClientKeys;
import org.lwjgl.glfw.GLFW;

/** Face selection and selection-bound stretching behavior. */
final class StretchSelectionModeHandler implements SelectionModeHandler {
    private final SelectionOverlayState state;
    private final GizmoInteractionManager gizmo;

    StretchSelectionModeHandler(SelectionOverlayState state, GizmoInteractionManager gizmo) {
        this.state = state;
        this.gizmo = gizmo;
    }

    @Override
    public EditSubMode mode() {
        return EditSubMode.STRETCH;
    }

    @Override
    public void prepareFrame(Minecraft minecraft, Camera camera, boolean emptyHand) {
        gizmo.reset();
        state.setSuppressDrawMode(false);
        state.setFaceMasksHidden(!emptyHand);
        if (emptyHand && state.hasCuboidSelection()) {
            SelectionInteractionController.updateHover(
                    minecraft, camera, state, isBackFaceModifierDown(minecraft));
        } else {
            state.updateHover(Optional.empty());
        }
    }

    @Override
    public boolean handleMouseButton(Minecraft minecraft, ClientInput.Button event) {
        if (event.getAction() != GLFW.GLFW_PRESS || !state.isEmptyHand()) {
            return false;
        }
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return SelectionInteractionController.selectHoveredFace(state);
        }
        return (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                || event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
                && state.hoverHit().isPresent();
    }

    @Override
    public boolean handleBlockHighlight(Minecraft minecraft, Camera camera) {
        if (!state.isEmptyHand()) {
            return false;
        }
        SelectionInteractionController.updateHover(
                minecraft, camera, state, isBackFaceModifierDown(minecraft));
        return state.hoverHit().isPresent();
    }

    private static boolean isBackFaceModifierDown(Minecraft minecraft) {
        long window = minecraft.getWindow().getWindow();
        return ClientKeys.isAltDown(window) || ClientKeys.isCtrlDown(window);
    }
}
