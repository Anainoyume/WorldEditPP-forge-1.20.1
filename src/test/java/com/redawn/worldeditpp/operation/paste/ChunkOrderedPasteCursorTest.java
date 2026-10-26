package com.redawn.worldeditpp.operation.paste;

import com.redawn.worldeditpp.operation.paste.ChunkOrderedPasteCursor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChunkOrderedPasteCursorTest {
    @Test
    void visitsEveryCellExactlyOnceAcrossPositiveAndNegativeChunks() {
        int sizeX = 35;
        int sizeY = 3;
        int sizeZ = 33;
        ChunkOrderedPasteCursor cursor = new ChunkOrderedPasteCursor(-17, -15, sizeX, sizeY, sizeZ);
        Set<Integer> visited = new HashSet<>();

        while (cursor.hasNext()) {
            ChunkOrderedPasteCursor.Cell cell = new ChunkOrderedPasteCursor.Cell(
                    cursor.currentX(), cursor.currentY(), cursor.currentZ());
            cursor.advance();
            assertTrue(cell.x() >= 0 && cell.x() < sizeX);
            assertTrue(cell.y() >= 0 && cell.y() < sizeY);
            assertTrue(cell.z() >= 0 && cell.z() < sizeZ);
            assertTrue(visited.add(cell.x() + sizeX * (cell.y() + sizeY * cell.z())));
        }

        assertEquals((long) sizeX * sizeY * sizeZ, cursor.total());
        assertEquals(cursor.total(), cursor.completed());
        assertEquals(cursor.total(), visited.size());
        assertFalse(cursor.hasNext());
    }

    @Test
    void neverReturnsToAChunkAfterAdvancingPastIt() {
        int targetMinX = 9;
        int targetMinZ = -20;
        ChunkOrderedPasteCursor cursor = new ChunkOrderedPasteCursor(targetMinX, targetMinZ, 40, 2, 37);
        Set<Long> completedChunks = new HashSet<>();
        long activeChunk = Long.MIN_VALUE;

        while (cursor.hasNext()) {
            ChunkOrderedPasteCursor.Cell cell = cursor.next();
            int chunkX = Math.floorDiv(targetMinX + cell.x(), 16);
            int chunkZ = Math.floorDiv(targetMinZ + cell.z(), 16);
            long chunk = ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
            if (chunk != activeChunk) {
                if (activeChunk != Long.MIN_VALUE) completedChunks.add(activeChunk);
                assertFalse(completedChunks.contains(chunk));
                activeChunk = chunk;
            }
        }
    }

    @Test
    void rejectsInvalidDimensionsAndOverflowingBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChunkOrderedPasteCursor(0, 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ChunkOrderedPasteCursor(Integer.MAX_VALUE, 0, 2, 1, 1));
    }
}
