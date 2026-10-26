package com.redawn.worldeditpp.bridge.worldedit.operation;

import com.redawn.worldeditpp.bridge.minecraft.operation.ChunkLease;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditBridge;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditPlatform;
import com.redawn.worldeditpp.bridge.worldedit.clipboard.RepeatedClipboard;
import com.redawn.worldeditpp.operation.paste.AdaptivePasteBatchSizer;
import com.redawn.worldeditpp.operation.paste.ChunkOrderedPasteCursor;
import com.redawn.worldeditpp.operation.task.TickBudget;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import net.minecraft.server.level.ServerLevel;

/** Mutable state and hot loop for one tick-sliced projection placement. */
public final class ProjectionPasteJob {
    private final ServerLevel level;
    private final Clipboard source;
    private final ChunkLease chunks;
    private final EditSession editSession;
    private final LocalSession localSession;
    private final int targetMinX;
    private final int targetMinY;
    private final int targetMinZ;
    private final ChunkOrderedPasteCursor cursor;
    private final boolean ignoreAir;
    private final RepeatedClipboard repeated;
    private final BaseBlock[] templateBlocks;
    private final AdaptivePasteBatchSizer batchSizer = new AdaptivePasteBatchSizer();
    private int lastReportedPercent;
    private boolean finished;
    private boolean finishSuccessful;

    public static ProjectionPasteJob open(
            ServerLevel level,
            Clipboard source,
            LocalSession localSession,
            int targetMinX,
            int targetMinY,
            int targetMinZ,
            boolean ignoreAir
    ) {
        EditSession editSession = null;
        try {
            editSession = WorldEditBridge.newEditSession(WorldEditPlatform.adapt(level));
            EditSessionTuning.configureIncrementalBulkWrite(editSession);
            return new ProjectionPasteJob(
                    level, source, editSession, localSession,
                    targetMinX, targetMinY, targetMinZ, ignoreAir);
        } catch (RuntimeException exception) {
            if (editSession != null) EditSessionLifecycle.rememberAndClose(localSession, editSession);
            throw exception;
        }
    }

    private ProjectionPasteJob(
            ServerLevel level,
            Clipboard source,
            EditSession editSession,
            LocalSession localSession,
            int targetMinX,
            int targetMinY,
            int targetMinZ,
            boolean ignoreAir
    ) {
        BlockVector3 dimensions = source.getDimensions();
        this.level = level;
        this.source = source;
        this.chunks = new ChunkLease(level);
        this.editSession = editSession;
        this.localSession = localSession;
        this.targetMinX = targetMinX;
        this.targetMinY = targetMinY;
        this.targetMinZ = targetMinZ;
        this.cursor = new ChunkOrderedPasteCursor(
                targetMinX, targetMinZ, dimensions.getX(), dimensions.getY(), dimensions.getZ());
        this.ignoreAir = ignoreAir;
        this.repeated = source instanceof RepeatedClipboard value ? value : null;
        this.templateBlocks = repeated == null ? null : new BaseBlock[repeated.templateVolume()];
    }

    public boolean belongsTo(ServerLevel currentLevel) {
        return level == currentLevel;
    }

    public long completed() {
        return cursor.completed();
    }

    public long total() {
        return cursor.total();
    }

    public boolean isComplete() {
        return !cursor.hasNext();
    }

    public boolean shouldReportProgress() {
        int percent = (int) (completed() * 100L / total());
        if (percent < lastReportedPercent + 5) return false;
        lastReportedPercent = percent;
        return true;
    }

    public void step(long timeBudgetNanos) throws WorldEditException {
        if (finished) throw new IllegalStateException("Projection paste job is already closed");
        long startedAt = System.nanoTime();
        int batchLimit = batchSizer.nextBatchLimit(total() - completed(), timeBudgetNanos);
        int processed = 0;
        TickBudget budget = new TickBudget(batchLimit, timeBudgetNanos);
        while (cursor.hasNext() && budget.take()) {
            int sourceX = cursor.currentX();
            int sourceY = cursor.currentY();
            int sourceZ = cursor.currentZ();
            if (!chunks.ready(targetMinX + sourceX, targetMinZ + sourceZ)) break;
            BaseBlock block = sourceBlock(sourceX, sourceY, sourceZ);
            if (!ignoreAir || !block.getBlockType().getMaterial().isAir()) {
                editSession.setBlock(BlockVector3.at(
                        targetMinX + sourceX,
                        targetMinY + sourceY,
                        targetMinZ + sourceZ), block);
            }
            // Skipped air is still a scanned cell and contributes to progress.
            cursor.advance();
            processed++;
        }
        // Flush at the scheduler boundary so side effects and history stay bounded per tick.
        if (processed > 0) editSession.flushSession();
        batchSizer.recordBatch(processed, System.nanoTime() - startedAt, timeBudgetNanos);
    }

    /** Flushes, remembers even partial work, closes the session and releases chunk tickets. */
    public boolean finish() {
        if (finished) return finishSuccessful;
        finished = true;
        try {
            finishSuccessful = EditSessionLifecycle.rememberAndClose(localSession, editSession);
            return finishSuccessful;
        } finally {
            chunks.close();
        }
    }

    private BaseBlock sourceBlock(int x, int y, int z) {
        if (repeated == null) return source.getFullBlock(BlockVector3.at(x, y, z));
        int cell = repeated.templateCell(x, y, z);
        BaseBlock block = templateBlocks[cell];
        if (block == null) templateBlocks[cell] = block = repeated.templateFullBlock(cell);
        return block;
    }
}
