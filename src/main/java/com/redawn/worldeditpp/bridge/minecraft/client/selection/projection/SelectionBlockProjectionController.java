package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.minecraft.client.ui.ClientFeedback;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController;
import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayRenderer;
import com.redawn.worldeditpp.bridge.minecraft.network.ClipboardAdjustmentPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.SelectionBlocksPayload;
import net.minecraft.client.Minecraft;
import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;

public final class SelectionBlockProjectionController {
    private static final SelectionBlockProjectionState STATE = new SelectionBlockProjectionState();
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
    private static int pendingRotationAxis = -1;
    private static int pendingRotationQuarterTurn;
    private static int pendingMirrorAxis = -1;

    public static SelectionBlockProjectionState state() {
        return STATE;
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level != lastLevel) {
            STATE.reset();
            pendingMirrorAxis = pendingRotationAxis = -1;
            SelectionOverlayRenderer.clearProjectionRenderResources();
            SelectionClientController.resetClipboardAdjustment();
            lastLevel = minecraft.level;
        }
        finishRotationIfReady();
        if (STATE.isActive()) {
            STATE.tickBake(minecraft);
        }
    }

    public static void onResourceReload() {
        boolean active = STATE.isActive();
        STATE.reset();
        pendingMirrorAxis = -1;
        pendingRotationAxis = -1;
        pendingRotationQuarterTurn = 0;
        SelectionOverlayRenderer.clearProjectionRenderResources();
        ClientFeedback.resetThrottle();
        if (active) {
            PlatformBridge.sendToServer(ClipboardAdjustmentPayload.cancel());
            ClientFeedback.notice("message.worldeditpp.projection.reload_cleared");
        }
    }

    public static boolean finishRotationIfReady() {
        if (pendingMirrorAxis >= 0 && !SelectionClientController.isRotationInProgress()) {
            STATE.mirror(pendingMirrorAxis);
            pendingMirrorAxis = -1;
            SelectionClientController.finishMirrorSelection();
            return true;
        }
        if (pendingRotationAxis < 0 || SelectionClientController.isRotationInProgress()) return false;
        STATE.rotate(pendingRotationAxis, pendingRotationQuarterTurn);
        pendingRotationAxis = -1;
        return true;
    }

    public static void requestAdjustment() {
        STATE.beginReceiving();
        PlatformBridge.sendToServer(ClipboardAdjustmentPayload.begin());
    }

    public static void requestSelectionAdjustment() {
        STATE.beginReceiving();
        PlatformBridge.sendToServer(ClipboardAdjustmentPayload.beginSelection());
    }

    public static void cancelAdjustment(boolean notifyServer) {
        STATE.reset();
        pendingMirrorAxis = -1;
        pendingRotationAxis = -1;
        if (notifyServer) PlatformBridge.sendToServer(ClipboardAdjustmentPayload.cancel());
    }

    public static void resetConnectionState() {
        STATE.reset();
        pendingMirrorAxis = -1;
        pendingRotationAxis = -1;
        pendingRotationQuarterTurn = 0;
        lastLevel = null;
        SelectionOverlayRenderer.clearProjectionRenderResources();
    }

    public static boolean stretchFace(int face, boolean expand) {
        if (!STATE.stretchFace(face, expand)) return false;
        PlatformBridge.sendToServer(ClipboardAdjustmentPayload.stretchFace(face, expand));
        return true;
    }

    public static boolean resize(boolean expand) {
        if (!STATE.resize(expand)) return false;
        PlatformBridge.sendToServer(ClipboardAdjustmentPayload.resize(expand));
        return true;
    }

    public static void scheduleRotation(int axis, int quarterTurn) {
        pendingRotationAxis = axis;
        pendingRotationQuarterTurn = quarterTurn;
        PlatformBridge.sendToServer(ClipboardAdjustmentPayload.rotate(axis, quarterTurn));
    }

    public static void scheduleMirror(int axis) {
        pendingMirrorAxis = axis;
        PlatformBridge.sendToServer(ClipboardAdjustmentPayload.mirror(axis));
    }

    public static void handleSelectionBlocks(SelectionBlocksPayload payload) {
        if (!STATE.isActive()) return;
        WorldEditPlusPlus.LOGGER.info("WorldEdit++ 收到投影分片 gen={} chunk={}/{} entries={} palette={}",
                payload.generation(), payload.chunkIndex(), payload.chunkCount(),
                payload.entries() == null ? 0 : payload.entries().length,
                payload.palette() == null ? 0 : payload.palette().length);
        if (payload.chunkCount() == 0) {
            STATE.onSelectionBlocks(payload);
            SelectionClientController.rejectClipboardAdjustment();
        } else if (STATE.onSelectionBlocks(payload)) {
            SelectionClientController.activateClipboardAdjustment(
                    STATE.sizeX(), STATE.sizeY(), STATE.sizeZ());
        }
    }

    private SelectionBlockProjectionController() {
    }
}
