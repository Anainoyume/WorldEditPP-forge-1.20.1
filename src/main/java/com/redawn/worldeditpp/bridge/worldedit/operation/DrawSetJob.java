package com.redawn.worldeditpp.bridge.worldedit.operation;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.minecraft.operation.ChunkLease;
import com.redawn.worldeditpp.bridge.worldedit.SelectionDrawService;
import com.redawn.worldeditpp.bridge.worldedit.SelectionDrawShapes;
import com.redawn.worldeditpp.bridge.worldedit.RegionSelection;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditPlatform;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditSessions;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditSelectionService;
import com.redawn.worldeditpp.mode.DrawMode;
import com.redawn.worldeditpp.operation.task.TickBudget;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/** Tick-sliced draw-mode SET: main-hand block, no offhand source filter. */
public final class DrawSetJob implements IncrementalJob {
    private final ServerPlayer player;
    private final ServerLevel level;
    private final BaseBlock replacement;
    private final LocalSession localSession;
    private final EditSession editSession;
    private final ChunkLease chunks;
    private final RegionSectionCursor cursor;
    private final int changeLimit;
    private long affected;
    private int lastPercent = -1;
    private boolean finished;

    /**
     * @return null when draw SET must stay on the native synchronous path (offhand filter, walls,
     *         missing selection/material, or a non-SET mode)
     */
    public static DrawSetJob tryOpen(ServerPlayer player, Direction clickedFace,
                                     boolean clickedUpperHalf, DrawMode mode) {
        if (mode != DrawMode.SET) return null;
        Region selection = WorldEditSelectionService.currentSelection(player)
                .map(RegionSelection::region).orElse(null);
        if (selection == null) return null;
        var registries = player.level().registryAccess();
        var source = SelectionDrawService.resolveDrawMaterial(
                player.getItemInHand(InteractionHand.OFF_HAND), registries).orElse(null);
        var replacement = SelectionDrawService.resolveDrawMaterial(
                player.getItemInHand(InteractionHand.MAIN_HAND), registries).orElse(null);
        if (source != null || replacement == null) return null;
        if (!replacement.exactState()) {
            replacement = replacement.withState(SelectionDrawService.orientForSet(
                    replacement.state(), player.getDirection(),
                    Direction.orderedByNearest(player)[0], clickedFace, clickedUpperHalf));
        }
        Region candidates = SelectionDrawShapes.region(selection, mode);
        LocalSession localSession = WorldEditSessions.of(player);
        if (localSession == null) return null;
        var actor = WorldEditPlatform.adaptPlayer(player);
        EditSession editSession = localSession.createEditSession(actor);
        try {
            EditSessionTuning.configureIncrementalBulkWrite(editSession);
            if (replacement.exactState()) EditSessionTuning.suppressBlockUpdates(editSession);
            return new DrawSetJob(player, candidates, replacement.toBaseBlock(),
                    localSession, editSession);
        } catch (RuntimeException exception) {
            EditSessionLifecycle.rememberAndClose(localSession, editSession);
            WorldEditPlusPlus.LOGGER.warn("Cannot start incremental draw SET", exception);
            return null;
        }
    }

    private DrawSetJob(ServerPlayer player, Region region, BaseBlock replacement,
                       LocalSession localSession, EditSession editSession) {
        this.player = player;
        this.level = player.serverLevel();
        this.replacement = replacement;
        this.localSession = localSession;
        this.editSession = editSession;
        this.chunks = new ChunkLease(level);
        this.cursor = new RegionSectionCursor(region, chunks,
                UniformSectionSkip.find(level, region, replacement));
        this.changeLimit = editSession.getBlockChangeLimit();
        player.displayClientMessage(Component.translatable("message.worldeditpp.set.start")
                .withStyle(ChatFormatting.YELLOW), true);
    }

    @Override public UUID ownerId() { return player.getUUID(); }

    @Override public UUID actorId() { return player.getUUID(); }

    @Override public boolean step(ServerPlayer actor, TickBudget budget) {
        if (finished) return true;
        if (actor == null || actor.serverLevel() != level) {
            fail(new IllegalStateException("Player left the draw job's dimension"));
            return true;
        }
        try {
            while (budget.take()) {
                BlockVector3 current = cursor.next();
                if (current == null) break;
                long remainingChanges = changeLimit < 0 ? Long.MAX_VALUE
                        : (long) changeLimit - affected;
                int result = IncrementalBlockWriter.setBlock(
                        editSession, current, replacement, remainingChanges);
                if (result == IncrementalBlockWriter.LIMIT_REACHED) {
                    fail(new IllegalStateException("Block change limit reached"));
                    return true;
                }
                if (result == IncrementalBlockWriter.CHANGED) affected++;
                showProgress();
            }
            if (cursor.finished()) {
                finish();
                return true;
            }
            return false;
        } catch (RuntimeException exception) {
            fail(exception);
            return true;
        }
    }

    private void showProgress() {
        long total = cursor.total();
        if (total <= 0L) return;
        int percent = (int) Math.min(100L, cursor.processed() * 100L / total);
        if (percent == lastPercent) return;
        lastPercent = percent;
        player.displayClientMessage(Component.translatable(
                        "message.worldeditpp.set.progress", percent)
                .withStyle(ChatFormatting.YELLOW), true);
    }

    private void finish() {
        if (finished) return;
        finished = true;
        boolean successful;
        try {
            successful = EditSessionLifecycle.rememberAndClose(localSession, editSession);
        } finally {
            chunks.close();
        }
        player.displayClientMessage(Component.empty(), true);
        if (successful) {
            player.sendSystemMessage(Component.translatable(
                    "message.worldeditpp.set.complete", affected).withStyle(ChatFormatting.GREEN));
        } else {
            player.sendSystemMessage(Component.translatable("message.worldeditpp.draw.failed")
                    .withStyle(ChatFormatting.RED));
        }
    }

    private void fail(Throwable exception) {
        if (finished) return;
        finished = true;
        WorldEditPlusPlus.LOGGER.warn("Incremental draw SET failed for {}",
                player.getGameProfile().getName(), exception);
        try {
            EditSessionLifecycle.rememberAndClose(localSession, editSession);
        } finally {
            chunks.close();
        }
        player.displayClientMessage(Component.translatable("message.worldeditpp.draw.failed")
                .withStyle(ChatFormatting.RED), true);
    }

    @Override public void close() {
        if (finished) return;
        finished = true;
        try {
            EditSessionLifecycle.rememberAndClose(localSession, editSession);
        } finally {
            chunks.close();
        }
        player.displayClientMessage(Component.empty(), true);
    }
}
