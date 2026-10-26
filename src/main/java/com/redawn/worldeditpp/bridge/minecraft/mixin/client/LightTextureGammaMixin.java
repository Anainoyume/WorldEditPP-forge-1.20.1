package com.redawn.worldeditpp.bridge.minecraft.mixin.client;

import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.client.OptionInstance;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.AccessibilityClientState;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightTexture.class)
public abstract class LightTextureGammaMixin {
    @Redirect(
            method = "updateLightTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;",
                    ordinal = 1
            )
    )
    private Object worldeditpp$useEditModeGamma(OptionInstance<?> option) {
        Object vanillaGamma = option.get();
        if (vanillaGamma instanceof Double value) {
            return AccessibilityClientState.gammaForRendering(value);
        }
        return vanillaGamma;
    }
}
