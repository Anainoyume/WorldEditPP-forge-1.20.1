package com.redawn.worldeditpp.bridge.minecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketCodec;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;

public record AccessibilityPayload(String action, double value) implements PacketPayload {
    private static final int MAX_ACTION_LENGTH = 64;
    public static final PacketCodec<FriendlyByteBuf, AccessibilityPayload> STREAM_CODEC = PacketCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.action, MAX_ACTION_LENGTH);
                buffer.writeDouble(payload.value);
            },
            buffer -> new AccessibilityPayload(
                    buffer.readUtf(MAX_ACTION_LENGTH), buffer.readDouble()));

    public AccessibilityPayload {
        if (action == null || action.isEmpty() || action.length() > MAX_ACTION_LENGTH
                || !Double.isFinite(value)) {
            throw new IllegalArgumentException("Invalid accessibility request");
        }
    }

}
