package com.redawn.worldeditpp.bridge.forge.worldedit.mixin;

import com.redawn.worldeditpp.bridge.minecraft.operation.ContainerRemoval;
import com.sk89q.worldedit.forge.ForgeWorld;
import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 7.2.15 clears container contents but leaves lazy loot tables and the lectern's HAS_BOOK set.
 * Disarm both at the native clear entry so live edits cannot materialize drops on removal.
 */
@Mixin(value = ForgeWorld.class, remap = false)
public abstract class WorldEditContainerClearMixin {
    @Shadow public abstract ServerLevel getWorld();

    @Inject(method = "clearContainerBlockContents(Lcom/sk89q/worldedit/math/BlockVector3;)Z", at = @At("HEAD"))
    private void worldeditpp$disarmContainerHazards(BlockVector3 position, CallbackInfoReturnable<Boolean> result) {
        ContainerRemoval.disarm(getWorld().getBlockEntity(
                new BlockPos(position.getX(), position.getY(), position.getZ())));
    }
}
