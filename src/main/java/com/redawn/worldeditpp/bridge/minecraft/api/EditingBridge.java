package com.redawn.worldeditpp.bridge.minecraft.api;

import com.redawn.worldeditpp.bridge.minecraft.network.BlockStateEditPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.ClipboardAdjustmentPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.WorldEditHistoryPayload;

import com.redawn.worldeditpp.network.*;
import net.minecraft.server.level.ServerPlayer;

/** Application-facing editing contract. WorldEdit types must not escape this boundary. */
public final class EditingBridge {
    public interface Backend {
        void editBlockState(ServerPlayer player, BlockStateEditPayload payload);
        void setCorners(ServerPlayer player, int x1, int y1, int z1, int x2, int y2, int z2);
        void selectPoint(ServerPlayer player, int x, int y, int z, boolean primary);
        void acknowledgeSelection(ServerPlayer player, long requestId);
        void teleport(ServerPlayer player);
        void clearSelection(ServerPlayer player);
        void history(ServerPlayer player, WorldEditHistoryPayload.Action action);
        void adjustClipboard(ServerPlayer player, ClipboardAdjustmentPayload payload);
        void draw(ServerPlayer player, net.minecraft.core.Direction face, boolean upperHalf, com.redawn.worldeditpp.mode.DrawMode mode);
        void syncSelection(ServerPlayer player);
        void clearSelectionSync(ServerPlayer player);
        void tickAdjustment(ServerPlayer player);
        void clearAdjustment(ServerPlayer player);
        void tickPlacements(net.minecraft.server.MinecraftServer server, boolean hasTime);
    }
    private static Backend backend;
    public static void install(Backend value) {
        if (backend != null) throw new IllegalStateException("Editing backend already installed");
        backend = java.util.Objects.requireNonNull(value);
    }
    public static Backend get() {
        return java.util.Objects.requireNonNull(backend, "Editing backend not installed");
    }
    private EditingBridge() {}
}
