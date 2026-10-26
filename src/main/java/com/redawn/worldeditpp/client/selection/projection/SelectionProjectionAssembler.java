package com.redawn.worldeditpp.client.selection.projection;

import com.redawn.worldeditpp.network.SelectionProjectionChunk;
import com.redawn.worldeditpp.network.SelectionProjectionLimits;
import com.redawn.worldeditpp.network.SelectionBlockEntityData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/** Reassembles one server snapshot before it is exposed to the renderer. */
public final class SelectionProjectionAssembler {
    private int generation = -1;
    private int chunkCount;
    private int minX;
    private int minY;
    private int minZ;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private int[] palette;
    private int receivedCount;
    private long receivedEntryInts;
    private long receivedBlockEntityNbtBytes;
    private boolean complete;
    private final BitSet seenCells = new BitSet();
    private final BitSet seenBlockEntities = new BitSet();
    private int maximumPaletteIndex;
    private final Map<Integer, SelectionProjectionChunk> chunks = new HashMap<>();

    public void reset() {
        generation = -1;
        chunkCount = 0;
        palette = null;
        receivedCount = 0;
        receivedEntryInts = 0;
        receivedBlockEntityNbtBytes = 0;
        complete = false;
        chunks.clear();
        seenCells.clear(); seenBlockEntities.clear(); maximumPaletteIndex = -1;
    }

    public AssemblyResult accept(SelectionProjectionChunk payload) {
        if (payload.chunkCount() == 0) {
            if (generation >= 0 && payload.generation() < generation) {
                return AssemblyResult.ignored();
            }
            generation = payload.generation();
            chunkCount = 0;
            palette = null;
            receivedCount = 0;
            receivedEntryInts = 0;
            receivedBlockEntityNbtBytes = 0;
            complete = true;
            chunks.clear();
            seenCells.clear(); seenBlockEntities.clear(); maximumPaletteIndex = -1;
            return AssemblyResult.cleared();
        }
        if (generation >= 0 && payload.generation() < generation) {
            return AssemblyResult.ignored();
        }
        if (payload.generation() != generation) {
            generation = payload.generation();
            chunkCount = payload.chunkCount();
            minX = payload.minX();
            minY = payload.minY();
            minZ = payload.minZ();
            sizeX = payload.sizeX();
            sizeY = payload.sizeY();
            sizeZ = payload.sizeZ();
            palette = null;
            receivedCount = 0;
            receivedEntryInts = 0;
            receivedBlockEntityNbtBytes = 0;
            complete = false;
            chunks.clear();
            seenCells.clear(); seenBlockEntities.clear(); maximumPaletteIndex = -1;
        }
        if (complete) {
            return AssemblyResult.ignored();
        }
        if (payload.chunkCount() != chunkCount || !sameBounds(payload)) {
            return AssemblyResult.rejected();
        }
        if (payload.chunkIndex() == 0) {
            palette = payload.palette();
        }
        if (chunks.putIfAbsent(payload.chunkIndex(), payload) != null) {
            return AssemblyResult.ignored();
        }
        // Validate bounded network chunks as they arrive, not a million-entry snapshot at once.
        long volume = SelectionProjectionLimits.checkedVolume(sizeX, sizeY, sizeZ);
        int[] part = payload.entries();
        for (int i = 0; i < part.length; i += 2) {
            int cell = part[i], index = part[i + 1];
            if (cell < 0 || cell >= volume || index < 0 || seenCells.get(cell)) {
                reset(); return AssemblyResult.rejected();
            }
            seenCells.set(cell); maximumPaletteIndex = Math.max(maximumPaletteIndex, index);
        }
        for (var data : payload.blockEntities()) {
            if (seenBlockEntities.get(data.cell())) { reset(); return AssemblyResult.rejected(); }
            seenBlockEntities.set(data.cell());
        }
        receivedCount++;
        receivedEntryInts += payload.entries().length;
        for (SelectionBlockEntityData data : payload.blockEntities()) {
            receivedBlockEntityNbtBytes += data.compressedSize();
        }
        if (receivedEntryInts > SelectionProjectionLimits.MAX_TOTAL_ENTRY_INTS) {
            reset();
            return AssemblyResult.rejected();
        }
        if (receivedBlockEntityNbtBytes > SelectionProjectionLimits.MAX_TOTAL_BLOCK_ENTITY_NBT_BYTES) {
            reset();
            return AssemblyResult.rejected();
        }
        if (receivedCount != chunkCount || palette == null) {
            return AssemblyResult.pending();
        }

        int[] entries = new int[(int) receivedEntryInts];
        List<SelectionBlockEntityData> blockEntities = new ArrayList<>();

        int cursor = 0;
        for (int index = 0; index < chunkCount; index++) {
            SelectionProjectionChunk chunk = chunks.get(index);
            if (chunk == null) {
                return AssemblyResult.rejected();
            }
            int[] chunkEntries = chunk.entries();
            System.arraycopy(chunkEntries, 0, entries, cursor, chunkEntries.length);
            cursor += chunkEntries.length;
            for (SelectionBlockEntityData data : chunk.blockEntities()) {
                blockEntities.add(data);
            }
        }
        if (palette.length == 0 || maximumPaletteIndex >= palette.length) {
            reset();
            return AssemblyResult.rejected();
        }
        SnapshotData snapshot = new SnapshotData(
                generation, minX, minY, minZ, sizeX, sizeY, sizeZ,
                palette, entries, blockEntities);
        chunks.clear();
        seenCells.clear(); seenBlockEntities.clear(); maximumPaletteIndex = -1;
        receivedEntryInts = 0;
        receivedBlockEntityNbtBytes = 0;
        complete = true;
        return AssemblyResult.complete(snapshot);
    }

    private boolean sameBounds(SelectionProjectionChunk payload) {
        return payload.minX() == minX && payload.minY() == minY && payload.minZ() == minZ
                && payload.sizeX() == sizeX && payload.sizeY() == sizeY && payload.sizeZ() == sizeZ;
    }

    public enum ResultType {
        PENDING,
        COMPLETE,
        CLEARED,
        IGNORED,
        REJECTED
    }

    public record AssemblyResult(ResultType type, SnapshotData snapshot) {
        private static AssemblyResult pending() {
            return new AssemblyResult(ResultType.PENDING, null);
        }

        private static AssemblyResult complete(SnapshotData snapshot) {
            return new AssemblyResult(ResultType.COMPLETE, snapshot);
        }

        private static AssemblyResult cleared() {
            return new AssemblyResult(ResultType.CLEARED, null);
        }

        private static AssemblyResult ignored() {
            return new AssemblyResult(ResultType.IGNORED, null);
        }

        private static AssemblyResult rejected() {
            return new AssemblyResult(ResultType.REJECTED, null);
        }
    }

    public record SnapshotData(int generation, int minX, int minY, int minZ,
                        int sizeX, int sizeY, int sizeZ, int[] palette, int[] entries,
                        List<SelectionBlockEntityData> blockEntities) {
        public SnapshotData {
            palette = palette.clone();
            entries = entries.clone();
            blockEntities = List.copyOf(blockEntities);
        }
    }
}
