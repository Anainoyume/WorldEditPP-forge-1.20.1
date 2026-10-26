package com.redawn.worldeditpp.bridge.minecraft.client.selection;

import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayState;

import com.redawn.common.selection.SelectionBox;
import com.redawn.common.selection.SelectionFace;
import com.redawn.common.selection.SelectionTransforms;
import com.redawn.common.selection.SelectionVector;

import com.redawn.worldeditpp.bridge.minecraft.client.mode.EditSubModeState;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionController;
import com.redawn.worldeditpp.mode.EditSubMode;
import com.redawn.worldeditpp.bridge.minecraft.network.NudgeSelectionPayload;
import java.util.Optional;
import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;

/** Applies one predicted selection change and sends the matching server request. */
final class SelectionEditActions {
    private final SelectionOverlayState state;

    SelectionEditActions(SelectionOverlayState state) {
        this.state = state;
    }

    boolean nudge(double delta, SelectionVector eye, SelectionVector look) {
        if (!EditSubModeState.supports(EditSubMode.Capability.STRETCH)
                || !canEdit()
                || state.selectedFace() == null) {
            return false;
        }
        if (SelectionClientController.isClipboardAdjustmentActive()) {
            return stretchProjectionFace(delta, eye, look);
        }
        return apply(SelectionTransforms.nudge(state.currentSelectionBox(), state.selectedFace(), delta, eye, look));
    }

    boolean resize(double delta) {
        if (!EditSubModeState.supports(EditSubMode.Capability.STRETCH) || !canEdit() || delta == 0) {
            return false;
        }
        if (SelectionClientController.isClipboardAdjustmentActive()) {
            boolean expand = delta > 0;
            SelectionBox box = state.currentSelectionBox();
            var projection = SelectionBlockProjectionController.state();
            int oldSizeX = projection.sizeX();
            int oldSizeY = projection.sizeY();
            int oldSizeZ = projection.sizeZ();
            if (!SelectionBlockProjectionController.resize(expand)) return false;
            double[] x = resizedAxisBounds(
                    box.minX(), box.maxX(), oldSizeX, projection.sizeX(), projection.tileSize(0));
            double[] y = resizedAxisBounds(
                    box.minY(), box.maxY(), oldSizeY, projection.sizeY(), projection.tileSize(1));
            double[] z = resizedAxisBounds(
                    box.minZ(), box.maxZ(), oldSizeZ, projection.sizeZ(), projection.tileSize(2));
            return applyLocal(new SelectionBox(x[0], y[0], z[0], x[1], y[1], z[1]));
        }
        return apply(SelectionTransforms.resize(state.currentSelectionBox(), delta));
    }

    void move(SelectionBox start, GizmoInteractionManager.DragMove move) {
        if (EditSubModeState.supports(EditSubMode.Capability.TRANSLATE)) {
            SelectionBox moved = SelectionTransforms.move(start, move.dx(), move.dy(), move.dz());
            if (SelectionClientController.isClipboardAdjustmentActive()) {
                applyLocal(moved);
            } else {
                apply(Optional.of(moved));
            }
        }
    }

    boolean rotate(SelectionFace face, boolean counterClockwise) {
        if (!EditSubModeState.supports(EditSubMode.Capability.ROTATE)
                || !canEdit()
                || state.isSelectionTransformInProgress()) {
            return false;
        }
        SelectionTransforms.RotatedSelection rotation = SelectionTransforms.rotate(
                state.currentSelectionBox(),
                state.pos1X(), state.pos1Y(), state.pos1Z(),
                state.pos2X(), state.pos2Y(), state.pos2Z(),
                face, counterClockwise
        );
        if (!state.rotateTo(rotation, System.nanoTime())) {
            return false;
        }
        SelectionBox box = rotation.box();
        if (SelectionClientController.isClipboardAdjustmentActive()) {
            SelectionBlockProjectionController.scheduleRotation(rotation.axis(), rotation.quarterTurn());
            return true;
        }
        PlatformBridge.sendToServer(new NudgeSelectionPayload(
                (int) box.minX(), (int) box.minY(), (int) box.minZ(),
                (int) box.maxX(), (int) box.maxY(), (int) box.maxZ(),
                rotation.pos1X(), rotation.pos1Y(), rotation.pos1Z(),
                rotation.pos2X(), rotation.pos2Y(), rotation.pos2Z(), state.beginSelectionRequest()
        ));
        return true;
    }

    private boolean canEdit() {
        return state.isEmptyHand() && state.hasCuboidSelection();
    }

    private boolean apply(Optional<SelectionBox> change) {
        if (change.isEmpty()) {
            return false;
        }
        SelectionBox box = change.get();
        int minX = (int) box.minX(), minY = (int) box.minY(), minZ = (int) box.minZ();
        int maxX = (int) box.maxX(), maxY = (int) box.maxY(), maxZ = (int) box.maxZ();
        state.nudgeTo(minX, minY, minZ, maxX, maxY, maxZ);
        PlatformBridge.sendToServer(new NudgeSelectionPayload(
                minX, minY, minZ, maxX, maxY, maxZ,
                state.animPos1X(), state.animPos1Y(), state.animPos1Z(),
                state.animPos2X(), state.animPos2Y(), state.animPos2Z(), state.beginSelectionRequest()));
        return true;
    }

    private boolean applyLocal(SelectionBox box) {
        state.nudgeLocalTo(
                (int) box.minX(), (int) box.minY(), (int) box.minZ(),
                (int) box.maxX(), (int) box.maxY(), (int) box.maxZ());
        return true;
    }

    private boolean stretchProjectionFace(double delta, SelectionVector eye, SelectionVector look) {
        SelectionBox box = state.currentSelectionBox();
        SelectionFace face = state.selectedFace();
        Optional<SelectionBox> oneBlock = SelectionTransforms.nudge(box, face, delta, eye, look);
        if (oneBlock.isEmpty()) return false;
        double oldPlane = face.plane(box);
        double direction = Math.signum(face.plane(oneBlock.get()) - oldPlane);
        int axis = face.ordinal() / 2;
        int unit = SelectionBlockProjectionController.state().tileSize(axis);
        SelectionBox target = box.withFace(face, oldPlane + direction * unit);
        double oldLength = axis == 0 ? box.maxX() - box.minX() + 1
                : axis == 1 ? box.maxY() - box.minY() + 1 : box.maxZ() - box.minZ() + 1;
        double newLength = axis == 0 ? target.maxX() - target.minX() + 1
                : axis == 1 ? target.maxY() - target.minY() + 1 : target.maxZ() - target.minZ() + 1;
        boolean expand = newLength > oldLength;
        if (newLength < unit || !SelectionBlockProjectionController.stretchFace(face.ordinal(), expand)) {
            return false;
        }
        return applyLocal(target);
    }

    private static double[] resizedAxisBounds(
            double min, double max, int oldSize, int newSize, int unit
    ) {
        int difference = newSize - oldSize;
        if (difference > 0) {
            int negativeGrowth = Math.min(unit, difference);
            return new double[]{min - negativeGrowth, max + difference - negativeGrowth};
        }
        int removed = -difference;
        int negativeRemoval = Math.min(unit, removed);
        return new double[]{min + negativeRemoval, max - (removed - negativeRemoval)};
    }
}
