package com.redawn.worldeditpp.bridge.minecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketCodec;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;

public record CuboidSelectionPayload(boolean visible,
                                     int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                     int pos1X, int pos1Y, int pos1Z, int pos2X, int pos2Y, int pos2Z,
                                     long acknowledgedRequestId)
        implements PacketPayload {
    public static final PacketCodec<FriendlyByteBuf, CuboidSelectionPayload> STREAM_CODEC = PacketCodec.of(
            CuboidSelectionPayload::encode,
            CuboidSelectionPayload::decode
    );

    public CuboidSelectionPayload(boolean visible,
                                  int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                  int pos1X, int pos1Y, int pos1Z, int pos2X, int pos2Y, int pos2Z) {
        this(visible, minX, minY, minZ, maxX, maxY, maxZ,
                pos1X, pos1Y, pos1Z, pos2X, pos2Y, pos2Z, 0);
    }

    public CuboidSelectionPayload withAcknowledgedRequest(long requestId) {
        return new CuboidSelectionPayload(visible, minX, minY, minZ, maxX, maxY, maxZ,
                pos1X, pos1Y, pos1Z, pos2X, pos2Y, pos2Z, requestId);
    }

    public static CuboidSelectionPayload hidden() {
        return new CuboidSelectionPayload(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static void encode(FriendlyByteBuf buffer, CuboidSelectionPayload payload) {
        buffer.writeBoolean(payload.visible);
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
        buffer.writeVarLong(payload.acknowledgedRequestId);
    }

    private static CuboidSelectionPayload decode(FriendlyByteBuf buffer) {
        return new CuboidSelectionPayload(
                buffer.readBoolean(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readVarLong()
        );
    }

}
