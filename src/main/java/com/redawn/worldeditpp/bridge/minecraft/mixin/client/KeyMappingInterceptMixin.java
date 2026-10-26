package com.redawn.worldeditpp.bridge.minecraft.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.EditModeKeyInterceptor;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public class KeyMappingInterceptMixin {

    @Inject(method = "click", at = @At("HEAD"), cancellable = true)
    private static void worldeditpp$interceptClick(InputConstants.Key key, CallbackInfo ci) {
        if (EditModeKeyInterceptor.onRawKey(key)) {
            ci.cancel();
        }
    }

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private static void worldeditpp$interceptSet(InputConstants.Key key, boolean state, CallbackInfo ci) {
        if (EditModeKeyInterceptor.onRawKeySet(key, state)) {
            ci.cancel();
        }
    }
}
