package com.redawn.worldeditpp.operation.task;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class BoundedHistoryPlannerTest {
    @Test void accountsForCopyingAndCompletedConsumers() throws Exception {
        try (var planner = new BoundedHistoryPlanner(8192, 1)) {
            var plan = planner.tryAcquire(1024);
            assertNotNull(plan);
            assertNull(planner.tryAcquire(1));
            plan.positions()[0] = ChunkPositionOrder.pack(12, 0, 0);
            plan.submit();
            await(plan::isDone);
            assertEquals(8192, planner.stats().reservedBytes());
            assertNull(planner.tryAcquire(1));
            plan.close(); plan.close();
            assertEquals(0, planner.stats().reservedBytes());
            assertThrows(java.util.concurrent.CancellationException.class, plan::positions);
            var next = planner.tryAcquire(1024);
            assertNotNull(next);
            next.close();
        }
    }

    @Test void cancellationRemovesQueuedWorkButKeepsRunningBytesUntilExit() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var planner = new BoundedHistoryPlanner(32768, 1, (values, cancelled) -> {
            started.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new AssertionError(e); }
            assertTrue(cancelled.getAsBoolean());
        })) {
            var running = planner.tryAcquire(1024); running.submit();
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var queued = planner.tryAcquire(1024); queued.submit();
            assertEquals(1, planner.stats().queued());
            queued.close();
            assertEquals(0, planner.stats().queued());
            running.close();
            assertEquals(8192, planner.stats().reservedBytes());
            assertThrows(IllegalStateException.class, () -> queued.submit());
            release.countDown();
            await(() -> planner.stats().reservedBytes() == 0);
        } finally { release.countDown(); }
    }

    @Test void saturatedQueueFallsBackAndShutdownAllowsNextServer() throws Exception {
        var started = new CountDownLatch(1);
        try (var planner = new BoundedHistoryPlanner(5 * 8192, 1, (values, cancelled) -> {
            started.countDown();
            while (!cancelled.getAsBoolean()) Thread.onSpinWait();
        })) {
            var running = planner.tryAcquire(1024); running.submit();
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var queued1 = planner.tryAcquire(1024); queued1.submit();
            var queued2 = planner.tryAcquire(1024); queued2.submit();
            var fallback = planner.tryAcquire(1024);
            fallback.positions()[0] = 123; fallback.submit();
            assertTrue(fallback.isDone());
            assertEquals(123, fallback.positions()[0]);
            planner.cancelAll();
            await(() -> planner.stats().plans() == 0);
            assertEquals(0, planner.stats().queued());
            var nextServer = planner.tryAcquire(1024);
            assertNotNull(nextServer); nextServer.close();
        }
    }

    @Test void failedSortRetainsBudgetUntilOwnerCloses() throws Exception {
        try (var planner = new BoundedHistoryPlanner(8192, 1, (values, cancelled) -> {
            throw new IllegalArgumentException("injected sort failure");
        })) {
            var plan = planner.tryAcquire(1024); plan.submit();
            await(plan::isDone);
            assertThrows(IllegalStateException.class, plan::positions);
            assertEquals(8192, planner.stats().reservedBytes());
            plan.close();
            assertEquals(0, planner.stats().plans());
        }
    }

    static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail("Planner did not reach expected state");
            Thread.sleep(1);
        }
    }
}
