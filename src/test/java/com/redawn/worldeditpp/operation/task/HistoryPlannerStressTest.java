package com.redawn.worldeditpp.operation.task;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

@Tag("history-stress")
class HistoryPlannerStressTest {
    private static final long LIMIT = 64L * 1024 * 1024;
    @Test @Timeout(180) void concurrentLargePlansAndCancellationStayBounded() throws Exception {
        var memory = ManagementFactory.getMemoryMXBean();
        var peak = new AtomicLong();
        var sampling = new AtomicBoolean(true);
        var gc = ManagementFactory.getGarbageCollectorMXBeans();
        long gcBefore = gc.stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum();
        long heapBefore = memory.getHeapMemoryUsage().getUsed();
        var report = new StringBuilder("Production planner stress; primitive sorting, not world mutation throughput\n");
        report.append("maxHeapBytes=").append(memory.getHeapMemoryUsage().getMax()).append(System.lineSeparator());
        var sampler = new Thread(() -> {
            while (sampling.get()) {
                peak.accumulateAndGet(memory.getHeapMemoryUsage().getUsed(), Math::max);
                try { Thread.sleep(2); } catch (InterruptedException e) { return; }
            }
        }, "history-stress-memory-sampler");
        sampler.setDaemon(true); sampler.start();
        try (var planner = new BoundedHistoryPlanner(LIMIT, 2)) {
            for (int count : new int[]{1_048_576, 4_194_304, 4_194_304, 4_194_304}) {
                long start = System.nanoTime();
                List<BoundedHistoryPlanner.Plan> plans = new ArrayList<>();
                long[] sums = new long[2], xors = new long[2];
                for (int p = 0; p < 2; p++) {
                    var plan = planner.tryAcquire(count); assertNotNull(plan); plans.add(plan);
                    long[] values = plan.positions();
                    for (int i = 0; i < count; i++) {
                        int n = (i * 1_664_525 + 1_013_904_223) & (count - 1);
                        long value = ChunkPositionOrder.pack((n & 2047) - 1024,
                                (n >>> 11 & 255) - 64, (n >>> 19) - 4);
                        values[i] = value; sums[p] += value; xors[p] ^= value;
                    }
                    plan.submit();
                }
                long filled = System.nanoTime();
                if (count == 4_194_304) {
                    for (int i = 0; i < 256; i++) assertNull(planner.tryAcquire(count));
                }
                for (var plan : plans) BoundedHistoryPlannerTest.await(plan::isDone);
                long sorted = System.nanoTime();
                for (int p = 0; p < plans.size(); p++) {
                    long[] values = plans.get(p).positions();
                    long sum = 0, xor = 0;
                    for (int i = 0; i < values.length; i++) {
                        if (i > 0 && ChunkPositionOrder.compare(values[i - 1], values[i]) > 0) fail("Not sorted");
                        sum += values[i]; xor ^= values[i];
                    }
                    assertEquals(sums[p], sum); assertEquals(xors[p], xor);
                }
                assertTrue(planner.stats().reservedBytes() <= LIMIT);
                report.append(String.format(java.util.Locale.ROOT,
                        "twoPlans countEach=%d fillAndSubmitMs=%.3f readyMs=%.3f reservedBytes=%d heapUsedBytes=%d%n",
                        count, (filled - start) / 1e6, (sorted - start) / 1e6,
                        planner.stats().reservedBytes(), memory.getHeapMemoryUsage().getUsed()));
                plans.forEach(BoundedHistoryPlanner.Plan::close); plans.clear();
                assertEquals(0, planner.stats().reservedBytes());
            }
            for (int round = 0; round < 10; round++) {
                var plan = planner.tryAcquire(4_194_304); assertNotNull(plan);
                long[] values = plan.positions();
                for (int i = 0; i < values.length; i++) values[i] = ChunkPositionOrder.pack(i % 1024, i % 256, i / 1024);
                values = null; plan.submit();
                BoundedHistoryPlannerTest.await(() -> planner.stats().running() > 0 || plan.isDone());
                assertFalse(plan.isDone(), "Cancellation workload finished before cancellation");
                long start = System.nanoTime(); plan.close();
                BoundedHistoryPlannerTest.await(() -> planner.stats().reservedBytes() == 0);
                report.append(String.format(java.util.Locale.ROOT, "cancel round=%d releaseMs=%.3f%n", round, (System.nanoTime() - start) / 1e6));
            }
            assertEquals(0, planner.stats().plans());
            assertEquals(0, planner.stats().queued());
            assertEquals(0, planner.stats().running());
            assertTrue(planner.stats().peakBytes() <= LIMIT);
            report.append("peakReservedBytes=").append(planner.stats().peakBytes()).append(System.lineSeparator());
            report.append("finalReservedBytes=").append(planner.stats().reservedBytes()).append(System.lineSeparator());
        } finally { sampling.set(false); sampler.join(); }
        long gcAfter = gc.stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum();
        report.append("heapBeforeBytes=").append(heapBefore).append(System.lineSeparator());
        report.append("sampledPeakHeapBytes=").append(peak.get()).append(System.lineSeparator());
        report.append("heapAfterBytes=").append(memory.getHeapMemoryUsage().getUsed()).append(System.lineSeparator());
        report.append("gcCollectionMs=").append(gcAfter - gcBefore).append(System.lineSeparator());
        Path path = Path.of("build/test-results/historyPlannerStress/performance.txt");
        Files.createDirectories(path.getParent()); Files.writeString(path, report);
        System.out.print(report);
    }
}
