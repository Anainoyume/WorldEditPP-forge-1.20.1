package com.redawn.worldeditpp.operation.paste;

import com.redawn.worldeditpp.operation.paste.AdaptivePasteBatchSizer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdaptivePasteBatchSizerTest {
    @Test
    void startsConservativelyAndGrowsAtMostTwofold() {
        AdaptivePasteBatchSizer sizer = new AdaptivePasteBatchSizer();
        assertEquals(AdaptivePasteBatchSizer.INITIAL_BATCH_CELLS,
                sizer.nextBatchLimit(Long.MAX_VALUE, 10_000_000L));

        sizer.recordBatch(1024, 1_000_000L, 10_000_000L);

        assertEquals(2048, sizer.batchCells());
    }

    @Test
    void shrinksAfterAnOverBudgetFlush() {
        AdaptivePasteBatchSizer sizer = new AdaptivePasteBatchSizer();
        sizer.recordBatch(1024, 40_000_000L, 10_000_000L);

        assertEquals(217, sizer.batchCells());
    }

    @Test
    void immediatelyScalesDownWhenTheAvailableTickBudgetDrops() {
        AdaptivePasteBatchSizer sizer = new AdaptivePasteBatchSizer();
        sizer.recordBatch(1024, 5_000_000L, 10_000_000L);

        int normalLimit = sizer.nextBatchLimit(Long.MAX_VALUE, 10_000_000L);
        int busyLimit = sizer.nextBatchLimit(Long.MAX_VALUE, 2_000_000L);

        assertTrue(busyLimit < normalLimit);
        assertTrue(busyLimit >= AdaptivePasteBatchSizer.MIN_BATCH_CELLS);
    }

    @Test
    void respectsMinimumMaximumAndRemainingCells() {
        AdaptivePasteBatchSizer slowSizer = new AdaptivePasteBatchSizer();
        for (int i = 0; i < 10; i++) {
            slowSizer.recordBatch(Math.max(1, slowSizer.batchCells()), 1_000_000_000L, 1L);
        }
        assertEquals(AdaptivePasteBatchSizer.MIN_BATCH_CELLS, slowSizer.batchCells());

        AdaptivePasteBatchSizer fastSizer = new AdaptivePasteBatchSizer();
        for (int i = 0; i < 10; i++) {
            fastSizer.recordBatch(fastSizer.batchCells(), 1L, 1_000_000_000L);
        }
        assertEquals(AdaptivePasteBatchSizer.MAX_BATCH_CELLS, fastSizer.batchCells());
        assertEquals(17, fastSizer.nextBatchLimit(17, 1_000_000_000L));
        assertTrue(fastSizer.nextBatchLimit(0, 1_000_000_000L) == 0);
    }
}
