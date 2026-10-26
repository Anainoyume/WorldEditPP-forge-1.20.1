package com.redawn.worldeditpp.bridge.worldedit.history;

import com.sk89q.worldedit.history.change.BlockChange;
import com.sk89q.worldedit.history.change.Change;
import com.sk89q.worldedit.history.changeset.ArrayListHistory;
import com.sk89q.worldedit.history.changeset.BlockOptimizedHistory;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.collection.LocatedBlockList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.AbstractList;
import java.util.List;

/**
 * Read-only, constant-space view of vanilla 7.2.15 history. Its existing previous index
 * already contains each location once; the current map holds that location's final NBT.
 * Do not materialize two million-change streams just to recover these existing pairs.
 */
final class IndexedBlockHistory extends AbstractList<Change> {
    private static final Field PREVIOUS = HistorySessionAccess.field(BlockOptimizedHistory.class, "previous");
    private static final Field CURRENT = HistorySessionAccess.field(BlockOptimizedHistory.class, "current");
    private static final Field OTHER = HistorySessionAccess.field(ArrayListHistory.class, "changes");
    private static final Field ORDER = HistorySessionAccess.field(LocatedBlockList.class, "order");
    private static final Method POSITION;
    static {
        try {
            POSITION = Class.forName("com.sk89q.worldedit.util.collection.PositionList").getDeclaredMethod("get", int.class);
            POSITION.setAccessible(true);
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    private final LocatedBlockList previous, current;
    private final List<Change> others;
    private final Object order;
    private final int blockCount;
    private final boolean undo;
    private long[] chunkOrder;

    @SuppressWarnings("unchecked")
    IndexedBlockHistory(BlockOptimizedHistory history, boolean undo) {
        this.undo = undo;
        previous = (LocatedBlockList) HistorySessionAccess.read(PREVIOUS, history);
        current = (LocatedBlockList) HistorySessionAccess.read(CURRENT, history);
        others = (List<Change>) HistorySessionAccess.read(OTHER, history);
        order = HistorySessionAccess.read(ORDER, previous);
        blockCount = previous.size();
    }

    int preflightSize() { return others.size(); }
    int blockCount() { return blockCount; }
    boolean isBlockIndex(int index) { return index >= others.size() && index < size(); }
    boolean shouldPlanChunkOrder() { return blockCount >= 4_096 && blockCount <= 4_194_304; }
    BlockVector3 originalBlockPosition(int index) {
        if (index < 0 || index >= blockCount) throw new IndexOutOfBoundsException(index);
        try { return (BlockVector3) POSITION.invoke(order, index); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("WorldEdit 7.2.15 history index mismatch", e); }
    }
    void useChunkOrder(long[] positions) {
        if (positions.length != blockCount) throw new IllegalArgumentException("History position count changed");
        chunkOrder = positions;
    }
    void releaseChunkOrder() { chunkOrder = null; }
    BlockVector3 blockPosition(int index) {
        if (!isBlockIndex(index)) throw new IndexOutOfBoundsException(index);
        int at = index - others.size();
        try {
            return chunkOrder == null
                    ? (BlockVector3) POSITION.invoke(order, undo ? blockCount - 1 - at : at)
                    : BlockVector3.fromLongPackedForm(chunkOrder[at]);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("WorldEdit 7.2.15 history index mismatch", e);
        }
    }
    com.sk89q.worldedit.world.block.BaseBlock blockAt(BlockVector3 position, boolean previousState) {
        var block = (previousState ? previous : current).get(position);
        if (block == null) throw new IllegalStateException("History streams disagree at " + position);
        return block;
    }
    @Override public int size() { return Math.addExact(others.size(), blockCount); }
    @Override public Change get(int index) {
        java.util.Objects.checkIndex(index, size());
        if (index < others.size()) return others.get(undo ? others.size() - 1 - index : index);
        var position = blockPosition(index);
        var before = previous.get(position);
        var after = current.get(position);
        if (before == null || after == null) throw new IllegalStateException("History streams disagree at " + position);
        return new BlockChange(position, before, after);
    }
}
