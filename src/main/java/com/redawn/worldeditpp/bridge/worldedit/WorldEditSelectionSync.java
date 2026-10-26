package com.redawn.worldeditpp.bridge.worldedit;

import com.redawn.worldeditpp.bridge.minecraft.mode.EditModeManager;
import com.redawn.worldeditpp.bridge.minecraft.network.CuboidSelectionPayload;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;

public final class WorldEditSelectionSync {
    private static final Map<UUID, CuboidSelectionPayload> LAST_SENT = new HashMap<>();
    private static final Map<UUID, Long> PROCESSED_REQUESTS = new HashMap<>();

    public static void acknowledgeRequest(ServerPlayer player, long requestId) {
        PROCESSED_REQUESTS.merge(player.getUUID(), requestId, Math::max);
    }

    public static void tick(ServerPlayer player) {
        UUID playerId = player.getUUID();
        // Include the sequence even when the coordinates did not change (e.g. expand then contract).
        CuboidSelectionPayload payload = currentPayload(player)
                .withAcknowledgedRequest(PROCESSED_REQUESTS.getOrDefault(playerId, 0L));
        CuboidSelectionPayload previous = LAST_SENT.get(playerId);
        if (!payload.equals(previous)) {
            PlatformBridge.sendToPlayer(player, payload);
            LAST_SENT.put(playerId, payload);
        }
    }

    public static void clear(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (!CuboidSelectionPayload.hidden().equals(LAST_SENT.get(playerId))) {
            PlatformBridge.sendToPlayer(player, CuboidSelectionPayload.hidden());
        }
        LAST_SENT.remove(playerId);
        PROCESSED_REQUESTS.remove(playerId);
    }

    private static CuboidSelectionPayload currentPayload(ServerPlayer player) {
        if (!EditModeManager.isEditing(player)) {
            return CuboidSelectionPayload.hidden();
        }

        RegionSelection selection = WorldEditSelectionService.currentSelection(player).orElse(null);
        if (selection == null || !selection.kind().supportsCuboidOverlay()) {
            // The custom overlay/gizmo is deliberately cuboid-only. Non-cuboid native
            // selections stay untouched on the server and are handled by WorldEdit itself.
            return CuboidSelectionPayload.hidden();
        }

        CuboidRegion cuboidRegion = selection.cuboid();
        BlockVector3 min = cuboidRegion.getMinimumPoint();
        BlockVector3 max = cuboidRegion.getMaximumPoint();
        BlockVector3 pos1 = cuboidRegion.getPos1();
        BlockVector3 pos2 = cuboidRegion.getPos2();
        return new CuboidSelectionPayload(true,
                min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ(),
                pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ());
    }

    private WorldEditSelectionSync() {
        // 选区同步只在服务端 tick 中按玩家状态发送最小必要数据。
    }
}
