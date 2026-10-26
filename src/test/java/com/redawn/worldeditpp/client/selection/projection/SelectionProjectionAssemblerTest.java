package com.redawn.worldeditpp.client.selection.projection;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.redawn.worldeditpp.network.SelectionProjectionChunk;
import com.redawn.worldeditpp.network.SelectionBlockEntityData;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelectionProjectionAssemblerTest {
    @Test
    void assemblesTwoPointSixMillionEntriesBeyondOldChunkLimit() {
        var assembler = new SelectionProjectionAssembler();
        int blocks = 2_600_000, count = (blocks + 4095) / 4096;
        SelectionProjectionAssembler.AssemblyResult result = null;
        for (int chunk = count - 1; chunk >= 0; chunk--) {
            int first = chunk * 4096, length = Math.min(4096, blocks - first);
            int[] entries = new int[length * 2];
            for (int i = 0; i < length; i++) entries[i * 2] = first + i;
            result = assembler.accept(new SelectionProjectionChunk(26, chunk, count,
                    0, 0, 0, 260, 100, 100, chunk == 0 ? new int[]{42} : null, entries, List.of()));
            assertEquals(chunk == 0 ? SelectionProjectionAssembler.ResultType.COMPLETE
                    : SelectionProjectionAssembler.ResultType.PENDING, result.type());
        }
        assertEquals(blocks * 2, result.snapshot().entries().length);
        assertEquals(blocks - 1, result.snapshot().entries()[blocks * 2 - 2]);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SelectionProjectionChunk(27, 0, 769,
                        0,0,0,260,100,100,new int[]{42},new int[]{0,0},List.of()));
    }

    @Test
    void assemblesOutOfOrderChunks() {
        SelectionProjectionAssembler assembler = new SelectionProjectionAssembler();

        SelectionBlockEntityData copied = new SelectionBlockEntityData(1, new byte[] {10, 20});
        SelectionProjectionAssembler.AssemblyResult pending = assembler.accept(
                chunk(5, 1, 2, null, 1, 0, List.of(copied)));
        SelectionProjectionAssembler.AssemblyResult complete = assembler.accept(chunk(5, 0, 2, new int[] {42}, 0, 0));

        assertEquals(SelectionProjectionAssembler.ResultType.PENDING, pending.type());
        assertEquals(SelectionProjectionAssembler.ResultType.COMPLETE, complete.type());
        assertArrayEquals(new int[] {0, 0, 1, 0}, complete.snapshot().entries());
        assertEquals(List.of(copied), complete.snapshot().blockEntities());
    }

    @Test
    void rejectsMismatchedBoundsWithoutDiscardingCurrentAssembly() {
        SelectionProjectionAssembler assembler = new SelectionProjectionAssembler();
        assembler.accept(chunk(3, 0, 2, new int[] {42}, 0, 0));

        SelectionProjectionChunk mismatch = new SelectionProjectionChunk(
                3, 1, 2, 1, 0, 0, 2, 1, 1, null, new int[] {1, 0}, List.of());
        SelectionProjectionAssembler.AssemblyResult rejected = assembler.accept(mismatch);
        SelectionProjectionAssembler.AssemblyResult complete = assembler.accept(chunk(3, 1, 2, null, 1, 0));

        assertEquals(SelectionProjectionAssembler.ResultType.REJECTED, rejected.type());
        assertEquals(SelectionProjectionAssembler.ResultType.COMPLETE, complete.type());
    }

    @Test
    void ignoresOlderGenerationAndAcceptsCurrentClear() {
        SelectionProjectionAssembler assembler = new SelectionProjectionAssembler();
        assembler.accept(chunk(4, 0, 2, new int[] {42}, 0, 0));

        SelectionProjectionAssembler.AssemblyResult stale = assembler.accept(clear(3));
        SelectionProjectionAssembler.AssemblyResult cleared = assembler.accept(clear(4));

        assertEquals(SelectionProjectionAssembler.ResultType.IGNORED, stale.type());
        assertNull(stale.snapshot());
        assertEquals(SelectionProjectionAssembler.ResultType.CLEARED, cleared.type());
        assertEquals(SelectionProjectionAssembler.ResultType.IGNORED,
                assembler.accept(chunk(4, 0, 1, new int[] {42}, 0, 0)).type());
    }

    @Test
    void rejectsPaletteIndexesAndCellsThatOnlyBecomeInvalidAfterAssembly() {
        SelectionProjectionAssembler badPalette = new SelectionProjectionAssembler();
        badPalette.accept(chunk(8, 1, 2, null, 1, 1));
        assertEquals(SelectionProjectionAssembler.ResultType.REJECTED,
                badPalette.accept(chunk(8, 0, 2, new int[] {42}, 0, 0)).type());

        SelectionProjectionAssembler duplicateCell = new SelectionProjectionAssembler();
        duplicateCell.accept(chunk(9, 1, 2, null, 0, 0));
        assertEquals(SelectionProjectionAssembler.ResultType.REJECTED,
                duplicateCell.accept(chunk(9, 0, 2, new int[] {42}, 0, 0)).type());
    }

    private static SelectionProjectionChunk chunk(int generation, int index, int count,
                                                   int[] palette, int cell, int paletteIndex) {
        return chunk(generation, index, count, palette, cell, paletteIndex, List.of());
    }

    private static SelectionProjectionChunk chunk(int generation, int index, int count,
                                                   int[] palette, int cell, int paletteIndex,
                                                   List<SelectionBlockEntityData> blockEntities) {
        return new SelectionProjectionChunk(
                generation, index, count, 0, 0, 0, 2, 1, 1,
                palette, new int[] {cell, paletteIndex}, blockEntities);
    }

    private static SelectionProjectionChunk clear(int generation) {
        return new SelectionProjectionChunk(
                generation, 0, 0, 0, 0, 0, 0, 0, 0, null, new int[0], List.of());
    }
}
