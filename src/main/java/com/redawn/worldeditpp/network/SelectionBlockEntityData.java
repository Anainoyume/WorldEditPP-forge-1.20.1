package com.redawn.worldeditpp.network;

import java.util.Arrays;

/** Immutable serialized block entity belonging to one projection cell. */
public record SelectionBlockEntityData(int cell, byte[] compressedNbt) {
    public SelectionBlockEntityData {
        if (cell < 0 || compressedNbt == null || compressedNbt.length == 0
                || compressedNbt.length > SelectionProjectionLimits.MAX_BLOCK_ENTITY_NBT_BYTES) {
            throw new IllegalArgumentException("Invalid projection block entity data");
        }
        compressedNbt = compressedNbt.clone();
    }

    @Override
    public byte[] compressedNbt() {
        return compressedNbt.clone();
    }

    public int compressedSize() {
        return compressedNbt.length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SelectionBlockEntityData data
                && cell == data.cell
                && Arrays.equals(compressedNbt, data.compressedNbt);
    }

    @Override
    public int hashCode() {
        return 31 * cell + Arrays.hashCode(compressedNbt);
    }
}
