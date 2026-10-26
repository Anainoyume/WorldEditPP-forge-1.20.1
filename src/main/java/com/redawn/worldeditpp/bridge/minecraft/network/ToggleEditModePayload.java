package com.redawn.worldeditpp.bridge.minecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketCodec;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;

public record ToggleEditModePayload(String keyName) implements PacketPayload {
    private static final int MAX_KEY_NAME_LENGTH = 128;
    public static final PacketCodec<FriendlyByteBuf, ToggleEditModePayload> STREAM_CODEC = PacketCodec.of(
            (buffer, payload) -> buffer.writeUtf(payload.keyName, MAX_KEY_NAME_LENGTH),
            buffer -> new ToggleEditModePayload(buffer.readUtf(MAX_KEY_NAME_LENGTH)));

    public ToggleEditModePayload {
        if (keyName == null || keyName.length() > MAX_KEY_NAME_LENGTH) {
            throw new IllegalArgumentException("Invalid edit mode key name");
        }
    }

}
