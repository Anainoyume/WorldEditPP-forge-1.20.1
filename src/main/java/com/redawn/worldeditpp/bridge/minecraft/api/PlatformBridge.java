package com.redawn.worldeditpp.bridge.minecraft.api;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Loader operations used by implementation code. Installed once by the common entrypoint. */
public final class PlatformBridge {
    public interface Backend {
        net.minecraft.world.entity.ai.attributes.Attribute blockReachAttribute();
        double blockReach(net.minecraft.world.entity.player.Player player);
        void sendToServer(PacketPayload payload);
        void sendToPlayer(ServerPlayer player, PacketPayload payload);
        CompoundTag persistentData(ServerPlayer player);
    }
    private static Backend backend;
    public static net.minecraft.world.entity.ai.attributes.Attribute blockReachAttribute() { return backend().blockReachAttribute(); }
    public static double blockReach(net.minecraft.world.entity.player.Player player) { return backend().blockReach(player); }
    public static void install(Backend value) {
        if (backend != null) throw new IllegalStateException("Platform backend already installed");
        backend = Objects.requireNonNull(value);
    }
    private static Backend backend() { return Objects.requireNonNull(backend, "Platform backend not installed"); }
    public static void sendToServer(PacketPayload payload) { backend().sendToServer(payload); }
    public static void sendToPlayer(ServerPlayer player, PacketPayload payload) { backend().sendToPlayer(player, payload); }
    public static CompoundTag persistentData(ServerPlayer player) { return backend().persistentData(player); }
    private PlatformBridge() { }
}
