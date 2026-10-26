package com.redawn.worldeditpp.bridge.forge.worldedit;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.worldedit.HistoryGate;
import com.redawn.worldeditpp.bridge.worldedit.history.HistoryEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Routes WorldEdit's own undo/redo commands into the tick-sliced engine, without registering
 * any command of its own. Everything else (permissions, parsing, messages) stays in WorldEdit.
 */
@Mod.EventBusSubscriber(modid = WorldEditPlusPlus.MOD_ID)
public final class ForgeHistoryCommands {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void command(CommandEvent event) {
        var parse = event.getParseResults();
        var source = parse.getContext().getSource();
        String[] args = parse.getReader().getString().trim().split("\\s+");
        if (args.length == 0) return;
        String name = args[0].replaceFirst("^/+", "");
        if (name.startsWith("worldedit:")) name = name.substring("worldedit:".length()).replaceFirst("^/+", "");
        boolean undo = name.equals("undo"), redo = name.equals("redo");
        if (!undo && !redo) {
            if ((name.equals("clearhistory") || name.equals("clearhistoryall"))
                    && source.getEntity() instanceof ServerPlayer player
                    && HistoryGate.denyWhileBusy(player)) event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            // A console undo must not bypass a player's reserved history cursor.
            source.sendFailure(Component.translatable("message.worldeditpp.history.player_required"));
            return;
        }
        int times = 1;
        try {
            if (args.length > 3) throw new IllegalArgumentException();
            if (args.length > 1) times = Integer.parseInt(args[1]);
            if (times < 1 || times > 32) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            player.sendSystemMessage(Component.translatable("message.worldeditpp.history.usage"));
            return;
        }
        if (HistoryGate.denyWhileBusy(player)) return;
        HistoryEngine.enqueue(player, undo, times, args.length > 2 ? args[2] : null);
    }

    @SubscribeEvent
    public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) HistoryEngine.disconnect(player);
    }
    @SubscribeEvent
    public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        HistoryEngine.shutdown(event.getServer());
    }
    @SubscribeEvent
    public static void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) HistoryEngine.onLogin(player);
    }
    private ForgeHistoryCommands() {}
}
