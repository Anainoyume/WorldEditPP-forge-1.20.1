package com.redawn.worldeditpp.bridge.minecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketCodec;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;

public record WandClickPayload(int posX, int posY, int posZ, boolean isPrimary) implements PacketPayload {
    public static final PacketCodec<FriendlyByteBuf, WandClickPayload> STREAM_CODEC = PacketCodec.of(
            WandClickPayload::encode,
            WandClickPayload::decode
    );

    private static void encode(FriendlyByteBuf buf, WandClickPayload p) {
        buf.writeInt(p.posX);
        buf.writeInt(p.posY);
        buf.writeInt(p.posZ);
        buf.writeBoolean(p.isPrimary);
    }

    private static WandClickPayload decode(FriendlyByteBuf buf) {
        return new WandClickPayload(buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean());
    }

}
