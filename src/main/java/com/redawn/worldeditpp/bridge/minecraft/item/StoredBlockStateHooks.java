package com.redawn.worldeditpp.bridge.minecraft.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Saved item state behavior, independent of vanilla injection signatures. */
public final class StoredBlockStateHooks {
    public static ItemStack picked(ItemStack stack, BlockState state, Player player, boolean controlDown) {
        if (!stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() == state.getBlock()
                && player.getAbilities().instabuild
                && controlDown
                && !state.hasBlockEntity()
                && !state.getProperties().isEmpty()) {
            BlockStateItemData.write(stack, state);
        }
        return stack;
    }

    public static BlockState placed(BlockPos position, Level level, ItemStack stack, BlockState current) {
        var stored = BlockStateItemData.read(stack);
        if (stored.isPresent()) {
            BlockState saved = stored.get().state();
            if (saved.is(current.getBlock()) && !saved.equals(current)) {
                level.setBlock(position, saved, Block.UPDATE_CLIENTS);
                return saved;
            }
        }
        return current;
    }
    private StoredBlockStateHooks() {}
}
