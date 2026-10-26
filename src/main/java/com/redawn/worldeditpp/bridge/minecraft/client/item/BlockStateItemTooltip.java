package com.redawn.worldeditpp.bridge.minecraft.client.item;

import com.redawn.worldeditpp.bridge.minecraft.item.BlockStateItemData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class BlockStateItemTooltip {
    public static void appendStoredState(net.minecraft.world.item.ItemStack stack, java.util.List<Component> tooltip) {
        BlockStateItemData.read(stack).ifPresent(stored -> {
            tooltip.add(Component.translatable("tooltip.worldeditpp.block_state")
                    .withStyle(ChatFormatting.GOLD));
            stored.properties().forEach((name, value) -> tooltip.add(
                    Component.literal("  " + name + ": " + value).withStyle(ChatFormatting.GRAY)));
        });
    }

    private BlockStateItemTooltip() { }
}
