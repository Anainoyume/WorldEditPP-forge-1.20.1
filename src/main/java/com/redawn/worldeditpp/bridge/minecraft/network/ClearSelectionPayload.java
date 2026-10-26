package com.redawn.worldeditpp.bridge.minecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketCodec;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;

public record ClearSelectionPayload() implements PacketPayload {
    public static final PacketCodec<FriendlyByteBuf, ClearSelectionPayload> STREAM_CODEC = PacketCodec.of(
            (buf, payload) -> {},
            buf -> new ClearSelectionPayload()
    );

}
