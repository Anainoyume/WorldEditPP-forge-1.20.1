package com.redawn.worldeditpp.bridge.worldedit.operation;

import com.redawn.worldeditpp.operation.task.BoundedHistoryPlanner;

/**
 * Bounded pure-data planning shared by incremental edit jobs.
 *
 * <p>Only position arrays, clipboards and other detached data may enter this service. Callers
 * must never touch a world, EditSession, LocalSession, Actor or chat from a worker.</p>
 */
public final class PlanningService {
    private static final BoundedHistoryPlanner PLANNER = new BoundedHistoryPlanner(64L * 1024 * 1024,
            Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() / 4)));

    public static BoundedHistoryPlanner.Plan acquire(int count) {
        return PLANNER.tryAcquire(count);
    }

    public static void cancelAll() {
        PLANNER.cancelAll();
    }

    private PlanningService() { }
}
