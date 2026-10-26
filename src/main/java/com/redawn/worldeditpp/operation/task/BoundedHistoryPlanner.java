package com.redawn.worldeditpp.operation.task;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Owns position arrays from allocation through consumption, including cancelled workers. */
public final class BoundedHistoryPlanner implements AutoCloseable {
    @FunctionalInterface
    interface Sorter { void sort(long[] positions, BooleanSupplier cancelled); }
    private final long limit;
    private final ThreadPoolExecutor executor;
    private final Sorter sorter;
    private final Set<Plan> plans = new HashSet<>();
    private long reservedBytes, peakBytes;
    private boolean closed;

    public BoundedHistoryPlanner(long limit, int workers) {
        this(limit, workers, ChunkPositionOrder::sort);
    }

    BoundedHistoryPlanner(long limit, int workers, Sorter sorter) {
        if (limit <= 0 || workers <= 0) throw new IllegalArgumentException();
        this.limit = limit;
        this.sorter = sorter;
        executor = new ThreadPoolExecutor(workers, workers, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2), runnable -> {
                    Thread thread = new Thread(runnable, "worldeditpp-history-plan");
                    thread.setDaemon(true);
                    thread.setPriority(Thread.NORM_PRIORITY - 1);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
    }

    /** Budget counts primitive payload bytes; array headers and small handles are additional. */
    public synchronized Plan tryAcquire(int count) {
        if (count <= 0) throw new IllegalArgumentException("Empty position plan");
        long bytes = Math.multiplyExact((long) count, Long.BYTES);
        if (closed || bytes > limit - reservedBytes) return null;
        reservedBytes += bytes;
        try {
            Plan plan = new Plan(new long[count], bytes);
            plans.add(plan);
            peakBytes = Math.max(peakBytes, reservedBytes);
            return plan;
        } catch (RuntimeException | Error failure) {
            reservedBytes -= bytes;
            throw failure;
        }
    }

    public synchronized Stats stats() {
        return new Stats(reservedBytes, peakBytes, plans.size(), executor.getQueue().size(),
                (int) plans.stream().filter(plan -> plan.running).count());
    }
    public record Stats(long reservedBytes, long peakBytes, int plans, int queued, int running) {}

    /** Used at server stop; this planner remains usable by a later integrated server. */
    public synchronized void cancelAll() {
        for (Plan plan : Set.copyOf(plans)) plan.close();
    }

    @Override public synchronized void close() {
        closed = true;
        cancelAll();
        executor.shutdown();
    }

    public final class Plan implements AutoCloseable, Runnable {
        private long[] positions;
        private final long bytes;
        private volatile boolean cancelled;
        private boolean submitted, running, done, released;
        private Throwable failure;

        private Plan(long[] positions, long bytes) { this.positions = positions; this.bytes = bytes; }

        /** Caller may fill before submit, or read after completion; never during sorting. */
        public long[] positions() {
            synchronized (BoundedHistoryPlanner.this) {
                if (cancelled) throw new CancellationException();
                if (submitted && !done) throw new IllegalStateException("Sort still running");
                if (failure != null) throw new IllegalStateException("History sort failed", failure);
                return positions;
            }
        }

        public void submit() {
            synchronized (BoundedHistoryPlanner.this) {
                if (cancelled || submitted) throw new IllegalStateException("Plan already submitted or closed");
                submitted = true;
                try { executor.execute(this); }
                catch (java.util.concurrent.RejectedExecutionException rejected) {
                    // Preserve original order without main-thread sorting when workers are saturated.
                    done = true;
                }
            }
        }

        public boolean isDone() { synchronized (BoundedHistoryPlanner.this) { return done; } }

        @Override public void run() {
            long[] working;
            synchronized (BoundedHistoryPlanner.this) {
                if (cancelled) { release(); return; }
                running = true;
                working = positions;
            }
            try { sorter.sort(working, () -> cancelled); }
            catch (Throwable exception) {
                synchronized (BoundedHistoryPlanner.this) { failure = exception; }
            } finally {
                working = null;
                synchronized (BoundedHistoryPlanner.this) {
                    running = false;
                    done = true;
                    if (cancelled) release();
                }
            }
        }

        /** Consumers must first discard their array references; running sorts release on exit. */
        @Override public void close() {
            synchronized (BoundedHistoryPlanner.this) {
                cancelled = true;
                executor.remove(this);
                if (!running) release();
            }
        }

        private void release() {
            if (released) return;
            positions = null;
            released = true;
            reservedBytes -= bytes;
            plans.remove(this);
        }
    }
}
