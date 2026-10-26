package com.redawn.worldeditpp.bridge.worldedit.history;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.minecraft.operation.ChunkLease;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditBridge;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditPlatform;
import com.redawn.worldeditpp.bridge.worldedit.operation.EditSessionTuning;
import com.redawn.worldeditpp.bridge.worldedit.operation.PlanningService;
import com.redawn.worldeditpp.operation.task.TickBudget;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.history.UndoContext;
import com.sk89q.worldedit.history.change.Change;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** One reserved history entry with resumable progress and no early history-pointer mutation. */
final class HistoryTask {
    final UUID owner;
    UUID actorId;
    final LocalSession session;
    final boolean undo;
    private final HistoryBatch batch;
    private final boolean recovery;
    /** Remaining history entries of one /undo N or /redo N command; zero means this is the last. */
    private int remaining;
    private HistoryCheckpoint.Reader reader;
    private EditSession recoveryRecord;
    private boolean committed;
    private HistorySessionAccess.Reservation reservation;
    private Iterator<Change> preparation;
    private List<Change> plan = new ArrayList<>();
    private IndexedBlockHistory indexed;
    private int preflightCursor;
    private long[] blockPositions;
    private int blockPositionCursor;
    private com.redawn.worldeditpp.operation.task.BoundedHistoryPlanner.Plan blockOrder;
    private boolean skipChunkOrder, orderSubmitted;
    private final java.util.Map<Change, net.minecraft.nbt.CompoundTag> entitySnapshots = new java.util.IdentityHashMap<>();
    private final UndoContext context = new UndoContext();
    private EditSession writer;
    private com.sk89q.worldedit.extent.Extent directBlockWriter;
    private com.sk89q.worldedit.entity.Player writerActor;
    private ServerLevel level;
    private ChunkLease chunks;
    private int cursor;
    private Change uncertain;
    private boolean indexedUncertain;
    private int entityWaitTicks;
    private long entityWaitSince;
    private boolean prepared, preparationFailed;
    private String failure;
    private String state = "queued";
    private int lastProgressPercent = -1;
    private String lastProgressStage;

    HistoryTask(UUID owner, UUID actorId, LocalSession session, boolean undo, HistoryBatch batch, int times) {
        this(owner, actorId, session, undo, false, batch, times);
    }
    HistoryTask(UUID owner, UUID actorId, LocalSession session, boolean undo, boolean recovery) {
        this(owner, actorId, session, undo, recovery, null, 1);
    }
    private HistoryTask(UUID owner, UUID actorId, LocalSession session, boolean undo, boolean recovery,
                        HistoryBatch batch, int times) {
        if (times < 1) throw new IllegalArgumentException("History operation needs at least one entry");
        this.owner = owner; this.actorId = actorId; this.session = session; this.undo = undo;
        this.recovery = recovery; this.batch = batch; this.remaining = times - 1;
    }

