package com.redawn.worldeditpp.bridge.minecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketCodec;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;

/** Persists the player's decision to hide the welcome message in this server save. */
public record DismissWelcomePayload() implements PacketPayload {
    public static final PacketCodec<FriendlyByteBuf, DismissWelcomePayload> STREAM_CODEC =
            PacketCodec.unit(new DismissWelcomePayload());
}
