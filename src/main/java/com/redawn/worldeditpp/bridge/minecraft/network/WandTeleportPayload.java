package com.redawn.worldeditpp.bridge.minecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketCodec;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;

/** Requests a loaded-chunk-only long-range teleport from the WorldEdit++ wand. */
public record WandTeleportPayload() implements PacketPayload {
    public static final PacketCodec<FriendlyByteBuf, WandTeleportPayload> STREAM_CODEC = PacketCodec.of(
            (buffer, payload) -> { },
            buffer -> new WandTeleportPayload());

}
