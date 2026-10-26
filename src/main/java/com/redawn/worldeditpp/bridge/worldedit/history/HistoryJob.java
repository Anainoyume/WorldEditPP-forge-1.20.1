package com.redawn.worldeditpp.bridge.worldedit.history;

import com.redawn.worldeditpp.bridge.worldedit.operation.IncrementalJob;
import com.redawn.worldeditpp.operation.task.TickBudget;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Scheduler adapter for the existing history state machine. */
final class HistoryJob implements IncrementalJob {
    private final HistoryTask task;

    HistoryJob(HistoryTask task) {
        this.task = task;
    }

    @Override public UUID ownerId() { return task.owner; }

    @Override public UUID actorId() { return task.actorId; }

    @Override public void prepare() {
        HistoryEngine.pin(task);
    }

    @Override public boolean step(ServerPlayer player, TickBudget budget) {
        HistoryEngine.beginStep(task);
        try {
            boolean completed = task.step(player, budget);
            if (completed) HistoryEngine.complete(task, player);
            return completed;
        } finally {
            HistoryEngine.endStep();
        }
    }

    @Override public int workUnits(boolean spareTime) {
        return spareTime ? 65_536 : 16_384;
    }

    @Override public long timeBudgetNanos(boolean spareTime) {
        return spareTime ? 10_000_000L : 3_000_000L;
    }
}
