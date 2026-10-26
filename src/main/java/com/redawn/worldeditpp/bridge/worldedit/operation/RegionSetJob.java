package com.redawn.worldeditpp.bridge.worldedit.operation;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.minecraft.operation.ChunkLease;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditBridge;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditPlatform;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditSessions;
import com.redawn.worldeditpp.operation.task.TickBudget;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.function.pattern.BlockPattern;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Tick-sliced implementation of {@code //set <pattern>}. */
public final class RegionSetJob implements IncrementalJob {
    private final ServerPlayer player;
    private final ServerLevel level;
    private final Pattern pattern;
    private final BaseBlock fixedBlock;
    private final LocalSession localSession;
    private final EditSession editSession;
    private final ChunkLease chunks;
    private final RegionSectionCursor cursor;
    private final int changeLimit;
    private long affected;
    private int lastPercent = -1;
    private boolean finished;

    public static RegionSetJob open(ServerPlayer player, Region region, Pattern pattern) {
        LocalSession session = WorldEditSessions.of(player);
        if (session == null) throw new IllegalStateException("WorldEdit session missing");
        var actor = WorldEditPlatform.adaptPlayer(player);
        EditSession edit = WorldEditBridge.worldEdit().newEditSessionBuilder()
                .world(WorldEditPlatform.adapt(player.serverLevel()))
                .actor(actor)
                .blockBag(session.getBlockBag(actor))
                .maxBlocks(session.getBlockChangeLimit())
                .build();
        try {
            EditSessionTuning.configureIncrementalBulkWrite(edit);
            return new RegionSetJob(player, region, pattern, fixedBlock(pattern), session, edit);
        } catch (RuntimeException exception) {
            EditSessionLifecycle.rememberAndClose(session, edit);
            throw exception;
        }
    }

    private static BaseBlock fixedBlock(Pattern pattern) {
        if (pattern instanceof BlockPattern blockPattern) return blockPattern.getBlock();
        if (pattern instanceof BlockStateHolder<?> holder) return holder.toBaseBlock();
        return null;
    }

    private RegionSetJob(ServerPlayer player, Region region, Pattern pattern, BaseBlock fixedBlock,
                         LocalSession localSession, EditSession editSession) {
        this.player = player;
        this.level = player.serverLevel();
        this.pattern = pattern;
        this.fixedBlock = fixedBlock;
        this.localSession = localSession;
        this.editSession = editSession;
        this.chunks = new ChunkLease(level);
        this.cursor = new RegionSectionCursor(region, chunks, fixedBlock == null
                ? java.util.Collections.emptySet()
                : UniformSectionSkip.find(level, region, fixedBlock));
        this.changeLimit = editSession.getBlockChangeLimit();
        player.displayClientMessage(Component.translatable("message.worldeditpp.set.start")
                .withStyle(ChatFormatting.YELLOW), true);
    }

    @Override public UUID ownerId() { return player.getUUID(); }

    @Override public UUID actorId() { return player.getUUID(); }

    @Override public boolean step(ServerPlayer actor, TickBudget budget) {
        if (finished) return true;
        if (actor == null || actor.serverLevel() != level) {
            fail(new IllegalStateException("Player left the set job's dimension"));
            return true;
        }
        try {
            while (budget.take()) {
                BlockVector3 current = cursor.next();
                if (current == null) break;
                BaseBlock block = fixedBlock != null ? fixedBlock : pattern.applyBlock(current);
                long remainingChanges = changeLimit < 0 ? Long.MAX_VALUE
                        : (long) changeLimit - affected;
                int result = IncrementalBlockWriter.setBlock(
                        editSession, current, block, remainingChanges);
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
        clearActionBar();
        if (successful) {
            player.sendSystemMessage(Component.translatable(
                    "message.worldeditpp.set.complete", affected).withStyle(ChatFormatting.GREEN));
        } else {
            player.sendSystemMessage(Component.translatable(
                    "message.worldeditpp.set.failed").withStyle(ChatFormatting.RED));
        }
    }

    private void fail(Throwable exception) {
        if (finished) return;
        finished = true;
        WorldEditPlusPlus.LOGGER.warn("Incremental //set failed for {}", player.getGameProfile().getName(), exception);
        try {
            EditSessionLifecycle.rememberAndClose(localSession, editSession);
        } finally {
            chunks.close();
        }
        clearActionBar();
        player.sendSystemMessage(Component.translatable("message.worldeditpp.set.failed")
                .withStyle(ChatFormatting.RED));
    }

    private void clearActionBar() {
        player.displayClientMessage(Component.empty(), true);
    }

    @Override public void close() {
        if (finished) return;
        finished = true;
        try {
            EditSessionLifecycle.rememberAndClose(localSession, editSession);
        } finally {
            chunks.close();
        }
        clearActionBar();
    }
}
