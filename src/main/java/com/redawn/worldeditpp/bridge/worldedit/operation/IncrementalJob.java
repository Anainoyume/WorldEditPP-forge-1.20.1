package com.redawn.worldeditpp.bridge.worldedit.operation;

import com.redawn.worldeditpp.operation.task.TickBudget;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * One server-thread incremental edit. Implementations must not start their own world-writing
 * threads; only pure planning may be delegated to {@code PlanningService}.
 */
public interface IncrementalJob {
    UUID ownerId();

    /** Player that receives feedback and whose dimension gates self-issued work. */
    default UUID actorId() { return ownerId(); }

    /** Cheap bookkeeping for every active job, even when the global budget skips its step. */
    default void prepare() { }

    /**
     * Runs one cooperative slice on the server thread.
     *
     * @return true when the job is complete and may be removed
     */
    boolean step(ServerPlayer player, TickBudget budget);

    default int workUnits(boolean spareTime) { return spareTime ? 65_536 : 16_384; }

    default long timeBudgetNanos(boolean spareTime) {
        return spareTime ? 10_000_000L : 3_000_000L;
    }

    /** Releases adapter resources when the scheduler removes or shuts down the job. */
    default void close() { }
}
