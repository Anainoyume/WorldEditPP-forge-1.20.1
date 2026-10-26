package com.redawn.worldeditpp.bridge.minecraft.item;

import java.util.Optional;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Shared native material classification; safe on remote clients without WorldEdit registries. */
public final class DrawMaterialItems {
    public static Optional<Block> block(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        if (stack.is(Items.WATER_BUCKET)) return Optional.of(Blocks.WATER);
        if (stack.is(Items.LAVA_BUCKET)) return Optional.of(Blocks.LAVA);
        if (stack.is(ModItems.AIR_BLOCK.get())) return Optional.of(Blocks.AIR);
        return stack.getItem() instanceof BlockItem item ? Optional.of(item.getBlock()) : Optional.empty();
    }

    private DrawMaterialItems() { }
}
