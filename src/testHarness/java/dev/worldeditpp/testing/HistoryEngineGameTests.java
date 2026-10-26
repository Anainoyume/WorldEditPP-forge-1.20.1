package dev.worldeditpp.testing;

import com.mojang.authlib.GameProfile;
import com.redawn.worldeditpp.bridge.worldedit.*;
import com.redawn.worldeditpp.bridge.worldedit.history.HistoryEngine;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.math.BlockVector3;
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
public final class HistoryEngineGameTests {
    @GameTest(template = "empty", batch = "historyEngine", timeoutTicks = 30000)
    public static void perPlayerHistorySurvivesInterruptions(GameTestHelper helper) throws Exception {
        var scenario = new Scenario(helper);
        helper.onEachTick(() -> {
            try { scenario.tick(); }
            catch (Throwable e) { scenario.close(); helper.fail("History operations scenario: " + e); }
        });
    }

    private static final class Scenario {
        final GameTestHelper helper;
        final ServerLevel level;
        final FakePlayer first, second;
        final LocalSession one, two;
        final BlockPos origin;
        int phase, ticks, savedCursor, fastUndoTicks;
        boolean done, redoQueued, recoveredDataCheckpoint, dropsPurged;
        com.sk89q.worldedit.entity.Entity historyEntity;
        BlockPos dataOrigin;
        com.redawn.worldeditpp.bridge.minecraft.operation.ChunkLease dataChunks;
        UUID checkpointEntityId;
        final String droppedItemMarker = UUID.randomUUID().toString();
        com.sk89q.worldedit.world.biome.BiomeType originalBiome, editedBiome;
        Scenario(GameTestHelper helper) throws Exception {
            this.helper = helper; level = helper.getLevel();
            // The GameTest world is reused across runs. Offset the fixture so leftover blocks,
            // chests and dropped items from a previous run cannot contaminate this scenario.
            var random = java.util.concurrent.ThreadLocalRandom.current();
            origin = helper.absolutePos(new BlockPos(512, 160, 512))
                    .offset(random.nextInt(4096) - 2048, 0, random.nextInt(4096) - 2048);
            first = player("QueueOne"); second = player("QueueTwo");
            one = session(first); two = session(second);
            fixture(one, origin, 4096);
            fixture(two, origin.offset(32,0,0), 64);
            for (int i = 0; i < 4096; i++) check(level.getBlockState(position(origin,i)).is(Blocks.STONE), "fixture is not stone at " + position(origin,i));
            var actor = WorldEditPlatform.adaptPlayer(first);
            check(actor.hasPermission("worldedit.history.undo"), "fixture permissions");
            // Exercise Brigadier -> Forge CommandEvent -> production operation, not just a service call.
            level.getServer().getCommands().performPrefixedCommand(first.createCommandSourceStack(), "/undo");
            HistoryEngine.enqueue(second, true, 1, null);
            check(HistoryEngine.busy(first.getUUID()), "undo command was not started");
        }

