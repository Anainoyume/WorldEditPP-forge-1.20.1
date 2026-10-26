package com.redawn.worldeditpp.bridge.minecraft.mode;

import com.redawn.worldeditpp.mode.EditModeState;
import net.minecraft.world.entity.player.Player;

/** Native player-side lookup; the editor's UUID state remains engine-independent. */
public final class MinecraftPlayerContext {
    public static boolean isEditing(Player player) {
        return player.level().isClientSide()
                ? EditModeState.isClientEditing(player.getUUID())
                : EditModeState.isServerEditing(player.getUUID());
    }

    private MinecraftPlayerContext() {}
}
