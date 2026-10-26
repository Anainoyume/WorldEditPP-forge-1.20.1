package dev.worldeditpp.testing;

import com.mojang.blaze3d.platform.InputConstants;
import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.ClientEditModeController;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.EditSubModeState;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.WorldEditPlusPlusKeyMappings;
import com.redawn.worldeditpp.bridge.minecraft.item.ModItems;
import com.redawn.worldeditpp.bridge.minecraft.network.ToggleEditModePayload;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.redawn.worldeditpp.mode.EditSubMode;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;

/** Isolated real-client regressions: vanilla input dispatch, prediction and server acknowledgement. */
final class InputInteractionProbe {
    private static final List<String> results = new ArrayList<>();
    private static final List<String> failures = new ArrayList<>();
    private static int phase, ticks, mode, slot, target;
    private static boolean done;
    private static BlockPos origin;
    private static CompletableFuture<List<String>> serverCheck;
    private static CompletableFuture<Void> fixture;
    private static CompoundTag clientBefore;
    private static Object clientEntity;

    static boolean tick(Minecraft mc) throws Exception {
        if (done) return true;
        if (++ticks > 200) throw new AssertionError("Input probe timeout phase=" + phase);
        switch (phase) {
            case 0 -> {
                mc.setScreen(null);
                if (GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS)
                    throw new AssertionError("Test requires Alt released before plain hotbar checks");
                KeyMapping.releaseAll();
                KeyMapping.resetMapping(); // options load / rebind rebuilds the Forge lookup in HashMap order
                slot = 0;
                next();
            }
            case 1 -> {
                mc.player.getInventory().selected = (slot + 1) % 9;
                press(mc, GLFW.GLFW_KEY_1 + slot);
                next();
            }
            case 2 -> {
                check(mc.player.getInventory().selected == slot, label() + " plain hotbar " + (slot + 1));
                if (++slot < 9) { phase = 1; ticks = 0; }
                else next();
            }
            case 3 -> {
                // Inactive editor remaps must not reserve another vanilla hotbar key either.
                var mapping = WorldEditPlusPlusKeyMappings.SUB_MODE_STRETCH.get();
                var previous = mapping.getKey();
                try {
                    mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_5));
                    KeyMapping.resetMapping();
                    var vanilla = mc.options.keyHotbarSlots[4];
                    while (vanilla.consumeClick()) { }
                    press(mc, GLFW.GLFW_KEY_5);
                    check(vanilla.consumeClick(), label() + " remapped inactive Alt+5 falls through");
                } finally {
                    mapping.setKey(previous);
                    KeyMapping.resetMapping();
                }
                // X is also vanilla's creative load-hotbar key; plain X outside editing must survive.
                if (mode == 0) {
                    while (mc.options.keyLoadHotbarActivator.consumeClick()) { }
                    press(mc, GLFW.GLFW_KEY_X);
                    check(mc.options.keyLoadHotbarActivator.consumeClick(), "normal vanilla X click");
                }
                verifyShortcutContracts(mc);
                origin = mc.player.blockPosition().offset(-1, 1, 2);
                var id = mc.player.getUUID();
                mc.player.getInventory().selected = 0;
                fixture = mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    var level = player.serverLevel();
                    player.getInventory().selected = 0;
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WORLD_EDIT_WAND.get()));
                    player.inventoryMenu.broadcastChanges();
                    for (int i = 0; i < 3; i++) {
                        var pos = origin.offset(i, 0, 0);
                        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(pos, (i == 0 ? Blocks.WHITE_BANNER : i == 1 ? Blocks.OAK_SIGN : Blocks.CHEST).defaultBlockState());
                        var entity = level.getBlockEntity(pos);
                        if (entity instanceof BannerBlockEntity banner) {
                            var item = new ItemStack(Items.WHITE_BANNER);
                            item.getOrCreateTagElement("BlockEntityTag").put("Patterns",
                                    new BannerPattern.Builder().addPattern(BannerPatterns.CREEPER, DyeColor.BLACK).toListTag());
                            item.setHoverName(Component.literal("Wand NBT regression"));
                            banner.fromItem(item, DyeColor.WHITE);
                        } else if (entity instanceof SignBlockEntity sign) {
                            sign.setText(sign.getFrontText().setMessage(0, Component.literal("KEEP THIS TEXT")), true);
                        } else if (entity instanceof ChestBlockEntity chest) {
                            var diamonds = new ItemStack(Items.DIAMOND, 37);
                            diamonds.getOrCreateTag().putString("probe", "wand-nbt");
                            chest.setItem(4, diamonds);
                            chest.setCustomName(Component.literal("KEEP THIS INVENTORY"));
                        }
                        entity.setChanged();
                        // Fixture-only full data sync also makes client-side chest loss observable.
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(level, pos));
                        player.connection.send(ClientboundBlockEntityDataPacket.create(entity, e -> e.saveWithFullMetadata()));
                    }
                });
                next();
            }
            case 4 -> {
                if (!fixture.isDone() || ticks < 15 || !mc.player.getMainHandItem().is(ModItems.WORLD_EDIT_WAND.get())) return false;
                fixture.join();
                target = 0;
                next();
            }
            case 5 -> {
                var pos = origin.offset(target, 0, 0);
                var entity = mc.level.getBlockEntity(pos);
                if (entity == null) throw new AssertionError("Missing NBT fixture at " + pos);
                clientBefore = entity.saveWithFullMetadata();
                clientEntity = entity;
                if (target == 0) check(clientBefore.getList("Patterns", 10).size() == 1, label() + " patterned banner fixture");
                // Same production prediction / packet entry used by Minecraft.startAttack().
                mc.gameMode.startDestroyBlock(pos, Direction.NORTH);
                check(mc.level.getBlockEntity(pos) == clientEntity, label() + " immediate entity retained " + target);
                next();
            }
            case 6 -> {
                if (ticks < 15) return false;
                var pos = origin.offset(target, 0, 0);
                var entity = mc.level.getBlockEntity(pos);
                check(entity != null && clientBefore.equals(entity.saveWithFullMetadata()),
                        label() + " client NBT after acknowledgement " + target);
                var id = mc.player.getUUID();
                var expected = clientBefore.copy();
                serverCheck = mc.getSingleplayerServer().submit(() -> {
                    var errors = new ArrayList<String>();
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    var serverEntity = player.serverLevel().getBlockEntity(pos);
                    if (serverEntity == null || !expected.equals(serverEntity.saveWithFullMetadata())) errors.add("server NBT " + pos);
                    var actor = ForgeAdapter.adaptPlayer(player);
                    var selector = WorldEdit.getInstance().getSessionManager().get(actor).getRegionSelector(actor.getWorld());
                    try {
                        if (!selector.getPrimaryPosition().equals(BlockVector3.at(pos.getX(), pos.getY(), pos.getZ()))) errors.add("pos1 was not selected " + pos);
                    } catch (Exception e) { errors.add("pos1 unavailable: " + e); }
                    return errors;
                });
                next();
            }
            case 7 -> {
                if (!serverCheck.isDone()) return false;
                var errors = serverCheck.join();
                check(errors.isEmpty(), label() + " server NBT and real WorldEdit pos1 " + target + " " + errors);
                if (++target < 3) { phase = 5; ticks = 0; }
                else { phase = 9; ticks = 0; }
            }
            case 8 -> {
                if (ClientEditModeController.isEditing() != (mode == 0) || ticks < 10) return false;
                if (mode++ == 0) { phase = 0; ticks = 0; }
                else { phase = 13; ticks = 0; }
            }
            case 9 -> {
                var id = mc.player.getUUID();
                fixture = mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    player.serverLevel().setBlockAndUpdate(origin.offset(0, 1, 0), Blocks.OAK_LOG.defaultBlockState());
                });
                next();
            }
            case 10 -> {
                if (!fixture.isDone() || ticks < 10) return false;
                fixture.join();
                var pos = origin.offset(0, 1, 0);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
                check(mc.level.getBlockState(pos).is(Blocks.OAK_LOG), label() + " right wand must not predict axe stripping");
                next();
            }
            case 11 -> {
                if (ticks < 15) return false;
                var pos = origin.offset(0, 1, 0);
                check(mc.level.getBlockState(pos).is(Blocks.OAK_LOG), label() + " right wand log after acknowledgement");
                var id = mc.player.getUUID();
                serverCheck = mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    var errors = new ArrayList<String>();
                    if (!player.serverLevel().getBlockState(pos).is(Blocks.OAK_LOG)) errors.add("log changed on server");
                    var actor = ForgeAdapter.adaptPlayer(player);
                    try {
                        var region = WorldEdit.getInstance().getSessionManager().get(actor).getSelection(actor.getWorld());
                        if (!region.getMaximumPoint().equals(BlockVector3.at(origin.getX() + 2, pos.getY(), origin.getZ()))) errors.add("pos2 not selected");
                    } catch (Exception e) { errors.add("pos2 unavailable: " + e); }
                    return errors;
                });
                next();
            }
            case 12 -> {
                if (!serverCheck.isDone()) return false;
                check(serverCheck.join().isEmpty(), label() + " real right-click WorldEdit pos2 " + serverCheck.join());
                PlatformBridge.sendToServer(new ToggleEditModePayload("input-regression"));
                phase = 8; ticks = 0;
            }
            case 13 -> {
                var id = mc.player.getUUID();
                fixture = mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_AXE));
                    player.inventoryMenu.broadcastChanges();
                    player.serverLevel().setBlockAndUpdate(origin.offset(1, 1, 0), Blocks.STONE.defaultBlockState());
                });
                next();
            }
            case 14 -> {
                if (!fixture.isDone() || ticks < 10 || !mc.player.getMainHandItem().is(Items.WOODEN_AXE)) return false;
                fixture.join();
                mc.gameMode.startDestroyBlock(origin.offset(1, 1, 0), Direction.NORTH);
                var pos = origin.offset(0, 1, 0);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
                check(mc.level.getBlockState(origin.offset(1, 1, 0)).isAir(), "ordinary axe still predicts breaking");
                check(mc.level.getBlockState(pos).is(Blocks.STRIPPED_OAK_LOG), "ordinary axe still predicts stripping");
                next();
            }
            case 15 -> {
                if (ticks < 15) return false;
                var id = mc.player.getUUID();
                serverCheck = mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    var errors = new ArrayList<String>();
                    if (!player.serverLevel().getBlockState(origin.offset(1, 1, 0)).isAir()) errors.add("ordinary axe break denied");
                    if (!player.serverLevel().getBlockState(origin.offset(0, 1, 0)).is(Blocks.STRIPPED_OAK_LOG)) errors.add("ordinary axe use denied");
                    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WORLD_EDIT_WAND.get()));
                    player.inventoryMenu.broadcastChanges();
                    return errors;
                });
                next();
            }
            case 16 -> {
                if (!serverCheck.isDone() || ticks < 10 || mc.gameMode.getPlayerMode() != net.minecraft.world.level.GameType.SURVIVAL
                        || !mc.player.getMainHandItem().is(ModItems.WORLD_EDIT_WAND.get())) return false;
                check(serverCheck.join().isEmpty(), "ordinary axe server break/use " + serverCheck.join());
                clientEntity = mc.level.getBlockEntity(origin);
                clientBefore = mc.level.getBlockEntity(origin).saveWithFullMetadata();
                mc.gameMode.startDestroyBlock(origin, Direction.NORTH);
                next();
            }
            case 17 -> {
                mc.gameMode.continueDestroyBlock(origin, Direction.NORTH);
                if (ticks < 20) return false;
                check(mc.level.getBlockEntity(origin) == clientEntity
                                && clientBefore.equals(mc.level.getBlockEntity(origin).saveWithFullMetadata()),
                        "survival wand held attack retains banner NBT");
                mc.gameMode.stopDestroyBlock();
                var expected = clientBefore.copy();
                var id = mc.player.getUUID();
                serverCheck = mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    var errors = new ArrayList<String>();
                    var banner = player.serverLevel().getBlockEntity(origin);
                    if (banner == null || !expected.equals(banner.saveWithFullMetadata())) errors.add("survival server banner changed");
                    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.inventoryMenu.broadcastChanges();
                    return errors;
                });
                next();
            }
            case 18 -> {
                if (!serverCheck.isDone() || ticks < 10 || mc.gameMode.getPlayerMode() != net.minecraft.world.level.GameType.CREATIVE) return false;
                check(serverCheck.join().isEmpty(), "survival server NBT " + serverCheck.join());
                Files.write(Path.of(System.getProperty("worldeditpp.test.report")).resolveSibling("input-interaction.txt"), results);
                if (!failures.isEmpty()) throw new AssertionError("Input regressions: " + failures);
                done = true;
                return true;
            }
            default -> throw new AssertionError("Unknown input probe phase " + phase);
        }
        return false;
    }

    private static void press(Minecraft mc, int key) {
        mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), key, 0, GLFW.GLFW_PRESS, 0);
        mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), key, 0, GLFW.GLFW_RELEASE, 0);
    }

    private static void verifyShortcutContracts(Minecraft mc) {
        var beforeMode = EditSubModeState.current();
        var hwnd = new WinDef.HWND(Pointer.createConstant(GLFWNativeWin32.glfwGetWin32Window(mc.getWindow().getWindow())));
        try {
            User32.INSTANCE.PostMessage(hwnd, 0x0100, new WinDef.WPARAM(0xA4), new WinDef.LPARAM(1L | (0x38L << 16)));
            GLFW.glfwPollEvents();
            if (GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT) != GLFW.GLFW_PRESS)
                throw new AssertionError("Test Alt fixture was not delivered to GLFW");
            var modes = new EditSubMode[]{EditSubMode.MOVE, EditSubMode.STRETCH, EditSubMode.ROTATE};
            for (int i = 0; i < 3; i++) {
                while (mc.options.keyHotbarSlots[i].consumeClick()) { }
                press(mc, GLFW.GLFW_KEY_1 + i);
                check(EditSubModeState.current() == (mode == 0 ? beforeMode : modes[i]), label() + " Alt+" + (i + 1) + " submode");
                check(mc.options.keyHotbarSlots[i].consumeClick() == (mode == 0), label() + " Alt+" + (i + 1) + " fallback/exclusion");
            }
        } finally {
            User32.INSTANCE.SendMessage(hwnd, 0x0101, new WinDef.WPARAM(0xA4), new WinDef.LPARAM(1L | (0x38L << 16) | 0xC0000000L));
            GLFW.glfwPollEvents();
            if (GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS)
                throw new AssertionError("Test Alt fixture did not release GLFW state");
            KeyMapping.releaseAll();
        }
        var draw = WorldEditPlusPlusKeyMappings.PREPARE_DRAW.get();
        var original = draw.getKey();
        try {
            draw.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_7));
            KeyMapping.resetMapping();
            while (mc.options.keyHotbarSlots[6].consumeClick()) { }
            mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), GLFW.GLFW_KEY_7, 0, GLFW.GLFW_PRESS, 0);
            check(draw.isDown(), label() + " remapped draw held");
            check(mc.options.keyHotbarSlots[6].isDown() == (mode == 0), label() + " vanilla held-key isolation");
            check(mc.options.keyHotbarSlots[6].consumeClick() == (mode == 0), label() + " vanilla click isolation");
            mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), GLFW.GLFW_KEY_7, 0, GLFW.GLFW_RELEASE, 0);
            check(!draw.isDown() && !mc.options.keyHotbarSlots[6].isDown(), label() + " all shared mappings released");
        } finally {
            draw.setKey(original);
            KeyMapping.resetMapping();
            KeyMapping.releaseAll();
        }
        if (mode == 1) EditSubModeState.switchTo(EditSubMode.MOVE);
    }

    private static String label() { return mode == 0 ? "normal" : "editing"; }
    private static void next() { phase++; ticks = 0; }
    private static void check(boolean passed, String message) {
        results.add((passed ? "PASS " : "FAIL ") + message);
        if (!passed) failures.add(message);
    }
    private InputInteractionProbe() { }
}
