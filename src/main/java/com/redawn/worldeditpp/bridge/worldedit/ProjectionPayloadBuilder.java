package com.redawn.worldeditpp.bridge.worldedit;

import com.redawn.worldeditpp.bridge.minecraft.network.ProjectionBlockEntityNbt;
import com.redawn.worldeditpp.bridge.minecraft.network.SelectionBlocksPayload;
import com.redawn.worldeditpp.network.SelectionBlockEntityData;
import com.redawn.worldeditpp.network.SelectionProjectionLimits;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Incrementally encodes one projection and releases each transport chunk after it is sent. */
final class ProjectionPayloadBuilder {
    /** ClientboundCustomPayloadPacket rejects a packet above 1,048,576 bytes. */
    private static final long MAX_PACKET_ESTIMATE_BYTES = 1_000_000L;
    /** Every int is written as a VarInt; 5 bytes is the worst case for a 32-bit value. */
    private static final long VARINT_MAX_BYTES = 5L;
    private final Clipboard source;
    private final BlockVector3 sourceMinimum;
    private final BlockVector3 dimensions;
    private final int[] sparseCells;
    private final IntArrayList palette = new IntArrayList();
    private final Int2IntOpenHashMap paletteIndex = new Int2IntOpenHashMap();
    private final List<OutgoingChunk> chunks = new ArrayList<>();
    private IntArrayList currentEntries = new IntArrayList(SelectionProjectionLimits.MAX_ENTRY_INTS_PER_CHUNK);
    private List<SelectionBlockEntityData> currentBlockEntities = new ArrayList<>();
    private long currentChunkNbtBytes;
    private int sparseCursor;
    private int cursorX;
    private int cursorY;
    private int cursorZ;
    private int sendCursor;
    private int blockCount;
    private int blockEntityCount;
    private long blockEntityBytes;
    private int[] encodedPalette;
    private boolean complete;
    private boolean failed;

    ProjectionPayloadBuilder(Clipboard source) {
        this.source = source;
        this.sourceMinimum = source.getMinimumPoint();
        this.dimensions = source.getDimensions();
        this.sparseCells = source instanceof ProjectionClipboard projection
                ? projection.nonAirCells() : null;
    }

    void build(int workLimit) {
        while (workLimit-- > 0 && !complete) {
            if (sparseCells != null) {
                if (sparseCursor >= sparseCells.length) {
                    complete = true;
                    break;
                }
                int cell = sparseCells[sparseCursor++];
                appendCell(cell % dimensions.getX(),
                        cell / dimensions.getX() % dimensions.getY(),
                        cell / (dimensions.getX() * dimensions.getY()));
            } else {
                appendCell(cursorX, cursorY, cursorZ);
                advanceCursor();
            }
        }
        if (complete && !failed) finishEncoding();
    }

    boolean complete() {
        return complete;
    }

    boolean failed() {
        return failed;
    }

    boolean hasPayload() {
        return complete && !failed && sendCursor < chunks.size();
    }

    SelectionBlocksPayload nextPayload(int generation) {
        if (!hasPayload()) throw new IllegalStateException("Projection payload is not ready");
        int index = sendCursor++;
        OutgoingChunk chunk = chunks.set(index, null);
        return new SelectionBlocksPayload(
                generation, index, chunks.size(),
                0, 0, 0, dimensions.getX(), dimensions.getY(), dimensions.getZ(),
                index == 0 ? encodedPalette : null,
                chunk.entries(), chunk.blockEntities());
    }

    int blockCount() {
        return blockCount;
    }

    int blockEntityCount() {
        return blockEntityCount;
    }

    long blockEntityBytes() {
        return blockEntityBytes;
    }

    void release() {
        chunks.clear();
        palette.clear();
        paletteIndex.clear();
        currentEntries = null;
        currentBlockEntities = null;
        encodedPalette = null;
    }

