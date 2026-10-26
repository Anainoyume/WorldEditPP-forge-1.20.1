package dev.worldeditpp.testing;

import com.redawn.common.test.TestAssertions;
import com.redawn.common.test.TestReport;
import dev.worldeditpp.testing.framework.ClientScenario;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;

/** Opt-in bootstrap test: real world, integrated server, chat and rendered frame. */
@Mod.EventBusSubscriber(modid = TestHarnessMod.ID, value = Dist.CLIENT)
public final class ClientSmoke {
    private static final String MESSAGE = "WEPP_SMOKE_CHAT_LOOPBACK";
    private static final long START = System.nanoTime();
    private static final ClientScenario SCENARIO = ClientScenarioRegistry.select();
    private static boolean opened;
    private static boolean sent;
    private static boolean received;
    private static boolean finished;
    private static boolean businessComplete;
    private static int ticksInWorld;
    private static int framesAfterChat;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!Boolean.getBoolean("worldeditpp.test.client") || finished) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            long timeoutSeconds = Boolean.getBoolean("worldeditpp.test.playerStress") ? 1_500L
                    : Boolean.getBoolean("worldeditpp.test.fullStress") ? 1_100L : 240L;
            require((System.nanoTime() - START) / 1_000_000_000 < timeoutSeconds, "Client smoke timed out");
            if (!opened && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                opened = true;
                require(ModList.get().isLoaded(System.getProperty("worldeditpp.test.expectedMod")), "Target mod not loaded");
                mc.options.renderDistance().set(Boolean.getBoolean("worldeditpp.test.historyProfile") ? 4 : 2);
                mc.options.simulationDistance().set(5);
                mc.options.pauseOnLostFocus = false;
                mc.options.onboardAccessibility = false;
                mc.options.save();
                String world = "wepp-smoke-" + UUID.randomUUID();
                mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings(world, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42L, false, false),
                        registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
            if (mc.level == null || mc.player == null || mc.getConnection() == null) return;
            if (++ticksInWorld >= 40 && !sent) {
                require(mc.getSingleplayerServer() != null, "Integrated server is missing");
                require(!Boolean.getBoolean("worldeditpp.test.fail"), "Intentional failure probe");
                sent = true;
                mc.getConnection().sendChat(MESSAGE);
            }
            if (received && !businessComplete) businessComplete = SCENARIO.tick(mc);
        } catch (Exception | AssertionError | OutOfMemoryError failure) {
            finish(failure);
        }
    }

    /** Test-only: never let the smoke window capture the real desktop cursor. */
    @SubscribeEvent
    public static void releaseCursor(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("worldeditpp.test.client")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse();
    }

    @SubscribeEvent
    public static void chat(ClientChatReceivedEvent.Player event) {
        if (sent && event.getMessage().getString().contains(MESSAGE)) {
            received = true;
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void projectionFrame(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if (!Boolean.getBoolean("worldeditpp.test.client") || finished) return;
        if (event.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        try {
            SCENARIO.render(event);
        } catch (Exception | AssertionError failure) {
            finish(failure);
        }
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!received || !businessComplete || finished || ++framesAfterChat < 10) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            Path screenshot = Path.of(System.getProperty("worldeditpp.test.report")).resolveSibling("client-smoke.png");
            try (var pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                pixels.writeToFile(screenshot);
            }
            finish(null);
        } catch (Exception | AssertionError failure) {
            finish(failure);
        }
    }

    private static void require(boolean condition, String message) {
        TestAssertions.check(condition, message);
    }

    private static void finish(Throwable failure) {
        finished = true;
        try {
            TestReport.write(Path.of(System.getProperty("worldeditpp.test.report")),
                    SCENARIO.name(), SCENARIO.category(),
                    (System.nanoTime() - START) / 1_000_000, failure);
        } catch (Exception reportFailure) {
            throw new IllegalStateException("Cannot write client smoke report", reportFailure);
        } finally {
            Minecraft.getInstance().stop();
        }
    }
}
