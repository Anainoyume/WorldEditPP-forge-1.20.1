package com.redawn.worldeditpp.network;

/** Shared validation limits for selection projection snapshots and packets. */
public final class SelectionProjectionLimits {
    public static final long MAX_VOLUME = 16_777_216L;
    /** Bounded to 768 protocol chunks, including the 2.6-million-block mixed stress case. */
    public static final int MAX_BLOCKS = 3_145_728;
    public static final int MAX_PALETTE_SIZE = 65_536;
    public static final int MAX_BLOCKS_PER_CHUNK = 4_096;
    public static final int MAX_ENTRY_INTS_PER_CHUNK = MAX_BLOCKS_PER_CHUNK * 2;
    public static final int MAX_BLOCK_ENTITY_COUNT = 65_536;
    public static final int MAX_BLOCK_ENTITY_NBT_BYTES = 512 * 1_024;
    public static final int MAX_BLOCK_ENTITY_UNCOMPRESSED_NBT_BYTES = 8 * 1_024 * 1_024;
    public static final long MAX_TOTAL_BLOCK_ENTITY_NBT_BYTES = 64L * 1_024 * 1_024;
    public static final int MAX_ESTIMATED_PAYLOAD_BYTES = 900_000;
    public static final int MAX_CHUNK_COUNT = 768;
    public static final int MAX_TOTAL_ENTRY_INTS = MAX_BLOCKS * 2;

    public static long checkedVolume(int sizeX, int sizeY, int sizeZ) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            return -1L;
        }
        try {
            return Math.multiplyExact(Math.multiplyExact((long) sizeX, sizeY), sizeZ);
        } catch (ArithmeticException ignored) {
            return -1L;
        }
    }

    public static boolean hasValidVolume(int sizeX, int sizeY, int sizeZ) {
        long volume = checkedVolume(sizeX, sizeY, sizeZ);
        return volume > 0L && volume <= MAX_VOLUME;
    }

    public static int cellIndex(int x, int y, int z, int sizeX, int sizeY) {
        long cell = x + (long) y * sizeX + (long) z * sizeX * sizeY;
        if (x < 0 || y < 0 || z < 0 || sizeX <= 0 || sizeY <= 0 || cell < 0L || cell > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Selection projection cell is out of range");
        }
        return (int) cell;
    }

    public static int cellX(int cell, int sizeX, int sizeY) {
        requireCellArguments(cell, sizeX, sizeY);
        return cell % sizeX;
    }

    public static int cellY(int cell, int sizeX, int sizeY) {
        requireCellArguments(cell, sizeX, sizeY);
        return cell / sizeX % sizeY;
    }

    public static int cellZ(int cell, int sizeX, int sizeY) {
        requireCellArguments(cell, sizeX, sizeY);
        return cell / Math.multiplyExact(sizeX, sizeY);
    }

    private static void requireCellArguments(int cell, int sizeX, int sizeY) {
        if (cell < 0 || sizeX <= 0 || sizeY <= 0) {
            throw new IllegalArgumentException("Invalid selection projection cell arguments");
        }
    }

    private SelectionProjectionLimits() {
    }
}
