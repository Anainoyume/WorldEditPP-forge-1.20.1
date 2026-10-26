package com.redawn.worldeditpp.bridge.minecraft.mixin.client;

import com.redawn.worldeditpp.bridge.minecraft.client.item.StoredBlockStateModels;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uses stored-state geometry with the standard full-block display presentation. */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererBlockStateMixin {
    @Unique
    private final StoredBlockStateModels worldeditpp$storedStateModels = new StoredBlockStateModels();

    @Inject(method = "getModel", at = @At("RETURN"), cancellable = true)
    private void worldeditpp$resolveStoredBlockStateModel(
            ItemStack stack,
            @Nullable Level level,
            @Nullable LivingEntity entity,
            int seed,
            CallbackInfoReturnable<BakedModel> callback
    ) {
        callback.setReturnValue(worldeditpp$storedStateModels.resolve(stack, callback.getReturnValue()));
    }

    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void worldeditpp$clearStoredStateModels(
            ResourceManager resourceManager,
            CallbackInfo callback
    ) {
        worldeditpp$storedStateModels.clear();
    }
}
