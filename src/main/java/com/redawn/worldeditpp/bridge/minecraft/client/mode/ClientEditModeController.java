package com.redawn.worldeditpp.bridge.minecraft.client.mode;

import com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController;
import com.redawn.common.selection.SelectionVector;
import com.redawn.worldeditpp.mode.EditSubMode;
import com.redawn.worldeditpp.mode.EditModeState;
import com.redawn.worldeditpp.bridge.minecraft.network.ToggleEditModePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;
import com.redawn.worldeditpp.bridge.minecraft.api.client.ClientInput;
import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;
import com.redawn.worldeditpp.bridge.minecraft.client.input.ClientKeys;
import java.util.UUID;

public final class ClientEditModeController {
    private static boolean originalNoGravity;
    private static boolean originalBlocksBuilding;
    private static boolean toggleRequestPending;
    private static boolean entrySnapshotPending;
    private static UUID clientPlayerId;
    private static Boolean pendingServerEditMode;

    private static float getEditFlyingSpeed() {
        return (float) AccessibilityClientState.flightSpeed;
    }

    public static void preTick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            clientPlayerId = player.getUUID();
            if (pendingServerEditMode != null) {
                boolean editing = pendingServerEditMode;
                pendingServerEditMode = null;
                applyServerEditMode(player, editing);
            }
        }
        if (player != null && EditModeState.isClientEditing(player.getUUID())) {
            applyClientEditMovement(player);
        }
    }

    public static boolean isEditing() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && EditModeState.isClientEditing(player.getUUID());
    }

    public static void handleMouseScroll(ClientInput.Scroll event) {
        if (Minecraft.getInstance().screen != null) return;
        if (!isEditing()) {
            return;
        }

        // Mirror selection has no wheel action; leave vanilla hotbar scrolling untouched.
        if (SelectionClientController.isMirrorSelecting()) return;
        if (SelectionClientController.isRotationInProgress()) {
            event.setCanceled(true);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        boolean ctrlDown = ClientKeys.isCtrlDown(window);
        boolean altDown = ClientKeys.isAltDown(window);

        if (altDown && SelectionClientController.wandState().isHoldingWand()) {
            double delta = event.getScrollDeltaY();
            if (delta != 0) {
                SelectionClientController.wandState().adjustDistance(delta);
            }
            event.setCanceled(true);
            return;
        }

        if (EditSubModeState.supports(EditSubMode.Capability.TRANSLATE)
                && SelectionClientController.gizmoManager().isHolded()) {
            event.setCanceled(true);
            return;
        }

        if (ctrlDown && altDown && EditSubModeState.supports(EditSubMode.Capability.STRETCH)) {
            if (EditModeKeyInterceptor.isDrawMode()) return;

            double delta = event.getScrollDeltaY();
            if (delta != 0) {
                SelectionClientController.tryExpandContractSelection(delta);
            }
            event.setCanceled(true);
            return;
        }

        if ((ctrlDown || altDown) && EditSubModeState.supports(EditSubMode.Capability.STRETCH)) {
            if (EditModeKeyInterceptor.isDrawMode()) return;

            double delta = event.getScrollDeltaY();
            if (delta != 0) {
                LocalPlayer player = minecraft.player;
                if (player != null) {
                    net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
                    net.minecraft.world.phys.Vec3 lookVec = player.getLookAngle();
                    SelectionVector eye = new SelectionVector(eyePos.x, eyePos.y, eyePos.z);
                    SelectionVector look = new SelectionVector(lookVec.x, lookVec.y, lookVec.z);
                    SelectionClientController.tryNudgeSelection(delta, eye, look);
                }
            }
            event.setCanceled(true);
        }
    }

    static void handleToggleEditMode() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || toggleRequestPending) {
            return;
        }

        if (!isEditing()) {
            originalNoGravity = player.isNoGravity();
            originalBlocksBuilding = player.blocksBuilding;
            entrySnapshotPending = true;
        }

        toggleRequestPending = true;
        PlatformBridge.sendToServer(new ToggleEditModePayload(WorldEditPlusPlusKeyMappings.CHANGE_MODE.get().getTranslatedKeyMessage().getString()));
    }

    static void applyServerEditMode(boolean editing) {
        toggleRequestPending = false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            pendingServerEditMode = editing;
            return;
        }
        applyServerEditMode(player, editing);
    }

    private static void applyServerEditMode(LocalPlayer player, boolean editing) {
        clientPlayerId = player.getUUID();
        if (!editing && SelectionClientController.hasClipboardAdjustment()) {
            SelectionClientController.cancelClipboardAdjustmentFromServer();
        }
        applyLocalEditMode(player, editing);
    }

    private static void applyLocalEditMode(LocalPlayer player, boolean editing) {
        boolean wasEditing = EditModeState.isClientEditing(player.getUUID());
        if (editing && !wasEditing) {
            if (!entrySnapshotPending) {
                originalNoGravity = player.isNoGravity();
                originalBlocksBuilding = player.blocksBuilding;
            }
            entrySnapshotPending = false;
        } else if (!editing && !wasEditing) {
            entrySnapshotPending = false;
        }
        EditModeState.setClientEditing(player.getUUID(), editing);
        if (!editing && wasEditing) {
            player.noPhysics = false;
            player.setNoGravity(originalNoGravity);
            player.blocksBuilding = originalBlocksBuilding;
        }
    }

    public static void resetConnectionState(LocalPlayer player) {
        UUID playerId = player != null ? player.getUUID() : clientPlayerId;
        if (player != null && playerId != null && EditModeState.isClientEditing(playerId)) {
            player.noPhysics = false;
            player.setNoGravity(originalNoGravity);
            player.blocksBuilding = originalBlocksBuilding;
        }
        if (playerId != null) {
            EditModeState.setClientEditing(playerId, false);
        }
        clientPlayerId = null;
        pendingServerEditMode = null;
        toggleRequestPending = false;
        entrySnapshotPending = false;
        originalNoGravity = false;
        originalBlocksBuilding = true;
    }

    private static void applyClientEditMovement(LocalPlayer player) {
        Abilities abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.flying = true;
        abilities.instabuild = true;
        abilities.mayBuild = true;
        abilities.invulnerable = true;
        abilities.setFlyingSpeed(getEditFlyingSpeed());

        // noPhysics 和 noGravity 提供接近观察者模式的穿墙移动体验，但仍保留创造模式的交互能力。
        player.noPhysics = true;
        player.blocksBuilding = false;
        player.setNoGravity(true);
        player.fallDistance = 0;
    }

    private ClientEditModeController() {
        // 客户端编辑模式控制器只通过静态事件入口调用。
    }
}
