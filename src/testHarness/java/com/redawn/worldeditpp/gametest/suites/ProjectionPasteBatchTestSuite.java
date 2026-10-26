package com.redawn.worldeditpp.gametest.suites;

import com.redawn.worldeditpp.gametest.framework.GameAssertions;
import com.redawn.worldeditpp.gametest.framework.GameSimulation;
import com.redawn.worldeditpp.gametest.framework.GameTestSuiteDescriptor;
import com.redawn.worldeditpp.gametest.framework.TestSuiteBase;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditBridge;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.function.mask.ExistingBlockMask;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.minecraft.core.BlockPos;

@GameTestSuiteDescriptor(name = "Projection paste batches")
public final class ProjectionPasteBatchTestSuite extends TestSuiteBase {
    @Override
    protected void runTests(GameSimulation simulation) {
        test("FAST session remains reusable and undoable across flushes",
                () -> testReusableSession(simulation));
        test("source mask skips every air type without skipping water or changing the source",
                () -> testIgnoreAir(simulation));
    }

    private void testIgnoreAir(GameSimulation simulation) {
        World world = simulation.worldEditWorld();
        BlockVector3 target = BlockVector3.at(24, 112, 24);
        var region = new CuboidRegion(BlockVector3.ZERO, BlockVector3.at(4, 0, 0));
        var source = new BlockArrayClipboard(region);
        var types = new com.sk89q.worldedit.world.block.BlockType[]{
                BlockTypes.AIR, BlockTypes.CAVE_AIR, BlockTypes.VOID_AIR, BlockTypes.STONE, BlockTypes.WATER};
        var originals = new BaseBlock[types.length];
        for (int x = 0; x < types.length; x++) {
            try {
                source.setBlock(BlockVector3.at(x, 0, 0), types[x].getDefaultState());
            } catch (WorldEditException exception) {
                throw new AssertionError(exception);
            }
            originals[x] = world.getFullBlock(target.add(x, 0, 0));
        }
        try {
            for (boolean ignoreAir : new boolean[]{false, true}) {
                try (var seed = WorldEditBridge.newEditSession(world)) {
                    for (int x = 0; x < types.length; x++) {
                        seed.setBlock(target.add(x, 0, 0), BlockTypes.GOLD_BLOCK.getDefaultState());
                    }
                }
                try (var edit = WorldEditBridge.newEditSession(world)) {
                    var paste = new ForwardExtentCopy(source, region, BlockVector3.ZERO, edit, target);
                    paste.setCopyingEntities(false);
                    paste.setCopyingBiomes(false);
                    if (ignoreAir) paste.setSourceMask(new ExistingBlockMask(source));
                    Operations.completeLegacy(paste);
                    edit.flushSession();
                    for (int x = 0; x < types.length; x++) {
                        var expected = ignoreAir && x < 3 ? BlockTypes.GOLD_BLOCK : types[x];
                        GameAssertions.assertEquals(expected, world.getBlock(target.add(x, 0, 0)).getBlockType(),
                                "Air policy must preserve destination blocks only at source air cells");
                        GameAssertions.assertEquals(types[x], source.getBlock(BlockVector3.at(x, 0, 0)).getBlockType(),
                                "Filtering must never change the projection or clipboard source");
                    }
                    try (var undo = WorldEditBridge.newEditSession(world)) {
                        edit.undo(undo);
                    }
                    for (int x = 0; x < types.length; x++) {
                        GameAssertions.assertEquals(BlockTypes.GOLD_BLOCK,
                                world.getBlock(target.add(x, 0, 0)).getBlockType(), "Single undo restores the destination");
                    }
                }
            }
        } catch (WorldEditException exception) {
            throw new IllegalStateException("Failed to exercise ignore-air projection placement", exception);
        } finally {
            try (var restore = WorldEditBridge.newEditSession(world)) {
                for (int x = 0; x < types.length; x++) restore.setBlock(target.add(x, 0, 0), originals[x]);
            } catch (WorldEditException exception) {
                throw new IllegalStateException("Failed to restore placement test area", exception);
            }
        }
    }

    private void testReusableSession(GameSimulation simulation) {
        World world = simulation.worldEditWorld();
        BlockPos spawn = simulation.overworld().getSharedSpawnPos();
        BlockVector3 first = BlockVector3.at(spawn.getX(), spawn.getY() + 2, spawn.getZ());
        BlockVector3 second = first.add(1, 0, 0);
        BaseBlock originalFirst = world.getFullBlock(first);
        BaseBlock originalSecond = world.getFullBlock(second);

        try (EditSession placement = WorldEditBridge.newEditSession(world)) {
            placement.setBlock(first, BlockTypes.STONE.getDefaultState());
            placement.flushSession();
            GameAssertions.assertEquals(BlockTypes.STONE, world.getBlock(first).getBlockType(),
                    "First flushed batch must be visible before the session closes");

            placement.setBlock(second, BlockTypes.DIRT.getDefaultState());
            placement.flushSession();
            GameAssertions.assertEquals(BlockTypes.DIRT, world.getBlock(second).getBlockType(),
                    "Second flushed batch must reuse the same session");

            try (EditSession undo = WorldEditBridge.newEditSession(world)) {
                placement.undo(undo);
            }
        } catch (WorldEditException exception) {
            throw new IllegalStateException("Failed to exercise projection paste batches", exception);
        }

        GameAssertions.assertEquals(originalFirst, world.getFullBlock(first),
                "One undo must restore the first flushed batch");
        GameAssertions.assertEquals(originalSecond, world.getFullBlock(second),
                "One undo must restore the second flushed batch");
    }
}
