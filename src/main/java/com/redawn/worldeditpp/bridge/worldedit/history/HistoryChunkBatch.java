package com.redawn.worldeditpp.bridge.worldedit.history;

import com.redawn.worldeditpp.bridge.minecraft.operation.ChunkLease;
import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import com.redawn.worldeditpp.bridge.minecraft.operation.ContainerRemoval;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * One server-thread execution slice. Reuses readiness and inspection state for a
 * contiguous chunk run, but never buffers writes or bypasses the WorldEdit extent.
 * Discard at every yield: a later tick must revalidate the lease and loaded chunk.
 */
final class HistoryChunkBatch {
    private final ServerLevel level;
    private final ChunkLease leases;
    private final BlockPos.MutableBlockPos inspection = new BlockPos.MutableBlockPos();
    private LevelChunk chunk;
    private int chunkX, chunkZ;

    HistoryChunkBatch(ServerLevel level, ChunkLease leases) {
        this.level = level;
        this.leases = leases;
    }

    boolean ready(BlockVector3 position) {
        int x = position.getX() >> 4, z = position.getZ() >> 4;
        if (chunk != null && chunkX == x && chunkZ == z) return true;
        // Do not retain a previous run if the next chunk is still loading.
        chunk = null;
        if (!leases.ready(position.getX(), position.getZ())) return false;
        chunk = level.getChunkSource().getChunkNow(x, z);
        if (chunk == null) return false;
        chunkX = x; chunkZ = z;
        return true;
    }

    void clearContainer(BlockVector3 position) {
        // Mutable position stays inside native inspection; never hand it to an extent.
        inspection.set(position.getX(), position.getY(), position.getZ());
        if (!chunk.getBlockState(inspection).hasBlockEntity()) return;
        var entity = chunk.getBlockEntity(inspection);
        ContainerRemoval.clear(entity);
    }

    void endRun() { chunk = null; }
}
