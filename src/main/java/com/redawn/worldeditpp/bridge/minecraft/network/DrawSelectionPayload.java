package com.redawn.worldeditpp.bridge.minecraft.network;

import com.redawn.worldeditpp.mode.DrawMode;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketCodec;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;

/** Requests the server to apply held blocks using the selected face and vertical hit half. */
public record DrawSelectionPayload(Direction clickedFace, boolean clickedUpperHalf, DrawMode mode,
                                   boolean valid) implements PacketPayload {
    public static final PacketCodec<FriendlyByteBuf, DrawSelectionPayload> STREAM_CODEC = PacketCodec.of(
            (buffer, payload) -> {
                buffer.writeByte(payload.clickedFace.get3DDataValue());
                buffer.writeBoolean(payload.clickedUpperHalf);
                buffer.writeUtf(payload.mode.id(), 32);
                buffer.writeBoolean(payload.valid);
            },
            buffer -> {
                try {
                    Direction face = Direction.from3DDataValue(buffer.readUnsignedByte());
                    boolean upper = buffer.readBoolean();
                    DrawMode mode = DrawMode.fromId(buffer.readUtf(32));
                    boolean valid = buffer.readBoolean();
                    return valid ? new DrawSelectionPayload(face, upper, mode) : invalid();
                } catch (RuntimeException malformed) {
                    // A corrupted packet must not reach the server action. Return an inert
                    // payload so the handler can reject it without a decoder exception.
                    return invalid();
                }
            });

    public DrawSelectionPayload(Direction clickedFace, boolean clickedUpperHalf, DrawMode mode) {
        this(clickedFace, clickedUpperHalf, mode, true);
    }

    public static DrawSelectionPayload invalid() {
        return new DrawSelectionPayload(Direction.UP, false, DrawMode.SET, false);
    }
}
