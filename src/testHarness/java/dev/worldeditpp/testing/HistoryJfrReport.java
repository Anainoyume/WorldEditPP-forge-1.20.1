package dev.worldeditpp.testing;

import java.nio.file.*;
import java.util.*;
import jdk.jfr.consumer.*;

/** Offline, bounded-memory report from the real client recording. Run with Java 17 source mode. */
public final class HistoryJfrReport {
    private static final class Stats {
        final Map<String, Long> categories = new TreeMap<>(), history = new TreeMap<>(), methods = new HashMap<>();
        final Map<String, Long> alloc = new HashMap<>(), allocSite = new HashMap<>(), allocThreads = new HashMap<>();
        final Map<String, Long> worker = new TreeMap<>();
        final Map<String, Double> blocking = new HashMap<>();
        long server, total, historyCount, allocBytes, gcCount, pauseCount, fileBytes;
        double gcMs, pauseMs, pauseMax, fileMs;
    }
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        var windows = Files.readAllLines(dir.resolve("history-world-windows.csv")).stream()
                .map(line -> Arrays.stream(line.split(",")).mapToLong(Long::parseLong).toArray()).toList();
        Stats[] operations = new Stats[windows.size()]; Arrays.setAll(operations, i -> new Stats());
        Stats combined = new Stats();
        try (var recording = new RecordingFile(dir.resolve("history-world-profile.jfr"))) {
            while (recording.hasMoreEvents()) {
                var event = recording.readEvent();
                long time = event.getStartTime().toEpochMilli();
                for (int i = 0; i < windows.size(); i++) if (time >= windows.get(i)[0] && time <= windows.get(i)[1]) {
                    accept(operations[i], event); accept(combined, event); break;
                }
            }
        }
        var text = new StringBuilder("# Real-client JFR attribution\n\n");
        text.append("Samples are statistical, not exact method timers. Exclusive categories partition sampled stacks; inclusive methods overlap.\n")
                .append("Allocation weights estimate cumulative allocated bytes, not retained heap. GC cycle duration is not equivalent to pause time.\n")
                .append("Filtering uses command-send to server-completion windows; fixture and validation are outside those windows (millisecond boundary precision).\n\n");
        output(text, "Combined", combined);
        for (int i = 0; i < operations.length; i++) output(text, (i % 2 == 0 ? "Undo " : "Redo ") + (i/2+1), operations[i]);
        text.append("\n## Tick details\n");
        var lines = Files.readAllLines(dir.resolve("history-world-ticks.csv"));
        for (int op = 0; op < windows.size(); op++) {
            List<Double> wall = new ArrayList<>(); double cpu = 0; long firstWrite = 0, heap = 0;
            for (String line : lines.subList(1, lines.size())) {
                String[] fields = line.split(","); if (Integer.parseInt(fields[0]) != op) continue;
                wall.add(Double.parseDouble(fields[5])); cpu += Double.parseDouble(fields[6]);
                heap = Math.max(heap, Long.parseLong(fields[7]));
                if (firstWrite == 0 && fields[3].equals("running") && Integer.parseInt(fields[4]) > 0) firstWrite = Long.parseLong(fields[8]);
            }
            Collections.sort(wall);
            text.append(String.format(Locale.ROOT,
                    "op=%d firstWriteObservedMs=%d tickP50=%.3f tickP95=%.3f tickMax=%.3f tickCpuSumMs=%.3f sampledHeapPeakMiB=%.3f%n",
                    op, firstWrite-windows.get(op)[0], wall.get(wall.size()/2), wall.get((int)(wall.size()*.95)),
                    wall.get(wall.size()-1), cpu, heap/1048576.0));
        }
        Files.writeString(dir.resolve("history-world-attribution.md"), text);
        System.out.println(text);
    }
    private static List<String> stack(RecordedEvent event) {
        if (event.getStackTrace() == null) return List.of();
        return event.getStackTrace().getFrames().stream().map(frame ->
                frame.getMethod().getType().getName() + "." + frame.getMethod().getName()).toList();
    }
    private static boolean has(List<String> stack, String fragment) { return stack.stream().anyMatch(s -> s.contains(fragment)); }
    private static void add(Map<String, Long> values, String key, long value) { values.merge(key, value, Long::sum); }
    private static void accept(Stats s, RecordedEvent e) {
        String type = e.getEventType().getName(); List<String> stack = stack(e);
        if (type.equals("jdk.GarbageCollection")) { s.gcCount++; s.gcMs += e.getDuration().toNanos()/1e6; }
        if (type.equals("jdk.GCPhasePause")) {
            double ms = e.getDuration().toNanos()/1e6; s.pauseCount++; s.pauseMs += ms; s.pauseMax = Math.max(s.pauseMax, ms);
        }
        if (type.equals("jdk.FileRead") || type.equals("jdk.FileWrite")) {
            s.fileMs += e.getDuration().toNanos()/1e6;
            s.fileBytes += e.getLong(type.equals("jdk.FileRead") ? "bytesRead" : "bytesWritten");
        }
        if (type.equals("jdk.ThreadPark") || type.equals("jdk.JavaMonitorEnter") || type.equals("jdk.ThreadSleep")) {
            var t = e.getThread();
            if (t != null && t.getJavaName().equals("Server thread")) {
                String site = stack.stream().filter(f -> !f.startsWith("java.") && !f.startsWith("jdk.") && !f.startsWith("sun.")).findFirst().orElse("unknown");
                s.blocking.merge(type + " " + site, e.getDuration().toNanos()/1e6, Double::sum);
            }
        }
        if (type.equals("jdk.ObjectAllocationSample")) {
            long weight = e.getLong("weight"); s.allocBytes += weight;
            var t = e.getThread(); String thread = t == null ? "unknown" : t.getJavaName();
            add(s.allocThreads, thread, weight);
            if (thread.equals("Server thread") && has(stack, "HistoryTask")) {
                add(s.alloc, e.getClass("objectClass").getName(), weight);
                add(s.allocSite, stack.stream().filter(f -> f.startsWith("com.redawn.") || f.startsWith("com.sk89q.") || f.startsWith("net.minecraft."))
                        .findFirst().orElse("unknown"), weight);
            }
        }
        if (!type.equals("jdk.ExecutionSample")) return;
        s.total++; var t = e.getThread("sampledThread"); String thread = t == null ? "unknown" : t.getJavaName();
        if (!thread.equals("Server thread")) {
            String cat = has(stack, "ChunkRenderDispatcher") ? "client-chunk-mesh" : has(stack, "LightEngine") ? "lighting"
                    : thread.startsWith("worldeditpp-history-plan") ? "history-sort" : thread.equals("Render thread") ? "render-thread-other" : "other-workers";
            add(s.worker, cat, 1); return;
        }
        s.server++;
        String cat = has(stack, "HistoryJob.step") ? "history-operations" : has(stack, "NaturalSpawner") ? "natural-spawning"
                : has(stack, "broadcastChanges") ? "network-broadcast" : has(stack, "ServerChunkCache.tick") ? "other-chunk-tick" : "other-server";
        add(s.categories, cat, 1);
        if (!cat.equals("history-operations")) return;
        s.historyCount++;
        String part = has(stack, "HistoryTask.closeWriter") ? "finish-flush"
                : has(stack, "IndexedBlockHistory.originalBlockPosition") ? "snapshot-position-read"
                : has(stack, "IndexedBlockHistory.get") ? "history-index-read"
                : has(stack, "HistoryChunkBatch.clearContainer") ? "container-guard"
                : has(stack, "ChunkLease.ready") || has(stack, "HistoryChunkBatch.ready") ? "chunk-gate"
                : has(stack, "BlockChange.undo") || has(stack, "BlockChange.redo") ? "block-write-extent-chain"
                : has(stack, "TickBudget.take") ? "budget-check" : "other-history";
        add(s.history, part, 1);
        for (String method : new HashSet<>(stack))
            if (!method.contains("$$Lambda$") && (method.startsWith("net.minecraft") || method.startsWith("com.sk89q") || method.startsWith("com.redawn")))
                add(s.methods, method, 1);
    }
    private static void output(StringBuilder out, String name, Stats s) {
        out.append("\n## ").append(name).append(System.lineSeparator());
        out.append(String.format(Locale.ROOT, "totalSamples=%d serverSamples=%d historySamples=%d allocEstimatedMiB=%.2f gcCycles=%d gcCycleMs=%.3f pauses=%d pauseMs=%.3f maxPauseMs=%.3f slowFileBytes=%d slowFileMs=%.3f%n",
                s.total,s.server,s.historyCount,s.allocBytes/1048576.0,s.gcCount,s.gcMs,s.pauseCount,s.pauseMs,s.pauseMax,s.fileBytes,s.fileMs));
        print(out, "Server exclusive sample categories", s.categories, 30);
        print(out, "History exclusive sample categories", s.history, 30);
        print(out, "History-only inclusive methods", s.methods, 65);
        print(out, "Other thread exclusive samples", s.worker, 30);
        print(out, "History allocation class estimated bytes", s.alloc, 20);
        print(out, "History allocation first application frame estimated bytes", s.allocSite, 20);
        print(out, "All-thread allocation estimated bytes", s.allocThreads, 8);
        print(out, "Server blocked durations ms", s.blocking, 15);
    }
    private static void print(StringBuilder out, String title, Map<String, ? extends Number> map, int limit) {
        out.append("\n").append(title).append(System.lineSeparator());
        map.entrySet().stream().sorted((a,b) -> Double.compare(b.getValue().doubleValue(), a.getValue().doubleValue()))
                .limit(limit).forEach(e -> out.append(e.getValue()).append(" ").append(e.getKey()).append(System.lineSeparator()));
    }
}
