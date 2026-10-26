package com.redawn.worldeditpp.bridge.forge;

import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;
import net.minecraft.nbt.CompoundTag;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** Forge transport and persistent player extension. Never loads client classes. */
public final class ForgePlatform implements PlatformBridge.Backend {
    @Override public net.minecraft.world.entity.ai.attributes.Attribute blockReachAttribute() { return net.minecraftforge.common.ForgeMod.BLOCK_REACH.get(); }
    @Override public double blockReach(net.minecraft.world.entity.player.Player player) { return player.getBlockReach(); }
    @Override public void sendToServer(PacketPayload payload) { ForgeNetworkRegistration.CHANNEL.sendToServer(payload); }
    @Override public void sendToPlayer(ServerPlayer player, PacketPayload payload) { ForgeNetworkRegistration.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload); }
    @Override public CompoundTag persistentData(ServerPlayer player) { return player.getPersistentData(); }
}
