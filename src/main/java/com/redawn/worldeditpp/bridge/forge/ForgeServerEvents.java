package com.redawn.worldeditpp.bridge.forge;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.minecraft.operation.ServerEditorLifecycle;
import com.redawn.worldeditpp.bridge.minecraft.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;


/** Converts loader events into implementation calls without owning editor state. */
@EventBusSubscriber(modid = WorldEditPlusPlus.MOD_ID)
public final class ForgeServerEvents {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Selection tools must not predict AxeItem.useOn (stripping/scraping) either.
        // Only suppress client prediction; useItemOn still sends the server pos2 action.
        if (event.getLevel().isClientSide
                && event.getItemStack().is(ModItems.WORLD_EDIT_WAND.get())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }
        if (ServerEditorLifecycle.denyRightClick(event.getEntity(), event.getHand(), event.getItemStack())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        // Forge 47.1.3 rolls back predicted BlockState, but not BlockEntity NBT.
        // Keep the client entity alive. The vanilla action packet is still sent,
        // so WorldEdit handles selection (and authorization) on the server as usual.
        if (event.getLevel().isClientSide
                && event.getItemStack().is(ModItems.WORLD_EDIT_WAND.get())) {
            event.setCanceled(true);
            return;
        }
        if (ServerEditorLifecycle.denyWand(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.player instanceof ServerPlayer player) ServerEditorLifecycle.tickPlayer(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ServerEditorLifecycle.tickServer(event.getServer(), event.haveTime());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ServerEditorLifecycle.logout(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ServerEditorLifecycle.login(player);
    }

    @SubscribeEvent
    public static void onChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ServerEditorLifecycle.denyGameMode(player, event.getNewGameMode())) event.setCanceled(true);
    }

    private ForgeServerEvents() {}
}
