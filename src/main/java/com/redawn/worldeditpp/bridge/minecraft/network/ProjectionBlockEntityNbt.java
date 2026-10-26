package com.redawn.worldeditpp.bridge.minecraft.network;

import com.redawn.worldeditpp.network.SelectionBlockEntityData;
import com.redawn.worldeditpp.network.SelectionProjectionLimits;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Full block entity NBT serialization shared by snapshot capture and restore. */
public final class ProjectionBlockEntityNbt {
    public static SelectionBlockEntityData capture(int cell, BlockEntity blockEntity,
                                                    HolderLookup.Provider registries) {
        return capture(cell, blockEntity.saveWithFullMetadata());
    }

    public static SelectionBlockEntityData capture(int cell, CompoundTag tag) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(tag, output);
            return new SelectionBlockEntityData(cell, output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to serialize projection block entity", exception);
        }
    }

    public static BlockEntity restore(SelectionBlockEntityData data, BlockPos pos, BlockState state,
                                      HolderLookup.Provider registries) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(data.compressedNbt());
             var gzip = new java.util.zip.GZIPInputStream(input);
             var decoded = new java.io.DataInputStream(gzip)) {
            CompoundTag tag = NbtIo.read(decoded,
                    new NbtAccounter(SelectionProjectionLimits.MAX_BLOCK_ENTITY_UNCOMPRESSED_NBT_BYTES));
            return BlockEntity.loadStatic(pos, state, tag);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Failed to deserialize projection block entity", exception);
        }
    }

    private ProjectionBlockEntityNbt() { }
}
