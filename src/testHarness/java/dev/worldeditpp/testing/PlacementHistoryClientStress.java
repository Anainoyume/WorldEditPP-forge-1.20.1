package dev.worldeditpp.testing;

import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.ClientEditModeController;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionController;
import com.redawn.worldeditpp.bridge.minecraft.network.ClipboardAdjustmentPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.ToggleEditModePayload;
import com.redawn.worldeditpp.bridge.minecraft.network.WandClickPayload;
import com.redawn.worldeditpp.bridge.worldedit.ClipboardAdjustmentService;
import com.redawn.worldeditpp.bridge.worldedit.history.HistoryEngine;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Isolated end-to-end stress: real client packets, incremental projection mesh, real world writes,
 * and the production /undo and /redo command path. Instrumentation never enters the release jar.
 */
@Mod.EventBusSubscriber(modid = TestHarnessMod.ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class PlacementHistoryClientStress {
    private static final int TILE_X = 120;
    private static final int SIZE_Y = 64;
    private static final int SIZE_Z = 64;
    private static final int FINAL_X = TILE_X * 4;
    private static final int BLOCKS = FINAL_X * SIZE_Y * SIZE_Z; // 1,966,080
    private static final BlockPos SOURCE = new BlockPos(0, 80, 0);
    private static final BlockPos TARGET = new BlockPos(0, 80, 128);
    private static final Map<String, StageMetric> METRICS = new LinkedHashMap<>();
    private static final List<String> TICKS = new ArrayList<>();
    private static final List<Window> WINDOWS = new ArrayList<>();
    private static volatile Throwable failure;
    private static volatile String activeStage = "idle";
    private static volatile boolean pastePresent, historyPresent;
    private static volatile int pasteCursor, historyCursor;
    private static int phase, ticks, stretch, waitTicks, historyRound = 1;
    private static boolean sawPaste, sawHistory;
    private static boolean finalStretchMeasured;
    private static long stageStartedNanos, stageStartedMillis, serverTickStarted, serverCpuStarted;
    private static UUID owner;
    private static CompletableFuture<?> future;
    private static Recording recording;
    private static Object historyQueues;
    private static Field historyCursorField;

    static boolean tick(Minecraft mc) throws Exception {
        if (failure != null) throw new AssertionError("Two-million-block stress failed", failure);
        if (++ticks > 24_000) throw new AssertionError("Stress phase timed out: " + phase);
        switch (phase) {
            case 0 -> {
                owner = mc.player.getUUID();
                PlatformBridge.sendToServer(new ToggleEditModePayload("stress"));
                next();
            }
            case 1 -> {
                if (!ClientEditModeController.isEditing()) return false;
                future = mc.getSingleplayerServer().submit(() -> createTemplate(mc));
                next();
            }
            case 2 -> {
                if (!future.isDone()) return false;
                future.join();
                if (Boolean.getBoolean("worldeditpp.test.buildingStress")) BuildingStressFixture.arm();
                PlatformBridge.sendToServer(new WandClickPayload(SOURCE.getX(), SOURCE.getY(), SOURCE.getZ(), true));
                BlockPos max = SOURCE.offset(TILE_X - 1, SIZE_Y - 1, SIZE_Z - 1);
                PlatformBridge.sendToServer(new WandClickPayload(max.getX(), max.getY(), max.getZ(), false));
                next();
            }
            case 3 -> {
                if (!SelectionClientController.hasCuboidSelection()) return false;
                SelectionClientController.requestSelectionAdjustment();
                next();
            }
            case 4 -> {
                var projection = SelectionBlockProjectionController.state();
                if (!projection.hasSnapshot() || !projection.isReady() || projection.sizeX() != TILE_X) return false;
                startRecording();
                startStretch();
                next();
            }
            case 5 -> {
                int expected = TILE_X * (stretch + 1);
                var projection = SelectionBlockProjectionController.state();
                if (!projection.isReady() || projection.sizeX() != expected) return false;
                if (!finalStretchMeasured) finishStage(BLOCKS / 4L * (stretch + 1), projectionMetrics(projection));
                if (stretch < 3) {
                    startStretch();
                } else {
                    finalStretchMeasured = true;
                    if (Boolean.getBoolean("worldeditpp.test.buildingStress") && !BuildingProjectionProbe.tick(mc, TARGET)) return false;
                    startStage("placement", BLOCKS);
                    sawPaste = false;
                    PlatformBridge.sendToServer(ClipboardAdjustmentPayload.confirm(
                            TARGET.getX(), TARGET.getY(), TARGET.getZ(), false));
                    next();
                }
            }
            case 6 -> {
                sawPaste |= pastePresent;
                if (!sawPaste || pastePresent) return false;
                finishStage(BLOCKS, "server_cursor=" + pasteCursor);
                future = verify(mc, true);
                next();
            }
            case 7 -> {
                if (!future.isDone()) return false;
                future.join();
                startHistory(mc, true);
                next();
            }
            case 8 -> {
                sawHistory |= historyPresent;
                if (!sawHistory || historyPresent) return false;
                finishStage(BLOCKS, "history_cursor=" + historyCursor);
                future = verify(mc, false);
                next();
            }
            case 9 -> {
                if (!future.isDone()) return false;
                future.join();
                startHistory(mc, false);
                next();
            }
            case 10 -> {
                sawHistory |= historyPresent;
                if (!sawHistory || historyPresent) return false;
                finishStage(BLOCKS, "history_cursor=" + historyCursor);
                future = verify(mc, true);
                next();
            }
            case 11 -> {
                if (!future.isDone()) return false;
                future.join();
                if (++waitTicks < 20) return false;
                if (Boolean.getBoolean("worldeditpp.test.buildingStress")) {
                    BuildingStressFixture.verifyNoDrops();
                    if (historyRound++ < 2) {
                        waitTicks = 0;
                        startHistory(mc, true);
                        phase = 8;
                        ticks = 0;
                        return false;
                    }
                }
                writeResults();
                if (Boolean.getBoolean("worldeditpp.test.buildingStress")) BuildingProjectionProbe.verify();
                return true;
            }
            default -> throw new AssertionError("Unknown stress phase " + phase);
        }
        return false;
    }

    private static void createTemplate(Minecraft mc) {
        ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(owner);
        if (player == null) throw new AssertionError("Stress player disappeared during setup");
        player.teleportTo(player.serverLevel(), SOURCE.getX() + TILE_X / 2.0,
                SOURCE.getY() + SIZE_Y + 4.0, SOURCE.getZ() - 8.0, 0, 45);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        var stone = Blocks.STONE.defaultBlockState();
        int minChunkX = SOURCE.getX() >> 4;
        int maxChunkX = SOURCE.offset(TILE_X - 1, 0, 0).getX() >> 4;
        int minChunkZ = SOURCE.getZ() >> 4;
        int maxChunkZ = SOURCE.offset(0, 0, SIZE_Z - 1).getZ() >> 4;
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                var chunk = player.serverLevel().getChunk(chunkX, chunkZ);
                int minX = Math.max(SOURCE.getX(), chunkX << 4);
                int maxX = Math.min(SOURCE.getX() + TILE_X - 1, (chunkX << 4) + 15);
                int minZ = Math.max(SOURCE.getZ(), chunkZ << 4);
                int maxZ = Math.min(SOURCE.getZ() + SIZE_Z - 1, (chunkZ << 4) + 15);
                for (int y = SOURCE.getY(); y < SOURCE.getY() + SIZE_Y; y++)
                    for (int z = minZ; z <= maxZ; z++)
                        for (int x = minX; x <= maxX; x++)
                            chunk.setBlockState(pos.set(x, y, z), Boolean.getBoolean("worldeditpp.test.buildingStress")
                                    ? BuildingStressFixture.state(x - SOURCE.getX(), y - SOURCE.getY(), z - SOURCE.getZ()) : stone, false);
                chunk.setUnsaved(true);
            }
        }
        if (Boolean.getBoolean("worldeditpp.test.buildingStress")) {
            BuildingStressFixture.populate(player.serverLevel(), SOURCE, TILE_X, SIZE_Y, SIZE_Z);
            BuildingStressFixture.populateOriginalTarget(player.serverLevel(), TARGET, FINAL_X, SIZE_Y, SIZE_Z);
            BuildingStressFixture.calibrate(player.serverLevel());
        }
    }

    private static void startStretch() {
        stretch++;
        startStage("stretch-" + stretch, BLOCKS / 4L * (stretch + 1));
        if (!SelectionBlockProjectionController.stretchFace(1, true))
            throw new AssertionError("Production projection rejected stretch " + stretch);
    }

    private static void startHistory(Minecraft mc, boolean undo) {
        sawHistory = false;
        startStage((undo ? "undo" : "redo") + (historyRound == 1 ? "" : "-" + historyRound), BLOCKS);
        mc.getConnection().sendCommand(undo ? "undo" : "redo");
    }

    private static CompletableFuture<?> verify(Minecraft mc, boolean stone) {
        return mc.getSingleplayerServer().submit(() -> {
            ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(owner);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int index = 0; index < BLOCKS; index++) {
                int x = index % FINAL_X;
                int yz = index / FINAL_X;
                int y = yz % SIZE_Y;
                int z = yz / SIZE_Y;
                var state = player.serverLevel().getBlockState(pos.set(
                        TARGET.getX() + x, TARGET.getY() + y, TARGET.getZ() + z));
                var expected = stone && Boolean.getBoolean("worldeditpp.test.buildingStress")
                        ? BuildingStressFixture.state(x % TILE_X, y, z)
                        : !stone && Boolean.getBoolean("worldeditpp.test.buildingStress")
                        ? BuildingStressFixture.originalTargetState(x, y, z)
                        : stone ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                if (state != expected)
                    throw new AssertionError("World mismatch after " + activeStage + " at " + pos + ": " + state);
            }
            if (Boolean.getBoolean("worldeditpp.test.buildingStress")) {
                BuildingStressFixture.verifyNoDrops();
                if (stone) BuildingStressFixture.verifyContents(player.serverLevel(), TARGET, FINAL_X, SIZE_Y, SIZE_Z);
                else BuildingStressFixture.verifyOriginalTarget(player.serverLevel(), TARGET, FINAL_X, SIZE_Y, SIZE_Z);
            }
        });
    }

    private static void startRecording() throws Exception {
        recording = new Recording(Configuration.getConfiguration("profile"));
        recording.setMaxSize(512L * 1024 * 1024);
        recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(2));
        recording.enable("jdk.ObjectAllocationSample").with("throttle", "500/s");
        recording.enable("jdk.GarbageCollection");
        recording.enable("jdk.GCPhasePause");
        recording.start();
        TICKS.add("stage,tickWallMs,tickCpuMs,cursor,heapUsedBytes,epochMillis");
    }

    private static void startStage(String name, long cells) {
        activeStage = name;
        stageStartedNanos = System.nanoTime();
        stageStartedMillis = System.currentTimeMillis();
        METRICS.put(name, new StageMetric(name, cells));
    }

    private static void finishStage(long cells, String detail) {
        String name = activeStage;
        long endNanos = System.nanoTime();
        long endMillis = System.currentTimeMillis();
        StageMetric metric = METRICS.get(name);
        metric.wallNanos = endNanos - stageStartedNanos;
        metric.detail = detail;
        WINDOWS.add(new Window(name, stageStartedMillis, endMillis));
        activeStage = "idle";
    }

    private static String projectionMetrics(Object projection) throws Exception {
        Object mesh = field(projection, "mesh");
        Map<?, ?> pages = (Map<?, ?>) field(mesh, "pages");
        Object scratch = field(mesh, "scratch");
        Method bytes = scratch.getClass().getDeclaredMethod("bytes");
        bytes.setAccessible(true);
        return "pages=" + pages.size() + ";scratchBytes=" + bytes.invoke(scratch);
    }

    private static void writeResults() throws Exception {
        recording.stop();
        String label = System.getProperty("worldeditpp.test.fullStressLabel", "baseline");
        if (!label.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid full stress label: " + label);
        }
        Path directory = Path.of(System.getProperty("worldeditpp.test.report")).toAbsolutePath().getParent()
                .resolve(label);
        Files.createDirectories(directory);
        Path jfr = directory.resolve("two-million-profile.jfr");
        recording.dump(jfr);
        recording.close();
        List<String> summary = new ArrayList<>();
        summary.add("stage,cells,wallMs,serverTicks,serverTickMs,maxServerTickMs,peakHeapBytes,cellsPerSecond,detail");
        for (StageMetric metric : METRICS.values()) summary.add(metric.csv());
        Files.write(directory.resolve("two-million-summary.csv"), summary);
        Files.write(directory.resolve("two-million-ticks.csv"), TICKS);
        analyze(jfr, directory.resolve("two-million-attribution.txt"));
        if (Boolean.getBoolean("worldeditpp.test.buildingStress")) {
            try (var files = Files.list(directory.getParent())) {
                for (Path evidence : files.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().startsWith("building-")).toList())
                    Files.copy(evidence, directory.resolve(evidence.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("worldeditpp.test.fullStress") || owner == null || failure != null) return;
        try {
            if (event.phase == TickEvent.Phase.START) {
                serverTickStarted = System.nanoTime();
                serverCpuStarted = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
                return;
            }
            updateOperationState();
            if (recording == null || serverTickStarted == 0) return;
            long wall = System.nanoTime() - serverTickStarted;
            long cpu = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - serverCpuStarted;
            serverTickStarted = 0;
            String stage = activeStage;
            if (stage.equals("idle")) return;
            long heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            int cursor = stage.equals("placement") ? pasteCursor
                    : stage.startsWith("undo") || stage.startsWith("redo") ? historyCursor : 0;
            StageMetric metric = METRICS.get(stage);
            if (metric != null) metric.tick(wall, heap);
            synchronized (TICKS) {
                TICKS.add(String.format(Locale.ROOT, "%s,%.3f,%.3f,%d,%d,%d", stage,
                        wall / 1e6, cpu / 1e6, cursor, heap, System.currentTimeMillis()));
            }
        } catch (Throwable throwable) {
            failure = throwable;
        }
    }

    private static void updateOperationState() throws Exception {
        Object states = staticField(ClipboardAdjustmentService.class, "STATES");
        Object state = ((Map<?, ?>) states).get(owner);
        Object job = state == null ? null : field(state, "pasteJob");
        pastePresent = job != null;
        if (job != null) {
            Object cursor = field(job, "cursor");
            pasteCursor = ((Number) cursor.getClass().getMethod("completed").invoke(cursor)).intValue();
        }
        Object task = historyTask();
        historyPresent = task != null;
        if (task != null) {
            if (historyCursorField == null) {
                historyCursorField = task.getClass().getDeclaredField("cursor");
                historyCursorField.setAccessible(true);
            }
            historyCursor = historyCursorField.getInt(task);
        }
    }

    private static Object historyTask() throws Exception {
        if (historyQueues == null) historyQueues = staticField(HistoryEngine.class, "ACTIVE");
        return ((java.util.Map<?, ?>) historyQueues).get(owner);
    }

    private static void analyze(Path jfr, Path output) throws Exception {
        Map<String, Map<String, Long>> allocations = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> serverSamples = new LinkedHashMap<>();
        Map<String, long[]> gc = new LinkedHashMap<>();
        for (Window window : WINDOWS) {
            allocations.put(window.name, new HashMap<>());
            serverSamples.put(window.name, new HashMap<>());
            gc.put(window.name, new long[2]);
        }
        try (RecordingFile file = new RecordingFile(jfr)) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                Window window = window(event.getStartTime().toEpochMilli());
                if (window == null) continue;
                String type = event.getEventType().getName();
                if (type.equals("jdk.ObjectAllocationSample")) {
                    allocations.get(window.name).merge(event.getClass("objectClass").getName(),
                            event.getLong("weight"), Long::sum);
                } else if (type.equals("jdk.ExecutionSample") && event.getThread("sampledThread") != null
                        && "Server thread".equals(event.getThread("sampledThread").getJavaName())
                        && event.getStackTrace() != null) {
                    Set<String> seen = new java.util.HashSet<>();
                    for (var frame : event.getStackTrace().getFrames()) {
                        String method = frame.getMethod().getType().getName() + "." + frame.getMethod().getName();
                        if (seen.add(method)) serverSamples.get(window.name).merge(method, 1, Integer::sum);
                    }
                } else if (type.equals("jdk.GarbageCollection")) {
                    long[] stats = gc.get(window.name);
                    stats[0]++;
                    stats[1] += event.getDuration().toNanos();
                }
            }
        }
        StringBuilder text = new StringBuilder("Real Forge client + integrated server; 480x64x64 = 1,966,080 blocks\n");
        for (Window window : WINDOWS) {
            text.append("\n[").append(window.name).append("] GC events=").append(gc.get(window.name)[0])
                    .append(" durationMs=").append(gc.get(window.name)[1] / 1e6).append('\n');
            appendTop(text, "Server inclusive samples", serverSamples.get(window.name));
            appendTop(text, "Allocation sample weights (all threads, bytes)", allocations.get(window.name));
        }
        text.append("\nExecution samples are statistical and inclusive entries overlap. Allocation weights are estimates.\n");
        Files.writeString(output, text);
    }

    private static Window window(long epochMillis) {
        for (Window window : WINDOWS)
            if (epochMillis >= window.startMillis && epochMillis <= window.endMillis) return window;
        return null;
    }

    private static void appendTop(StringBuilder text, String title, Map<String, ? extends Number> values) {
        text.append(title).append('\n');
        values.entrySet().stream().sorted(Comparator.comparingLong(
                        (Map.Entry<String, ? extends Number> entry) -> entry.getValue().longValue()).reversed())
                .limit(30).forEach(entry -> text.append(entry.getValue()).append(' ')
                        .append(entry.getKey()).append('\n'));
    }

    private static Object staticField(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void next() { phase++; ticks = 0; }

    private record Window(String name, long startMillis, long endMillis) { }

    private static final class StageMetric {
        final String name;
        final long cells;
        long wallNanos, tickNanos, maxTickNanos, peakHeap;
        int ticks;
        String detail = "";

        StageMetric(String name, long cells) { this.name = name; this.cells = cells; }

        synchronized void tick(long nanos, long heap) {
            ticks++;
            tickNanos += nanos;
            maxTickNanos = Math.max(maxTickNanos, nanos);
            peakHeap = Math.max(peakHeap, heap);
        }

        synchronized String csv() {
            double seconds = wallNanos / 1e9;
            return String.format(Locale.ROOT, "%s,%d,%.3f,%d,%.3f,%.3f,%d,%.1f,%s", name, cells,
                    wallNanos / 1e6, ticks, tickNanos / 1e6, maxTickNanos / 1e6, peakHeap,
                    seconds == 0 ? 0 : cells / seconds, detail);
        }
    }
}
