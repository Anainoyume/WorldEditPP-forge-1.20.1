package com.redawn.worldeditpp.bridge.minecraft.operation;

import net.minecraft.world.Clearable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Clears container state before a block is replaced.
 *
 * <p>Vanilla removal handlers materialize contents: {@code ChestBlock.onRemove} drops the
 * inventory and {@code LecternBlock.onRemove} pops the book. WorldEdit 7.2.15 calls
 * {@code Clearable.clearContent()} but leaves two hazards behind: a lazy {@code LootTable} is
 * rolled by the first {@code getItem} during that drop, and a lectern keeps {@code HAS_BOOK=true}
 * so it pops an empty book. Both the WorldEdit native clear entry (live edits) and the history
 * writer use {@link #disarm(BlockEntity)} first.</p>
 */
public final class ContainerRemoval {
    /** Removes the state that vanilla removal would turn into item entities; keeps items intact. */
    public static void disarm(BlockEntity entity) {
        if (entity == null) return;
        if (entity instanceof RandomizableContainerBlockEntity randomizable) {
            // clearContent only empties Items. A later getItem during onRemove would
            // otherwise unpack LootTable and drop freshly generated loot.
            randomizable.setLootTable(null, 0L);
        }
        if (entity instanceof LecternBlockEntity lectern) {
            clearLecternBook(lectern);
        }
    }

    public static void clear(BlockEntity entity) {
        if (entity == null) return;
        disarm(entity);
        if (entity instanceof Clearable clearable) {
            clearable.clearContent();
            entity.setChanged();
        }
    }

    private static void clearLecternBook(LecternBlockEntity lectern) {
        BlockState state = lectern.getBlockState();
        if (!state.hasProperty(LecternBlock.HAS_BOOK) || !state.getValue(LecternBlock.HAS_BOOK)) return;
        lectern.clearContent();
        Level level = lectern.getLevel();
        if (level != null && !level.isClientSide) {
            // Same-block property change: LecternBlock.onRemove only pops when the block changes.
            level.setBlock(lectern.getBlockPos(), state.setValue(LecternBlock.HAS_BOOK, false), 2);
        }
    }

    private ContainerRemoval() {}
}
