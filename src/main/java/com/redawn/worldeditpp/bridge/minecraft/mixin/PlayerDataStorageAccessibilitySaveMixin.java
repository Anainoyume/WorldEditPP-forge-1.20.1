package com.redawn.worldeditpp.bridge.minecraft.mixin;

import com.redawn.worldeditpp.bridge.minecraft.mode.EditModeManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerDataStorage.class)
public abstract class PlayerDataStorageAccessibilitySaveMixin {
    @Unique
    private Player worldeditpp$savingPlayer;

    @Inject(method = "save", at = @At("HEAD"))
    private void worldeditpp$rememberSavingPlayer(Player player, CallbackInfo callbackInfo) {
        this.worldeditpp$savingPlayer = player;
    }

    @ModifyArg(
            method = "save",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/io/File;)V"
            ),
            index = 0
    )
    private CompoundTag worldeditpp$sanitizeEditModeSaveData(CompoundTag saveData) {
        if (this.worldeditpp$savingPlayer instanceof ServerPlayer serverPlayer && EditModeManager.isEditing(serverPlayer)) {
            EditModeManager.sanitizeSaveData(serverPlayer, saveData);
        }
        return saveData;
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void worldeditpp$clearSavingPlayer(Player player, CallbackInfo callbackInfo) {
        this.worldeditpp$savingPlayer = null;
    }
}
