package com.redawn.worldeditpp.bridge.minecraft.mixin.client;

import com.redawn.worldeditpp.mode.EditModeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererEditModeCullingMixin {
    @ModifyArg(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V"
            ),
            index = 3
    )
    private boolean worldeditpp$useSpectatorCullingWhenEditing(boolean isSpectator) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return isSpectator;
        }

        // 原版观察者在实体位于实体方块中时会禁用 smart culling，避免地下透视时区块被错误遮挡剔除。
        // 编辑模式复用这条渲染语义，但不改变真实游戏模式，仍保留创造模式交互能力。
        return isSpectator || EditModeState.isClientEditing(player.getUUID());
    }
}
