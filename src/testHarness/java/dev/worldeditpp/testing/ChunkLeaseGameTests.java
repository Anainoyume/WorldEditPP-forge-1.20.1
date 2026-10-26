package dev.worldeditpp.testing;

import com.redawn.worldeditpp.bridge.minecraft.operation.ChunkLease;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(TestHarnessMod.ID)
@PrefixGameTestTemplate(false)
public final class ChunkLeaseGameTests {
    @GameTest(template = "empty", batch = "chunkLease", timeoutTicks = 30000)
    public static void absentChunkLoadsAcrossTicks(GameTestHelper helper) {
        var level = helper.getLevel();
        // Well outside spawn/test structures; no synchronous getChunk call prepares it.
        int x = 2048, z = -2048;
        while (level.getChunkSource().getChunkNow(x,z) != null) x += 16;
        int chunkX = x, chunkZ = z;
        var lease = new ChunkLease(level);
        helper.assertTrue(!lease.ready(chunkX << 4,chunkZ << 4),"first request must yield while a chunk is absent");
        int[] ticks = {0};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) return;
            try {
                ticks[0]++;
                if (!lease.ready(chunkX << 4,chunkZ << 4)) return;
                helper.assertTrue(ticks[0] > 0,"load must let server ticks continue");
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    helper.assertTrue(level.getChunkSource().getChunkNow(chunkX+dx,chunkZ+dz) != null,"neighbour side-effect chunk not loaded");
                verifyBatchSlice(helper, lease, chunkX, chunkZ);
                lease.close(); done[0] = true; helper.succeed();
            } catch (Exception e) { lease.close(); done[0] = true; helper.fail(e.toString()); }
        });
    }

    private static void verifyBatchSlice(GameTestHelper helper, ChunkLease lease, int x, int z) throws Exception {
        // Reflection keeps the production helper package-private and avoids test-only APIs.
        Class<?> type = Class.forName("com.redawn.worldeditpp.bridge.worldedit.history.HistoryChunkBatch");
        var constructor = type.getDeclaredConstructor(net.minecraft.server.level.ServerLevel.class, ChunkLease.class);
        constructor.setAccessible(true);
        var ready = type.getDeclaredMethod("ready", com.sk89q.worldedit.math.BlockVector3.class); ready.setAccessible(true);
        var clear = type.getDeclaredMethod("clearContainer", com.sk89q.worldedit.math.BlockVector3.class); clear.setAccessible(true);
        var end = type.getDeclaredMethod("endRun"); end.setAccessible(true);
        var chunk = type.getDeclaredField("chunk"); chunk.setAccessible(true);
        var position = com.sk89q.worldedit.math.BlockVector3.at((x << 4) + 2, 180, (z << 4) + 3);
        Object batch = constructor.newInstance(helper.getLevel(), lease);
        helper.assertTrue((boolean) ready.invoke(batch, position), "batch did not accept ready chunk");
        Object original = chunk.get(batch);
        helper.assertTrue((boolean) ready.invoke(batch, position.add(1, 1, 1)), "same chunk run lost readiness");
        helper.assertTrue(chunk.get(batch) == original, "same chunk run changed native chunk");
        end.invoke(batch);
        helper.assertTrue(chunk.get(batch) == null, "non-block barrier retained chunk");
        helper.assertTrue((boolean) ready.invoke(batch, position), "batch did not restart");
        var nativePos = new net.minecraft.core.BlockPos(position.getX(), position.getY(), position.getZ());
        helper.getLevel().setBlockAndUpdate(nativePos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getLevel().getBlockEntity(nativePos);
        chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
        clear.invoke(batch, position);
        helper.assertTrue(chest.isEmpty(), "batch container guard did not clear contents");
        helper.assertTrue(helper.getLevel().getBlockState(nativePos).is(net.minecraft.world.level.block.Blocks.CHEST),
                "batch guard mutated block outside WorldEdit");
        helper.getLevel().setBlockAndUpdate(nativePos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        // A failed switch must never leave the previous native chunk cached.
        int absentX = x + 512;
        while (helper.getLevel().getChunkSource().getChunkNow(absentX, z) != null) absentX += 16;
        var absent = com.sk89q.worldedit.math.BlockVector3.at(absentX << 4, 180, z << 4);
        helper.assertTrue(!(boolean) ready.invoke(batch, absent), "absent chunk switch blocked/loaded synchronously");
        helper.assertTrue(chunk.get(batch) == null, "failed switch retained previous chunk");
        lease.close();
        Object nextSlice = constructor.newInstance(helper.getLevel(), lease);
        helper.assertTrue(chunk.get(nextSlice) == null, "next tick inherited stale batch");
        helper.assertTrue((boolean) ready.invoke(nextSlice, position), "released/reacquired lease did not restart");
    }
}