        void tick() throws Exception {
            if (done) return;
            ticks++;
            failIfFrozen(first); failIfFrozen(second);
            if (dataChunks != null && !dataChunks.ready(dataOrigin.getX(), dataOrigin.getZ())) return;
            if (dataChunks != null && !dropsPurged) {
                level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(dataOrigin).inflate(128)).forEach(net.minecraft.world.entity.Entity::discard);
                dropsPurged = true;
            }
            switch (phase) {
                case 0 -> {
                    int count = cursor(first);
                    if (count < 1 || count >= 4096) return;
                    savedCursor = count;
                    check(plannerStats().reservedBytes() >= 4096L * Long.BYTES,
                            "executing sorted plan released its memory permit too early");
                    int air = 0;
                    for (int i = 0; i < 4096; i++) if (level.getBlockState(position(origin,i)).isAir()) air++;
                    check(air == count, "undo cursor disagrees with world: " + count + " vs " + air);
                    check(pointer(one) == 1, "history cursor moved before completion");
                    verifyEditLock();
                    level.getServer().getCommands().performPrefixedCommand(first.createCommandSourceStack(), "/clearhistory");
                    level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "/undo 1 QueueOne");
                    check(pointer(one) == 1,"clearhistory/console command bypassed the reserved cursor");
                    players().remove(first.getUUID());
                    next();
                }
                case 1 -> {
                    if (ticks < 8 || HistoryEngine.busy(second.getUUID())) return;
                    check(cursor(first) == savedCursor, "offline owner advanced");
                    check(pointer(two) == 0 && level.getBlockState(origin.offset(32,0,0)).isAir(), "other player's operation did not progress");
                    players().put(first.getUUID(), first);
                    first.setServerLevel(level.getServer().getLevel(Level.NETHER));
                    next();
                }
                case 2 -> {
                    if (ticks < 6) return;
                    check(cursor(first) == savedCursor, "task wrote in another dimension");
                    first.setServerLevel(level);
                    next();
                }
                case 3 -> {
                    if (HistoryEngine.busy(first.getUUID())) return;
                    check(pointer(one) == 0, "interrupted undo did not commit");
                    check(plannerStats().reservedBytes() == 0, "completed operation leaked planning memory");
                    for (int i = 0; i < 4096; i++) check(level.getBlockState(position(origin,i)).isAir(), "undo content mismatch at " + position(origin,i));
                    HistoryEngine.enqueue(first, false, 1, null);
                    next();
                }
                case 4 -> {
                    if (HistoryEngine.busy(first.getUUID())) return;
                    fastUndoTicks = ticks;
                    check(fastUndoTicks <= 64, "4096-block redo regressed to " + fastUndoTicks + " ticks");
                    check(pointer(one) == 1, "redo did not commit");
                    for (int i = 0; i < 4096; i++) check(level.getBlockState(position(origin,i)).is(Blocks.STONE), "redo content mismatch");
                    HistoryEngine.enqueue(first, true, 1, null);
                    next();
                }
                case 5 -> {
                    int count = cursor(first);
                    if (count < 1 || count >= 4096) return;
                    savedCursor = count;
                    // Normal stop checkpoint, then automatic recovery on the next login.
                    HistoryEngine.shutdown(level.getServer());
                    check(plannerStats().reservedBytes() == 0, "checkpoint retained consumed plan");
                    verifyCompactCheckpoint(savedCursor);
                    one.clearHistory();
                    HistoryEngine.onLogin(first);
                    check(HistoryEngine.busy(first.getUUID()), "checkpoint did not start recovery");
                    next();
                }
                case 6 -> {
                    if (HistoryEngine.busy(first.getUUID())) return;
                    for (int i = 0; i < 4096; i++) check(level.getBlockState(position(origin,i)).is(Blocks.STONE), "checkpoint did not restore the partial undo");
                    check(pointer(one) == 1, "recovery must itself be undoable");
                    dataFixture();
                    HistoryEngine.enqueue(first, true, 1, null);
                    next();
                }
                case 7 -> {
                    if (HistoryEngine.busy(first.getUUID())) return;
                    check(level.getBlockState(dataOrigin).isAir(), "NBT block undo");

                    check(droppedItems() == 0, "container undo spawned item entities: " + droppedItemDetails());
                    check(allDroppedItems() == 0, "unopened loot chest undo generated drops");
                    check(level.getBlockEntity(position(dataOrigin,4087)).saveWithFullMetadata()
                            .getString("LootTable").equals("minecraft:chests/simple_dungeon"), "undo did not restore original loot chest");
                    check(entities() == 0, "entity creation undo");
                    check(biome().equals(originalBiome),"biome undo");
                    HistoryEngine.enqueue(first, false, 1, null);
                    next();
                }
                case 8 -> {
                    if (HistoryEngine.busy(first.getUUID())) return;
                    verifyData();
                    check(droppedItems() == 0, "container redo spawned item entities");
                    check(allDroppedItems() == 0, "loot chest redo generated drops");
                    checkpointEntityId = fixtureEntities().get(0).getUUID();
                    HistoryEngine.enqueue(first, true, 1, null);
                    next();
                }
                case 9 -> {
                    int count = cursor(first);
                    if (count < 0) {
                        check(pointer(one) == 0, "fast data undo did not commit");
                        HistoryEngine.enqueue(first, false, 1, null);
                        next();
                        return;
                    }
                    if (count < 3 || count >= 4098) return;
                    HistoryEngine.shutdown(level.getServer());
                    one.clearHistory();
                    HistoryEngine.onLogin(first);
                    recoveredDataCheckpoint = true;
                    next();
                }
                case 10 -> {
                    if (HistoryEngine.busy(first.getUUID())) return;
                    verifyData();
                    if (recoveredDataCheckpoint) {
                        check(fixtureEntities().get(0).getUUID().equals(checkpointEntityId),
                                "checkpoint must preserve the entity UUID");
                    }
                    HistoryEngine.enqueue(first, true, 1, null);
                    next();
                }
                case 11 -> {
                    if (HistoryEngine.busy(first.getUUID())) return;
                    check(entities() == 0, "recovery undo must remove the restored entity");
                    check(biome().equals(originalBiome),"recovery undo must restore the prior biome");
                    HistoryEngine.enqueue(first, false, 1, null);
                    next();
                }
                case 12 -> {
                    if (HistoryEngine.busy(first.getUUID())) return;
                    check(plannerStats().reservedBytes() == 0, "completed history leaked planning memory");
                    verifyData(); close(); done = true; helper.succeed();
                }
            }
        }
        void verifyEditLock() throws Exception {
            var actor = WorldEditPlatform.adaptPlayer(first);
            var pos = WorldEditPlatform.adapt(origin.offset(0,32,0));
            try (var edit = WorldEditBridge.worldEdit().newEditSessionBuilder().world(WorldEditPlatform.adapt(level)).actor(actor).build()) {
                edit.disableBuffering();
                boolean denied = false;
                try { edit.setBlock(pos, BlockTypes.GOLD_BLOCK.getDefaultState()); }
                catch (IllegalStateException expected) { denied = true; }
                check(denied, "busy owner can still create a history edit");
                check(edit.getChangeSet().size() == 0, "rejected edit polluted history");
                denied = false;
                try { edit.rawSetBlock(pos, BlockTypes.GOLD_BLOCK.getDefaultState()); }
                catch (IllegalStateException expected) { denied = true; }
                check(denied, "raw edit bypassed the lock");
            }
            check(level.getBlockState(WorldEditPlatform.toBlockPos(pos)).isAir(), "rejected edit changed the world");
        }
        void verifyCompactCheckpoint(int recordCount) throws Exception {
            var path = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data/worldeditpp-history").resolve(first.getUUID() + ".pending");
            check(java.nio.file.Files.isRegularFile(path), "checkpoint file missing");
            try (var input = new java.io.DataInputStream(new java.util.zip.GZIPInputStream(
                    java.nio.file.Files.newInputStream(path)))) {
                check(input.readInt() == 0x57505032, "checkpoint did not use compact v2 encoding");
            }
            long generousLimit = Math.max(4096L, recordCount * 8L);
            check(java.nio.file.Files.size(path) <= generousLimit,
                    "compact checkpoint unexpectedly large: " + java.nio.file.Files.size(path));
        }
        void dataFixture() throws Exception {
            one.clearHistory(); dataOrigin = origin.offset(64,0,0);
            // GameTest reuses its world across runs. Stale drops from earlier runs can fall far
            // below the fixture and survive a 32-block sweep, so clear a wide area before the
            // history cycle; only drops produced by this cycle may reach the assertions.
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(dataOrigin).inflate(128)).forEach(net.minecraft.world.entity.Entity::discard);
            // FakePlayers are not chunk-loading network players. Keep the assertion area
            // visible even after production tasks correctly release their own tickets.
            dataChunks = new com.redawn.worldeditpp.bridge.minecraft.operation.ChunkLease(level);
            dataChunks.ready(dataOrigin.getX(),dataOrigin.getZ());
            var world = WorldEditPlatform.adapt(level);
            try (var edit = WorldEditBridge.newEditSession(world)) {
                edit.disableBuffering();
                for (int i = 0; i < 4096; i++) {
                    var pos = position(dataOrigin,i);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    if (i == 4087) {
                        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
                        ((net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(pos))
                                .setLootTable(new net.minecraft.resources.ResourceLocation("minecraft:chests/simple_dungeon"), 123456789L);
                    }
                    edit.setBlock(WorldEditPlatform.adapt(pos), BlockTypes.STONE.getDefaultState());
                }
                var chest = new net.minecraft.nbt.CompoundTag();
                chest.putString("id","minecraft:chest");
                var items = new net.minecraft.nbt.ListTag();
                var item = new net.minecraft.nbt.CompoundTag();
                item.putByte("Slot",(byte)0); item.putByte("Count",(byte)7); item.putString("id","minecraft:emerald");
                var itemTag = new net.minecraft.nbt.CompoundTag();
                itemTag.putString("wepp_history_test", droppedItemMarker);
                item.put("tag", itemTag);
                items.add(item); chest.put("Items",items);
                edit.setBlock(WorldEditPlatform.adapt(dataOrigin), BlockTypes.CHEST.getDefaultState().toBaseBlock(WorldEditPlatform.fromNative(chest)));
                edit.setBlock(WorldEditPlatform.adapt(position(dataOrigin,4095)), BlockTypes.CHEST.getDefaultState().toBaseBlock(WorldEditPlatform.fromNative(chest)));
                var loot = new net.minecraft.nbt.CompoundTag();
                loot.putString("id", "minecraft:chest");
                loot.putString("LootTable", "minecraft:chests/simple_dungeon");
                loot.putLong("LootTableSeed", 123456789L);
                // Same unopened NBT as a naturally generated chest, copied to several cells.
                for (int i = 4088; i < 4094; i++) edit.setBlock(WorldEditPlatform.adapt(position(dataOrigin,i)),
                        BlockTypes.CHEST.getDefaultState().toBaseBlock(WorldEditPlatform.fromNative(loot)));
                originalBiome = biome();
                editedBiome = originalBiome.equals(com.sk89q.worldedit.world.biome.BiomeTypes.DESERT)
                        ? com.sk89q.worldedit.world.biome.BiomeTypes.SWAMP : com.sk89q.worldedit.world.biome.BiomeTypes.DESERT;
                edit.setBiome(WorldEditPlatform.adapt(dataOrigin.offset(0,20,0)), editedBiome);
                var tag = new net.minecraft.nbt.CompoundTag(); tag.putBoolean("NoAI",true); tag.putBoolean("NoGravity",true);
                tag.putString("CustomName",net.minecraft.network.chat.Component.Serializer.toJson(net.minecraft.network.chat.Component.literal(first.getUUID().toString())));
                var entity = new com.sk89q.worldedit.entity.BaseEntity(com.sk89q.worldedit.world.entity.EntityTypes.PIG, WorldEditPlatform.fromNative(tag));
                historyEntity = edit.createEntity(new com.sk89q.worldedit.util.Location(world, dataOrigin.getX()+2.5,dataOrigin.getY()+20,dataOrigin.getZ()+2.5), entity);
                check(historyEntity != null, "entity fixture creation");
                edit.flushSession(); one.remember(edit);
            }
            // The isolated GameTest world is reused across runs. Direct fixture cleanup can
            // drop contents left by a previous failed run; exclude those before exercising history.
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(dataOrigin).inflate(32)).forEach(net.minecraft.world.entity.Entity::discard);
        }
        int entities() {
            return fixtureEntities().size();
        }
        int droppedItems() {
            return level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(dataOrigin).inflate(32)).stream()
                    .filter(item -> item.getItem().is(net.minecraft.world.item.Items.EMERALD))
                    .filter(item -> item.getItem().hasTag())
                    .filter(item -> droppedItemMarker.equals(item.getItem().getTag().getString("wepp_history_test")))
                    .toList().size();
        }
        int allDroppedItems() {
            return level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(dataOrigin).inflate(32)).size();
        }
        String droppedItemDetails() {
            return level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new net.minecraft.world.phys.AABB(dataOrigin).inflate(32)).stream()
                    .filter(item -> item.getItem().is(net.minecraft.world.item.Items.EMERALD))
                    .filter(item -> item.getItem().hasTag())
                    .filter(item -> droppedItemMarker.equals(item.getItem().getTag().getString("wepp_history_test")))
                    .map(item -> item.getItem() + "@" + item.blockPosition()).toList().toString();
        }
        List<net.minecraft.world.entity.animal.Pig> fixtureEntities() {
            return level.getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class,
                    new net.minecraft.world.phys.AABB(dataOrigin).inflate(25),
                    pig -> pig.hasCustomName() && pig.getCustomName().getString().equals(first.getUUID().toString()));
        }
        void verifyData() {
            check(allDroppedItems() == 0, "history/recovery generated container drops");
            for (int i = 4088; i < 4094; i++) {
                var entity = level.getBlockEntity(position(dataOrigin, i));
                check(entity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity, "loot chest missing");
                var nbt = entity.saveWithFullMetadata();
                check(nbt.getString("LootTable").equals("minecraft:chests/simple_dungeon"), "redo/recovery lost unopened loot table");
                check(nbt.getLong("LootTableSeed") == 123456789L, "redo/recovery changed loot seed");
            }
            for (var pos : List.of(dataOrigin,position(dataOrigin,4095))) {
                check(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity, "chest missing at " + pos);
                var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(pos);
                check(chest.getItem(0).is(net.minecraft.world.item.Items.EMERALD) && chest.getItem(0).getCount() == 7, "chest NBT was lost");
            }
            check(biome().equals(editedBiome),"edited biome was lost");
            check(entities() == 1, "entity duplicated or missing: count=" + entities() + " phase=" + phase);
        }
        com.sk89q.worldedit.world.biome.BiomeType biome() {
            return WorldEditPlatform.adapt(level).getBiome(WorldEditPlatform.adapt(dataOrigin.offset(0,20,0)));
        }
        void fixture(LocalSession history, BlockPos start, int count) throws Exception {
            try (var edit = WorldEditBridge.newEditSession(WorldEditPlatform.adapt(level))) {
                edit.disableBuffering();
                for (int i = 0; i < count; i++) {
                    var pos = position(start,i);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    edit.setBlock(BlockVector3.at(pos.getX(),pos.getY(),pos.getZ()), BlockTypes.STONE.getDefaultState());
                }
                edit.flushSession(); history.remember(edit);
            }
        }
        FakePlayer player(String name) throws Exception {
            var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
            level.getServer().getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(),4,false));
            players().put(player.getUUID(),player); return player;
        }
        @SuppressWarnings("unchecked") Map<UUID,ServerPlayer> players() throws Exception {
            var field = PlayerList.class.getDeclaredField("playersByUUID"); field.setAccessible(true);
            return (Map<UUID,ServerPlayer>) field.get(level.getServer().getPlayerList());
        }
        void next() {
            phase++; ticks = 0;
            com.redawn.worldeditpp.WorldEditPlusPlus.LOGGER.info("History test phase {}", phase);
        }
        void close() {
            try {
                first.setServerLevel(level);
                if (dataOrigin != null) fixtureEntities().forEach(net.minecraft.world.entity.Entity::discard);
                if (dataChunks != null) dataChunks.close();
                HistoryEngine.shutdown(level.getServer());
                players().remove(first.getUUID()); players().remove(second.getUUID());
                level.getServer().getPlayerList().getOps().remove(first.getGameProfile());
                level.getServer().getPlayerList().getOps().remove(second.getGameProfile());
            } catch (Exception ignored) { }
        }
    }
    private static LocalSession session(ServerPlayer player) { return WorldEditBridge.worldEdit().getSessionManager().get(WorldEditPlatform.adaptPlayer(player)); }
    private static com.redawn.worldeditpp.operation.task.BoundedHistoryPlanner.Stats plannerStats() throws Exception {
        var type = Class.forName("com.redawn.worldeditpp.bridge.worldedit.operation.PlanningService");
        var field = type.getDeclaredField("PLANNER"); field.setAccessible(true);
        return ((com.redawn.worldeditpp.operation.task.BoundedHistoryPlanner) field.get(null)).stats();
    }
    private static int pointer(LocalSession session) throws Exception {
        var field = LocalSession.class.getDeclaredField("historyPointer"); field.setAccessible(true); return field.getInt(session);
    }
    private static Object activeTask(ServerPlayer player) throws Exception {
        var field = HistoryEngine.class.getDeclaredField("ACTIVE"); field.setAccessible(true);
        return ((java.util.Map<?, ?>) field.get(null)).get(player.getUUID());
    }
    private static int cursor(ServerPlayer player) throws Exception {
        Object task = activeTask(player);
        if (task == null) return -1;
        var count = task.getClass().getDeclaredField("cursor"); count.setAccessible(true); return count.getInt(task);
    }
    private static void failIfFrozen(ServerPlayer player) throws Exception {
        Object task = activeTask(player);
        if (task == null) return;
        var failure = task.getClass().getDeclaredField("failure"); failure.setAccessible(true);
        check(failure.get(task) == null,"unexpected suspended history: " + failure.get(task));
    }
    private static BlockPos position(BlockPos start, int i) { return start.offset(i % 16, i / 256, i / 16 % 16); }
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
}
