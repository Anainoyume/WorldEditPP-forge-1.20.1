package com.redawn.worldeditpp.bridge.minecraft.mixin;

import com.redawn.worldeditpp.mode.EditModeState;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerNoPhysicsMixin {
    @Inject(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            )
    )
    private void worldeditpp$keepEditModeNoPhysics(CallbackInfo callbackInfo) {
        Player player = (Player) (Object) this;
        if (!com.redawn.worldeditpp.bridge.minecraft.mode.MinecraftPlayerContext.isEditing(player)) {
            return;
        }

        // 原版旁观者模式在 Player.tick() 中通过 noPhysics 取消移动碰撞。
        // 编辑模式不是原版旁观者，必须在原版写入 noPhysics = isSpectator() 后重新覆盖。
        player.noPhysics = true;
        player.blocksBuilding = false;
        player.setOnGround(false);
    }

    @Redirect(
            method = "updatePlayerPose",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isSpectator()Z")
    )
    private boolean worldeditpp$useSpectatorPoseRules(Player player) {
        // 只复用观察者的姿态选择语义，避免低矮空间把编辑模式玩家压成爬行姿态。
        return player.isSpectator() || com.redawn.worldeditpp.bridge.minecraft.mode.MinecraftPlayerContext.isEditing(player);
    }
}
