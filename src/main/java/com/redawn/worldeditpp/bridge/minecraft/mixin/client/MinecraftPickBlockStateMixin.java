package com.redawn.worldeditpp.bridge.minecraft.mixin.client;

import com.redawn.worldeditpp.bridge.minecraft.item.StoredBlockStateHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Adds portable state data while preserving vanilla pick-block inventory behavior. */
@Mixin(Minecraft.class)
public abstract class MinecraftPickBlockStateMixin {
    @Redirect(
            method = "pickBlock",
            at = @At(
                    remap = false,
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getCloneItemStack("
                            + "Lnet/minecraft/world/phys/HitResult;"
                            + "Lnet/minecraft/world/level/BlockGetter;"
                            + "Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/entity/player/Player;)"
                            + "Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack worldeditpp$copyBlockState(
            BlockState state,
            HitResult hitResult,
            BlockGetter level,
            BlockPos position,
            Player player
    ) {
        ItemStack stack = state.getCloneItemStack(hitResult, level, position, player);
        return StoredBlockStateHooks.picked(stack, state, player, Screen.hasControlDown());
    }
}