    boolean step(ServerPlayer player, TickBudget budget) {
        if (failure != null) { releaseWriter(); return false; }
        if (player == null) { transition("offline", null); releaseWriter(); return false; }
        try {
            var actor = WorldEditPlatform.adaptPlayer(player);
            if (!HistoryPermissions.allows(actor, undo, !actorId.equals(owner))) {
                transition("permission", player); releaseWriter(); return false;
            }
            if (committed) return finish(player);
            if (level == null && recovery) {
                reader = new HistoryCheckpoint.Reader(player.getServer(), owner);
                level = reader.level; chunks = new ChunkLease(level);
                recoveryRecord = WorldEditBridge.newEditSession(WorldEditPlatform.adapt(level));
                recoveryRecord.close();
            } else if (reservation == null && !recovery) {
                reservation = HistorySessionAccess.reserve(session, undo);
                if (reservation == null) { return true; }
                level = WorldEditPlatform.adapt(reservation.edit().getWorld());
                chunks = new ChunkLease(level);
                if (reservation.edit().getChangeSet().getClass() == com.sk89q.worldedit.history.changeset.BlockOptimizedHistory.class) {
                    indexed = new IndexedBlockHistory((com.sk89q.worldedit.history.changeset.BlockOptimizedHistory) reservation.edit().getChangeSet(), undo);
                    plan = indexed;
                }
                preparation = undo ? reservation.edit().getChangeSet().backwardIterator()
                        : reservation.edit().getChangeSet().forwardIterator();
            }
            // Only a self-issued operation depends on the issuer's current dimension. An
            // administrator's /undo <player> and login recovery write the operation's own world,
            // so gating them on the issuer's level would suspend the owner forever.
            if (!recovery && actorId.equals(owner) && player.serverLevel() != level) {
                transition("dimension", player); releaseWriter(); return false;
            }
            if (!recovery && !reservation.valid()) throw new IllegalStateException("WorldEdit history was modified outside the operation");
            if (!prepared) {
                transition("preparing", player);
                if (indexed != null) {
                    // Blocks are known vanilla pairs. Preflight arbitrary entity/biome
                    // changes before any mutation; unknown Change still freezes.
                    while (preflightCursor < indexed.preflightSize() && budget.take()) {
                        Change change = indexed.get(preflightCursor++);
                        HistoryChanges.retainEntity(change, level);
                        HistoryChanges.position(change);
                    }
                    if (preflightCursor < indexed.preflightSize()) return false;
                    if (indexed.shouldPlanChunkOrder() && !skipChunkOrder) {
                        if (blockOrder == null) {
                            blockOrder = PlanningService.acquire(indexed.blockCount());
                            // Avoid deadlock/starvation behind paused owners retaining completed plans.
                            if (blockOrder == null) skipChunkOrder = true;
                            else blockPositions = blockOrder.positions();
                        }
                        if (!skipChunkOrder && !orderSubmitted) {
                            while (blockPositionCursor < blockPositions.length && budget.take()) {
                                blockPositions[blockPositionCursor] = indexed.originalBlockPosition(blockPositionCursor).toLongPackedForm();
                                blockPositionCursor++;
                            }
                            if (blockPositionCursor < blockPositions.length) return false;
                            blockPositions = null;
                            blockOrder.submit();
                            orderSubmitted = true;
                        }
                        if (!skipChunkOrder) {
                            if (!blockOrder.isDone()) { transition("planning", player); return false; }
                            indexed.useChunkOrder(blockOrder.positions());
                        }
                    }
                    prepared = true;
                    preparation = null;
                }
                if (!prepared) {
                while ((recovery ? reader.hasNext() : preparation.hasNext()) && budget.take()) {
                    Change change = recovery ? reader.next() : preparation.next();
                    HistoryChanges.retainEntity(change, level);
                    HistoryChanges.position(change);
                    plan.add(change);
                }
                if (recovery ? reader.hasNext() : preparation.hasNext()) return false;
                prepared = true; preparation = null;
                if (reader != null) { reader.close(); reader = null; }
                }
                showProgress(player);
            }
            if (cursor == plan.size()) return finish(player);
            ensureWriter(player);
            // Context never survives this step/yield; callbacks still see immediate writes.
            var chunkBatch = new HistoryChunkBatch(level, chunks);
            while (budget.take()) {
                int planIndex = cursor;
                boolean directBlock = uncertain == null && indexed != null && indexed.isBlockIndex(planIndex);
                Change change = directBlock ? null : uncertain != null ? uncertain : plan.get(planIndex);
                if (directBlock) {
                    var position = indexed.blockPosition(planIndex);
                    if (!chunkBatch.ready(position)) { transition("chunks", player); return false; }
                    transition("running", player);
                    indexedUncertain = true;
                    chunkBatch.clearContainer(position);
                    // Undo restores the previous state; redo applies the current state.
                    directBlockWriter.setBlock(position, indexed.blockAt(position, undo));
                    indexedUncertain = false;
                    cursor++;
                    showProgress(player);
                    if (cursor == plan.size()) return finish(player);
                    continue;
                }
                var position = HistoryChanges.position(change);
                boolean block = change.getClass() == com.sk89q.worldedit.history.change.BlockChange.class;
                if (!block) chunkBatch.endRun();
                if (!(block ? chunkBatch.ready(position) : chunks.ready(position.getX(), position.getZ()))) {
                    transition("chunks", player); return false;
                }
                if (HistoryChanges.entity(change) && !level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4))) {
                    if (entityWaitTicks++ == 0) entityWaitSince = System.nanoTime();
                    if (entityWaitTicks > 600 && System.nanoTime() - entityWaitSince > 30_000_000_000L)
                        throw new IllegalStateException("Entity chunk load timed out at " + position);
                    transition("chunks", player); return false;
                }
                entityWaitTicks = 0;
                HistoryChanges.checkEntityRemoval(change, undo);
                transition("running", player);
                // Retain a possibly partly applied change so a failed step stays diagnosable.
                var entityBefore = HistoryChanges.liveEntitySnapshot(change);
                if (entityBefore != null) entitySnapshots.put(change, entityBefore);
                uncertain = change;
                // WorldEdit's Forge adapter also clears vanilla Clearable block entities,
                // but doing it explicitly here keeps the clear inside our history boundary.
                if (block) chunkBatch.clearContainer(position);
                if (undo) change.undo(context); else change.redo(context);
                HistoryChanges.retainEntity(change, level);
                var entityAfter = HistoryChanges.liveEntitySnapshot(change);
                if (entityAfter != null) entitySnapshots.put(change, entityAfter);
                uncertain = null;
                if (recovery) recoveryRecord.getChangeSet().add(new HistoryChanges.ReversedChange(change));
                cursor++;
                showProgress(player);
                if (cursor == plan.size()) return finish(player);
            }
        } catch (Exception exception) {
            preparationFailed |= !prepared;
            failure = exception.toString();
            transition("failed", player);
            WorldEditPlusPlus.LOGGER.error("History task frozen: owner={} undo={} cursor={} world={}",
                    owner, undo, cursor, level == null ? "unreserved" : level.dimension().location(), exception);
            releaseWriter();
        }
        return false;
    }

    private void ensureWriter(ServerPlayer player) {
        if (writer != null) return;
        writerActor = WorldEditPlatform.adaptPlayer(player);
        writer = WorldEditBridge.worldEdit().newEditSessionBuilder().world(WorldEditPlatform.adapt(level))
                .blockBag(session.getBlockBag(writerActor)).actor(writerActor).build();
        // FAWE's main safe win is batching expensive effects. Keep client chunk marks,
        // suppress per-block physics/neighbour work, then relight each dirty chunk once.
        EditSessionTuning.configureIncrementalBulkWrite(writer);
        if (writer.getSurvivalExtent() != null) writer.getSurvivalExtent().setStripNbt(!writerActor.hasPermission("worldedit.setnbt"));
        context.setExtent(HistorySessionAccess.bypass(writer));
        // The indexed path is already chunk ordered and never buffers. Keep validation,
        // survival checks and side effects, while skipping WorldEdit's redundant reorder chain.
        directBlockWriter = HistorySessionAccess.bypassReorder(writer);
    }

    private boolean finish(ServerPlayer player) throws java.io.IOException {
        closeWriter();
        if (reader != null) { reader.close(); reader = null; }
        if (!committed) {
            if (recovery) session.remember(recoveryRecord);
            else reservation.commit();
            committed = true;
        }
        if (recovery) HistoryCheckpoint.complete(player.getServer(), owner);
        chunks.close();
        releasePlanning();
        if (batch != null) batch.stepApplied();
        if (!recovery && remaining > 0) {
            // /undo N runs its entries back to back without a queue: reset and reserve the next one.
            remaining--;
            resetForNext();
            return false;
        }
        if (recovery) tell(player, "complete", undo ? "undo" : "redo");
        clearProgress(player);
        return true;
    }

    /** Clears every per-entry field so the same command can run its next history entry. */
    private void resetForNext() {
        reservation = null; preparation = null;
        plan = new ArrayList<>(); indexed = null; preflightCursor = 0;
        blockPositions = null; blockPositionCursor = 0; blockOrder = null;
        skipChunkOrder = false; orderSubmitted = false; entitySnapshots.clear();
        writer = null; directBlockWriter = null; writerActor = null;
        level = null; chunks = null; cursor = 0;
        uncertain = null; indexedUncertain = false; entityWaitTicks = 0; entityWaitSince = 0;
        prepared = false; preparationFailed = false;
        committed = false; reader = null; recoveryRecord = null;
        state = "queued";
        lastProgressPercent = -1;
        lastProgressStage = null;
    }

    void checkpoint(net.minecraft.server.MinecraftServer server) throws java.io.IOException {
        try {
            releaseWriter();
            if (reader != null) { reader.close(); reader = null; }
            if (!recovery && !committed && level != null) {
                // An uncertain write is not counted yet; checkpoint it separately so recovery
                // restores it before the counted prefix.
                Change checkpointUncertain = uncertain;
                if (checkpointUncertain == null && indexedUncertain && indexed != null) {
                    checkpointUncertain = indexed.get(cursor);
                }
                HistoryCheckpoint.write(server, owner, level, plan, cursor,
                        checkpointUncertain, undo, entitySnapshots);
            }
        } finally { releasePlanning(); }
    }

    private void releasePlanning() {
        blockPositions = null;
        if (indexed != null) indexed.releaseChunkOrder();
        if (blockOrder != null) { blockOrder.close(); blockOrder = null; }
    }

    HistoryBatch batch() { return batch; }

    private String progress() {
        return (undo ? "undo" : "redo") + " " + state + " " + cursor + "/" + plan.size()
                + (level == null ? "" : " [" + level.dimension().location() + "]")
                + (failure == null ? "" : " — " + failure)
                + (uncertain != null || indexedUncertain || preparationFailed ? " (interrupted)" : "");
    }

    private void transition(String next, ServerPlayer player) {
        if (state.equals(next)) return;
        String previous = state; state = next;
        if (next.equals("offline") || next.equals("dimension") || next.equals("failed") || next.equals("permission")) {
            WorldEditPlusPlus.LOGGER.info("History {}: owner={} progress={}/{}", next, owner, cursor, plan.size());
            if (player != null) tell(player, "suspended", progress());
            clearProgress(player);
        } else if (next.equals("running") && (previous.equals("offline") || previous.equals("dimension"))) {
            tell(player, "resumed", progress());
            showProgress(player);
        } else if (next.equals("preparing") || next.equals("planning")) {
            showPreparing(player);
        }
    }

    private void showPreparing(ServerPlayer player) {
        if (player == null || "preparing".equals(lastProgressStage)) return;
        lastProgressStage = "preparing";
        lastProgressPercent = -1;
        player.displayClientMessage(Component.translatable(
                        "message.worldeditpp.history." + (undo ? "undo" : "redo") + "_preparing")
                .withStyle(ChatFormatting.YELLOW), true);
    }

    /** Action-bar percentage, throttled to one packet per percentage point. */
    private void showProgress(ServerPlayer player) {
        if (player == null || plan.isEmpty()) return;
        int total = plan.size();
        int percent = (int) Math.min(100L, (long) cursor * 100L / total);
        if (percent == lastProgressPercent) return;
        lastProgressPercent = percent;
        lastProgressStage = "progress";
        player.displayClientMessage(Component.translatable(
                        "message.worldeditpp.history." + (undo ? "undo" : "redo") + "_progress", percent)
                .withStyle(ChatFormatting.YELLOW), true);
    }

    private void clearProgress(ServerPlayer player) {
        lastProgressPercent = -1;
        lastProgressStage = null;
        if (player != null) player.displayClientMessage(Component.empty(), true);
    }

    private void closeWriter() {
        if (writer == null) return;
        // Do not clear the reference until flush succeeds: failures remain recoverable.
        writer.flushSession();
        WorldEditBridge.worldEdit().flushBlockBag(writerActor, writer);
        writer.close(); writer = null; writerActor = null; directBlockWriter = null;
    }
    void releaseWriter() {
        try { closeWriter(); }
        catch (RuntimeException exception) {
            failure = exception.toString();
            WorldEditPlusPlus.LOGGER.error("History writer flush failed for {}", owner, exception);
        } finally { if (chunks != null) chunks.close(); }
    }
    static void tell(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable("message.worldeditpp.history." + key, args));
    }
    static void tellBusy(ServerPlayer player) {
        // Repeated denied commands update the action bar instead of consuming chat history.
        player.displayClientMessage(Component.translatable("message.worldeditpp.history.busy"), true);
    }
}
