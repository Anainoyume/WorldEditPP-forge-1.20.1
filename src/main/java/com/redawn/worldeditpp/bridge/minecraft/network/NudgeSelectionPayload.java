package com.redawn.worldeditpp.bridge.minecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketCodec;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;

public record NudgeSelectionPayload(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                    int pos1X, int pos1Y, int pos1Z, int pos2X, int pos2Y, int pos2Z,
                                    long requestId)
        implements PacketPayload {
    public static final PacketCodec<FriendlyByteBuf, NudgeSelectionPayload> STREAM_CODEC = PacketCodec.of(
            NudgeSelectionPayload::encode,
            NudgeSelectionPayload::decode
    );

    private static void encode(FriendlyByteBuf buffer, NudgeSelectionPayload payload) {
        buffer.writeInt(payload.minX);
        buffer.writeInt(payload.minY);
        buffer.writeInt(payload.minZ);
        buffer.writeInt(payload.maxX);
        buffer.writeInt(payload.maxY);
        buffer.writeInt(payload.maxZ);
        buffer.writeInt(payload.pos1X);
        buffer.writeInt(payload.pos1Y);
        buffer.writeInt(payload.pos1Z);
        buffer.writeInt(payload.pos2X);
        buffer.writeInt(payload.pos2Y);
        buffer.writeInt(payload.pos2Z);
        buffer.writeVarLong(payload.requestId);
    }

    private static NudgeSelectionPayload decode(FriendlyByteBuf buffer) {
        return new NudgeSelectionPayload(
                buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readVarLong()
        );
    }

}
