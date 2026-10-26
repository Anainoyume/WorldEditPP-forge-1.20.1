package com.redawn.worldeditpp.gametest.suites;

import com.redawn.worldeditpp.bridge.minecraft.blockstate.BlockStatePropertiesEditor;
import com.redawn.worldeditpp.bridge.api.BlockStateView;
import com.redawn.worldeditpp.bridge.minecraft.blockstate.MinecraftBlockStateView;
import com.redawn.worldeditpp.gametest.framework.GameAssertions;
import com.redawn.worldeditpp.gametest.framework.GameSimulation;
import com.redawn.worldeditpp.gametest.framework.GameTestSuiteDescriptor;
import com.redawn.worldeditpp.gametest.framework.TestSuiteBase;
import com.redawn.worldeditpp.bridge.worldedit.BlockStateEditService;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditBridge;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.Half;

@GameTestSuiteDescriptor(name = "Single block state editor")
public final class BlockStateEditorTestSuite extends TestSuiteBase {
    @Override protected void runTests(GameSimulation simulation) {
        test("portable state view preserves identity, schema and immutable updates", () -> {
            var original = Blocks.OAK_STAIRS.defaultBlockState();
            BlockStateView view = MinecraftBlockStateView.of(original);
            GameAssertions.assertEquals("minecraft:oak_stairs", view.blockId(), "Registry ID changed");
            GameAssertions.assertEquals(original.getProperties().size(), view.properties().size(), "Property lost");
            for (var property : view.properties()) {
                GameAssertions.check(property.allowedValues().contains(property.value()), "Current value missing");
                var same = view.withProperty(property.name(), property.value()).orElseThrow();
                GameAssertions.check(MinecraftBlockStateView.unwrap(same) == original, "Round-trip changed native state");
            }
            BlockStateView changed = view.withProperty("half", "top").orElseThrow();
            GameAssertions.assertEquals(Half.TOP, MinecraftBlockStateView.unwrap(changed).getValue(StairBlock.HALF), "Update failed");
            GameAssertions.assertEquals(Half.BOTTOM, MinecraftBlockStateView.unwrap(view).getValue(StairBlock.HALF), "Original mutated");
            GameAssertions.check(view.withProperty("unknown", "true").isEmpty(), "Unknown property accepted");
            GameAssertions.check(view.withProperty("half", "sideways").isEmpty(), "Invalid value accepted");
        });
        test("native properties enumerate and round-trip without changing other states", () -> {
            for (var block : new Block[]{Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.REDSTONE_WIRE,
                    Blocks.WHEAT, Blocks.OAK_FENCE, Blocks.CHEST, Blocks.STONE}) {
                for (var original : block.getStateDefinition().getPossibleStates()) {
                    for (var property : original.getProperties()) {
                        for (String value : BlockStatePropertiesEditor.values(property)) {
                            var changed = BlockStatePropertiesEditor.apply(original, property.getName(), value).orElseThrow();
                            GameAssertions.assertEquals(value, BlockStatePropertiesEditor.value(changed, property), "Value must parse");
                            for (var other : original.getProperties()) if (other != property) {
                                GameAssertions.assertEquals(original.getValue(other), changed.getValue(other), "Other property changed");
                            }
                        }
                    }
                }
            }
        });
        test("invalid names and values are rejected", () -> {
            var state = Blocks.OAK_STAIRS.defaultBlockState();
            GameAssertions.check(BlockStatePropertiesEditor.apply(state, "big", "true").isEmpty(), "Unknown name accepted");
            GameAssertions.check(BlockStatePropertiesEditor.apply(state, "facing", "up").isEmpty(), "Invalid facing accepted");
            GameAssertions.check(BlockStatePropertiesEditor.apply(state, "half", "TOP").isEmpty(), "Invalid spelling accepted");
        });
        test("state edit retains chest contents without collecting undo changes", () -> editWithoutHistory(simulation));
        test("explicit stair shape survives a single-block edit", () -> {
            var state = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP);
            var changed = BlockStatePropertiesEditor.apply(state, "shape", "outer_right").orElseThrow();
            GameAssertions.assertEquals(Half.TOP, changed.getValue(StairBlock.HALF), "Half changed unexpectedly");
        });
    }

    private void editWithoutHistory(GameSimulation simulation) {
        var level = simulation.overworld();
        BlockPos pos = new BlockPos(18, 110, 18);
        BlockPos neighbor = pos.east();
        level.getChunkAt(pos);
        var world = simulation.worldEditWorld();
        var vector = BlockVector3.at(pos.getX(), pos.getY(), pos.getZ());
        var adjacent = BlockVector3.at(neighbor.getX(), neighbor.getY(), neighbor.getZ());
        var old = world.getFullBlock(vector);
        var oldNeighbor = world.getFullBlock(adjacent);
        try {
            level.setBlock(pos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(neighbor, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            var chest = (ChestBlockEntity) level.getBlockEntity(pos);
            chest.setItem(4, new ItemStack(Items.DIAMOND, 23));
            chest.setChanged();
            try (var edit = WorldEditBridge.newEditSession(world)) {
                BlockStateEditService.setState(edit, vector,
                        level.getBlockState(pos).setValue(ChestBlock.FACING, Direction.EAST));
                edit.flushSession();
                GameAssertions.assertEquals(Direction.EAST, level.getBlockState(pos).getValue(ChestBlock.FACING), "Facing was not applied");
                var updated = (ChestBlockEntity) level.getBlockEntity(pos);
                GameAssertions.check(updated.getItem(4).is(Items.DIAMOND) && updated.getItem(4).getCount() == 23, "Chest inventory lost");
                GameAssertions.check(level.getBlockState(neighbor).is(Blocks.DIAMOND_BLOCK), "Neighbor changed");
                GameAssertions.check(edit.getChangeSet().size() == 0, "State editor must not track history");
                GameAssertions.assertEquals(0, edit.getChangeSet().size(), "State editor collected undo changes");
                try (var undo = WorldEditBridge.newEditSession(world)) {
                    edit.undo(undo);
                    undo.flushSession();
                }
                GameAssertions.assertEquals(Direction.EAST, level.getBlockState(pos).getValue(ChestBlock.FACING), "Empty history unexpectedly undid the state edit");
                GameAssertions.check(((ChestBlockEntity) level.getBlockEntity(pos)).getItem(4).getCount() == 23, "Empty history lost inventory");
            }
        } catch (WorldEditException exception) {
            throw new AssertionError(exception);
        } finally {
            try (var restore = WorldEditBridge.newEditSession(world)) {
                restore.setBlock(vector, old);
                restore.setBlock(adjacent, oldNeighbor);
            } catch (WorldEditException exception) {
                throw new AssertionError(exception);
            }
        }
    }
}
