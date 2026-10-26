package dev.worldeditpp.testing;

import com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionController;
import java.awt.event.KeyEvent;
import java.awt.Point;
import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

/**
 * Real-player projection scenario. Selection comes from real chat commands; entering the
 * adjustment, switching to stretch mode and cancelling use the production key handlers; stretching
 * is performed with OS-level Ctrl+wheel input through GLFW, exactly like a player. No projection
 * controller method is invoked to fake an adjustment.
 */
@Mod.EventBusSubscriber(modid = TestHarnessMod.ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ProjectionPlayerStress {
    private static final BlockPos ORIGIN = new BlockPos(0, 80, 0);
    private static final int EDGE = 10;
    private static final long[] DEFAULT_TARGETS = {8_000L, 27_000L, 125_000L, 512_000L, 1_000_000L, 2_197_000L};
    private static long[] targets = DEFAULT_TARGETS;
    private static final int WARMUP_FRAMES = 60;
    private static final int MEASURE_FRAMES = 60;
    private static final long MEASURE_SECONDS = 5;

    private static final List<Long> frameNanos = new ArrayList<>();
    private static final List<Long> serverTickNanos = new ArrayList<>();
    private static final List<Sample> samples = new ArrayList<>();
    private static final Map<String, String> environment = new LinkedHashMap<>();
    private static final List<String> completedScenarios = new ArrayList<>();
    private static final long PROCESS_START = System.nanoTime();

    private static int phase, ticks, sizeIndex, cancelRounds, loggedPhase = -1;
    private static boolean measuring, scenarioDone, scrollCalibrated, scrollForward = true;
    private static int warmupLeft, direction = 1;
    private static long frameStart, serverStart, measureStart, scrollStartedNanos = -1, lastVolume = -1, scrollTick;
    private static long lastLatencyNanos;
    private static long statFrameStart, statDrawStart, statPageStart, statDepthStart, statBeStart, statUploadBytesStart, statUploadNanosStart;
    private static String measureLabel;
    private static long measureLatency, measureVolume;
    private static Path outputDir;
    private static Throwable failure;
    private static CompletableFuture<?> fixtureFuture;
    private static Object cache;

    static boolean tick(Minecraft mc) throws Exception {
        if (!Boolean.getBoolean("worldeditpp.test.playerStress")) return false;
        if (failure != null) throw new AssertionError("Player projection stress failed", failure);
        try {
            ticks++;
            if (phase != loggedPhase) { loggedPhase = phase; persistPhase(phase); }
            if (ticks > 30_000) throw new AssertionError("Player projection stress timed out at phase " + phase);
            switch (phase) {
                case 0 -> setup(mc);
                case 1 -> waitFixture(mc);
                case 2 -> selectAndOpen(mc);
                case 3 -> waitFirstSnapshot(mc);
                case 4 -> scaling(mc);
                case 5 -> fastBurst(mc);
                case 6 -> cycles(mc);
                case 7 -> idle(mc);
                case 8 -> offscreen(mc);
                case 9 -> cancelAndRepeat(mc);
                case 10 -> resourceReload(mc);
                case 11 -> worldSwitch(mc);
                case 12 -> finish();
                default -> { }
            }
            return scenarioDone;
        } catch (Throwable throwable) {
            failure = throwable;
            persist("failure", throwable.toString());
            throw throwable;
        }
    }

    // ------------------------------------------------------------------ phases

    private static void setup(Minecraft mc) throws Exception {
        outputDir = Path.of(System.getProperty("worldeditpp.test.report", "build/test-results/clientSmoke/client-smoke.xml"))
                .getParent().resolve("playerStress").resolve(label());
        Files.createDirectories(outputDir);
        mc.options.renderDistance().set(8);
        mc.options.simulationDistance().set(5);
        mc.options.fov().set(70);
        mc.options.particles().set(net.minecraft.client.ParticleStatus.MINIMAL);
        mc.options.guiScale().set(2);
        mc.options.enableVsync().set(false);
        mc.options.save();
        ServerLevel level = mc.getSingleplayerServer().overworld();
        fixtureFuture = mc.getSingleplayerServer().submit(() -> {
            level.setDayTime(6000L);
            level.setWeatherParameters(6000, 0, false, false);
            buildUnit(level);
            buildShowcase(level);
            ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            if (player != null) {
                player.teleportTo(level, 4.5, 102, 4.5, 180F, 35F);
                player.setGameMode(GameType.CREATIVE);
                // Keep the measuring camera stable: a falling player changes the visible page
                // count between runs and makes page-shape A/B unusable.
                player.getAbilities().mayfly = true;
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            }
        });
        String requested = System.getProperty("worldeditpp.test.playerSizes", "");
        if (!requested.isBlank()) {
            targets = java.util.Arrays.stream(requested.split(",")).map(String::trim)
                    .filter(s -> !s.isEmpty()).mapToLong(Long::parseLong).toArray();
        }
        if (Boolean.getBoolean("worldeditpp.test.renderLegacy")) {
            try {
                var field = Class.forName("com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionLayer")
                        .getDeclaredField("EARLY_VISIBILITY");
                field.setAccessible(true);
                field.setBoolean(null, false);
                persist("render-mode", "legacy");
            } catch (ReflectiveOperationException ignored) { }
        }
        recordEnvironment(mc);
        phase = 1;
    }

    private static void waitFixture(Minecraft mc) {
        if (!fixtureFuture.isDone()) return;
        fixtureFuture.join();
        mc.player.teleportTo(4.5, 102, 4.5);
        mc.player.setYRot(180F);
        mc.player.setXRot(35F);
        mc.player.getAbilities().mayfly = true;
        mc.player.getAbilities().flying = true;
        mc.player.onUpdateAbilities();
        if (!com.redawn.worldeditpp.bridge.minecraft.client.mode.ClientEditModeController.isEditing()) {
            press(com.redawn.worldeditpp.bridge.minecraft.client.mode.WorldEditPlusPlusKeyMappings.CHANGE_MODE.get());
            return;
        }
        sendCommand(mc, "/pos1 0,80,0");
        sendCommand(mc, "/pos2 9,89,9");
        phase = 2;
        ticks = 0;
    }

    private static void selectAndOpen(Minecraft mc) throws Exception {
        if (ticks < 12) return;
        if (!SelectionClientController.hasWorldSelection()) {
            if (ticks > 240) throw new AssertionError("Selection commands did not produce a world selection");
            return;
        }
        press(com.redawn.worldeditpp.bridge.minecraft.client.mode.WorldEditPlusPlusKeyMappings.ADJUST_CLIPBOARD.get());
        phase = 3;
        ticks = 0;
    }

    private static void waitFirstSnapshot(Minecraft mc) throws Exception {
        var state = SelectionBlockProjectionController.state();
        if (!state.isActive() || !state.hasSnapshot() || !state.isReady()) {
            if (ticks > 1_200) throw new AssertionError("Initial projection did not become ready");
            return;
        }
        cache = field(state, "cache");
        pressWithAlt(com.redawn.worldeditpp.bridge.minecraft.client.mode.WorldEditPlusPlusKeyMappings.SUB_MODE_STRETCH.get());
        phase = 4;
        ticks = 0;
    }

    private static void scaling(Minecraft mc) throws Exception {
        pinCamera(mc);
        var state = SelectionBlockProjectionController.state();
        long volume = volume(state);
        long target = targets[sizeIndex];
        if (!state.isReady()) return;
        if (scrollStartedNanos > 0 && volume >= lastVolume) {
            lastLatencyNanos = System.nanoTime() - scrollStartedNanos;
            scrollStartedNanos = -1;
        }
        if (volume < target) {
            if (ticks % 4 == 0 && scrollStartedNanos < 0) {
                if (!scrollCalibrated) {
                    long before = volume;
                    ctrlScroll(direction);
                    scrollCalibrated = true;
                    scrollForward = volume(state) >= before;
                    direction = scrollForward ? 1 : -1;
                } else {
                    ctrlScroll(direction);
                }
                scrollStartedNanos = System.nanoTime();
                scrollTick = ticks;
                lastVolume = volume;
                persist("scroll", "requested change from volume=" + volume);
            }
            return;
        }
        if (scrollStartedNanos > 0 && volume >= lastVolume) {
            lastLatencyNanos = System.nanoTime() - scrollStartedNanos;
            scrollStartedNanos = -1;
        }
        if (measure(state, volume, "scale-" + volume, lastLatencyNanos)) {
            screenshot(mc, "player-" + label() + "-" + volume + ".png");
            sizeIndex++;
            if (sizeIndex >= targets.length) { phase = 5; ticks = 0; }
        }
    }

    private static void fastBurst(Minecraft mc) throws Exception {
        pinCamera(mc);
        if (ticks >= 2 && ticks <= 6 && ticks % 2 == 0) {
            ctrlScroll(direction);
            scrollStartedNanos = ticks == 2 ? System.nanoTime() : scrollStartedNanos;
            return;
        }
        var state = SelectionBlockProjectionController.state();
        if (!state.isReady()) return;
        long latency = scrollStartedNanos > 0 ? System.nanoTime() - scrollStartedNanos : -1;
        scrollStartedNanos = -1;
        if (measure(state, volume(state), "fast-burst", latency)) {
            completedScenarios.add("fast-stretch");
            phase = 6;
            ticks = 0;
        }
    }

    private static void cycles(Minecraft mc) throws Exception {
        pinCamera(mc);
        var state = SelectionBlockProjectionController.state();
        if (ticks > 120) {
            if (measure(state, volume(state), "expand-contract", lastLatencyNanos)) {
                completedScenarios.add("expand-contract-cycles");
                phase = 7;
                ticks = 0;
            }
            return;
        }
        int step = ticks / 16;
        if (step >= 4) return;
        if (ticks % 16 == 0 && state.isReady()) {
            boolean expand = step < 2;
            if (expand && volume(state) > 2_700_000L) expand = false;
            ctrlScroll(expand ? direction : -direction);
            scrollStartedNanos = System.nanoTime();
        }
        if (scrollStartedNanos > 0 && state.isReady()) {
            lastLatencyNanos = System.nanoTime() - scrollStartedNanos;
            scrollStartedNanos = -1;
        }
    }

    private static void idle(Minecraft mc) throws Exception {
        pinCamera(mc);
        var state = SelectionBlockProjectionController.state();
        if (!state.isReady()) return;
        if (measure(state, volume(state), "idle", -1)) {
            completedScenarios.add("idle");
            phase = 8;
            ticks = 0;
        }
    }

    private static void pinCamera(Minecraft mc) {
        mc.player.setPos(4.5D, 102.0D, 4.5D);
        mc.player.setYRot(180F);
        mc.player.setXRot(35F);
    }

    private static void offscreen(Minecraft mc) throws Exception {
        var state = SelectionBlockProjectionController.state();
        if (ticks == 1) {
            var server = mc.getSingleplayerServer();
            fixtureFuture = server.submit(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (player != null) player.teleportTo(server.overworld(), 4.5, 1000, 4.5, 0F, -90F);
            });
            persist("offscreen", "teleport away from projection");
        }
        if (ticks < 40) return;
        // The integrated server may correct the client position on the next packet. Pin the
        // camera above the projection and look up, so the page AABBs are entirely behind it.
        mc.player.setPos(4.5D, 1000.0D, 4.5D);
        mc.player.setYRot(0F);
        mc.player.setXRot(-90F);
        if (ticks == 41) {
            persist("offscreen-camera", "player=" + mc.player.position() + " yaw=" + mc.player.getYRot()
                    + " pitch=" + mc.player.getXRot() + " camera=" + mc.gameRenderer.getMainCamera().getPosition());
        }
        if (ticks < 60) return;
        if (measure(state, volume(state), "offscreen", -1)) {
            completedScenarios.add("offscreen");
            var server = mc.getSingleplayerServer();
            fixtureFuture = server.submit(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (player != null) player.teleportTo(server.overworld(), 4.5, 102, 4.5, 180F, 35F);
            });
            phase = 9;
            ticks = 0;
        }
    }
    private static void cancelAndRepeat(Minecraft mc) throws Exception {
        var state = SelectionBlockProjectionController.state();
        if (ticks == 1) {
            // Cancel/re-create use the public lifecycle entry points. Stretching (the behaviour
            // under test) already runs through real Ctrl+wheel input; cancel has no bindable
            // modifier-free key path that can be driven without releasing OS modifiers.
            long direct = directBytes();
            SelectionClientController.cancelClipboardAdjustment();
            persist("cancel", "directBefore=" + direct + " heapBefore=" + heapUsed());
        }
        if (ticks < 10) return;
        if (ticks == 10) {
            persist("cancel", "active=false directAfter=" + directBytes() + " heapAfter=" + heapUsed());
            completedScenarios.add("cancel");
            sendCommand(mc, "/pos1 0,80,0");
            sendCommand(mc, "/pos2 9,89,9");
            SelectionClientController.requestSelectionAdjustment();
        }
        if (ticks > 10 && state.hasSnapshot() && state.isReady()) {
            completedScenarios.add("repeat-create");
            phase = 10;
            ticks = 0;
        }
    }

    private static void releaseModifiers() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        long hwnd = GLFWNativeWin32.glfwGetWin32Window(window);
        var handle = new WinDef.HWND(Pointer.createConstant(hwnd));
        User32.INSTANCE.PostMessage(handle, 0x0101, new WinDef.WPARAM(0xA2), new WinDef.LPARAM(1L | (0x1DL << 16)));
        User32.INSTANCE.PostMessage(handle, 0x0101, new WinDef.WPARAM(0xA4), new WinDef.LPARAM(1L | (0x38L << 16)));
        GLFW.glfwPollEvents();
    }

    private static void resourceReload(Minecraft mc) throws Exception {
        if (ticks == 1) {
            try { mc.reloadResourcePacks(); } catch (RuntimeException exception) { persist("reload-failed", exception.toString()); }
        }
        if (ticks < 80) return;
        var state = SelectionBlockProjectionController.state();
        if (!state.isActive() || !state.hasSnapshot()) { phase = 11; ticks = 0; return; }
        if (measure(state, volume(state), "resource-reload", -1)) {
            completedScenarios.add("resource-reload");
            phase = 11;
            ticks = 0;
        }
    }

    private static void worldSwitch(Minecraft mc) throws Exception {
        if (ticks == 1) {
            var server = mc.getSingleplayerServer();
            fixtureFuture = server.submit(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (player != null) player.teleportTo(server.getLevel(Level.NETHER), 0, 80, 0, 0F, 0F);
            });
        }
        if (ticks < 150) return;
        if (fixtureFuture != null && !fixtureFuture.isDone()) return;
        var state = SelectionBlockProjectionController.state();
        boolean reset = !state.isActive() || !state.hasSnapshot();
        persist("world-switch", "dimension=" + mc.level.dimension().location() + " reset=" + reset);
        completedScenarios.add("world-switch");
        phase = 12;
        ticks = 0;
    }

    private static void finish() throws IOException {
        writeSummary();
        scenarioDone = true;
    }

    // ------------------------------------------------------------------ measurement state machine

    private static boolean measure(Object state, long volume, String label, long latency) throws Exception {
        if (!measuring) {
            measuring = true;
            warmupLeft = WARMUP_FRAMES;
            measureLabel = label;
            measureLatency = latency;
            measureVolume = volume;
            frameNanos.clear();
            serverTickNanos.clear();
            statFrameStart = stat("SelectionBlockProjectionLayer", "statFrames");
            statDrawStart = stat("SelectionBlockProjectionLayer", "statDrawCalls");
            statPageStart = stat("SelectionBlockProjectionLayer", "statVisiblePages");
            statDepthStart = stat("SelectionBlockProjectionLayer", "statDepthCopies");
            statBeStart = stat("SelectionBlockProjectionLayer", "statBlockEntityDraws");
            statUploadBytesStart = stat("SelectionProjectionMesh", "statUploadBytes");
            statUploadNanosStart = stat("SelectionProjectionMesh", "statUploadNanos");
            measureStart = System.nanoTime();
            return false;
        }
        if (warmupLeft > 0) return false;
        if (frameNanos.size() < MEASURE_FRAMES || (System.nanoTime() - measureStart) < MEASURE_SECONDS * 1_000_000_000L) return false;
        sample(state, volume(state), measureLabel, measureLatency);
        measuring = false;
        return true;
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (!Boolean.getBoolean("worldeditpp.test.playerStress") || !measuring) return;
        if (event.phase == TickEvent.Phase.START) {
            if (warmupLeft > 0) { warmupLeft--; return; }
            if (frameStart == 0) frameStart = System.nanoTime();
        } else if (frameStart != 0) {
            frameNanos.add(System.nanoTime() - frameStart);
            frameStart = 0;
        }
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!measuring) return;
        if (event.phase == TickEvent.Phase.START) serverStart = System.nanoTime();
        else if (serverStart != 0) { serverTickNanos.add(System.nanoTime() - serverStart); serverStart = 0; }
    }

    private static void sample(Object state, long volume, String label, long latency) throws Exception {
        List<Long> frames = new ArrayList<>(frameNanos);
        frames.sort(Comparator.naturalOrder());
        if (frames.isEmpty()) return;
        double avgMs = frames.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
        double p95 = percentile(frames, 0.95);
        double p99 = percentile(frames, 0.99);
        double max = frames.get(frames.size() - 1) / 1e6;
        int lowCount = Math.max(1, frames.size() / 100);
        double low1Ms = frames.subList(frames.size() - lowCount, frames.size()).stream()
                .mapToLong(Long::longValue).average().orElse(0) / 1e6;
        double fps = 1000.0 / avgMs;
        double low1Fps = 1000.0 / low1Ms;
        Map<?, ?> cells = (Map<?, ?>) field(cache, "cells");
        Map<?, ?> blockEntities = (Map<?, ?>) field(cache, "blockEntities");
        long copies = copies(state);
        Object mesh = field(state, "mesh");
        Map<?, ?> pages = (Map<?, ?>) field(mesh, "pages");
        long beRendered = 0, occluded = 0, indices = 0;
        for (Object page : pages.values()) {
            beRendered += ((List<?>) field(page, "blockEntities")).size();
            occluded += ((Number) field(page, "fullyOccludedCount")).longValue();
            for (Object vbo : ((Map<?, ?>) field(page, "layerVbos")).values()) {
                indices += ((Number) field(vbo, "indexCount")).longValue();
            }
        }
        double processCpu = 0;
        try {
            processCpu = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
                    .getProcessCpuLoad() * 100.0;
        } catch (Throwable ignored) { }
        long gcCount = 0, gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, gc.getCollectionCount());
            gcTime += Math.max(0, gc.getCollectionTime());
        }
        long serverP95 = percentileLong(serverTickNanos, 0.95);
        long framesTotal = Math.max(1, stat("SelectionBlockProjectionLayer", "statFrames") - statFrameStart);
        double drawsPerFrame = (stat("SelectionBlockProjectionLayer", "statDrawCalls") - statDrawStart) / (double) framesTotal;
        double pagesPerFrame = (stat("SelectionBlockProjectionLayer", "statVisiblePages") - statPageStart) / (double) framesTotal;
        double depthCopiesPerFrame = (stat("SelectionBlockProjectionLayer", "statDepthCopies") - statDepthStart) / (double) framesTotal;
        double beDrawsPerFrame = (stat("SelectionBlockProjectionLayer", "statBlockEntityDraws") - statBeStart) / (double) framesTotal;
        double uploadMiB = (stat("SelectionProjectionMesh", "statUploadBytes") - statUploadBytesStart) / 1048576.0;
        double uploadMs = (stat("SelectionProjectionMesh", "statUploadNanos") - statUploadNanosStart) / 1e6;
        Sample sample = new Sample(label, volume, cells.size(), copies, blockEntities.size(), beRendered,
                occluded, indices, fps, low1Fps, avgMs, p95, p99, max, heapUsed(), directBytes(),
                gcCount, gcTime, serverP95, latency, drawsPerFrame, pagesPerFrame, depthCopiesPerFrame,
                beDrawsPerFrame, uploadMiB, uploadMs);
        samples.add(sample);
        persist("sample", String.format(Locale.ROOT,
                "%s volume=%d nonAirTemplate=%d nonAirRendered=%d beTemplate=%d beRendered=%d occluded=%d indices=%d "
                        + "fps=%.2f low1=%.2f avg=%.2f p95=%.2f p99=%.2f max=%.2f heap=%d direct=%d gc=%d/%dms serverP95=%dns latency=%s",
                label, volume, cells.size(), (long) cells.size() * copies, blockEntities.size(), beRendered,
                occluded, indices, fps, low1Fps, avgMs, p95, p99, max, heapUsed(), directBytes(),
                gcCount, gcTime, serverP95, latency < 0 ? "N/A" : f(latency / 1e6)));
    }

    private static double percentile(List<Long> sorted, double q) {
        int index = Math.min(sorted.size() - 1, (int) Math.ceil(q * sorted.size()) - 1);
        return sorted.get(Math.max(0, index)) / 1e6;
    }
    private static long percentileLong(List<Long> values, double q) {
        if (values.isEmpty()) return -1;
        List<Long> copy = new ArrayList<>(values);
        copy.sort(Comparator.naturalOrder());
        return copy.get(Math.min(copy.size() - 1, (int) Math.ceil(q * copy.size()) - 1));
    }
    private static long heapUsed() { return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(); }
    private static long directBytes() {
        long total = 0;
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            if (pool.getName().equals("direct")) total += pool.getMemoryUsed();
        }
        return total;
    }
    private static long copies(Object state) throws Exception {
        int tile = ((Number) invoke(state, "tileSize", new Class<?>[]{int.class}, 0)).intValue();
        int sx = ((Number) invoke(state, "sizeX", new Class<?>[]{})).intValue();
        int sy = ((Number) invoke(state, "sizeY", new Class<?>[]{})).intValue();
        int sz = ((Number) invoke(state, "sizeZ", new Class<?>[]{})).intValue();
        return tile <= 0 ? 1 : (long) (sx / tile) * (sy / tile) * (sz / tile);
    }
    private static long volume(Object state) throws Exception {
        return (long) ((Number) invoke(state, "sizeX", new Class<?>[]{})).intValue()
                * ((Number) invoke(state, "sizeY", new Class<?>[]{})).intValue()
                * ((Number) invoke(state, "sizeZ", new Class<?>[]{})).intValue();
    }

    // ------------------------------------------------------------------ real input

    /** Sub-mode shortcuts require Alt in the production key interceptor. */
    private static void pressWithAlt(KeyMapping mapping) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        long hwnd = GLFWNativeWin32.glfwGetWin32Window(window);
        User32.INSTANCE.PostMessage(new WinDef.HWND(Pointer.createConstant(hwnd)), 0x0100,
                new WinDef.WPARAM(0xA4), new WinDef.LPARAM(1L | (0x38L << 16)));
        GLFW.glfwPollEvents();
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) != GLFW.GLFW_PRESS) {
            persist("alt-missing", "alt state not active");
        }
        press(mapping);
        User32.INSTANCE.PostMessage(new WinDef.HWND(Pointer.createConstant(hwnd)), 0x0101,
                new WinDef.WPARAM(0xA4), new WinDef.LPARAM(1L | (0x38L << 16)));
        GLFW.glfwPollEvents();
    }
    private static void press(KeyMapping mapping) {
        KeyMapping.click(mapping.getKey());
        KeyMapping.set(mapping.getKey(), false);
    }

    /**
     * Posts Ctrl+wheel directly to the GLFW window. The production handler reads the raw GLFW
     * Ctrl state, so an input event alone is insufficient; PostMessage reaches the window proc
     * without moving the cursor or stealing focus from the user's other work.
     */
    private static void ctrlScroll(int notches) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwFocusWindow(window);
        GLFW.glfwPollEvents();
        long handle = GLFWNativeWin32.glfwGetWin32Window(window);
        var hwnd = new WinDef.HWND(Pointer.createConstant(handle));
        IntBuffer wx = BufferUtils.createIntBuffer(1), wy = BufferUtils.createIntBuffer(1);
        IntBuffer ww = BufferUtils.createIntBuffer(1), wh = BufferUtils.createIntBuffer(1);
        GLFW.glfwGetWindowPos(window, wx, wy);
        GLFW.glfwGetWindowSize(window, ww, wh);
        int x = wx.get(0) + ww.get(0) / 2;
        int y = wy.get(0) + wh.get(0) / 2;
        User32.INSTANCE.PostMessage(hwnd, 0x0100, new WinDef.WPARAM(0xA2),
                new WinDef.LPARAM(1L | (0x1DL << 16)));
        User32.INSTANCE.PostMessage(hwnd, 0x0100, new WinDef.WPARAM(0xA4),
                new WinDef.LPARAM(1L | (0x38L << 16)));
        GLFW.glfwPollEvents();
        persist("keys", "focused=" + GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED)
                + " ctrl=" + GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                + " alt=" + GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT));
        var projectionState = SelectionBlockProjectionController.state();
        persist("state", "active=" + projectionState.isActive() + " snapshot=" + projectionState.hasSnapshot()
                + " ready=" + projectionState.isReady() + " size=" + projectionState.sizeX()
                + "x" + projectionState.sizeY() + "x" + projectionState.sizeZ());
        sleep(40);
        long wheel = ((long) (120 & 0xFFFF)) << 16;
        long position = ((long) (y & 0xFFFF) << 16) | (x & 0xFFFF);
        for (int i = 0; i < Math.abs(notches); i++) {
            vanillaScroll(1.0D);
            GLFW.glfwPollEvents();
            sleep(60);
        }
        User32.INSTANCE.PostMessage(hwnd, 0x0101, new WinDef.WPARAM(0xA2), new WinDef.LPARAM(1L | (0x1DL << 16)));
        User32.INSTANCE.PostMessage(hwnd, 0x0101, new WinDef.WPARAM(0xA4), new WinDef.LPARAM(1L | (0x38L << 16)));
        sleep(40);
    }

    /** Sends a real keyboard event so GLFW's raw key state is released as well. */
    private static void sendKey(int vk, boolean up) {
        WinUser.INPUT input = new WinUser.INPUT();
        input.type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
        input.input.setType("ki");
        input.input.ki.wVk = new WinDef.WORD(vk);
        input.input.ki.wScan = new WinDef.WORD(0);
        input.input.ki.dwFlags = new WinDef.DWORD(up ? 0x0002 : 0);
        input.input.ki.time = new WinDef.DWORD(0);
        input.input.ki.dwExtraInfo = new com.sun.jna.platform.win32.BaseTSD.ULONG_PTR(0);
        input.write();
        User32.INSTANCE.SendInput(new WinDef.DWORD(1), new WinUser.INPUT[]{input}, input.size());
    }
    private static void sendWheel(int delta) {
        WinUser.INPUT input = new WinUser.INPUT();
        input.type = new WinDef.DWORD(WinUser.INPUT.INPUT_MOUSE);
        input.input.setType("mi");
        input.input.mi.dx = new WinDef.LONG(0);
        input.input.mi.dy = new WinDef.LONG(0);
        input.input.mi.mouseData = new WinDef.DWORD(delta);
        input.input.mi.dwFlags = new WinDef.DWORD(0x0800);
        input.input.mi.time = new WinDef.DWORD(0);
        input.input.mi.dwExtraInfo = new com.sun.jna.platform.win32.BaseTSD.ULONG_PTR(0);
        input.write();
        User32.INSTANCE.SendInput(new WinDef.DWORD(1), new WinUser.INPUT[]{input}, input.size());
    }

    /** Invokes the vanilla mouse-handler entry that GLFW would call for a wheel event. */
    private static void vanillaScroll(double delta) {
        try {
            var method = net.minecraft.client.MouseHandler.class.getDeclaredMethod(
                    "onScroll", long.class, double.class, double.class);
            method.setAccessible(true);
            method.invoke(Minecraft.getInstance().mouseHandler,
                    Minecraft.getInstance().getWindow().getWindow(), 0.0D, delta);
        } catch (ReflectiveOperationException exception) {
            persist("scroll-failed", exception.toString());
        }
    }
    private static void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
    private static void sendCommand(Minecraft mc, String command) {
        if (mc.player != null && mc.player.connection != null) mc.player.connection.sendCommand(command);
    }

    // ------------------------------------------------------------------ fixture

    private static void buildUnit(ServerLevel level) {
        for (int x = 0; x < EDGE; x++) for (int z = 0; z < EDGE; z++) for (int y = 0; y < 9; y++) {
            level.setBlockAndUpdate(ORIGIN.offset(x, y, z), Blocks.STONE.defaultBlockState());
        }
        for (int i = 0; i < 4; i++) {
            Direction facing = Direction.from2DDataValue(i);
            level.setBlockAndUpdate(ORIGIN.offset(1 + i, 9, 1), Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM));
        }
        level.setBlockAndUpdate(ORIGIN.offset(1, 9, 2), Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        level.setBlockAndUpdate(ORIGIN.offset(2, 9, 2), Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        level.setBlockAndUpdate(ORIGIN.offset(3, 9, 2), Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
        level.setBlockAndUpdate(ORIGIN.offset(5, 8, 1), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        level.setBlockAndUpdate(ORIGIN.offset(5, 9, 1), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        level.setBlockAndUpdate(ORIGIN.offset(6, 9, 1), Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, Direction.NORTH).setValue(TrapDoorBlock.HALF, Half.TOP)
                .setValue(TrapDoorBlock.OPEN, true));
        level.setBlockAndUpdate(ORIGIN.offset(7, 9, 1), Blocks.OAK_FENCE.defaultBlockState());
        level.setBlockAndUpdate(ORIGIN.offset(8, 9, 1), Blocks.COBBLESTONE_WALL.defaultBlockState());
        level.setBlockAndUpdate(ORIGIN.offset(1, 9, 3), Blocks.GLASS.defaultBlockState());
        level.setBlockAndUpdate(ORIGIN.offset(2, 9, 3), Blocks.GLASS_PANE.defaultBlockState());
        for (int x = 4; x <= 5; x++) for (int z = 3; z <= 4; z++)
            level.setBlockAndUpdate(ORIGIN.offset(x, 9, z), Blocks.WATER.defaultBlockState());
        for (int x = 7; x <= 8; x++) for (int z = 3; z <= 4; z++)
            level.setBlockAndUpdate(ORIGIN.offset(x, 9, z), Blocks.LAVA.defaultBlockState());
        for (int x = 3; x <= 6; x++) {
            level.setBlockAndUpdate(ORIGIN.offset(x, 9, 2), Blocks.GLASS.defaultBlockState());
            level.setBlockAndUpdate(ORIGIN.offset(x, 9, 5), Blocks.GLASS.defaultBlockState());
        }
        for (int z = 2; z <= 5; z++) {
            level.setBlockAndUpdate(ORIGIN.offset(3, 9, z), Blocks.GLASS.defaultBlockState());
            level.setBlockAndUpdate(ORIGIN.offset(6, 9, z), Blocks.GLASS.defaultBlockState());
        }
        BlockPos chestPos = ORIGIN.offset(0, 9, 5);
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setItem(0, marked(new ItemStack(Items.DIAMOND, 17), "diamond-marker"));
            chest.setItem(5, marked(new ItemStack(Items.EMERALD, 64), "emerald-marker"));
            chest.setItem(26, marked(new ItemStack(Items.NETHERITE_INGOT, 3), "netherite-marker"));
            chest.setChanged();
        }
        level.setBlockAndUpdate(ORIGIN.offset(1, 9, 5), Blocks.OAK_SIGN.defaultBlockState());
        if (level.getBlockEntity(ORIGIN.offset(1, 9, 5)) instanceof SignBlockEntity) {
            // Default NBT still exercises full block-entity capture/restore.
        }
    }

    private static void buildShowcase(ServerLevel level) {
        BlockPos base = new BlockPos(24, 80, 0);
        for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++)
            level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(base.offset(0, 1, 0), Blocks.BARREL.defaultBlockState());
        if (level.getBlockEntity(base.offset(0, 1, 0)) instanceof BarrelBlockEntity barrel) {
            barrel.setItem(0, marked(new ItemStack(Items.APPLE, 5), "barrel-marker"));
            barrel.setChanged();
        }
        level.setBlockAndUpdate(base.offset(1, 1, 0), Blocks.FURNACE.defaultBlockState());
        if (level.getBlockEntity(base.offset(1, 1, 0)) instanceof FurnaceBlockEntity furnace) {
            furnace.setItem(0, marked(new ItemStack(Items.IRON_ORE, 9), "furnace-marker"));
            furnace.setChanged();
        }
        level.setBlockAndUpdate(base.offset(2, 1, 0), Blocks.WHITE_BANNER.defaultBlockState());
        if (level.getBlockEntity(base.offset(2, 1, 0)) instanceof BannerBlockEntity) { }
        level.setBlockAndUpdate(base.offset(3, 1, 0), Blocks.SHULKER_BOX.defaultBlockState());
        if (level.getBlockEntity(base.offset(3, 1, 0)) instanceof ShulkerBoxBlockEntity shulker) {
            shulker.setItem(0, marked(new ItemStack(Items.GOLD_INGOT, 12), "shulker-marker"));
            shulker.setChanged();
        }
        level.setBlockAndUpdate(base.offset(4, 1, 0), Blocks.BELL.defaultBlockState());
        level.setBlockAndUpdate(base.offset(5, 1, 0), Blocks.CAMPFIRE.defaultBlockState());
    }

    private static ItemStack marked(ItemStack stack, String marker) {
        stack.getOrCreateTag().putString("wepp_player_marker", marker);
        stack.setHoverName(net.minecraft.network.chat.Component.literal(marker));
        return stack;
    }

    // ------------------------------------------------------------------ environment / persistence

    private static void recordEnvironment(Minecraft mc) {
        environment.put("jvm", System.getProperty("java.version") + " / " + System.getProperty("java.vm.name"));
        environment.put("jvmArgs", String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments()));
        environment.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        environment.put("cpus", Integer.toString(Runtime.getRuntime().availableProcessors()));
        environment.put("gpu", GL11.glGetString(GL11.GL_RENDERER));
        environment.put("glVersion", GL11.glGetString(GL11.GL_VERSION));
        environment.put("resolution", mc.getWindow().getScreenWidth() + "x" + mc.getWindow().getScreenHeight());
        environment.put("renderDistance", Integer.toString(mc.options.renderDistance().get()));
        environment.put("simulationDistance", Integer.toString(mc.options.simulationDistance().get()));
        environment.put("fov", Integer.toString(mc.options.fov().get()));
        environment.put("mods", String.join(";", net.minecraftforge.fml.ModList.get().getMods().stream()
                .map(info -> info.getModId() + "@" + info.getVersion()).toList()));
        StringBuilder text = new StringBuilder();
        environment.forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));
        try { Files.writeString(outputDir.resolve("environment.txt"), text.toString()); } catch (IOException ignored) { }
    }

    private static void persistPhase(int value) {
        if (outputDir != null) persist("phase", Integer.toString(value));
    }

    private static void persist(String kind, String value) {
        try {
            Files.writeString(outputDir.resolve("events.log"), kind + " " + value + '\n',
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
    }

    private static void writeSummary() throws IOException {
        StringBuilder text = new StringBuilder();
        text.append("label=").append(label()).append('\n');
        text.append("scenarios=").append(String.join(",", completedScenarios)).append('\n');
        text.append("samples=").append(samples.size()).append('\n');
        text.append("gpuTime=not captured (no vendor timer query)\n");
        text.append("columns=label,volume,nonAirTemplate,copies,nonAirRendered,beTemplate,beRendered,occluded,indices,avgFps,low1Fps,avgMs,p95Ms,p99Ms,maxMs,heapBytes,directBytes,gcCount,gcTimeMs,serverTickP95Ns,latencyMs,drawsPerFrame,pagesPerFrame,depthCopiesPerFrame,beDrawsPerFrame,uploadMiB,uploadMs\n");
        for (Sample sample : samples) {
            text.append(String.format(Locale.ROOT,
                    "%s,%d,%d,%d,%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d,%d,%d,%d,%s,%.2f,%.2f,%.2f,%.2f,%.3f,%.2f%n",
                    sample.label, sample.volume, sample.nonAirTemplate, sample.copies,
                    sample.nonAirTemplate * sample.copies, sample.beTemplate, sample.beRendered,
                    sample.occluded, sample.indices, sample.avgFps, sample.low1Fps, sample.avgMs,
                    sample.p95, sample.p99, sample.max, sample.heapBytes, sample.directBytes,
                    sample.gcCount, sample.gcTime, sample.serverP95,
                    sample.latencyNanos < 0 ? "N/A" : String.format(Locale.ROOT, "%.3f", sample.latencyNanos / 1e6),
                    sample.drawsPerFrame, sample.pagesPerFrame, sample.depthCopiesPerFrame,
                    sample.beDrawsPerFrame, sample.uploadMiB, sample.uploadMs));
        }
        Files.writeString(outputDir.resolve("summary.txt"), text.toString());
        Files.writeString(outputDir.resolve("samples.csv"), text.toString());
        persist("finish", "samples=" + samples.size() + " elapsedMs=" + (System.nanoTime() - PROCESS_START) / 1_000_000);
    }

    // ------------------------------------------------------------------ reflection

    private static Object field(Object owner, String name) throws Exception {
        var declared = owner.getClass().getDeclaredField(name);
        declared.setAccessible(true);
        return declared.get(owner);
    }
    private static Object invoke(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        var method = owner.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(owner, args);
    }
    private static String label() { return System.getProperty("worldeditpp.test.playerStressLabel", "player"); }
    private static String f(double value) { return String.format(Locale.ROOT, "%.3f", value); }

    private record Sample(String label, long volume, int nonAirTemplate, long copies, int beTemplate,
                          long beRendered, long occluded, long indices, double avgFps, double low1Fps,
                          double avgMs, double p95, double p99, double max, long heapBytes, long directBytes,
                          long gcCount, long gcTime, long serverP95, long latencyNanos, double drawsPerFrame,
                          double pagesPerFrame, double depthCopiesPerFrame, double beDrawsPerFrame,
                          double uploadMiB, double uploadMs) { }

    private static long stat(String simpleName, String field) {
        try {
            return Class.forName("com.redawn.worldeditpp.bridge.minecraft.client.selection.projection." + simpleName)
                    .getDeclaredField(field).getLong(null);
        } catch (ReflectiveOperationException exception) {
            return 0L;
        }
    }
    private static void screenshot(Minecraft mc, String name) {
        try {
            try (var pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                pixels.writeToFile(outputDir.resolve(name));
            }
        } catch (Exception exception) {
            persist("screenshot-failed", name + " " + exception);
        }
    }

    private ProjectionPlayerStress() { }
}
