package com.redawn.worldeditpp.operation.task;

/** Sorts WorldEdit packed block positions into chunk-local order without allocating per entry. */
public final class ChunkPositionOrder {
    private static final long BITS_26 = (1L << 26) - 1L;
    private static final long BITS_12 = (1L << 12) - 1L;

    public static long pack(int x, int y, int z) {
        if (x < -33_554_432 || x > 33_554_431 || z < -33_554_432 || z > 33_554_431
                || y < -2_048 || y > 2_047) {
            throw new IllegalArgumentException("Position exceeds WorldEdit packed-coordinate limits");
        }
        return (x & BITS_26) | ((z & BITS_26) << 26) | ((y & BITS_12) << 52);
    }

    public static int x(long packed) { return (int) (packed << 38 >> 38); }
    public static int y(long packed) { return (int) (packed >> 52); }
    public static int z(long packed) { return (int) (packed << 12 >> 38); }

    public static void sort(long[] positions) {
        sort(positions, () -> false);
    }

    public static void sort(long[] positions, java.util.function.BooleanSupplier cancelled) {
        if (positions.length > 1) quickSort(positions, 0, positions.length - 1, cancelled);
    }

    public static int compare(long left, long right) {
        int lx = x(left), rx = x(right);
        int compared = Integer.compare(lx >> 4, rx >> 4);
        if (compared != 0) return compared;
        int lz = z(left), rz = z(right);
        compared = Integer.compare(lz >> 4, rz >> 4);
        if (compared != 0) return compared;
        compared = Integer.compare(y(left), y(right));
        if (compared != 0) return compared;
        compared = Integer.compare(lz & 15, rz & 15);
        return compared != 0 ? compared : Integer.compare(lx & 15, rx & 15);
    }

    private static void quickSort(long[] values, int low, int high, java.util.function.BooleanSupplier cancelled) {
        while (low < high) {
            if (cancelled.getAsBoolean()) return;
            int left = low, right = high;
            int checks = 0;
            long pivot = values[(low + high) >>> 1];
            while (left <= right) {
                if ((++checks & 1023) == 0 && cancelled.getAsBoolean()) return;
                while (compare(values[left], pivot) < 0) {
                    left++;
                    if ((++checks & 1023) == 0 && cancelled.getAsBoolean()) return;
                }
                while (compare(values[right], pivot) > 0) {
                    right--;
                    if ((++checks & 1023) == 0 && cancelled.getAsBoolean()) return;
                }
                if (left <= right) {
                    long value = values[left]; values[left++] = values[right]; values[right--] = value;
                }
            }
            // Recurse into the smaller side and iterate the larger one to cap stack depth.
            if (right - low < high - left) {
                if (low < right) quickSort(values, low, right, cancelled);
                low = left;
            } else {
                if (left < high) quickSort(values, left, high, cancelled);
                high = right;
            }
        }
    }

    private ChunkPositionOrder() {}
}
