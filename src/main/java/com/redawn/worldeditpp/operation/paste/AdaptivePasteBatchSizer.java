package com.redawn.worldeditpp.operation.paste;

/** Adjusts a flushed paste batch from its complete enqueue-and-flush cost. */
public final class AdaptivePasteBatchSizer {
    public static final int MIN_BATCH_CELLS = 64;
    public static final int INITIAL_BATCH_CELLS = 1024;
    public static final int MAX_BATCH_CELLS = 32768;
    private static final double TARGET_BUDGET_FRACTION = 0.85;
    private static final double ESTIMATE_NEW_WEIGHT = 0.4;

    private int batchCells = INITIAL_BATCH_CELLS;
    private double nanosPerCellEstimate;

    public int nextBatchLimit(long remainingCells, long budgetNanos) {
        if (remainingCells <= 0) return 0;
        int budgetAdjustedCells = batchCells;
        if (nanosPerCellEstimate > 0.0 && budgetNanos > 0) {
            long measuredTarget = measuredTarget(budgetNanos);
            budgetAdjustedCells = (int) Math.max(
                    MIN_BATCH_CELLS, Math.min(batchCells, measuredTarget));
        }
        return (int) Math.min(remainingCells, budgetAdjustedCells);
    }

    public void recordBatch(int processedCells, long elapsedNanos, long budgetNanos) {
        if (processedCells <= 0 || elapsedNanos <= 0 || budgetNanos <= 0) return;
        double observedNanosPerCell = (double) elapsedNanos / processedCells;
        nanosPerCellEstimate = nanosPerCellEstimate == 0.0
                ? observedNanosPerCell
                : nanosPerCellEstimate * (1.0 - ESTIMATE_NEW_WEIGHT)
                        + observedNanosPerCell * ESTIMATE_NEW_WEIGHT;

        long measuredTarget = measuredTarget(budgetNanos);
        if (elapsedNanos > budgetNanos) {
            long immediateTarget = (long) (budgetNanos * TARGET_BUDGET_FRACTION
                    * processedCells / elapsedNanos);
            measuredTarget = Math.min(measuredTarget, immediateTarget);
        }
        int upperStep = Math.min(MAX_BATCH_CELLS, batchCells * 2);
        batchCells = (int) Math.max(MIN_BATCH_CELLS, Math.min(upperStep, measuredTarget));
    }

    public int batchCells() {
        return batchCells;
    }

    private long measuredTarget(long budgetNanos) {
        return (long) (budgetNanos * TARGET_BUDGET_FRACTION / nanosPerCellEstimate);
    }
}
