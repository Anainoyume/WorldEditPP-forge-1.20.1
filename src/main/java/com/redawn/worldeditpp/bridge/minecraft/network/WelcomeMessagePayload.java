package com.redawn.worldeditpp.bridge.minecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketCodec;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;

/** Requests the localized, key-aware welcome message after the player joins a world. */
public record WelcomeMessagePayload() implements PacketPayload {
    public static final PacketCodec<FriendlyByteBuf, WelcomeMessagePayload> STREAM_CODEC =
            PacketCodec.unit(new WelcomeMessagePayload());
}
