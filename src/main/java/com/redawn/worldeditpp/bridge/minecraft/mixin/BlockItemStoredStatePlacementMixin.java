package com.redawn.worldeditpp.bridge.minecraft.mixin;

import com.redawn.worldeditpp.bridge.minecraft.item.StoredBlockStateHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies portable WorldEdit++ state data through vanilla's post-placement state update. */
@Mixin(BlockItem.class)
public abstract class BlockItemStoredStatePlacementMixin {
    @Inject(method = "updateBlockStateFromTag", at = @At("RETURN"), cancellable = true)
    private void worldeditpp$applyStoredState(
            BlockPos position,
            Level level,
            ItemStack stack,
            BlockState placedState,
            CallbackInfoReturnable<BlockState> callback
    ) {
        callback.setReturnValue(StoredBlockStateHooks.placed(
                position, level, stack, callback.getReturnValue()));
    }
}
