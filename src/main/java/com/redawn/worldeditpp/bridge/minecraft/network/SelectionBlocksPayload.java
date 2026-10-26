package com.redawn.worldeditpp.bridge.minecraft.network;

import com.redawn.worldeditpp.network.SelectionBlockEntityData;
import com.redawn.worldeditpp.network.SelectionProjectionChunk;
import com.redawn.worldeditpp.network.SelectionProjectionLimits;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketCodec;
import com.redawn.worldeditpp.bridge.minecraft.network.PacketPayload;

/**
 * 选区方块投影数据（S→C，分片推送）。
 *
 * entries 是扁平 int 对数组：(cellIndex, paletteIndex) 交替存放；
 * palette 仅在 chunkIndex == 0 的分片携带，其余分片传 null。
 * chunkCount == 0 表示清空投影。
 */
public record SelectionBlocksPayload(int generation, int chunkIndex, int chunkCount,
                                     int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ,
                                     int[] palette, int[] entries,
                                     List<SelectionBlockEntityData> blockEntities) implements PacketPayload {
    public static final PacketCodec<FriendlyByteBuf, SelectionBlocksPayload> STREAM_CODEC = PacketCodec.of(
            SelectionBlocksPayload::encode,
            SelectionBlocksPayload::decode
    );

    public SelectionBlocksPayload {
        SelectionProjectionChunk chunk = new SelectionProjectionChunk(
                generation, chunkIndex, chunkCount, minX, minY, minZ, sizeX, sizeY, sizeZ,
                palette, entries, blockEntities);
        palette = chunk.palette();
        entries = chunk.entries();
        blockEntities = chunk.blockEntities();
    }

    private static void encode(FriendlyByteBuf buffer, SelectionBlocksPayload payload) {
        buffer.writeVarInt(payload.generation);
        buffer.writeVarInt(payload.chunkIndex);
        buffer.writeVarInt(payload.chunkCount);
        buffer.writeVarInt(payload.minX);
        buffer.writeVarInt(payload.minY);
        buffer.writeVarInt(payload.minZ);
        buffer.writeVarInt(payload.sizeX);
        buffer.writeVarInt(payload.sizeY);
        buffer.writeVarInt(payload.sizeZ);
        buffer.writeVarIntArray(payload.palette != null ? payload.palette : new int[0]);
        buffer.writeVarIntArray(payload.entries);
        buffer.writeVarInt(payload.blockEntities.size());
        for (SelectionBlockEntityData data : payload.blockEntities) {
            buffer.writeVarInt(data.cell());
            buffer.writeByteArray(data.compressedNbt());
        }
    }

    private static SelectionBlocksPayload decode(FriendlyByteBuf buffer) {
        int generation = buffer.readVarInt();
        int chunkIndex = buffer.readVarInt();
        int chunkCount = buffer.readVarInt();
        int minX = buffer.readVarInt();
        int minY = buffer.readVarInt();
        int minZ = buffer.readVarInt();
        int sizeX = buffer.readVarInt();
        int sizeY = buffer.readVarInt();
        int sizeZ = buffer.readVarInt();
        int[] palette = buffer.readVarIntArray(SelectionProjectionLimits.MAX_PALETTE_SIZE);
        int[] entries = buffer.readVarIntArray(SelectionProjectionLimits.MAX_ENTRY_INTS_PER_CHUNK);
        int blockEntityCount = buffer.readVarInt();
        if (blockEntityCount < 0 || blockEntityCount > SelectionProjectionLimits.MAX_BLOCK_ENTITY_COUNT
                || blockEntityCount > entries.length / 2) {
            throw new IllegalArgumentException("Invalid projection block entity count");
        }
        List<SelectionBlockEntityData> blockEntities = new ArrayList<>(blockEntityCount);
        long nbtBytes = 0;
        for (int index = 0; index < blockEntityCount; index++) {
            int cell = buffer.readVarInt();
            byte[] nbt = buffer.readByteArray(SelectionProjectionLimits.MAX_BLOCK_ENTITY_NBT_BYTES);
            nbtBytes += nbt.length;
            if (nbtBytes > SelectionProjectionLimits.MAX_ESTIMATED_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Projection block entity NBT chunk is too large");
            }
            blockEntities.add(new SelectionBlockEntityData(cell, nbt));
        }
        return new SelectionBlocksPayload(generation, chunkIndex, chunkCount,
                minX, minY, minZ, sizeX, sizeY, sizeZ,
                palette.length > 0 ? palette : null, entries, blockEntities);
    }

    public static int cellIndex(int x, int y, int z, int sizeX, int sizeY) {
        return SelectionProjectionLimits.cellIndex(x, y, z, sizeX, sizeY);
    }

    public static int cellX(int cell, int sizeX, int sizeY) {
        return SelectionProjectionLimits.cellX(cell, sizeX, sizeY);
    }

    public static int cellY(int cell, int sizeX, int sizeY) {
        return SelectionProjectionLimits.cellY(cell, sizeX, sizeY);
    }

    public static int cellZ(int cell, int sizeX, int sizeY) {
        return SelectionProjectionLimits.cellZ(cell, sizeX, sizeY);
    }

    public SelectionProjectionChunk asChunk() {
        return new SelectionProjectionChunk(
                generation, chunkIndex, chunkCount, minX, minY, minZ, sizeX, sizeY, sizeZ,
                palette, entries, blockEntities);
    }

}
