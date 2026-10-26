package com.redawn.worldeditpp.bridge.worldedit.history;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditBridge;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditPlatform;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditSessions;
import com.redawn.worldeditpp.bridge.worldedit.operation.IncrementalJobScheduler;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.extension.platform.Actor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tick-sliced undo/redo engine. Independent of commands, projection paste and any queue:
 * one operation per history owner, advanced on the server tick under a bounded budget.
 *
 * <p>Adapters decide whether an operation is allowed (permissions, paste exclusion) and call
 * {@link #enqueue}; the engine owns only the history work, its planning budget and the
 * stop-server checkpoint. Recovery is started by the login hook when a checkpoint exists.
 */
public final class HistoryEngine {
    /** Active operation per history owner; a second request is rejected, never queued. */
    private static final Map<UUID, HistoryTask> ACTIVE = new LinkedHashMap<>();
    private static UUID executingActor;
    private static final java.util.Set<UUID> failedPins = new java.util.HashSet<>();

    public static void enqueue(ServerPlayer player, boolean undo, int times, String targetName) {
        var actor = WorldEditPlatform.adaptPlayer(player);
        boolean other = targetName != null && !targetName.equalsIgnoreCase(player.getGameProfile().getName());
        if (!HistoryPermissions.allows(actor, undo, other)) {
            HistoryTask.tell(player, "denied"); return;
        }
        LocalSession session = other ? WorldEditBridge.worldEdit().getSessionManager().findByName(targetName)
                : WorldEditSessions.of(player);
        if (session == null) { HistoryTask.tell(player, "unknown_player", targetName); return; }
        UUID owner = HistorySessionAccess.owner(session);
        if (java.nio.file.Files.exists(HistoryCheckpoint.path(player.getServer(), owner))) {
            HistoryTask.tell(player, "recovery_available"); return;
        }
        if (busy(owner)) { HistoryTask.tellBusy(player); return; }
        HistoryTask task = new HistoryTask(owner, player.getUUID(), session, undo, new HistoryBatch(undo), times);
        ACTIVE.put(owner, task);
        if (!IncrementalJobScheduler.instance().register(new HistoryJob(task))) {
            ACTIVE.remove(owner);
            HistoryTask.tellBusy(player);
        }
    }

    /** Scheduler hook: pin an active session even when this tick's budget skips its step. */
    static void pin(HistoryTask task) {
        try {
            HistorySessionAccess.keepAlive(task.owner, task.session);
            failedPins.remove(task.owner);
        } catch (RuntimeException exception) {
            if (failedPins.add(task.owner)) {
                WorldEditPlusPlus.LOGGER.warn("History session pin failed: {}", task.owner, exception);
            }
        }
    }

    /** Scheduler hook: actor scope for the current step. */
    static void beginStep(HistoryTask task) {
        executingActor = task.actorId;
    }

    static void endStep() {
        executingActor = null;
    }

    static void complete(HistoryTask task, ServerPlayer player) {
        ACTIVE.remove(task.owner);
        if (task.batch() != null) task.batch().completed(player);
    }

    public static boolean busy(UUID owner) { return ACTIVE.containsKey(owner); }
    public static boolean mayCreateEdit(Actor actor) {
        return actor == null || actor.getUniqueId().equals(executingActor) || !busy(actor.getUniqueId());
    }
    public static void tellBusy(ServerPlayer player) { HistoryTask.tellBusy(player); }

    /**
     * Starts recovery automatically when an interrupted operation left a checkpoint.
     * The checkpoint is consumed only after the whole restore is committed.
     */
    public static void onLogin(ServerPlayer player) {
        UUID owner = player.getUUID();
        if (busy(owner) || !java.nio.file.Files.exists(HistoryCheckpoint.path(player.getServer(), owner))) return;
        var actor = WorldEditPlatform.adaptPlayer(player);
        if (!HistoryPermissions.allows(actor, true, false)) return;
        LocalSession session = WorldEditSessions.of(player);
        if (session == null) return;
        HistoryTask task = new HistoryTask(owner, owner, session, true, true);
        ACTIVE.put(owner, task);
        if (!IncrementalJobScheduler.instance().register(new HistoryJob(task))) {
            ACTIVE.remove(owner);
            return;
        }
        HistoryTask.tell(player, "recovering");
    }

    public static void shutdown(MinecraftServer server) {
        for (UUID owner : List.copyOf(ACTIVE.keySet())) {
            IncrementalJobScheduler.instance().unregister(owner);
            try { ACTIVE.get(owner).checkpoint(server); }
            catch (Exception exception) { WorldEditPlusPlus.LOGGER.error("Cannot save partial history recovery for " + owner, exception); }
        }
        ACTIVE.clear(); failedPins.clear(); executingActor = null;
        IncrementalJobScheduler.instance().clear();
        com.redawn.worldeditpp.bridge.worldedit.operation.PlanningService.cancelAll();
    }
    public static void disconnect(ServerPlayer player) {
        for (HistoryTask task : List.copyOf(ACTIVE.values())) {
            if (task.actorId.equals(player.getUUID())) task.releaseWriter();
        }
    }
    private HistoryEngine() {}
}
