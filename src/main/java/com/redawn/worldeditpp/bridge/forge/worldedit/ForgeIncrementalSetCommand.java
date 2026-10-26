package com.redawn.worldeditpp.bridge.forge.worldedit;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.worldedit.HistoryGate;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditPlatform;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditSessions;
import com.redawn.worldeditpp.bridge.worldedit.operation.IncrementalJobScheduler;
import com.redawn.worldeditpp.bridge.worldedit.operation.RegionSetJob;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Replaces only {@code //set <pattern>} with the incremental job engine. Other commands keep
 * WorldEdit's own parsing, permissions, limits and messages.
 */
@Mod.EventBusSubscriber(modid = WorldEditPlusPlus.MOD_ID)
public final class ForgeIncrementalSetCommand {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void command(CommandEvent event) {
        String[] args = event.getParseResults().getReader().getString().trim().split("\\s+");
        if (args.length != 2) return;
        String name = args[0].replaceFirst("^/+", "");
        if (name.startsWith("worldedit:")) name = name.substring("worldedit:".length()).replaceFirst("^/+", "");
        if (!name.equals("set")) return;
        if (!(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var actor = WorldEditPlatform.adaptPlayer(player);
        if (!actor.hasPermission("worldedit.region.set")) return;
        LocalSession session = WorldEditSessions.of(player);
        if (session == null) return;
        World world = WorldEditPlatform.adapt(player.serverLevel());
        Region region;
        try {
            region = session.getSelection(world);
        } catch (IncompleteRegionException exception) {
            return;
        }
        Pattern pattern;
        try {
            ParserContext context = new ParserContext();
            context.setActor(actor);
            context.setWorld(world);
            context.setSession(session);
            context.setExtent(world);
            pattern = WorldEdit.getInstance().getPatternFactory().parseFromInput(args[1], context);
        } catch (InputParseException | RuntimeException exception) {
            return;
        }
        if (pattern == null) return;
        event.setCanceled(true);
        if (HistoryGate.denyWhileBusy(player)) return;
        RegionSetJob job;
        try {
            job = RegionSetJob.open(player, region, pattern);
        } catch (RuntimeException exception) {
            WorldEditPlusPlus.LOGGER.warn("Cannot start incremental //set", exception);
            player.sendSystemMessage(Component.translatable("message.worldeditpp.set.failed")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (!IncrementalJobScheduler.instance().register(job)) {
            job.close();
            HistoryGate.denyWhileBusy(player);
        }
    }

    private ForgeIncrementalSetCommand() { }
}
