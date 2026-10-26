package com.redawn.worldeditpp.bridge.minecraft.operation;

import com.redawn.worldeditpp.bridge.minecraft.network.AccessibilityPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.BlockStateEditPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.ClearSelectionPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.ClipboardAdjustmentPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.DrawSelectionPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.NudgeSelectionPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.ToggleEditModePayload;
import com.redawn.worldeditpp.bridge.minecraft.network.WandClickPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.WandTeleportPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.WorldEditHistoryPayload;

import com.redawn.worldeditpp.network.*;
import com.redawn.worldeditpp.bridge.minecraft.mode.EditModeManager;
import com.redawn.worldeditpp.bridge.minecraft.mode.WorldEditAuthorizationService;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import com.redawn.worldeditpp.bridge.minecraft.api.EditingBridge;

/** Server-thread editor actions and authorization, independent of the transport. */
public final class ServerEditorActions {
    public static void blockStateEdit(BlockStateEditPayload payload, ServerPlayer source) {
        withPlayer(source, player -> EditingBridge.get().editBlockState(player, payload));
    }

    public static void toggle(ToggleEditModePayload payload, ServerPlayer source) {
        withPlayer(source, player -> EditModeManager.toggle(player, payload.keyName()));
    }

    public static void accessibility(AccessibilityPayload payload, ServerPlayer source) {
        withActiveEditor(source,
                player -> EditModeManager.applyAccessibility(player, payload.action(), payload.value()));
    }

    public static void nudge(NudgeSelectionPayload payload, ServerPlayer source) {
        withPlayer(source, player -> {
            if (WorldEditAuthorizationService.require(player) && EditModeManager.isEditing(player)) {
                EditingBridge.get().setCorners(player,
                        payload.pos1X(), payload.pos1Y(), payload.pos1Z(),
                        payload.pos2X(), payload.pos2Y(), payload.pos2Z());
            }
            // Rejected edits also complete the request, so the client can reconcile its prediction.
            EditingBridge.get().acknowledgeSelection(player, payload.requestId());
        });
    }

    public static void wand(WandClickPayload payload, ServerPlayer source) {
        withActiveEditor(source, player -> EditingBridge.get().selectPoint(player,
                payload.posX(), payload.posY(), payload.posZ(), payload.isPrimary()));
    }

    public static void wandTeleport(WandTeleportPayload payload, ServerPlayer source) {
        withActiveEditor(source, EditingBridge.get()::teleport);
    }

    public static void clear(ClearSelectionPayload payload, ServerPlayer source) {
        withActiveEditor(source, EditingBridge.get()::clearSelection);
    }

    public static void history(WorldEditHistoryPayload payload, ServerPlayer source) {
        withActiveEditor(source, player -> EditingBridge.get().history(player, payload.action()));
    }

    public static void clipboardAdjustment(ClipboardAdjustmentPayload payload, ServerPlayer source) {
        withActiveEditor(source, player -> EditingBridge.get().adjustClipboard(player, payload));
    }

    public static void drawSelection(DrawSelectionPayload payload, ServerPlayer source) {
        if (!payload.valid()) return;
        withActiveEditor(source, player -> EditingBridge.get().draw(
                player, payload.clickedFace(), payload.clickedUpperHalf(), payload.mode()));
    }

    private static void withActiveEditor(ServerPlayer source, Consumer<ServerPlayer> action) {
        withPlayer(source, player -> {
            if (WorldEditAuthorizationService.require(player) && EditModeManager.isEditing(player)) {
                action.accept(player);
            }
        });
    }

    private static void withPlayer(ServerPlayer source, Consumer<ServerPlayer> action) {
        action.accept(source);
    }

    private ServerEditorActions() { }
}
