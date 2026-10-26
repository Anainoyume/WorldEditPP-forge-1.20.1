package dev.worldeditpp.testing;

import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;
import com.redawn.worldeditpp.bridge.minecraft.api.client.ClientInput;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.ClientEditModeController;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.EditSubModeState;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionController;
import com.redawn.worldeditpp.bridge.minecraft.network.ToggleEditModePayload;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditBridge;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditPlatform;
import com.redawn.worldeditpp.bridge.worldedit.operation.IncrementalJobScheduler;
import com.redawn.common.selection.SelectionBox;
import com.redawn.common.selection.SelectionVector;
import com.redawn.worldeditpp.mode.EditSubMode;
import com.sk89q.worldedit.LocalSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Real-client coverage of every 1.20.1 block-entity inventory through the production edit paths:
 * {@code //set}, adjustment-mode stretch, adjustment-mode move, {@code //copy}/{@code //paste},
 * and command undo/redo. Every step fails on a single spawned {@link net.minecraft.world.entity.item.ItemEntity}.
 */
@Mod.EventBusSubscriber(modid = TestHarnessMod.ID, value = Dist.CLIENT)
final class ContainerClientProbe {
    private static final BlockPos ORIGIN = new BlockPos(0, 80, 0);
    private static final int W = ContainerStressFixture.SIZE_X;
    private static final int H = ContainerStressFixture.SIZE_Y;
    private static final int D = ContainerStressFixture.SIZE_Z;
    private static final BlockPos MOVE_MIN = ORIGIN.offset(0, 10, 0);
    private static final BlockPos CLIP_MIN = ORIGIN.offset(0, 0, 32);
    private static final BlockPos CLIP_PLAYER_SOURCE = new BlockPos(12, 81, 0);
    private static final BlockPos CLIP_PLAYER_TARGET = new BlockPos(12, 81, 32);

    private static int phase;
    private static int ticks;
    private static final java.util.Map<String, CompletableFuture<Boolean>> futures = new java.util.HashMap<>();
    private static ServerPlayer player;
    private static ServerLevel level;
    private static LocalSession session;
    private static UUID owner;
    private static boolean pressedMove;
    private static List<ContainerStressFixture.Cell> sourceCells;
    private static List<ContainerStressFixture.Cell> voidCells;

    static boolean tick(Minecraft mc) throws Exception {
        // Fail the phase within ~30 s instead of leaving the window open for minutes.
        if (++ticks > 600) {
            throw new AssertionError("Container phase timed out: " + phase + " ticks=" + ticks);
        }
        switch (phase) {
            case 0 -> {
                if (!server(mc, () -> {
                    owner = mc.player.getUUID();
                    player = mc.getSingleplayerServer().getPlayerList().getPlayer(owner);
                    if (player == null) throw new AssertionError("Container player missing");
                    level = player.serverLevel();
                    session = WorldEditBridge.worldEdit().getSessionManager().get(WorldEditPlatform.adaptPlayer(player));
                    session.clearHistory();
                    player.getAbilities().mayfly = true;
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(level, 7.5, 84.0, 7.5, 0.0F, 30.0F);
                    ContainerStressFixture.populate(level, ORIGIN);
                    ContainerStressFixture.verifyNoItemEntities(level, "setup");
                    BuildingStressFixture.calibrate(level);
                    sourceCells = ContainerStressFixture.capture(level, ORIGIN, W, H, D);
                    voidCells = ContainerStressFixture.captureChest(level, ContainerStressFixture.VOID_CHEST);
                    return true;
                })) return false;
                PlatformBridge.sendToServer(new ToggleEditModePayload("container"));
                next();
            }
            case 1 -> {
                if (!ClientEditModeController.isEditing()) return false;
                emptyHand(mc);
                command(mc, "/pos1 0,80,0");
                command(mc, "/pos2 15,81,15");
                next();
            }
            case 2 -> {
                if (!selectionMatches(mc, 0, 80, 0, 15, 81, 15)) return false;
                command(mc, "/set stone");
                next();
            }
            case 3 -> {
                if (!server(mc, () -> {
                    if (IncrementalJobScheduler.instance().busy(owner)) return false;
                    if (!level.getBlockState(ORIGIN.offset(6, 1, 0)).is(Blocks.STONE)) return false;
                    noDrops("after set");
                    return true;
                })) return false;
                command(mc, "/undo");
                next();
            }
            case 4 -> {
                if (!server(mc, () -> sourceRestored("after set undo"))) return false;
                command(mc, "/redo");
                next();
            }
            case 5 -> {
                if (!server(mc, () -> {
                    if (IncrementalJobScheduler.instance().busy(owner)) return false;
                    if (!level.getBlockState(ORIGIN.offset(6, 1, 0)).is(Blocks.STONE)) return false;
                    noDrops("after redo");
                    return true;
                })) return false;
                command(mc, "/undo");
                next();
            }
            case 6 -> {
                if (!server(mc, () -> sourceRestored("after second undo"))) return false;
                SelectionClientController.requestSelectionAdjustment();
                next();
            }
            case 7 -> {
                var projection = SelectionBlockProjectionController.state();
                if (!SelectionClientController.isClipboardAdjustmentActive() || !projection.hasSnapshot()
                        || !projection.isReady() || projection.sizeX() != W || projection.sizeY() != H
                        || projection.sizeZ() != D) return false;
                EditSubModeState.switchTo(EditSubMode.STRETCH);
                if (!aimAtEastFace(mc)) return false;
                next();
            }
            case 8 -> {
                if (SelectionClientController.selectedFace() == null) {
                    press(mc, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                    return false;
                }
                if (SelectionClientController.selectedFace().ordinal() != 1)
                    throw new AssertionError("Stretch selected wrong face: " + SelectionClientController.selectedFace());
                Camera camera = mc.gameRenderer.getMainCamera();
                Vec3 eye = camera.getPosition();
                Vec3 look = new Vec3(camera.getLookVector());
                boolean nudged = SelectionClientController.tryNudgeSelection(-1.0,
                        new SelectionVector(eye.x, eye.y, eye.z), new SelectionVector(look.x, look.y, look.z));
                if (!nudged) throw new AssertionError("Production stretch nudge was rejected");
                SelectionBox box = SelectionClientController.currentSelectionBox();
                if (box.maxX() != 31) throw new AssertionError("Stretched box is " + box);
                next();
            }
            case 9 -> {
                var projection = SelectionBlockProjectionController.state();
                if (!projection.isReady() || projection.sizeX() != W * 2) return false;
                EditSubModeState.switchTo(EditSubMode.MOVE);
                SelectionClientController.confirmClipboardAdjustment(false);
                next();
            }
            case 10 -> {
                if (!server(mc, () -> {
                    if (!placementDone()) return false;
                    if (IncrementalJobScheduler.instance().busy(owner)) return false;
                    ContainerStressFixture.verify(level,
                            ContainerStressFixture.repeatedX(sourceCells, W), "after stretch paste");
                    noDrops("after stretch paste");
                    return true;
                })) return false;
                command(mc, "/undo");
                next();
            }
            case 11 -> {
                if (!server(mc, () -> {
                    if (IncrementalJobScheduler.instance().busy(owner)) return false;
                    if (!level.getBlockState(ORIGIN.offset(20, 1, 0)).isAir()) return false;
                    ContainerStressFixture.verify(level, sourceCells, "after stretch undo");
                    ContainerStressFixture.verifyAir(level, ORIGIN.offset(W, 0, 0), W, H, D, "stretch undo");
                    noDrops("after stretch undo");
                    return true;
                })) return false;
                command(mc, "/redo");
                next();
            }
            case 12 -> {
                if (!server(mc, () -> {
                    if (IncrementalJobScheduler.instance().busy(owner)) return false;
                    if (level.getBlockState(ORIGIN.offset(20, 1, 0)).isAir()) return false;
                    ContainerStressFixture.verify(level,
                            ContainerStressFixture.repeatedX(sourceCells, W), "after stretch redo");
                    noDrops("after stretch redo");
                    return true;
                })) return false;
                command(mc, "/undo");
                next();
            }
            case 13 -> {
                if (!server(mc, () -> {
                    if (IncrementalJobScheduler.instance().busy(owner)) return false;
                    if (!level.getBlockState(ORIGIN.offset(20, 1, 0)).isAir()) return false;
                    ContainerStressFixture.verify(level, sourceCells, "after stretch cleanup");
                    ContainerStressFixture.verifyAir(level, ORIGIN.offset(W, 0, 0), W, H, D, "stretch cleanup");
                    noDrops("after stretch cleanup");
                    return true;
                })) return false;
                SelectionClientController.requestSelectionAdjustment();
                next();
            }
            case 14 -> {
                var projection = SelectionBlockProjectionController.state();
                if (!SelectionClientController.isClipboardAdjustmentActive() || !projection.isReady()
                        || projection.sizeX() != W) return false;
                EditSubModeState.switchTo(EditSubMode.MOVE);
                emptyHand(mc);
                if (!aimAtMoveArrow(mc)) return false;
                pressedMove = false;
                next();
            }
            case 15 -> {
                if (SelectionClientController.gizmoManager().hoveredElement()
                        != com.redawn.common.gizmo.GizmoElement.Y_ARROW) return false;
                if (!pressedMove) {
                    ClientInput.Button event = press(mc, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                    if (!event.consumed() || !SelectionClientController.gizmoManager().isHolded())
                        throw new AssertionError("Production gizmo press did not start a move drag");
                    pressedMove = true;
                }
                next();
            }
            case 16 -> {
                if (!teleportKeepingLook(mc, 12.5, 93.88, 8.5)) return false;
                next();
            }
            case 17 -> {
                SelectionBox box = SelectionClientController.currentSelectionBox();
                if (box.minY() != 90.0 || box.maxY() != 91.0) return false;
                release(mc, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                SelectionClientController.confirmClipboardAdjustment(false);
                next();
            }
            case 18 -> {
                if (!server(mc, () -> {
                    if (!placementDone()) return false;
                    if (IncrementalJobScheduler.instance().busy(owner)) return false;
                    ContainerStressFixture.verify(level,
                            ContainerStressFixture.shifted(sourceCells, 0, 10, 0), "after move paste");
                    noDrops("after move paste");
                    return true;
                })) return false;
                command(mc, "/undo");
                next();
            }
            case 19 -> {
                if (!server(mc, () -> {
                    if (IncrementalJobScheduler.instance().busy(owner)) return false;
                    if (!level.getBlockState(MOVE_MIN.offset(6, 1, 0)).isAir()) return false;
                    ContainerStressFixture.verify(level, sourceCells, "after move undo");
                    ContainerStressFixture.verifyAir(level, MOVE_MIN, W, H, D, "move undo");
                    noDrops("after move undo");
                    return true;
                })) return false;
                command(mc, "/redo");
                next();
            }
            case 20 -> {
                if (!server(mc, () -> {
                    if (IncrementalJobScheduler.instance().busy(owner)) return false;
                    if (level.getBlockState(MOVE_MIN.offset(6, 1, 0)).isAir()) return false;
                    ContainerStressFixture.verify(level,
                            ContainerStressFixture.shifted(sourceCells, 0, 10, 0), "after move redo");
                    noDrops("after move redo");
                    return true;
                })) return false;
                command(mc, "/undo");
                next();
            }
            case 21 -> {
                if (!server(mc, () -> {
                    if (IncrementalJobScheduler.instance().busy(owner)) return false;
                    if (!level.getBlockState(MOVE_MIN.offset(6, 1, 0)).isAir()) return false;
                    ContainerStressFixture.verify(level, sourceCells, "after move cleanup");
                    ContainerStressFixture.verifyAir(level, MOVE_MIN, W, H, D, "move cleanup");
                    noDrops("after move cleanup");
                    return true;
                })) return false;
                if (!server(mc, 1, () -> {
                    player.getAbilities().flying = true;
                    player.teleportTo(level, CLIP_PLAYER_SOURCE.getX() + 0.5, 81.0,
                            CLIP_PLAYER_SOURCE.getZ() + 0.5, 0.0F, 30.0F);
                    return true;
                })) return false;
                command(mc, "/pos1 0,80,0");
                command(mc, "/pos2 15,81,15");
                command(mc, "/copy");
                next();
            }
            case 22 -> {
                if (!server(mc, () -> {
                    try {
                        return session.getClipboard().getClipboard().getRegion().getVolume() == (long) W * H * D;
                    } catch (com.sk89q.worldedit.EmptyClipboardException waiting) {
                        return false;
                    }
                })) return false;
                if (!server(mc, 1, () -> {
                    player.getAbilities().flying = true;
                    player.teleportTo(level, CLIP_PLAYER_TARGET.getX() + 0.5, 81.0,
                            CLIP_PLAYER_TARGET.getZ() + 0.5, 0.0F, 30.0F);
                    return true;
                })) return false;
                next();
            }
            case 23 -> {
                if (ticks < 4) return false;
                command(mc, "/paste");
                next();
            }
            case 24 -> {
                if (!server(mc, () -> {
                    if (IncrementalJobScheduler.instance().busy(owner)) return false;
                    if (!level.getBlockState(CLIP_MIN.offset(6, 1, 0)).is(Blocks.CHEST)) return false;
                    ContainerStressFixture.verify(level,
                            ContainerStressFixture.shifted(sourceCells, 0, 0, 32), "after clipboard paste");
                    noDrops("after clipboard paste");
                    return true;
                })) return false;
                command(mc, "/undo");
                next();
            }
            case 25 -> {
                if (!server(mc, () -> {
                    if (IncrementalJobScheduler.instance().busy(owner)) return false;
                    if (!level.getBlockState(CLIP_MIN.offset(6, 1, 0)).isAir()) return false;
                    ContainerStressFixture.verify(level, sourceCells, "after clipboard undo");
                    ContainerStressFixture.verifyAir(level, CLIP_MIN, W, H, D, "clipboard undo");
                    noDrops("after clipboard undo");
                    return true;
                })) return false;
                command(mc, "/redo");
                next();
            }
            case 26 -> {
                if (!server(mc, () -> {
                    if (IncrementalJobScheduler.instance().busy(owner)) return false;
                    if (!level.getBlockState(CLIP_MIN.offset(6, 1, 0)).is(Blocks.CHEST)) return false;
                    ContainerStressFixture.verify(level,
                            ContainerStressFixture.shifted(sourceCells, 0, 0, 32), "after clipboard redo");
                    noDrops("after clipboard redo");
                    return true;
                })) return false;
                command(mc, "/undo");
                next();
            }
            case 27 -> {
                if (!server(mc, () -> {
                    if (IncrementalJobScheduler.instance().busy(owner)) return false;
                    if (!level.getBlockState(CLIP_MIN.offset(6, 1, 0)).isAir()) return false;
                    ContainerStressFixture.verify(level, sourceCells, "after clipboard cleanup");
                    ContainerStressFixture.verifyAir(level, CLIP_MIN, W, H, D, "clipboard cleanup");
                    noDrops("after clipboard cleanup");
                    return true;
                })) return false;
                command(mc, "/pos1 64,180,0");
                command(mc, "/pos2 64,180,0");
                command(mc, "/set air");
                next();
            }
            case 28 -> {
                if (!server(mc, () -> {
                    if (IncrementalJobScheduler.instance().busy(owner)) return false;
                    if (!level.getBlockState(ContainerStressFixture.VOID_CHEST).isAir()) return false;
                    noDrops("after void chest set air");
                    return true;
                })) return false;
                command(mc, "/undo");
                next();
            }
            case 29 -> {
                if (!server(mc, () -> {
                    if (IncrementalJobScheduler.instance().busy(owner)) return false;
                    if (level.getBlockState(ContainerStressFixture.VOID_CHEST).isAir()) return false;
                    ContainerStressFixture.verify(level, voidCells, "after void chest undo");
                    noDrops("after void chest undo");
                    return true;
                })) return false;
                command(mc, "/redo");
                next();
            }
            case 30 -> {
                if (!server(mc, () -> {
                    if (IncrementalJobScheduler.instance().busy(owner)) return false;
                    if (!level.getBlockState(ContainerStressFixture.VOID_CHEST).isAir()) return false;
                    noDrops("after void chest redo");
                    return true;
                })) return false;
                command(mc, "/undo");
                next();
            }
            case 31 -> {
                if (!server(mc, () -> {
                    if (IncrementalJobScheduler.instance().busy(owner)) return false;
                    if (level.getBlockState(ContainerStressFixture.VOID_CHEST).isAir()) return false;
                    ContainerStressFixture.verify(level, voidCells, "after void chest second undo");
                    ContainerStressFixture.verify(level, sourceCells, "final source");
                    BuildingStressFixture.verifyNoDrops();
                    ContainerStressFixture.verifyNoItemEntities(level, "final");
                    writeReport();
                    return true;
                })) return false;
                return true;
            }
            default -> throw new AssertionError("Unknown container phase " + phase);
        }
        return false;
    }

    private static boolean placementDone() {
        return !SelectionClientController.isClipboardAdjustmentPlacing()
                && !SelectionClientController.hasClipboardAdjustment();
    }

    private static boolean sourceRestored(String phase) {
        if (IncrementalJobScheduler.instance().busy(owner)) return false;
        if (level.getBlockState(ORIGIN.offset(6, 1, 0)).isAir()) return false;
        ContainerStressFixture.verify(level, sourceCells, phase);
        ContainerStressFixture.verifyNoItemEntities(level, phase);
        BuildingStressFixture.verifyNoDrops();
        return true;
    }

    private static void noDrops(String phase) {
        BuildingStressFixture.verifyNoDrops();
        ContainerStressFixture.verifyNoItemEntities(level, phase);
    }

    private static boolean selectionMatches(Minecraft mc, int minX, int minY, int minZ,
                                            int maxX, int maxY, int maxZ) {
        if (!SelectionClientController.hasWorldSelection()) return false;
        SelectionBox box = SelectionClientController.currentSelectionBox();
        return box.minX() == minX && box.minY() == minY && box.minZ() == minZ
                && box.maxX() == maxX && box.maxY() == maxY && box.maxZ() == maxZ;
    }

    private static void emptyHand(Minecraft mc) {
        mc.player.getInventory().selected = 0;
        mc.player.getInventory().setItem(0, ItemStack.EMPTY);
        // The server owns the synced inventory: clear its slot too, otherwise the next
        // container sync restores the held wand and the gizmo resets every frame.
        if (player != null) {
            mc.getSingleplayerServer().submit(() -> {
                player.getInventory().selected = 0;
                player.getInventory().setItem(0, ItemStack.EMPTY);
                return null;
            });
        }
    }

    private static ClientInput.Button press(Minecraft mc, int button) {
        ClientInput.Button event = new ClientInput.Button(button, GLFW.GLFW_PRESS);
        SelectionClientController.handleMouseButton(event);
        return event;
    }

    private static void release(Minecraft mc, int button) {
        SelectionClientController.handleMouseButton(new ClientInput.Button(button, GLFW.GLFW_RELEASE));
    }

    private static boolean aimAtEastFace(Minecraft mc) {
        return teleportLookingAt(mc, 21.5, 80.0, 8.5, 16.0, 81.0, 8.5);
    }

    private static boolean aimAtMoveArrow(Minecraft mc) {
        // Shallow diagonal view of the Y arrow: a near-vertical ray enters the shaft cylinder
        // beyond its segment and is rejected by the arrow hit test.
        return teleportLookingAt(mc, 12.5, 83.88, 8.5, 8.0, 81.5, 8.0);
    }

    private static boolean teleportLookingAt(Minecraft mc, double x, double y, double z,
                                             double tx, double ty, double tz) {
        double eyeY = y + 1.62;
        double dx = tx - x;
        double dy = ty - eyeY;
        double dz = tz - z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontal));
        if (!server(mc, () -> {
            player.getAbilities().flying = true;
            player.teleportTo(level, x, y, z, yaw, pitch);
            return true;
        })) return false;
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.setYHeadRot(yaw);
        return near(mc, x, y, z);
    }

    private static boolean teleportKeepingLook(Minecraft mc, double x, double y, double z) {
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        if (!server(mc, () -> {
            player.getAbilities().flying = true;
            player.teleportTo(level, x, y, z, yaw, pitch);
            return true;
        })) return false;
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        return near(mc, x, y, z);
    }

    private static boolean near(Minecraft mc, double x, double y, double z) {
        return Math.abs(mc.player.getX() - x) < 0.3
                && Math.abs(mc.player.getY() - y) < 0.3
                && Math.abs(mc.player.getZ() - z) < 0.3;
    }

    private static void writeReport() {
        try {
            Path report = Path.of(System.getProperty("worldeditpp.test.report"))
                    .resolveSibling("container-history.txt");
            Files.writeString(report,
                    "Container stress: " + ContainerStressFixture.containerCount()
                            + " block-entity containers (manual fills, 16 shulker colors, double chest,"
                            + " opened and unopened loot tables, furnaces, brewing stand, bookshelf,"
                            + " jukebox, campfires, lectern).\n"
                            + "Paths: //set stone -> undo/redo; adjustment stretch +X paste -> undo/redo;"
                            + " adjustment gizmo move +10Y paste -> undo/redo; //copy -> //paste -> undo/redo;"
                            + " floating chest over void -> //set air -> undo/redo.\n"
                            + "Every phase verified exact block state and item/NBT per slot, plus lazy"
                            + " LootTable preservation. 0 ItemEntity spawns and 0 live item entities.\n");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void command(Minecraft mc, String command) {
        mc.getConnection().sendCommand(command);
    }

    private static void next() {
        phase++;
        ticks = 0;
        futures.clear();
    }

    private static boolean server(Minecraft mc, Supplier<Boolean> check) {
        return server(mc, 0, check);
    }

    /**
     * Each (phase, slot) owns one future so two sequential checks in the same phase cannot
     * consume each other's result on the following tick.
     */
    private static boolean server(Minecraft mc, int slot, Supplier<Boolean> check) {
        String key = phase + ":" + slot;
        CompletableFuture<Boolean> pending = futures.get(key);
        if (pending == null) {
            futures.put(key, mc.getSingleplayerServer().submit(check));
            return false;
        }
        if (!pending.isDone()) return false;
        futures.remove(key);
        return pending.join();
    }

    private ContainerClientProbe() {}
}
