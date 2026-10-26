package com.redawn.worldeditpp.bridge.worldedit;

import com.redawn.worldeditpp.bridge.worldedit.history.HistoryEngine;
import com.redawn.worldeditpp.bridge.worldedit.operation.IncrementalJobScheduler;
import com.sk89q.worldedit.extension.platform.Actor;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Central busy/ownership gate for all incremental edit jobs. A job may edit through its own
 * step (history's executing actor), but no second job/external EditSession may start.
 */
public final class HistoryGate {
    public static boolean denyWhileBusy(ServerPlayer player) {
        UUID owner = player.getUUID();
        if (!IncrementalJobScheduler.instance().busy(owner)
                && !HistoryEngine.busy(owner)
                && !ClipboardAdjustmentService.hasPlacement(owner)) return false;
        HistoryEngine.tellBusy(player);
        return true;
    }

    public static boolean mayCreateEdit(Actor actor) {
        if (actor == null) return true;
        UUID owner = actor.getUniqueId();
        if (!HistoryEngine.mayCreateEdit(actor)) return false;
        IncrementalJobScheduler scheduler = IncrementalJobScheduler.instance();
        if (scheduler.isStepping(owner)) return true;
        return !scheduler.busy(owner) && !ClipboardAdjustmentService.hasPlacement(owner);
    }

    private HistoryGate() {}
}
