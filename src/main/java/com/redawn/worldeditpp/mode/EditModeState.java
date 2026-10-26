package com.redawn.worldeditpp.mode;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EditModeState {
    private static final Set<UUID> CLIENT_EDITING_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SERVER_EDITING_PLAYERS = ConcurrentHashMap.newKeySet();
    public static volatile boolean isInEditMode;

    /** Client-side state. Kept separate from the integrated server's state in the same JVM. */
    public static void setEditing(UUID playerId, boolean editing) {
        setClientEditing(playerId, editing);
    }

    public static void setClientEditing(UUID playerId, boolean editing) {
        update(CLIENT_EDITING_PLAYERS, playerId, editing);
    }

    public static void setServerEditing(UUID playerId, boolean editing) {
        update(SERVER_EDITING_PLAYERS, playerId, editing);
    }

    private static void update(Set<UUID> players, UUID playerId, boolean editing) {
        if (editing) {
            players.add(playerId);
        } else {
            players.remove(playerId);
        }
        isInEditMode = !CLIENT_EDITING_PLAYERS.isEmpty() || !SERVER_EDITING_PLAYERS.isEmpty();
    }

    /** Client-side lookup retained for client input and rendering code. */
    public static boolean isEditing(UUID playerId) {
        return isClientEditing(playerId);
    }

    public static boolean isClientEditing(UUID playerId) {
        return CLIENT_EDITING_PLAYERS.contains(playerId);
    }

    public static boolean isServerEditing(UUID playerId) {
        return SERVER_EDITING_PLAYERS.contains(playerId);
    }

    public static void clearAll() {
        CLIENT_EDITING_PLAYERS.clear();
        SERVER_EDITING_PLAYERS.clear();
        isInEditMode = false;
    }

    private EditModeState() {
        // 编辑模式状态需要同时被客户端控制器、服务端管理器和 Mixin 查询。
    }
}
