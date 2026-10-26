package com.redawn.worldeditpp.bridge.worldedit.operation;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.history.change.BlockChange;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;

/**
 * Fast history-aware single-block write for large uniform edits.
 *
 * <p>{@link EditSession#setBlock} walks the history, mask, change-limiter, reorder and
 * chunk-batching extents for every block. The incremental jobs already own ordering and
 * batching, so they can read the previous state once, append the same {@link BlockChange}
 * WorldEdit would append, and use the raw path for survival, block-bag and side effects.</p>
 */
final class IncrementalBlockWriter {
    static final int UNCHANGED = 0;
    static final int CHANGED = 1;
    static final int LIMIT_REACHED = -1;

    static int setBlock(EditSession session, BlockVector3 position, BaseBlock block,
                        long remainingChanges) {
        BaseBlock previous = session.getFullBlock(position);
        if (previous.equals(block)) return UNCHANGED;
        if (remainingChanges <= 0L) return LIMIT_REACHED;
        session.getChangeSet().add(new BlockChange(position, previous, block));
        return session.smartSetBlock(position, block) ? CHANGED : UNCHANGED;
    }

    private IncrementalBlockWriter() { }
}
