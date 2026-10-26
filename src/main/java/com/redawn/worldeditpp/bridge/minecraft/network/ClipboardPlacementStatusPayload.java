package com.redawn.worldeditpp.bridge.minecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketCodec;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;

/** Server acknowledgement and progress for a projection placement. */
public record ClipboardPlacementStatusPayload(Status status, int completedCells, int totalCells)
        implements PacketPayload {
    public static final PacketCodec<FriendlyByteBuf, ClipboardPlacementStatusPayload> STREAM_CODEC =
            PacketCodec.of(ClipboardPlacementStatusPayload::encode, ClipboardPlacementStatusPayload::decode);

    public enum Status {
        STARTED,
        PROGRESS,
        COMPLETED,
        FAILED
    }

    public ClipboardPlacementStatusPayload {
        if (completedCells < 0 || totalCells < 0 || completedCells > totalCells) {
            throw new IllegalArgumentException("Invalid clipboard placement progress");
        }
        if (status == Status.COMPLETED && completedCells != totalCells) {
            throw new IllegalArgumentException("Completed placement must report all cells");
        }
    }

    private static void encode(FriendlyByteBuf buffer, ClipboardPlacementStatusPayload payload) {
        buffer.writeVarInt(payload.status.ordinal());
        buffer.writeVarInt(payload.completedCells);
        buffer.writeVarInt(payload.totalCells);
    }

    private static ClipboardPlacementStatusPayload decode(FriendlyByteBuf buffer) {
        int statusId = buffer.readVarInt();
        if (statusId < 0 || statusId >= Status.values().length) {
            throw new IllegalArgumentException("Unknown clipboard placement status");
        }
        return new ClipboardPlacementStatusPayload(
                Status.values()[statusId], buffer.readVarInt(), buffer.readVarInt());
    }

}
