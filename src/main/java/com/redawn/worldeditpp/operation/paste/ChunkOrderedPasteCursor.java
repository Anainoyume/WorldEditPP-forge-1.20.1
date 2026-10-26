package com.redawn.worldeditpp.operation.paste;

import java.util.NoSuchElementException;

/** Iterates a cuboid once while keeping consecutive cells in the same destination chunk. */
public final class ChunkOrderedPasteCursor {
    public record Cell(int x, int y, int z) { }

    private final int targetMinX;
    private final int targetMinZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int minChunkX;
    private final int maxChunkX;
    private final int maxChunkZ;
    private final long total;

    private int chunkX;
    private int chunkZ;
    private int startX;
    private int endX;
    private int startZ;
    private int endZ;
    private int x;
    private int y;
    private int z;
    private long completed;
    private boolean done;

    public ChunkOrderedPasteCursor(int targetMinX, int targetMinZ, int sizeX, int sizeY, int sizeZ) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("Paste dimensions must be positive");
        }
        long targetMaxX = (long) targetMinX + sizeX - 1L;
        long targetMaxZ = (long) targetMinZ + sizeZ - 1L;
        if (targetMaxX > Integer.MAX_VALUE || targetMaxX < Integer.MIN_VALUE
                || targetMaxZ > Integer.MAX_VALUE || targetMaxZ < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("Paste bounds overflow integer coordinates");
        }
        this.targetMinX = targetMinX;
        this.targetMinZ = targetMinZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.minChunkX = Math.floorDiv(targetMinX, 16);
        this.maxChunkX = Math.floorDiv((int) targetMaxX, 16);
        this.chunkX = minChunkX;
        this.chunkZ = Math.floorDiv(targetMinZ, 16);
        this.maxChunkZ = Math.floorDiv((int) targetMaxZ, 16);
        this.total = Math.multiplyExact(Math.multiplyExact((long) sizeX, sizeY), sizeZ);
        initializeChunk();
    }

    public boolean hasNext() {
        return !done;
    }

    public Cell next() {
        ensureAvailable();
        Cell cell = new Cell(x, y, z);
        advance();
        return cell;
    }

    public int currentX() {
        ensureAvailable();
        return x;
    }

    public int currentY() {
        ensureAvailable();
        return y;
    }

    public int currentZ() {
        ensureAvailable();
        return z;
    }

    public void advance() {
        ensureAvailable();
        completed++;
        advanceCoordinates();
    }

    public long completed() {
        return completed;
    }

    public long total() {
        return total;
    }

    private void initializeChunk() {
        int chunkWorldMinX = chunkX << 4;
        int chunkWorldMinZ = chunkZ << 4;
        startX = Math.max(0, chunkWorldMinX - targetMinX);
        endX = Math.min(sizeX - 1, chunkWorldMinX + 15 - targetMinX);
        startZ = Math.max(0, chunkWorldMinZ - targetMinZ);
        endZ = Math.min(sizeZ - 1, chunkWorldMinZ + 15 - targetMinZ);
        x = startX;
        y = 0;
        z = startZ;
    }

    private void ensureAvailable() {
        if (done) throw new NoSuchElementException();
    }

    private void advanceCoordinates() {
        if (++x <= endX) return;
        x = startX;
        if (++z <= endZ) return;
        z = startZ;
        if (++y < sizeY) return;

        if (++chunkX > maxChunkX) {
            chunkX = minChunkX;
            if (++chunkZ > maxChunkZ) {
                done = true;
                return;
            }
        }
        initializeChunk();
    }
}
