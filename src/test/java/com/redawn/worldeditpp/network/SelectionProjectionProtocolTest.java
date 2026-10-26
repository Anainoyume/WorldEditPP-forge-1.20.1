package com.redawn.worldeditpp.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SelectionProjectionProtocolTest {
    @Test
    void cellCoordinatesRoundTrip() {
        int sizeX = 17;
        int sizeY = 9;
        int cell = SelectionProjectionLimits.cellIndex(16, 8, 4, sizeX, sizeY);

        assertEquals(16, SelectionProjectionLimits.cellX(cell, sizeX, sizeY));
        assertEquals(8, SelectionProjectionLimits.cellY(cell, sizeX, sizeY));
        assertEquals(4, SelectionProjectionLimits.cellZ(cell, sizeX, sizeY));
    }

    @Test
    void rejectsMalformedChunkMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new SelectionProjectionChunk(
                1, 1, 1, 0, 0, 0, 1, 1, 1, null, new int[] {0, 0}, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SelectionProjectionChunk(
                1, 0, 1, 0, 0, 0, 1, 1, 1, null, new int[] {0, 0}, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SelectionProjectionChunk(
                1, 0, 1, 0, 0, 0, 1, 1, 1, new int[] {1}, new int[] {0}, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SelectionProjectionChunk(
                1, 0, 1, 0, 0, 0, 1, 1, 1, new int[] {1}, new int[] {1, 0}, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SelectionProjectionChunk(
                1, 0, 1, 0, 0, 0, 2, 1, 1, new int[] {1},
                new int[] {0, 0, 0, 0}, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SelectionProjectionChunk(
                1, 0, 1, 0, 0, 0, 1, 1, 1, new int[] {1}, new int[] {0, 1}, List.of()));
    }

    @Test
    void clearChunkHasCanonicalMetadata() {
        new SelectionProjectionChunk(1, 0, 0, 0, 0, 0, 0, 0, 0, null, new int[0], List.of());

        assertThrows(IllegalArgumentException.class, () -> new SelectionProjectionChunk(
                1, 0, 0, 1, 0, 0, 0, 0, 0, null, new int[0], List.of()));
    }

    @Test
    void rejectsOutOfRangeCellCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> SelectionProjectionLimits.cellIndex(-1, 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> SelectionProjectionLimits.cellIndex(Integer.MAX_VALUE, Integer.MAX_VALUE,
                        Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void acceptsOneEmptyDataChunkForAnAllAirProjection() {
        new SelectionProjectionChunk(1, 0, 1, 0, 0, 0, 4, 5, 6,
                new int[] {0}, new int[0], List.of());
    }

    @Test
    void blockEntityDataIsDefensivelyCopiedAndMustMatchAChunkCell() {
        byte[] nbt = {1, 2, 3};
        SelectionBlockEntityData data = new SelectionBlockEntityData(7, nbt);
        nbt[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, data.compressedNbt());

        new SelectionProjectionChunk(1, 0, 1, 0, 0, 0, 8, 1, 1,
                new int[] {1}, new int[] {7, 0}, List.of(data));
        assertThrows(IllegalArgumentException.class, () -> new SelectionProjectionChunk(
                1, 0, 1, 0, 0, 0, 8, 1, 1,
                new int[] {1}, new int[] {6, 0}, List.of(data)));
    }

}
