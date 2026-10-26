package com.redawn.worldeditpp.bridge.minecraft.mixin.client;

import com.redawn.worldeditpp.bridge.minecraft.client.tutorial.WelcomeMessageController;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Handles only WorldEdit++'s opaque welcome links before vanilla treats them as commands. */
@Mixin(Screen.class)
public abstract class ScreenWelcomeLinkMixin {
    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void worldeditpp$handleWelcomeLink(Style style, CallbackInfoReturnable<Boolean> callback) {
        if (WelcomeMessageController.handle(style)) callback.setReturnValue(true);
    }
}
