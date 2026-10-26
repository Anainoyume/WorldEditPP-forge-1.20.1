package com.redawn.worldeditpp.bridge.minecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketCodec;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;

public record BlockStateEditResultPayload(long sessionId, long requestId, int stateId, Status status)
        implements PacketPayload {
    public enum Status { APPLIED, INVALID, UNAVAILABLE, DENIED, FAILED }
    public static final PacketCodec<FriendlyByteBuf, BlockStateEditResultPayload> STREAM_CODEC = PacketCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.sessionId);
                buffer.writeVarLong(payload.requestId);
                buffer.writeVarInt(payload.stateId);
                buffer.writeEnum(payload.status);
            }, buffer -> new BlockStateEditResultPayload(buffer.readLong(), buffer.readVarLong(),
                    buffer.readVarInt(), buffer.readEnum(Status.class)));

}
