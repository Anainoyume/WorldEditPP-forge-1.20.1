package com.redawn.worldeditpp.bridge.minecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketCodec;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;

public record SyncAccessibilityPayload(double gamma, boolean blockReachEnabled, double blockReachDistance, double flightSpeed, boolean editModeActive, String editSubMode)
        implements PacketPayload {
    private static final int MAX_SUB_MODE_LENGTH = 32;
    public static final PacketCodec<FriendlyByteBuf, SyncAccessibilityPayload> STREAM_CODEC = PacketCodec.of(
            (buffer, payload) -> {
                buffer.writeDouble(payload.gamma);
                buffer.writeBoolean(payload.blockReachEnabled);
                buffer.writeDouble(payload.blockReachDistance);
                buffer.writeDouble(payload.flightSpeed);
                buffer.writeBoolean(payload.editModeActive);
                buffer.writeUtf(payload.editSubMode, MAX_SUB_MODE_LENGTH);
            },
            buffer -> new SyncAccessibilityPayload(
                    buffer.readDouble(), buffer.readBoolean(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readBoolean(), buffer.readUtf(MAX_SUB_MODE_LENGTH)));

    public SyncAccessibilityPayload {
        if (!Double.isFinite(gamma) || gamma < 0.0 || gamma > 12.0
                || !Double.isFinite(blockReachDistance) || blockReachDistance < 0.0 || blockReachDistance > 64.0
                || !Double.isFinite(flightSpeed) || flightSpeed < 0.02 || flightSpeed > 0.60
                || editSubMode == null || editSubMode.isEmpty()
                || editSubMode.length() > MAX_SUB_MODE_LENGTH) {
            throw new IllegalArgumentException("Invalid accessibility synchronization");
        }
    }

}
