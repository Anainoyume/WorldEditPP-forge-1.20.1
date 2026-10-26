package com.redawn.worldeditpp.operation;

import com.redawn.worldeditpp.operation.task.ChunkPositionOrder;
import java.util.HashSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkPositionOrderTest {
    @Test
    void packedCoordinatesRoundTripAtSignedBoundaries() {
        int[][] values = {{0, 0, 0}, {-1, -64, -1}, {33_554_431, 2_047, -33_554_432}};
        for (int[] value : values) {
            long packed = ChunkPositionOrder.pack(value[0], value[1], value[2]);
            assertEquals(value[0], ChunkPositionOrder.x(packed));
            assertEquals(value[1], ChunkPositionOrder.y(packed));
            assertEquals(value[2], ChunkPositionOrder.z(packed));
        }
        assertThrows(IllegalArgumentException.class, () -> ChunkPositionOrder.pack(33_554_432, 0, 0));
    }

    @Test
    void sortKeepsEveryPositionAndNeverReturnsToACompletedChunk() {
        Random random = new Random(0x575050L);
        long[] positions = new long[20_000];
        HashSet<Long> original = new HashSet<>();
        for (int i = 0; i < positions.length; i++) {
            long packed;
            do packed = ChunkPositionOrder.pack(random.nextInt(2048) - 1024,
                    random.nextInt(384) - 64, random.nextInt(2048) - 1024);
            while (!original.add(packed));
            positions[i] = packed;
        }

        ChunkPositionOrder.sort(positions);
        HashSet<Long> sorted = new HashSet<>();
        HashSet<Long> completedChunks = new HashSet<>();
        long active = Long.MIN_VALUE;
        for (int i = 0; i < positions.length; i++) {
            assertTrue(sorted.add(positions[i]));
            if (i > 0) assertTrue(ChunkPositionOrder.compare(positions[i - 1], positions[i]) <= 0);
            long chunk = ((long) (ChunkPositionOrder.x(positions[i]) >> 4) << 32)
                    ^ ((ChunkPositionOrder.z(positions[i]) >> 4) & 0xffffffffL);
            if (chunk != active) {
                if (active != Long.MIN_VALUE) completedChunks.add(active);
                assertFalse(completedChunks.contains(chunk));
                active = chunk;
            }
        }
        assertEquals(original, sorted);
    }
}
