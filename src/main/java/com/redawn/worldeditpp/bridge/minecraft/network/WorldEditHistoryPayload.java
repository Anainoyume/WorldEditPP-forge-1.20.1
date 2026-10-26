package com.redawn.worldeditpp.bridge.minecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketCodec;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;

/** Requests one operation on the sending player's WorldEdit history. */
public record WorldEditHistoryPayload(Action action) implements PacketPayload {
    public static final PacketCodec<FriendlyByteBuf, WorldEditHistoryPayload> STREAM_CODEC = PacketCodec.of(
            WorldEditHistoryPayload::encode,
            WorldEditHistoryPayload::decode
    );

    public enum Action {
        UNDO,
        REDO
    }

    private static void encode(FriendlyByteBuf buffer, WorldEditHistoryPayload payload) {
        buffer.writeVarInt(payload.action.ordinal());
    }

    private static WorldEditHistoryPayload decode(FriendlyByteBuf buffer) {
        int actionId = buffer.readVarInt();
        if (actionId < 0 || actionId >= Action.values().length) {
            throw new IllegalArgumentException("Unknown WorldEdit history action");
        }
        return new WorldEditHistoryPayload(Action.values()[actionId]);
    }

}
