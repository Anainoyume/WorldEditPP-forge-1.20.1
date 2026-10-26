package com.redawn.worldeditpp.bridge.forge;

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
import com.redawn.worldeditpp.bridge.minecraft.network.DismissWelcomePayload;

import com.redawn.worldeditpp.network.*;
import net.minecraft.server.level.ServerPlayer;

import com.redawn.worldeditpp.bridge.minecraft.operation.ServerEditorActions;
import java.util.function.Consumer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Transport thread handoff only. Permission checks remain in ServerEditorActions. */
final class ForgeServerPayloadHandlers {
    static void blockStateEdit(BlockStateEditPayload payload, Supplier<NetworkEvent.Context> context) {
        withPlayer(context, player -> ServerEditorActions.blockStateEdit(payload, player));
    }

    static void toggle(ToggleEditModePayload payload, Supplier<NetworkEvent.Context> context) {
        withPlayer(context, player -> ServerEditorActions.toggle(payload, player));
    }

    static void accessibility(AccessibilityPayload payload, Supplier<NetworkEvent.Context> context) {
        withPlayer(context, player -> ServerEditorActions.accessibility(payload, player));
    }

    static void nudge(NudgeSelectionPayload payload, Supplier<NetworkEvent.Context> context) {
        withPlayer(context, player -> ServerEditorActions.nudge(payload, player));
    }

    static void wand(WandClickPayload payload, Supplier<NetworkEvent.Context> context) {
        withPlayer(context, player -> ServerEditorActions.wand(payload, player));
    }

    static void wandTeleport(WandTeleportPayload payload, Supplier<NetworkEvent.Context> context) {
        withPlayer(context, player -> ServerEditorActions.wandTeleport(payload, player));
    }

    static void clear(ClearSelectionPayload payload, Supplier<NetworkEvent.Context> context) {
        withPlayer(context, player -> ServerEditorActions.clear(payload, player));
    }

    static void history(WorldEditHistoryPayload payload, Supplier<NetworkEvent.Context> context) {
        withPlayer(context, player -> ServerEditorActions.history(payload, player));
    }

    static void clipboardAdjustment(ClipboardAdjustmentPayload payload, Supplier<NetworkEvent.Context> context) {
        withPlayer(context, player -> ServerEditorActions.clipboardAdjustment(payload, player));
    }

    static void drawSelection(DrawSelectionPayload payload, Supplier<NetworkEvent.Context> context) {
        withPlayer(context, player -> ServerEditorActions.drawSelection(payload, player));
    }

    static void dismissWelcome(DismissWelcomePayload payload, Supplier<NetworkEvent.Context> context) {
        withPlayer(context, com.redawn.worldeditpp.bridge.minecraft.operation.WelcomeMessageService::dismiss);
    }

    private static void withPlayer(Supplier<NetworkEvent.Context> context, Consumer<ServerPlayer> action) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player != null) action.accept(player);
        });
    }
    private ForgeServerPayloadHandlers() {}
}
