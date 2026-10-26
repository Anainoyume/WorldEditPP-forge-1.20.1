package com.redawn.worldeditpp.bridge.forge.worldedit.mixin;

import com.redawn.worldeditpp.bridge.minecraft.operation.NativeBiomeAccess;
import com.sk89q.worldedit.forge.ForgeWorld;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.biome.BiomeType;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fix the native 7.2.15 palette write, preserving all WorldEdit/protection extent wrappers. */
@Mixin(value = ForgeWorld.class, remap = false)
public abstract class WorldEditBiomeMixin {
    @Shadow public abstract ServerLevel getWorld();

    @Inject(method = "setBiome(Lcom/sk89q/worldedit/math/BlockVector3;Lcom/sk89q/worldedit/world/biome/BiomeType;)Z",
            at = @At("HEAD"), cancellable = true)
    private void worldeditpp$writeBiomeCell(BlockVector3 pos, BiomeType biome, CallbackInfoReturnable<Boolean> result) {
        result.setReturnValue(NativeBiomeAccess.set(getWorld(),pos.getX(),pos.getY(),pos.getZ(),biome.getId()));
    }
}
