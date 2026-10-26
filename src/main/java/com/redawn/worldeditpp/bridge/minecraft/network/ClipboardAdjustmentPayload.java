package com.redawn.worldeditpp.bridge.minecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketCodec;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;

/** Commands for the independent clipboard projection adjustment session. */
public record ClipboardAdjustmentPayload(Action action, int first, int second, int third)
        implements PacketPayload {
    public static final PacketCodec<FriendlyByteBuf, ClipboardAdjustmentPayload> STREAM_CODEC = PacketCodec.of(
            ClipboardAdjustmentPayload::encode,
            ClipboardAdjustmentPayload::decode
    );

    public enum Action {
        COPY,
        BEGIN,
        BEGIN_SELECTION,
        CANCEL,
        STRETCH_FACE,
        RESIZE,
        ROTATE,
        CONFIRM,
        MIRROR,
        CONFIRM_IGNORE_AIR
    }

    public static ClipboardAdjustmentPayload copy() {
        return new ClipboardAdjustmentPayload(Action.COPY, 0, 0, 0);
    }

    public static ClipboardAdjustmentPayload begin() {
        return new ClipboardAdjustmentPayload(Action.BEGIN, 0, 0, 0);
    }

    public static ClipboardAdjustmentPayload beginSelection() {
        return new ClipboardAdjustmentPayload(Action.BEGIN_SELECTION, 0, 0, 0);
    }

    public static ClipboardAdjustmentPayload cancel() {
        return new ClipboardAdjustmentPayload(Action.CANCEL, 0, 0, 0);
    }

    public static ClipboardAdjustmentPayload stretchFace(int face, boolean expand) {
        return new ClipboardAdjustmentPayload(Action.STRETCH_FACE, face, expand ? 1 : -1, 0);
    }

    public static ClipboardAdjustmentPayload resize(boolean expand) {
        return new ClipboardAdjustmentPayload(Action.RESIZE, expand ? 1 : -1, 0, 0);
    }

    public static ClipboardAdjustmentPayload rotate(int axis, int quarterTurn) {
        return new ClipboardAdjustmentPayload(Action.ROTATE, axis, quarterTurn, 0);
    }

    public static ClipboardAdjustmentPayload mirror(int axis) {
        return new ClipboardAdjustmentPayload(Action.MIRROR, axis, 0, 0);
    }

    public static ClipboardAdjustmentPayload confirm(int minX, int minY, int minZ, boolean ignoreAir) {
        return new ClipboardAdjustmentPayload(
                ignoreAir ? Action.CONFIRM_IGNORE_AIR : Action.CONFIRM, minX, minY, minZ);
    }

    private static void encode(FriendlyByteBuf buffer, ClipboardAdjustmentPayload payload) {
        buffer.writeVarInt(payload.action.ordinal());
        buffer.writeInt(payload.first);
        buffer.writeInt(payload.second);
        buffer.writeInt(payload.third);
    }

    private static ClipboardAdjustmentPayload decode(FriendlyByteBuf buffer) {
        int actionId = buffer.readVarInt();
        if (actionId < 0 || actionId >= Action.values().length) {
            throw new IllegalArgumentException("Unknown clipboard adjustment action");
        }
        return new ClipboardAdjustmentPayload(
                Action.values()[actionId], buffer.readInt(), buffer.readInt(), buffer.readInt());
    }

}