    private void appendCell(int x, int y, int z) {
        BlockVector3 position = sourceMinimum.add(x, y, z);
        BaseBlock fullBlock = source.getFullBlock(position);
        BlockState blockState = WorldEditPlatform.adapt(fullBlock.toImmutableState());
        if (blockState.isAir()) return;
        if (blockCount >= SelectionProjectionLimits.MAX_BLOCKS) {
            fail();
            return;
        }

        int blockStateId = Block.getId(blockState);
        int paletteEntry = paletteIndex.getOrDefault(blockStateId, -1);
        if (paletteEntry < 0) {
            paletteEntry = palette.size();
            palette.add(blockStateId);
            paletteIndex.put(blockStateId, paletteEntry);
        }
        int cell = SelectionBlocksPayload.cellIndex(
                x, y, z, dimensions.getX(), dimensions.getY());
        currentEntries.add(cell);
        currentEntries.add(paletteEntry);
        blockCount++;

        if (fullBlock.getNbtData() != null) {
            CompoundTag tag = WorldEditPlatform.toNative(fullBlock.getNbtData()).copy();
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            SelectionBlockEntityData data = ProjectionBlockEntityNbt.capture(cell, tag);
            long nextBytes = blockEntityBytes + data.compressedSize();
            if (blockEntityCount >= SelectionProjectionLimits.MAX_BLOCK_ENTITY_COUNT
                    || nextBytes > SelectionProjectionLimits.MAX_TOTAL_BLOCK_ENTITY_NBT_BYTES
                    || currentChunkNbtBytes + data.compressedSize() > chunkNbtLimit()) {
                fail();
                return;
            }
            currentBlockEntities.add(data);
            blockEntityCount++;
            blockEntityBytes = nextBytes;
            currentChunkNbtBytes += data.compressedSize();
        }
        if (currentEntries.size() == SelectionProjectionLimits.MAX_ENTRY_INTS_PER_CHUNK) {
            finishChunk();
        }
    }

    private void advanceCursor() {
        if (failed) return;
        if (++cursorX >= dimensions.getX()) {
            cursorX = 0;
            if (++cursorY >= dimensions.getY()) {
                cursorY = 0;
                if (++cursorZ >= dimensions.getZ()) complete = true;
            }
        }
    }

    private void finishEncoding() {
        if (encodedPalette != null) return;
        if (!currentEntries.isEmpty() || chunks.isEmpty()) finishChunk();
        if (chunks.size() > SelectionProjectionLimits.MAX_CHUNK_COUNT) {
            fail();
            return;
        }
        if (palette.isEmpty()) palette.add(Block.getId(Blocks.AIR.defaultBlockState()));
        encodedPalette = palette.toIntArray();
        palette.clear();
        paletteIndex.clear();
    }

    private void finishChunk() {
        chunks.add(new OutgoingChunk(
                currentEntries.toIntArray(), List.copyOf(currentBlockEntities)));
        currentEntries = new IntArrayList(SelectionProjectionLimits.MAX_ENTRY_INTS_PER_CHUNK);
        currentBlockEntities = new ArrayList<>();
        currentChunkNbtBytes = 0;
    }

    /**
     * Bytes left for block-entity NBT in the chunk being built. Chunk 0 also carries the
     * final palette, so it reserves the worst-case palette and entry varints up front;
     * without this a legal-looking chunk could exceed the packet limit and throw from send().
     */
    private long chunkNbtLimit() {
        long entries = (long) SelectionProjectionLimits.MAX_ENTRY_INTS_PER_CHUNK * VARINT_MAX_BYTES;
        long palette = chunks.isEmpty()
                ? (long) SelectionProjectionLimits.MAX_PALETTE_SIZE * VARINT_MAX_BYTES : 0L;
        long limit = MAX_PACKET_ESTIMATE_BYTES - entries - palette;
        return Math.min(limit, SelectionProjectionLimits.MAX_ESTIMATED_PAYLOAD_BYTES);
    }

    private void fail() {
        failed = true;
        complete = true;
    }

    private record OutgoingChunk(int[] entries, List<SelectionBlockEntityData> blockEntities) { }
}
