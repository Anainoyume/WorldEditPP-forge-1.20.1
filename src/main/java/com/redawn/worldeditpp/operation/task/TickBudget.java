package com.redawn.worldeditpp.operation.task;

import java.util.function.LongSupplier;

/** A cooperative limit on both work and elapsed time; one slow operation cannot be preempted. */
public final class TickBudget {
    private final LongSupplier clock;
    private final long deadline;
    private int remaining;

    public TickBudget(int units, long nanos) { this(units, nanos, System::nanoTime); }

    public TickBudget(int units, long nanos, LongSupplier clock) {
        if (units < 0 || nanos < 0) throw new IllegalArgumentException("Negative tick budget");
        this.remaining = units;
        this.clock = clock;
        this.deadline = clock.getAsLong() + nanos;
    }

    public boolean take() {
        if (remaining <= 0 || clock.getAsLong() - deadline >= 0) return false;
        remaining--;
        return true;
    }

    /** Time left before the cooperative deadline; never negative. */
    public long remainingNanos() {
        return Math.max(0L, deadline - clock.getAsLong());
    }
}
