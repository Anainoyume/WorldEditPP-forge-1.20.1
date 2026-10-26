package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.redawn.worldeditpp.bridge.minecraft.mixin.client.BufferBuilderStorageMixin;
import org.lwjgl.system.MemoryUtil;

/** Owns only buffers allocated by this mod. Never frees vanilla/shared builders. */
final class OwnedBufferBuilder implements AutoCloseable {
    private BufferBuilder buffer;

    OwnedBufferBuilder(int capacity) { buffer = new BufferBuilder(capacity); }

    BufferBuilder buffer() {
        if (buffer == null) throw new IllegalStateException("Projection buffer is closed");
        return buffer;
    }

    void reset() {
        if (buffer.building()) buffer.end().release();
        buffer.discard();
    }

    long bytes() { return buffer == null ? 0 : ((BufferBuilderStorageMixin) buffer).worldeditpp$storage().capacity(); }

    @Override public void close() {
        if (buffer == null) return;
        // End/discard before reading the pointer: an in-progress end() may grow the buffer.
        if (buffer.building()) buffer.end().release();
        else buffer.discard();
        var storage = ((BufferBuilderStorageMixin) buffer).worldeditpp$storage();
        long address = storage == null ? 0L : MemoryUtil.memAddress0(storage);
        // ModernFix's BufferBuilder leak fix frees a non-null f_85648_ in finalize().
        // Clear our field before freeing so that finalizer cannot double-free the same block.
        ((BufferBuilderStorageMixin) buffer).worldeditpp$setStorage(null);
        buffer = null;
        if (address != 0L) MemoryUtil.getAllocator(false).free(address);
    }
}
