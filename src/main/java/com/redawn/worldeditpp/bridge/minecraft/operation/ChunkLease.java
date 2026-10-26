package com.redawn.worldeditpp.bridge.minecraft.operation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/** Non-blocking FULL-chunk gate, including neighbours needed by placement side effects. */
public final class ChunkLease implements AutoCloseable {
    private static final TicketType<UUID> TYPE = TicketType.create("worldeditpp_operation", UUID::compareTo);
    private static final int MAX_CENTERS = 16;
    private final ServerLevel level;
    private final LinkedHashMap<ChunkPos, Lease> leases = new LinkedHashMap<>(16, 0.75F, true);
    /** Sequential iteration stays in one chunk for thousands of cells; reuse it without allocating. */
    private int lastChunkX = Integer.MIN_VALUE;
    private int lastChunkZ = Integer.MIN_VALUE;
    private Lease lastLease;

    public ChunkLease(ServerLevel level) { this.level = level; }

    public boolean ready(int blockX, int blockZ) {
        int tick = level.getServer().getTickCount();
        int chunkX = blockX >> 4, chunkZ = blockZ >> 4;
        Lease lease;
        if (chunkX == lastChunkX && chunkZ == lastChunkZ && lastLease != null) {
            lease = lastLease;
        } else {
            ChunkPos requested = new ChunkPos(chunkX, chunkZ);
            lease = leases.get(requested);
            if (lease == null) {
                if (leases.size() >= MAX_CENTERS) evictOldest();
                lease = new Lease();
                leases.put(requested, lease);
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                    level.getChunkSource().addRegionTicket(TYPE,
                            new ChunkPos(requested.x + x, requested.z + z), 0, lease.ticket);
                }
            }
            lastChunkX = chunkX;
            lastChunkZ = chunkZ;
            lastLease = lease;
        }
        if (lease.readyAtTick == tick) return true;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            // getChunkFuture on the 1.20.1 server thread calls managedBlock! Poll instead.
            if (level.getChunkSource().getChunkNow(chunkX + x, chunkZ + z) == null) {
                // GameTest and accelerated servers can run 600 ticks in under a second.
                if (++lease.waitingTicks > 600 && System.nanoTime() - lease.waitingSince > 30_000_000_000L)
                    throw new IllegalStateException("Chunk load timed out at " + new ChunkPos(chunkX, chunkZ));
                return false;
            }
        }
        lease.waitingTicks = 0;
        lease.readyAtTick = tick;
        return true;
    }

    @Override public void close() {
        for (var entry : leases.entrySet()) release(entry.getKey(), entry.getValue());
        leases.clear();
        lastLease = null;
        lastChunkX = Integer.MIN_VALUE;
        lastChunkZ = Integer.MIN_VALUE;
    }

    private void evictOldest() {
        Iterator<Map.Entry<ChunkPos, Lease>> iterator = leases.entrySet().iterator();
        var entry = iterator.next();
        if (entry.getValue() == lastLease) lastLease = null;
        release(entry.getKey(), entry.getValue());
        iterator.remove();
    }

    private void release(ChunkPos center, Lease lease) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            level.getChunkSource().removeRegionTicket(TYPE,
                    new ChunkPos(center.x + x, center.z + z), 0, lease.ticket);
        }
    }

    private static final class Lease {
        final UUID ticket = UUID.randomUUID();
        final long waitingSince = System.nanoTime();
        int waitingTicks;
        int readyAtTick = Integer.MIN_VALUE;
    }
}
