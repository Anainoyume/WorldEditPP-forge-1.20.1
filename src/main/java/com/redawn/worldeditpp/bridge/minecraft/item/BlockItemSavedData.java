package com.redawn.worldeditpp.bridge.minecraft.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;

/** Identifies block items that carry a saved block state or block-entity data. */
public final class BlockItemSavedData {
    public static boolean hasSavedData(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        if (BlockStateItemData.hasStoredProperties(stack)) return true;

        // Copied components (including a custom name) are treated as saved data; the marker
        // tells the player the item was produced by the inspector/pick path.
        return blockItem.getBlock() instanceof EntityBlock
                && stack.getTag() != null;
    }

    private BlockItemSavedData() { }
}
