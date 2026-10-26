package com.redawn.worldeditpp.bridge.worldedit;

import com.redawn.worldeditpp.bridge.minecraft.mode.EditModeManager;
import com.redawn.worldeditpp.bridge.minecraft.network.WorldEditHistoryPayload;
import com.redawn.worldeditpp.bridge.worldedit.history.HistoryEngine;
import net.minecraft.server.level.ServerPlayer;

/** Network entry point; hotkeys share the same single-operation rule as commands. */
public final class WorldEditHistoryService {
    public static void apply(ServerPlayer player, WorldEditHistoryPayload.Action action) {
        if (!EditModeManager.isEditing(player) || HistoryGate.denyWhileBusy(player)) return;
        HistoryEngine.enqueue(player, action == WorldEditHistoryPayload.Action.UNDO, 1, null);
    }
    private WorldEditHistoryService() {}
}
