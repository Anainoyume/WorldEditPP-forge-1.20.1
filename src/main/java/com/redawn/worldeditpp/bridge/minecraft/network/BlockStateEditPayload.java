package com.redawn.worldeditpp.bridge.minecraft.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketCodec;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Edits one named property of one locked block, never an entire selection. */
public record BlockStateEditPayload(long sessionId, long requestId, ResourceLocation dimension,
                                    BlockPos position, ResourceLocation block, String property, String value)
        implements PacketPayload {
    public static final PacketCodec<FriendlyByteBuf, BlockStateEditPayload> STREAM_CODEC = PacketCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.sessionId);
                buffer.writeVarLong(payload.requestId);
                buffer.writeResourceLocation(payload.dimension);
                buffer.writeBlockPos(payload.position);
                buffer.writeResourceLocation(payload.block);
                buffer.writeUtf(payload.property, 128);
                buffer.writeUtf(payload.value, 128);
            }, buffer -> new BlockStateEditPayload(buffer.readLong(), buffer.readVarLong(),
                    buffer.readResourceLocation(), buffer.readBlockPos(), buffer.readResourceLocation(),
                    buffer.readUtf(128), buffer.readUtf(128)));

}
