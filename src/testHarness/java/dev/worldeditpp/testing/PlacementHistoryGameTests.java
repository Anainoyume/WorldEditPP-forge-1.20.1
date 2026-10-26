package dev.worldeditpp.testing;

import com.mojang.authlib.GameProfile;
import com.redawn.worldeditpp.bridge.minecraft.mode.EditModeManager;
import com.redawn.worldeditpp.bridge.minecraft.network.ClipboardAdjustmentPayload;
import com.redawn.worldeditpp.bridge.minecraft.operation.ServerEditorLifecycle;
import com.redawn.worldeditpp.bridge.worldedit.*;
import com.redawn.worldeditpp.bridge.worldedit.history.HistoryEngine;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.server.players.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

@GameTestHolder(TestHarnessMod.ID)
@PrefixGameTestTemplate(false)
public final class PlacementHistoryGameTests {
    @GameTest(template = "empty", batch = "pasteHistory", timeoutTicks = 30000)
    public static void placementAndHistoryCannotStrandEachOther(GameTestHelper helper) throws Exception {
        var scenario = new Scenario(helper);
        helper.onEachTick(() -> {
            if (scenario.done) return;
            try { scenario.tick(); }
            catch (Throwable failure) { scenario.close(); helper.fail("Placement/history: " + failure); }
        });
    }
    private static final class Scenario {
        final GameTestHelper helper;
        final ServerLevel level;
        final FakePlayer player;
        final LocalSession session;
        final WorldEditEditingBackend backend = new WorldEditEditingBackend();
        final BlockPos origin;
        final List<ServerPlayer> online;
        final Map<UUID,ServerPlayer> identities;
        int round, phase, ticks, partial;
        boolean done, sawPartial;
        Scenario(GameTestHelper helper) throws Exception {
            this.helper = helper; level = helper.getLevel();
            origin = helper.absolutePos(new BlockPos(1024,100,1024));
            player = new FakePlayer(level, new GameProfile(UUID.randomUUID(),"PasteQueue"));
            var list = level.getServer().getPlayerList();
            online = (List<ServerPlayer>) field(list, PlayerList.class, "players");
            identities = (Map<UUID,ServerPlayer>) field(list, PlayerList.class, "playersByUUID");
            list.getOps().add(new ServerOpListEntry(player.getGameProfile(),4,false));
            online.add(player); identities.put(player.getUUID(),player);
            EditModeManager.toggle(player,"test");
            session = WorldEditBridge.worldEdit().getSessionManager().get(WorldEditPlatform.adaptPlayer(player));
            var clip = new BlockArrayClipboard(new CuboidRegion(BlockVector3.ZERO,BlockVector3.at(15,63,15)));
            for (BlockVector3 pos : clip.getRegion()) clip.setBlock(pos,BlockTypes.STONE.getDefaultState());
            session.setClipboard(new ClipboardHolder(clip));
            start();
        }
        void start() {
            session.clearHistory();
            for (int i = 0; i < 16384; i++) level.setBlock(position(i),Blocks.AIR.defaultBlockState(),2);
            backend.adjustClipboard(player,ClipboardAdjustmentPayload.begin());
            ticks = 0; phase = 0;
        }
        void tick() throws Exception {
            ticks++;
            if (phase == 0) {
                backend.tickAdjustment(player);
                Object state = state();
                if (state == null || field(state,state.getClass(),"payloads") != null) return;
                BlockPos at = position(0);
                backend.adjustClipboard(player,ClipboardAdjustmentPayload.confirm(at.getX(),at.getY(),at.getZ(),false));
                check(ClipboardAdjustmentService.hasPlacement(player.getUUID()),"Paste not started");
                verifyEditLock();
                level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(),"/undo");
                level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(),"/redo");
                check(!HistoryEngine.busy(player.getUUID()),"History was admitted during an active paste");
                phase = 1; ticks = 0;
            } else if (phase == 1) {
                if (ClipboardAdjustmentService.hasPlacement(player.getUUID())) {
                    check(!HistoryEngine.busy(player.getUUID()),"History operation appeared while paste was active");
                    int placed = placed();
                    if (placed == 0) return;
                    sawPartial = true; partial = placed;
                    if (round == 1) {
                        // A second BEGIN must not replace/orphan the existing placement.
                        ClipboardAdjustmentService.handle(player,ClipboardAdjustmentPayload.begin());
                        check(placed() == partial,"Second BEGIN replaced active paste");
                        backend.adjustClipboard(player,ClipboardAdjustmentPayload.cancel());
                        check(!ClipboardAdjustmentService.hasPlacement(player.getUUID()),"Busy history blocked paste cancellation");
                    } else if (round == 2) {
                        player.setServerLevel(level.getServer().getLevel(Level.NETHER));
                        phase = 2; ticks = 0; return;
                    } else if (round == 3) {
                        ServerEditorLifecycle.logout(player);
                        HistoryEngine.disconnect(player);
                        online.remove(player); identities.remove(player.getUUID());
                        phase = 2; ticks = 0; return;
                    } else if (round == 0) return;
                }
                phase = 3;
            } else if (phase == 2) {
                if (ticks < 8) return;
                check(!ClipboardAdjustmentService.hasPlacement(player.getUUID()),"Interrupted paste remained live");
                check(!HistoryEngine.busy(player.getUUID()),"Interrupted paste created an implicit history task");
                player.setServerLevel(level);
                if (!online.contains(player)) online.add(player);
                identities.put(player.getUUID(),player);
                phase = 3;
            } else if (phase == 3) {
                int expected = round == 0 ? 16384 : partial;
                int stone = 0;
                for (int i = 0; i < 16384; i++) if (level.getBlockState(position(i)).is(Blocks.STONE)) stone++;
                check(stone == expected,"Round " + round + " wrong paste result: " + stone + " expected " + expected);
                check(sawPartial,"No actual partial paste observed");
                HistoryEngine.enqueue(player,true,1,null);
                check(HistoryEngine.busy(player.getUUID()),"Undo was not admitted after paste finished");
                phase = 4;
            } else if (phase == 4) {
                if (HistoryEngine.busy(player.getUUID())) return;
                check(stones() == 0,"Undo after paste did not complete");
                HistoryEngine.enqueue(player,false,1,null);
                check(HistoryEngine.busy(player.getUUID()),"Redo was not admitted after undo finished");
                phase = 5;
            } else if (phase == 5) {
                if (HistoryEngine.busy(player.getUUID())) return;
                int expected = round == 0 ? 16384 : partial;
                check(stones() == expected,"Redo after paste restored the wrong prefix");
                if (++round < 4) start();
                else { close(); done = true; helper.succeed(); }
            }
        }
        int stones() {
            int count = 0;
            for (int i = 0; i < 16384; i++) if (level.getBlockState(position(i)).is(Blocks.STONE)) count++;
            return count;
        }
        void verifyEditLock() throws Exception {
            var actor = WorldEditPlatform.adaptPlayer(player);
            var pos = WorldEditPlatform.adapt(origin.offset(round * 48, 80, 0));
            try (var edit = WorldEditBridge.worldEdit().newEditSessionBuilder()
                    .world(WorldEditPlatform.adapt(level)).actor(actor).build()) {
                boolean denied = false;
                try { edit.setBlock(pos, BlockTypes.GOLD_BLOCK.getDefaultState()); }
                catch (IllegalStateException expected) { denied = true; }
                check(denied,"Active paste did not lock a new WorldEdit operation");
                check(edit.getChangeSet().size() == 0,"Rejected operation polluted history");
            }
            check(level.getBlockState(WorldEditPlatform.toBlockPos(pos)).isAir(),
                    "Rejected operation changed the world");
        }
        BlockPos position(int i) { return origin.offset(round*48 + i%16,i/256,i/16%16); }
        Object state() throws Exception {
            return ((Map<?, ?>) field(null,ClipboardAdjustmentService.class,"STATES")).get(player.getUUID());
        }
        int placed() throws Exception {
            Object job = field(state(),state().getClass(),"pasteJob");
            var cursor = field(job,job.getClass(),"cursor");
            return ((Number) cursor.getClass().getMethod("completed").invoke(cursor)).intValue();
        }
        void close() {
            done = true;
            try {
                ClipboardAdjustmentService.clear(player);
                HistoryEngine.shutdown(level.getServer());
                // Keep the registered owner available until rollback can finish on failure.
                if (!HistoryEngine.busy(player.getUUID())) {
                    online.remove(player); identities.remove(player.getUUID());
                    level.getServer().getPlayerList().getOps().remove(player.getGameProfile());
                    EditModeManager.handleLogout(player);
                }
            } catch (Exception ignored) { }
        }
    }
    private static Object field(Object object,Class<?> type,String name) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
}
