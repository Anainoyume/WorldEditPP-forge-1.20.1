package dev.worldeditpp.testing;

import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.ClientEditModeController;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionController;
import com.redawn.worldeditpp.bridge.minecraft.client.screen.BlockStateEditorScreen;
import com.redawn.worldeditpp.bridge.minecraft.client.tutorial.WorldEditPlusPlusTutorial;
import com.redawn.worldeditpp.bridge.minecraft.network.*;
import com.redawn.worldeditpp.mode.DrawMode;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Uses the real network connection; fixture setup alone runs directly on the integrated server. */
final class ClientPortScenario {
    private static int phase, ticks;
    private static BlockPos first, last, paste;
    private static CompletableFuture<Void> fixture;
    private static double originalGamma;
    private static final double TEST_GAMMA = 0.83D;
    private static final double FAR_X = 25_000_000.5D;
    private static final double FAR_Z = -25_000_000.5D;

    static boolean tick(Minecraft mc) throws Exception {
        ticks++;
        if (ticks > 800) throw new AssertionError("Client business scenario timed out at phase " + phase);
        switch (phase) {
            case 0 -> {
                require(!ClientEditModeController.isEditing(), "Welcome guide starts outside edit mode");
                var chat = new net.minecraft.client.gui.screens.ChatScreen("");
                mc.setScreen(chat);
                require(chat.handleComponentClicked(net.minecraft.network.chat.Style.EMPTY.withClickEvent(
                        new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                com.redawn.worldeditpp.bridge.minecraft.client.tutorial.WelcomeMessageController.OPEN_COMMAND))),
                        "Welcome chat link must be handled by the production Screen mixin");
                require(mc.screen instanceof com.redawn.worldeditpp.bridge.minecraft.client.tutorial.TutorialScreen,
                        "Welcome link must open the guide outside edit mode");
                mc.screen.tick();
                require(mc.screen instanceof com.redawn.worldeditpp.bridge.minecraft.client.tutorial.TutorialScreen,
                        "Guide must remain open on the next tick outside edit mode");
                mc.screen.onClose();
                originalGamma = mc.options.gamma().get();
                mc.options.gamma().set(TEST_GAMMA);
                var id = mc.player.getUUID();
                double y = mc.player.getY();
                fixture = mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    player.teleportTo(FAR_X, y, FAR_Z);
                });
                next();
            }
            case 1 -> {
                if (!fixture.isDone()
                        || Math.abs(mc.player.getX() - FAR_X) > 1.0D
                        || Math.abs(mc.player.getZ() - FAR_Z) > 1.0D) return false;
                fixture.join();
                PlatformBridge.sendToServer(new ToggleEditModePayload("test"));
                next();
            }
            case 2 -> {
                if (!ClientEditModeController.isEditing()) return false;
                require(!mc.player.isSpectator() && mc.player.noPhysics, "Edit mode must provide noclip without spectator");
                require(Math.abs(mc.options.gamma().get() - TEST_GAMMA) < 1.0E-9,
                        "Entering edit mode must not overwrite the player's vanilla brightness");
                first = mc.player.blockPosition().offset(-1, 1, 3);
                last = first.offset(2, 1, 1);
                var id = mc.player.getUUID();
                fixture = mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    for (var pos : BlockPos.betweenClosed(first, last)) player.serverLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_STAIRS, 64));
                    player.inventoryMenu.broadcastChanges();
                });
                mc.player.setYRot(0);
                mc.player.setXRot(0);
                next();
            }
            case 3 -> {
                if (!fixture.isDone()) return false;
                fixture.join();
                PlatformBridge.sendToServer(new WandClickPayload(first.getX(), first.getY(), first.getZ(), true));
                PlatformBridge.sendToServer(new WandClickPayload(last.getX(), last.getY(), last.getZ(), false));
                next();
            }
            case 4 -> {
                if (ticks < 10 || !SelectionClientController.hasCuboidSelection()) return false;
                var box = SelectionClientController.currentSelectionBox();
                require(box.minX() == first.getX() && box.maxX() == last.getX(), "Selection corners must synchronize");
                PlatformBridge.sendToServer(new DrawSelectionPayload(Direction.UP, false, DrawMode.SET));
                next();
            }
            case 5 -> {
                if (!all(mc, false)) return false;
                PlatformBridge.sendToServer(new WorldEditHistoryPayload(WorldEditHistoryPayload.Action.UNDO));
                next();
            }
            case 6 -> {
                if (!all(mc, true)) return false;
                PlatformBridge.sendToServer(new WorldEditHistoryPayload(WorldEditHistoryPayload.Action.REDO));
                next();
            }
            case 7 -> {
                if (!all(mc, false)) return false;
                paste = first.offset(5, 3, 4);
                if (Boolean.getBoolean("worldeditpp.test.cloudProbe")) {
                    // Float the projection just above the cloud layer so it can occlude clouds.
                    paste = new BlockPos(paste.getX(), 195, paste.getZ());
                }
                fixture = mc.getSingleplayerServer().submit(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    if (Boolean.getBoolean("worldeditpp.test.glassProbe")) {
                        // Replace the entire camera-facing minimum-Z face with glass so the
                        // diagnostic view looks through it at the opaque stairs behind.
                        for (var pos : BlockPos.betweenClosed(first, last)) {
                            if (pos.getZ() == first.getZ() && !pos.equals(first)) {
                                level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
                            }
                        }
                    }
                    level.setBlockAndUpdate(last, Blocks.CHEST.defaultBlockState());
                    var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(last);
                    chest.setItem(3, new ItemStack(Items.DIAMOND, 17));
                    chest.setChanged();
                });
                next();
            }
            case 8 -> {
                if (!fixture.isDone()) return false;
                fixture.join();
                SelectionClientController.requestClipboardCopy();
                SelectionClientController.requestClipboardAdjustment();
                next();
            }
            case 9 -> {
                var projection = SelectionBlockProjectionController.state();
                if (!projection.isReady() || !projection.hasSnapshot() || ticks < 40) return false;
                require(projection.sizeX() == 3 && projection.sizeY() == 2 && projection.sizeZ() == 2,
                        "Clipboard snapshot must preserve all dimensions");
                require(ProjectionIncrementalProbe.blockEntityCount(projection) == 1, "Projection must instantiate the captured chest");
                if (!ProjectionPlacementProbe.tick(mc, paste)) return false;
                screenshot(mc, "projection.png");
                PlatformBridge.sendToServer(ClipboardAdjustmentPayload.confirm(paste.getX(), paste.getY(), paste.getZ(), false));
                next();
            }
            case 10 -> {
                if (!mc.level.getBlockState(paste.offset(2, 1, 1)).is(Blocks.CHEST)
                        || SelectionClientController.hasClipboardAdjustment() || ticks < 20) return false;
                for (var pos : BlockPos.betweenClosed(paste, paste.offset(2, 1, 1))) {
                    boolean expectedGlass = Boolean.getBoolean("worldeditpp.test.glassProbe")
                            && pos.getZ() == paste.getZ()
                            && !pos.equals(paste)
                            && !pos.equals(paste.offset(2, 1, 1));
                    require(mc.level.getBlockState(pos).is(pos.equals(paste.offset(2, 1, 1))
                                    ? Blocks.CHEST : expectedGlass ? Blocks.GLASS : Blocks.OAK_STAIRS),
                            "Clipboard placement must preserve every block");
                }
                // The render probe walks around the displaced clipboard; return within editing reach.
                mc.player.moveTo(first.getX() + 0.5, first.getY(), first.getZ() - 2.5, 0, 0);
                fixture = mc.getSingleplayerServer().submit(() -> {
                    var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity)
                            mc.getSingleplayerServer().overworld().getBlockEntity(paste.offset(2, 1, 1));
                    require(chest != null && chest.getItem(3).is(Items.DIAMOND) && chest.getItem(3).getCount() == 17,
                            "Network clipboard paste must retain chest inventory NBT");
                });
                next();
            }
            case 11 -> {
                if (!fixture.isDone() || SelectionClientController.hasClipboardAdjustment() || ticks < 10) return false;
                fixture.join();
                mc.hitResult = new BlockHitResult(Vec3.atCenterOf(first), Direction.NORTH, first, false);
                BlockStateEditorScreen.openTarget();
                require(mc.screen instanceof BlockStateEditorScreen, "Block state editor must open");
                PlatformBridge.sendToServer(new BlockStateEditPayload(123, 1, mc.level.dimension().location(), first,
                        BuiltInRegistries.BLOCK.getKey(Blocks.OAK_STAIRS), "facing", "west"));
                next();
            }
            case 12 -> {
                if (mc.level.getBlockState(first).getValue(StairBlock.FACING) != Direction.WEST || ticks < 20) return false;
                screenshot(mc, "block-state-editor.png");
                mc.setScreen(null);
                WorldEditPlusPlusTutorial.open(null);
                next();
            }
            case 13 -> {
                if (ticks < 30) return false;
                screenshot(mc, "tutorial.png");
                mc.setScreen(null);
                var info = net.minecraftforge.fml.ModList.get().getModContainerById("worldeditpp").orElseThrow().getModInfo();
                var factory = net.minecraftforge.client.ConfigScreenHandler.getScreenFactoryFor(info).orElseThrow();
                mc.setScreen(factory.apply(mc, null));
                next();
            }
            case 14 -> {
                if (ticks < 20) return false;
                require(mc.screen != null, "Forge mod configuration screen must render");
                screenshot(mc, "mod-configuration.png");
                mc.screen.onClose();
                PlatformBridge.sendToServer(new ToggleEditModePayload("test"));
                next();
            }
            case 15 -> {
                if (ClientEditModeController.isEditing() || ticks < 10) return false;
                require(!mc.player.noPhysics, "Leaving edit mode must restore collision");
                require(Math.abs(mc.options.gamma().get() - TEST_GAMMA) < 1.0E-9,
                        "Leaving edit mode must restore the player's vanilla brightness");
                if (!ProjectionIncrementalProbe.tick(mc)) return false;
                next();
            }
            case 16 -> {
                if (!ProjectionStressProbe.tick(mc)) return false;
                mc.options.gamma().set(originalGamma);
                return true;
            }
            default -> throw new AssertionError("Unknown test phase");
        }
        return false;
    }

    private static boolean all(Minecraft mc, boolean air) {
        for (var pos : BlockPos.betweenClosed(first, last)) {
            var block = mc.level.getBlockState(pos);
            if (air ? !block.isAir() : !block.is(Blocks.OAK_STAIRS)) return false;
        }
        return true;
    }
    private static void next() { phase++; ticks = 0; }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void screenshot(Minecraft mc, String name) throws Exception {
        Path path = Path.of(System.getProperty("worldeditpp.test.report")).resolveSibling(name);
        try (var pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { pixels.writeToFile(path); }
    }
}
