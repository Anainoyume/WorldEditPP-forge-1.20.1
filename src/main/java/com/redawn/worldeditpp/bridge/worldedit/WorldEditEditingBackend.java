package com.redawn.worldeditpp.bridge.worldedit;

import com.redawn.worldeditpp.bridge.minecraft.network.BlockStateEditPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.ClipboardAdjustmentPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.WorldEditHistoryPayload;

import com.redawn.worldeditpp.bridge.minecraft.api.EditingBridge;
import com.redawn.worldeditpp.bridge.worldedit.operation.IncrementalJobScheduler;
import com.redawn.worldeditpp.network.*;
import net.minecraft.server.level.ServerPlayer;



public final class WorldEditEditingBackend implements EditingBridge.Backend {
    @Override
    public void editBlockState(ServerPlayer player, BlockStateEditPayload payload) {
        if (HistoryGate.denyWhileBusy(player)) return;
        BlockStateEditService.handle(player, payload);
    }

    @Override
    public void setCorners(ServerPlayer player, int x1, int y1, int z1, int x2, int y2, int z2) {
        WorldEditSelectionService.setCorners(player, x1, y1, z1, x2, y2, z2);
    }

    @Override
    public void selectPoint(ServerPlayer player, int x, int y, int z, boolean primary) {
        WorldEditSelectionService.selectPoint(player, x, y, z, primary);
    }

    @Override
    public void acknowledgeSelection(ServerPlayer player, long requestId) {
        WorldEditSelectionSync.acknowledgeRequest(player, requestId);
    }

    @Override
    public void teleport(ServerPlayer player) {
        WandTeleportService.teleport(player);
    }

    @Override
    public void clearSelection(ServerPlayer player) {
        WorldEditSelectionService.clear(player);
    }

    @Override
    public void history(ServerPlayer player, WorldEditHistoryPayload.Action action) {
        WorldEditHistoryService.apply(player, action);
    }

    @Override
    public void adjustClipboard(ServerPlayer player, ClipboardAdjustmentPayload payload) {
        // Cancel finalizes the existing partial paste, releasing the queue dependency.
        if (payload.action() != ClipboardAdjustmentPayload.Action.CANCEL && HistoryGate.denyWhileBusy(player)) {
            if (payload.action() == ClipboardAdjustmentPayload.Action.CONFIRM
                    || payload.action() == ClipboardAdjustmentPayload.Action.CONFIRM_IGNORE_AIR) {
                com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge.sendToPlayer(player,
                        new com.redawn.worldeditpp.bridge.minecraft.network.ClipboardPlacementStatusPayload(
                                com.redawn.worldeditpp.bridge.minecraft.network.ClipboardPlacementStatusPayload.Status.FAILED, 0, 0));
            }
            return;
        }
        ClipboardAdjustmentService.handle(player, payload);
    }

    @Override
    public void draw(ServerPlayer player, net.minecraft.core.Direction face, boolean upperHalf, com.redawn.worldeditpp.mode.DrawMode mode) {
        if (HistoryGate.denyWhileBusy(player)) return;
        SelectionDrawService.applyHeldBlocks(player, face, upperHalf, mode);
    }

    @Override
    public void syncSelection(ServerPlayer player) {
        WorldEditSelectionSync.tick(player);
    }

    @Override
    public void clearSelectionSync(ServerPlayer player) {
        WorldEditSelectionSync.clear(player);
    }

    @Override
    public void tickAdjustment(ServerPlayer player) {
        ClipboardAdjustmentService.tick(player);
    }

    @Override
    public void clearAdjustment(ServerPlayer player) {
        ClipboardAdjustmentService.clear(player);
    }

    @Override
    public void tickPlacements(net.minecraft.server.MinecraftServer server, boolean hasTime) {
        IncrementalJobScheduler.instance().tick(server, hasTime);
    }

}
