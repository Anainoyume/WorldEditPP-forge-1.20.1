package com.redawn.worldeditpp.bridge.worldedit.operation;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.operation.task.TickBudget;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Single server-tick entry for all incremental editing work.
 *
 * <p>One global cooperative budget covers history, projection placement and future command jobs.
 * Jobs are visited round-robin and each receives a bounded slice. World writes always stay on the
 * server thread; the scheduler never creates threads.</p>
 */
public final class IncrementalJobScheduler {
    private static final IncrementalJobScheduler INSTANCE = new IncrementalJobScheduler();
    private static final long GLOBAL_SPARE_BUDGET_NANOS = 12_000_000L;
    private static final long GLOBAL_BUSY_BUDGET_NANOS = 4_000_000L;
    private static final int GLOBAL_WORK_UNITS = 256;

    private final Map<UUID, IncrementalJob> jobs = new LinkedHashMap<>();
    private int rotation;
    private UUID steppingOwner;

    public static IncrementalJobScheduler instance() {
        return INSTANCE;
    }

    /** @return false when another job already owns this player (caller should report busy) */
    public synchronized boolean register(IncrementalJob job) {
        if (jobs.containsKey(job.ownerId())) return false;
        jobs.put(job.ownerId(), job);
        return true;
    }

    public synchronized void unregister(UUID ownerId) {
        jobs.remove(ownerId);
    }

    public synchronized boolean busy(UUID ownerId) {
        return jobs.containsKey(ownerId);
    }

    /** True while that owner's own job is inside step, allowing its internal world edits. */
    public synchronized boolean isStepping(UUID ownerId) {
        return ownerId != null && ownerId.equals(steppingOwner);
    }

    public void tick(MinecraftServer server, boolean spareTime) {
        List<IncrementalJob> snapshot;
        synchronized (this) {
            if (jobs.isEmpty()) return;
            snapshot = new ArrayList<>(jobs.values());
        }
        TickBudget global = new TickBudget(GLOBAL_WORK_UNITS,
                spareTime ? GLOBAL_SPARE_BUDGET_NANOS : GLOBAL_BUSY_BUDGET_NANOS);
        int count = snapshot.size();
        List<IncrementalJob> completed = new ArrayList<>();
        for (IncrementalJob job : snapshot) {
            try {
                job.prepare();
            } catch (RuntimeException exception) {
                WorldEditPlusPlus.LOGGER.error("Incremental edit job preparation failed: owner={}",
                        job.ownerId(), exception);
            }
        }
        for (int offset = 0; offset < count && global.take(); offset++) {
            IncrementalJob job = snapshot.get((rotation + offset) % count);
            synchronized (this) {
                if (jobs.get(job.ownerId()) != job) continue;
            }
            ServerPlayer player = server == null ? null
                    : server.getPlayerList().getPlayer(job.actorId());
            long nanos = Math.min(job.timeBudgetNanos(spareTime), global.remainingNanos());
            if (nanos <= 0L) break;
            TickBudget budget = new TickBudget(job.workUnits(spareTime), nanos);
            boolean done;
            steppingOwner = job.ownerId();
            try {
                done = job.step(player, budget);
            } catch (RuntimeException exception) {
                WorldEditPlusPlus.LOGGER.error("Incremental edit job failed: owner={}",
                        job.ownerId(), exception);
                done = true;
            } finally {
                steppingOwner = null;
            }
            if (done) {
                synchronized (this) {
                    jobs.remove(job.ownerId(), job);
                }
                completed.add(job);
            }
        }
        for (IncrementalJob job : completed) {
            try {
                job.close();
            } catch (RuntimeException exception) {
                WorldEditPlusPlus.LOGGER.error("Incremental edit job cleanup failed: owner={}",
                        job.ownerId(), exception);
            }
        }
        if (count > 0) rotation = (rotation + 1) % count;
    }

    /** Stops all jobs, used when the logical server shuts down. */
    public void clear() {
        List<IncrementalJob> remaining;
        synchronized (this) {
            remaining = new ArrayList<>(jobs.values());
            jobs.clear();
            rotation = 0;
        }
        for (IncrementalJob job : remaining) {
            try {
                job.close();
            } catch (RuntimeException exception) {
                WorldEditPlusPlus.LOGGER.error("Incremental edit job shutdown failed: owner={}",
                        job.ownerId(), exception);
            }
        }
    }

    private IncrementalJobScheduler() { }
}
