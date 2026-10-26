package dev.worldeditpp.testing;

import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.ClientEditModeController;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController;
import com.redawn.worldeditpp.bridge.minecraft.network.ToggleEditModePayload;
import com.redawn.worldeditpp.bridge.worldedit.operation.IncrementalJobScheduler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

/** Two-million-block /set pressure path, including its undo. Test-only. */
@Mod.EventBusSubscriber(modid = TestHarnessMod.ID, value = Dist.CLIENT)
public final class MillionSetClientProbe {
    private static final int SIZE_X = 120;
    private static final int SIZE_Y = 64;
    private static final int SIZE_Z = 256;
    private static final BlockPos MIN = new BlockPos(0, 80, 0);
    private static final long BLOCKS = (long) SIZE_X * SIZE_Y * SIZE_Z;
    private static int phase, ticks;
    private static UUID owner;
    private static long started;
    private static long setMillis = -1, undoMillis = -1;

    static boolean tick(Minecraft mc) throws Exception {
        if (++ticks > 24_000) throw new AssertionError("Million set phase timed out: " + phase);
        switch (phase) {
            case 0 -> {
                owner = mc.player.getUUID();
                PlatformBridge.sendToServer(new ToggleEditModePayload("million-set"));
                next();
            }
            case 1 -> {
                if (!ClientEditModeController.isEditing()) return false;
                command(mc, "/pos1 0,80,0");
                command(mc, "/pos2 " + (MIN.getX() + SIZE_X - 1) + ","
                        + (MIN.getY() + SIZE_Y - 1) + "," + (MIN.getZ() + SIZE_Z - 1));
                next();
            }
            case 2 -> {
                if (!SelectionClientController.hasCuboidSelection()) return false;
                started = System.nanoTime();
                command(mc, "/set stone");
                next();
            }
            case 3 -> {
                if (IncrementalJobScheduler.instance().busy(owner)) return false;
                if (!server(mc, player -> player.serverLevel().getBlockState(
                        MIN.offset(SIZE_X - 1, SIZE_Y - 1, SIZE_Z - 1)).is(Blocks.STONE))) return false;
                setMillis = (System.nanoTime() - started) / 1_000_000L;
                started = System.nanoTime();
                command(mc, "/undo");
                next();
            }
            case 4 -> {
                if (IncrementalJobScheduler.instance().busy(owner)) return false;
                undoMillis = (System.nanoTime() - started) / 1_000_000L;
                Files.writeString(Path.of(System.getProperty("worldeditpp.test.report"))
                                .resolveSibling("million-set.txt"),
                        String.format(Locale.ROOT,
                                "blocks=%d set=%dms undo=%dms%n", BLOCKS, setMillis, undoMillis));
                return true;
            }
            default -> throw new AssertionError("Unknown million set phase " + phase);
        }
        return false;
    }

    private static boolean server(Minecraft mc,
                                  java.util.function.Predicate<ServerPlayer> test) throws Exception {
        var future = mc.getSingleplayerServer().submit(() -> {
            ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(owner);
            return player != null && test.test(player);
        });
        return future.get();
    }

    private static void command(Minecraft mc, String command) {
        mc.getConnection().sendCommand(command);
    }

    private static void next() {
        phase++;
        ticks = 0;
    }

    private MillionSetClientProbe() { }
}
