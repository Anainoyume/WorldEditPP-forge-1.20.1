package com.redawn.worldeditpp.bridge.minecraft.operation;

import com.redawn.worldeditpp.bridge.minecraft.api.EditingBridge;
import com.redawn.worldeditpp.bridge.minecraft.item.ModItems;
import com.redawn.worldeditpp.bridge.minecraft.mode.EditModeManager;
import com.redawn.worldeditpp.bridge.minecraft.mode.WorldEditAuthorizationService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** Editing lifecycle and interaction policy. Call tick methods on the server thread. */
public final class ServerEditorLifecycle {
    public static boolean denyWand(Player player) {
        if (player instanceof ServerPlayer serverPlayer
                && player.getMainHandItem().is(ModItems.WORLD_EDIT_WAND.get())
                && !WorldEditAuthorizationService.isAuthorized(serverPlayer)) {
            WorldEditAuthorizationService.deny(serverPlayer);
            return true;
        }
        return false;
    }

    public static boolean denyRightClick(Player player, InteractionHand hand, ItemStack stack) {
        return denyWand(player) || (hand == InteractionHand.OFF_HAND
                && player.getMainHandItem().is(ModItems.WORLD_EDIT_WAND.get())
                && stack.getItem() instanceof BlockItem);
    }

    public static void tickPlayer(ServerPlayer player) {
        EditModeManager.applyServerTick(player);
        EditingBridge.get().syncSelection(player);
        if (EditModeManager.isEditing(player)) EditingBridge.get().tickAdjustment(player);
        else EditingBridge.get().clearAdjustment(player);
    }

    public static void tickServer(MinecraftServer server, boolean hasTime) {
        EditingBridge.get().tickPlacements(server, hasTime);
    }

    public static void logout(ServerPlayer player) {
        EditingBridge.get().clearSelectionSync(player);
        EditingBridge.get().clearAdjustment(player);
        EditModeManager.handleLogout(player);
        WorldEditAuthorizationService.clear(player);
    }

    public static void login(ServerPlayer player) {
        EditModeManager.restoreOnLogin(player);
        EditModeManager.syncToClient(player);
        WelcomeMessageService.login(player);
    }

    public static boolean denyGameMode(ServerPlayer player, GameType requested) {
        if (!EditModeManager.isEditing(player) || requested == GameType.CREATIVE) return false;
        player.displayClientMessage(
                Component.translatable("message.worldeditpp.edit_mode.cannot_change_gamemode")
                        .withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    private ServerEditorLifecycle() {}
}
