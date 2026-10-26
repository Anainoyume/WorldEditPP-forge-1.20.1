package dev.worldeditpp.testing;

import com.redawn.worldeditpp.bridge.worldedit.WorldEditBridge;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditPlatform;
import com.redawn.worldeditpp.bridge.worldedit.history.HistoryEngine;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.world.block.BlockTypes;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Isolated real-client world-write profile. No profiler branches in production code. */
@Mod.EventBusSubscriber(modid = TestHarnessMod.ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class HistoryClientProfile {
    // Large enough to cross the indexed/chunk-order path, but bounded so fixture creation
    // itself cannot consume the client smoke watchdog on an integrated server.
    private static final int COUNT = 64 * 64 * 64;
    private static final BlockPos ORIGIN = new BlockPos(0, 80, 0);
    private static final List<String> tickRows = new ArrayList<>();
    private static final List<String> rows = new ArrayList<>();
    private static final List<Long> frameTimes = new ArrayList<>();
    private static final Map<String, long[]> stages = new TreeMap<>();
    private static final List<long[]> windows = new ArrayList<>();
    private static volatile boolean initialized, fixtureReady, active, operationDone, finished;
    private static volatile Throwable failure;
    private static UUID owner;
    private static LocalSession session;
    private static EditSession edit;
    private static int fixtureCursor, operation, ticks, previousCursor, applied;
    private static long start, tickStart, frameStart, heapPeak, processPeak, tickCpuStart, readyAt;
    private static boolean sawTask;
    private static String stage;
    private static Object queues;
    private static Field cursorField, stateField, failureField;
    private static Recording recording;
    private static CompletableFuture<Void> setup;
    private static CompletableFuture<Void> fixture;

    public static boolean tick(Minecraft mc) throws Exception {
        if (failure != null) throw new AssertionError("History profile failed", failure);
        if (!initialized) {
            initialized = true;
            // Resolve against report path: game working directory differs from project root.
            owner = mc.player.getUUID();
            setup = mc.getSingleplayerServer().submit(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(owner);
                player.teleportTo(32, 150, 32);
                player.getAbilities().flying = true; player.onUpdateAbilities();
                session = WorldEditBridge.worldEdit().getSessionManager().get(WorldEditPlatform.adaptPlayer(player));
                session.clearHistory();
                edit = WorldEditBridge.newEditSession(WorldEditPlatform.adapt(player.serverLevel()));
                edit.disableBuffering();
                edit.setSideEffectApplier(com.sk89q.worldedit.util.SideEffectSet.none().with(
                        com.sk89q.worldedit.util.SideEffect.LIGHTING, com.sk89q.worldedit.util.SideEffect.State.DELAYED));
            });
            return false;
        }
        if (!fixtureReady) {
            if (setup == null || !setup.isDone()) return false;
            setup.join();
            if (fixture == null) {
                fixture = mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(owner);
                    int end = Math.min(COUNT, fixtureCursor + 8192);
                    try {
                        while (fixtureCursor < end)
                            edit.setBlock(WorldEditPlatform.adapt(position(fixtureCursor++)), BlockTypes.STONE.getDefaultState());
                    } catch (com.sk89q.worldedit.WorldEditException exception) {
                        throw new IllegalStateException(exception);
                    }
                    if (fixtureCursor == COUNT) {
                        edit.close(); session.remember(edit); edit = null;
                        verify(player, false);
                    }
                });
                return false;
            }
            if (!fixture.isDone()) return false;
            fixture.join();
            fixture = null;
            if (fixtureCursor < COUNT) return false;
            fixtureReady = true;
            readyAt = System.nanoTime();
            com.redawn.worldeditpp.WorldEditPlusPlus.LOGGER.info("History profile fixture ready: {} blocks", COUNT);
            return false;
        }
        if (System.nanoTime() - readyAt < 1_000_000_000L) return false;
        if (recording == null) {
            recording = new Recording(Configuration.getConfiguration("profile"));
            recording.setMaxSize(128L * 1024 * 1024);
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(2));
            recording.enable("jdk.ObjectAllocationSample").with("throttle", "300/s");
            recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(2));
            recording.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(5));
            recording.enable("jdk.FileRead").withThreshold(Duration.ofMillis(1));
            recording.enable("jdk.FileWrite").withThreshold(Duration.ofMillis(1));
            recording.start();
            tickRows.add("operation,tick,stateAtStart,stateAtEnd,cursor,tickWallMs,tickCpuMs,heapUsedBytes,epochMillis");
            rows.add("operation,blocks,wallMs,serverTicks,serverTickMs,applied,heapPeakBytes,processCommittedPeakBytes");
        }
        if (!active && !operationDone) {
            start = System.nanoTime(); ticks = applied = previousCursor = 0; sawTask = false;
            heapPeak = processPeak = 0; stages.clear();
            windows.add(new long[]{System.currentTimeMillis(), 0});
            active = true;
            mc.getConnection().sendCommand(operation % 2 == 0 ? "undo" : "redo");
            com.redawn.worldeditpp.WorldEditPlusPlus.LOGGER.info("History profile start operation {}", operation);
        }
        if (operationDone) {
            // Require actual client chunk synchronization at one position per section.
            for (int x = 8; x < 64; x += 16) for (int y = 8; y < 64; y += 16) for (int z = 8; z < 64; z += 16) {
                var block = mc.level.getBlockState(ORIGIN.offset(x, y, z));
                if (operation % 2 == 0 ? !block.isAir() : !block.is(net.minecraft.world.level.block.Blocks.STONE)) {
                    if (System.nanoTime() - start > 60_000_000_000L) throw new AssertionError("Client synchronization timed out");
                    return false;
                }
            }
            rows.add("client-observed,operation=" + operation + ",wallMs=" + (System.nanoTime()-start)/1e6);
            operationDone = false;
            readyAt = System.nanoTime();
            if (++operation == 4) {
                finished = true;
                recording.stop();
                Path directory = Path.of(System.getProperty("worldeditpp.test.report")).toAbsolutePath().getParent();
                String label = System.getProperty("worldeditpp.test.historyProfileLabel", "");
                if (!label.matches("[A-Za-z0-9_-]*")) throw new IllegalArgumentException("Invalid profile label");
                if (!label.isEmpty()) directory = directory.resolve(label);
                Files.createDirectories(directory);
                Path jfr = directory.resolve("history-world-profile.jfr");
                recording.dump(jfr); recording.close();
                Files.write(directory.resolve("history-world-timings.csv"), rows);
                Files.write(directory.resolve("history-world-ticks.csv"), tickRows);
                Files.write(directory.resolve("history-world-windows.csv"), windows.stream()
                        .map(window -> window[0] + "," + window[1]).toList());
                analyze(jfr, directory);
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void server(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("worldeditpp.test.historyProfile") || !initialized || finished || failure != null) return;
        try {
            if (setup == null || !setup.isDone()) return;
            setup.join();
            var player = event.getServer().getPlayerList().getPlayer(owner);
            if (!fixtureReady) return;
            if (!active) return;
            if (event.phase == TickEvent.Phase.START) {
                tickStart = System.nanoTime();
                tickCpuStart = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
                Object task = task();
                stage = task == null ? "network-dispatch" : (String) stateField.get(task);
                return;
            }
            if (tickStart == 0) return;
            long elapsed = System.nanoTime() - tickStart; tickStart = 0; ticks++;
            long cpu = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - tickCpuStart;
            long[] stats = stages.computeIfAbsent(stage, ignored -> new long[3]);
            stats[0]++; stats[1] += elapsed; stats[2] = Math.max(stats[2], elapsed);
            heapPeak = Math.max(heapPeak, ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
            processPeak = Math.max(processPeak, ((com.sun.management.OperatingSystemMXBean)
                    ManagementFactory.getOperatingSystemMXBean()).getCommittedVirtualMemorySize());
            Object task = task();
            tickRows.add(String.format(Locale.ROOT, "%d,%d,%s,%s,%d,%.3f,%.3f,%d,%d", operation, ticks, stage,
                    task == null ? "none" : stateField.get(task), task == null ? COUNT : cursorField.getInt(task),
                    elapsed/1e6, cpu/1e6, ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(),
                    System.currentTimeMillis()));
            if (task != null) {
                sawTask = true;
                if (failureField.get(task) != null) throw new AssertionError(failureField.get(task));
                int cursor = cursorField.getInt(task);
                applied += Math.max(0, cursor - previousCursor); previousCursor = cursor;
            } else if (sawTask) {
                applied += COUNT - previousCursor;
                long end = System.nanoTime(); windows.get(windows.size() - 1)[1] = System.currentTimeMillis();
                long tickNanos = stages.values().stream().mapToLong(value -> value[1]).sum();
                rows.add(String.format(Locale.ROOT, "%s-%d,%d,%.3f,%d,%.3f,%d,%d,%d",
                        operation % 2 == 0 ? "undo" : "redo", operation / 2 + 1, COUNT,
                        (end - start) / 1e6, ticks, tickNanos / 1e6, applied, heapPeak, processPeak));
                stages.forEach((name, value) -> rows.add(String.format(Locale.ROOT,
                        "stage,%s,ticks=%d,totalTickMs=%.3f,maxTickMs=%.3f", name, value[0], value[1]/1e6, value[2]/1e6)));
                verify(player, operation % 2 == 0);
                operationDone = true;
                active = false;
                com.redawn.worldeditpp.WorldEditPlusPlus.LOGGER.info("History profile completed operation {} in {} ms", operation, (end-start)/1e6);
            } else if (System.nanoTime() - start > 10_000_000_000L) {
                throw new AssertionError("Network history command never started");
            }
        } catch (Throwable exception) { failure = exception; }
    }

    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        if (!Boolean.getBoolean("worldeditpp.test.historyProfile") || !active) return;
        if (event.phase == TickEvent.Phase.START) frameStart = System.nanoTime();
        else if (frameStart != 0) { frameTimes.add(System.nanoTime() - frameStart); frameStart = 0; }
    }

    private static Object task() throws Exception {
        if (queues == null) {
            Field field = HistoryEngine.class.getDeclaredField("ACTIVE"); field.setAccessible(true); queues = field.get(null);
        }
        Object task = ((java.util.Map<?, ?>) queues).get(owner);
        if (task != null && cursorField == null) {
            cursorField = field(task, "cursor"); stateField = field(task, "state"); failureField = field(task, "failure");
        }
        return task;
    }
    private static Field field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static BlockPos position(int i) { return ORIGIN.offset(i & 63, i >>> 12, i >>> 6 & 63); }
    private static void verify(ServerPlayer player, boolean air) {
        for (int i = 0; i < COUNT; i++) {
            var state = player.serverLevel().getBlockState(position(i));
            if (air ? !state.isAir() : !state.is(net.minecraft.world.level.block.Blocks.STONE))
                throw new AssertionError("World contents mismatch at " + position(i));
        }
    }

    private static void analyze(Path jfr, Path directory) throws Exception {
        Map<String, Integer> inclusive = new HashMap<>(), leaf = new HashMap<>(), threads = new HashMap<>();
        Map<String, Long> allocations = new HashMap<>();
        long samples = 0, serverSamples = 0, gcCount = 0; double gcMs = 0;
        try (var file = new RecordingFile(jfr)) {
            while (file.hasMoreEvents()) {
                var event = file.readEvent(); long time = event.getStartTime().toEpochMilli();
                if (windows.stream().noneMatch(window -> time >= window[0] && time <= window[1])) continue;
                String type = event.getEventType().getName();
                if (type.equals("jdk.GarbageCollection")) { gcCount++; gcMs += event.getDuration().toNanos()/1e6; }
                if (type.equals("jdk.ObjectAllocationSample")) {
                    allocations.merge(event.getClass("objectClass").getName(), event.getLong("weight"), Long::sum);
                }
                if (!type.equals("jdk.ExecutionSample")) continue;
                samples++;
                var thread = event.getThread("sampledThread");
                String threadName = thread == null ? "unknown" : thread.getJavaName();
                threads.merge(threadName, 1, Integer::sum);
                if (!threadName.equals("Server thread") || event.getStackTrace() == null) continue;
                serverSamples++;
                Set<String> seen = new HashSet<>(); boolean first = true;
                for (var frame : event.getStackTrace().getFrames()) {
                    var method = frame.getMethod(); String key = method.getType().getName() + "." + method.getName();
                    if (first) { leaf.merge(key, 1, Integer::sum); first = false; }
                    if (seen.add(key)) inclusive.merge(key, 1, Integer::sum);
                }
            }
        }
        var text = new StringBuilder("Real client / integrated server; 262144 stone blocks, two undo/redo cycles\n");
        text.append("Execution samples=").append(samples).append(" server=").append(serverSamples)
                .append(" GC events=").append(gcCount).append(" GC durationMs=").append(gcMs).append(System.lineSeparator());
        append(text, "Threads (samples)", threads); append(text, "Server inclusive (overlapping samples)", inclusive);
        append(text, "Server leaf (samples)", leaf); append(text, "Allocation estimated weights, all threads (bytes)", allocations);
        Collections.sort(frameTimes);
        if (!frameTimes.isEmpty()) text.append("Render frame ms p50=").append(frameTimes.get(frameTimes.size()/2)/1e6)
                .append(" p95=").append(frameTimes.get((int)(frameTimes.size()*.95))/1e6)
                .append(" max=").append(frameTimes.get(frameTimes.size()-1)/1e6).append(System.lineSeparator());
        text.append("Stage timing is whole server tick grouped by state at tick START, not exclusive method timing.\n")
                .append("JFR samples are statistical; inclusive entries overlap. Allocation weights are estimates.\n");
        Files.writeString(directory.resolve("history-world-profile.txt"), text);
    }
    private static void append(StringBuilder text, String title, Map<String, ? extends Number> values) {
        text.append(System.lineSeparator()).append(title).append(System.lineSeparator());
        values.entrySet().stream().sorted((a,b) -> Long.compare(b.getValue().longValue(), a.getValue().longValue()))
                .limit(60).forEach(entry -> text.append(entry.getValue()).append(' ').append(entry.getKey()).append(System.lineSeparator()));
    }
}
