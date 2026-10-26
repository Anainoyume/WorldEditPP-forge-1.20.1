package com.redawn.worldeditpp.network;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Minecraft-independent representation of one projection snapshot chunk. */
public record SelectionProjectionChunk(int generation, int chunkIndex, int chunkCount,
                                       int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ,
                                       int[] palette, int[] entries,
                                       List<SelectionBlockEntityData> blockEntities) {
    public SelectionProjectionChunk {
        validate(generation, chunkIndex, chunkCount, minX, minY, minZ, sizeX, sizeY, sizeZ,
                palette, entries, blockEntities);
        palette = palette == null ? null : palette.clone();
        entries = entries.clone();
        blockEntities = List.copyOf(blockEntities);
    }

    @Override
    public int[] palette() {
        return palette == null ? null : palette.clone();
    }

    @Override
    public int[] entries() {
        return entries.clone();
    }

    private static void validate(int generation, int chunkIndex, int chunkCount,
                                 int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ,
                                 int[] palette, int[] entries,
                                 List<SelectionBlockEntityData> blockEntities) {
        if (generation < 0 || chunkCount < 0 || chunkCount > SelectionProjectionLimits.MAX_CHUNK_COUNT
                || chunkIndex < 0 || (chunkCount > 0 && chunkIndex >= chunkCount)) {
            throw new IllegalArgumentException("Invalid selection projection chunk");
        }
        if (blockEntities == null) {
            throw new IllegalArgumentException("Projection block entity list is required");
        }
        if (chunkCount == 0) {
            if (chunkIndex != 0 || minX != 0 || minY != 0 || minZ != 0
                    || sizeX != 0 || sizeY != 0 || sizeZ != 0 || palette != null
                    || entries == null || entries.length != 0 || !blockEntities.isEmpty()) {
                throw new IllegalArgumentException("Invalid selection projection clear packet");
            }
            return;
        }
        if (!SelectionProjectionLimits.hasValidVolume(sizeX, sizeY, sizeZ)) {
            throw new IllegalArgumentException("Invalid selection projection dimensions");
        }
        if (entries == null || (entries.length & 1) != 0
                || entries.length > SelectionProjectionLimits.MAX_ENTRY_INTS_PER_CHUNK) {
            throw new IllegalArgumentException("Selection projection entries must be pairs");
        }
        if (entries.length == 0 && (chunkCount != 1 || chunkIndex != 0 || !blockEntities.isEmpty())) {
            throw new IllegalArgumentException("Only one empty first chunk may represent an all-air projection");
        }
        if ((chunkIndex == 0) != (palette != null)) {
            throw new IllegalArgumentException("Only the first selection projection chunk may contain a palette");
        }
        if (palette != null && (palette.length == 0 || palette.length > SelectionProjectionLimits.MAX_PALETTE_SIZE)) {
            throw new IllegalArgumentException("Invalid selection projection palette");
        }
        if (blockEntities.size() > entries.length / 2) {
            throw new IllegalArgumentException("Too many projection block entities in chunk");
        }
        long volume = SelectionProjectionLimits.checkedVolume(sizeX, sizeY, sizeZ);
        Set<Integer> entryCells = new HashSet<>();
        for (int index = 0; index < entries.length; index += 2) {
            int cell = entries[index];
            int paletteIndex = entries[index + 1];
            if (cell < 0 || cell >= volume || !entryCells.add(cell)) {
                throw new IllegalArgumentException("Projection entry cell is out of range or duplicated");
            }
            if (paletteIndex < 0 || (palette != null && paletteIndex >= palette.length)) {
                throw new IllegalArgumentException("Projection entry palette index is out of range");
            }
        }
        Set<Integer> blockEntityCells = new HashSet<>();
        long nbtBytes = 0;
        for (SelectionBlockEntityData data : blockEntities) {
            if (!entryCells.contains(data.cell()) || !blockEntityCells.add(data.cell())) {
                throw new IllegalArgumentException("Projection block entity must match one unique chunk entry");
            }
            nbtBytes += data.compressedSize();
        }
        if (nbtBytes > SelectionProjectionLimits.MAX_ESTIMATED_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Projection block entity NBT chunk is too large");
        }
    }
}
