package dev.worldeditpp.testing;

import com.redawn.worldeditpp.bridge.worldedit.WorldEditBridge;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditPlatform;
import com.redawn.worldeditpp.bridge.worldedit.operation.IncrementalJobScheduler;
import com.sk89q.worldedit.LocalSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Actual WorldEdit chat commands on 2,048 full double chests, separate from projection paste. */
final class ChestHistoryClientProbe {
    private static final BlockPos SOURCE = new BlockPos(0, 80, 0);
    private static final BlockPos TARGET = new BlockPos(0, 80, 128);
    private static int phase, ticks;
    private static CompletableFuture<Boolean> future;
    private static ServerPlayer player;
    private static LocalSession session;

    static boolean tick(Minecraft mc) throws Exception {
        if (++ticks > 2400) throw new AssertionError("Chest command phase timed out: " + phase);
        switch (phase) {
            case 0 -> {
                if (!poll(mc, () -> {
                    player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    session = WorldEditBridge.worldEdit().getSessionManager().get(WorldEditPlatform.adaptPlayer(player));
                    session.clearHistory();
                    player.teleportTo(0.5, 100, 0.5);
                    player.getAbilities().flying = true; player.onUpdateAbilities();
                    for (int z = 0; z < 64; z++) for (int x = 0; x < 64; x++) {
                        var pos = SOURCE.offset(x, 0, z);
                        player.serverLevel().getChunkAt(pos).setBlockState(pos,
                                Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE,
                                        x % 2 == 0 ? ChestType.LEFT : ChestType.RIGHT), false);
                        var container = (Container) player.serverLevel().getBlockEntity(pos);
                        for (int slot = 0; slot < 27; slot++) {
                            var stack = new ItemStack(Items.DIAMOND, 64);
                            stack.getOrCreateTag().putInt("wepp_full_chest_slot", slot);
                            container.setItem(slot, stack);
                        }
                        container.setChanged();
                    }
                    BuildingStressFixture.calibrate(player.serverLevel());
                    return true;
                })) return false;
                command(mc, "/pos1 0,80,0"); command(mc, "/pos2 63,80,63"); command(mc, "/copy");
                next();
            }
            case 1 -> {
                if (!poll(mc, () -> {
                    try { if (session.getClipboard().getClipboard().getRegion().getVolume() != 4096) return false; }
                    catch (com.sk89q.worldedit.EmptyClipboardException waiting) { return false; }
                    player.teleportTo(0.5, 100, 128.5);
                    return true;
                })) return false;
                next();
            }
            case 2 -> {
                if (ticks < 10 || mc.player.blockPosition().getZ() != 128) return false;
                command(mc, "/paste"); next();
            }
            case 3 -> { if (awaitState(mc, "chest")) { command(mc, "undo"); next(); } }
            case 4 -> { if (awaitState(mc, "air")) { command(mc, "redo"); next(); } }
            case 5 -> { if (awaitState(mc, "chest")) { command(mc, "/undo"); next(); } }
            case 6 -> { if (awaitState(mc, "air")) { command(mc, "/redo"); next(); } }
            case 7 -> {
                if (!awaitState(mc, "chest")) return false;
                command(mc, "/pos1 0,80,128"); command(mc, "/pos2 63,80,191"); command(mc, "/set stone");
                next();
            }
            case 8 -> { if (awaitState(mc, "stone")) { command(mc, "undo"); next(); } }
            case 9 -> { if (awaitState(mc, "chest")) { command(mc, "redo"); next(); } }
            case 10 -> { if (awaitState(mc, "stone")) { command(mc, "/set chest"); next(); } }
            case 11 -> {
                if (!poll(mc, () -> {
                    if (!player.serverLevel().getBlockState(TARGET.offset(63, 0, 63)).is(Blocks.CHEST)) return false;
                    // Fill after the edit has already been remembered: undo must clear live
                    // contents, even though the history's current snapshot was an empty chest.
                    for (int z = 0; z < 64; z++) for (int x = 0; x < 64; x++) {
                        if (!(player.serverLevel().getBlockEntity(TARGET.offset(x, 0, z)) instanceof Container container))
                            throw new AssertionError("//set chest did not create all containers");
                        for (int slot = 0; slot < 27; slot++) container.setItem(slot, new ItemStack(Items.EMERALD, 64));
                        container.setChanged();
                    }
                    return true;
                })) return false;
                command(mc, "undo"); next();
            }
            case 12 -> { if (awaitState(mc, "stone")) next(); }
            case 13 -> {
                if (ticks < 20) return false;
                if (!poll(mc, () -> {
                    verify(SOURCE, "chest");
                    BuildingStressFixture.verifyNoDrops();
                    return true;
                })) return false;
                Files.writeString(Path.of(System.getProperty("worldeditpp.test.report")).resolveSibling("chest-history.txt"),
                        "4096 chest blocks / 2048 double chests; 27 full NBT-marked stacks per half.\n"
                        + "Client //copy -> //paste -> /undo -> /redo -> //undo -> //redo -> //set stone -> /undo -> /redo.\n"
                        + "Then //set chest -> fill all live inventories after history capture -> /undo.\n"
                        + "All block states, 110592 inventory slots and source inventories verified; 0 item spawns including delayed window.\n");
                return true;
            }
            default -> throw new AssertionError("Unknown chest phase " + phase);
        }
        return false;
    }

    private static boolean awaitState(Minecraft mc, String expected) {
        return poll(mc, () -> {
            if (IncrementalJobScheduler.instance().busy(player.getUUID())) return false;
            var state = player.serverLevel().getBlockState(TARGET.offset(63, 0, 63));
            if (!state.is(expected.equals("air") ? Blocks.AIR : expected.equals("stone") ? Blocks.STONE : Blocks.CHEST))
                return false;
            verify(TARGET, expected);
            BuildingStressFixture.verifyNoDrops();
            return true;
        });
    }

    private static void verify(BlockPos origin, String expected) {
        for (int z = 0; z < 64; z++) for (int x = 0; x < 64; x++) {
            var pos = origin.offset(x, 0, z);
            var expectedState = expected.equals("air") ? Blocks.AIR.defaultBlockState()
                    : expected.equals("stone") ? Blocks.STONE.defaultBlockState()
                    : Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE,
                            x % 2 == 0 ? ChestType.LEFT : ChestType.RIGHT);
            if (player.serverLevel().getBlockState(pos) != expectedState)
                throw new AssertionError("Chest state mismatch in phase " + phase + " at " + pos);
            if (!expected.equals("chest")) continue;
            if (!(player.serverLevel().getBlockEntity(pos) instanceof Container container))
                throw new AssertionError("Chest entity missing at " + pos);
            for (int slot = 0; slot < 27; slot++) {
                var stack = container.getItem(slot);
                if (!stack.is(Items.DIAMOND) || stack.getCount() != 64 || stack.getTag() == null
                        || !stack.getTag().contains("wepp_full_chest_slot") || stack.getTag().getInt("wepp_full_chest_slot") != slot)
                    throw new AssertionError("Chest inventory mismatch at " + pos + " slot=" + slot + " phase=" + phase);
            }
        }
    }

    private static boolean poll(Minecraft mc, Supplier<Boolean> check) {
        if (future == null) { future = mc.getSingleplayerServer().submit(check); return false; }
        if (!future.isDone()) return false;
        boolean result = future.join(); future = null; return result;
    }
    private static void command(Minecraft mc, String command) { mc.getConnection().sendCommand(command); }
    private static void next() { phase++; ticks = 0; }
}
