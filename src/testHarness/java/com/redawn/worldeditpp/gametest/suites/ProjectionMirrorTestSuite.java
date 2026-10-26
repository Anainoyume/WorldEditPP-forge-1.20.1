package com.redawn.worldeditpp.gametest.suites;

import com.redawn.worldeditpp.gametest.framework.GameAssertions;
import com.redawn.worldeditpp.gametest.framework.GameSimulation;
import com.redawn.worldeditpp.gametest.framework.GameTestSuiteDescriptor;
import com.redawn.worldeditpp.gametest.framework.TestSuiteBase;
import com.redawn.worldeditpp.math.DiscreteRotation;
import com.redawn.worldeditpp.bridge.minecraft.math.MinecraftBlockStateRotation;
import com.redawn.worldeditpp.bridge.worldedit.ProjectionBlockStateRotation;
import com.redawn.worldeditpp.bridge.worldedit.ProjectionClipboardRotation;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.forge.internal.NBTConverter;
import com.sk89q.worldedit.regions.CuboidRegion;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;

@GameTestSuiteDescriptor(name = "Projection reflection")
public final class ProjectionMirrorTestSuite extends TestSuiteBase {
    @Override
    protected void runTests(GameSimulation simulation) {
        test("all stair shapes mirror facing, half and handedness", this::stairs);
        test("slabs preserve waterlogging and double type", this::slabs);
        test("native and WorldEdit mirrors agree and reverse twice", this::parityAndInvolution);
        test("mixed transforms recover original stair after illegal poses", this::mixedTransforms);
        test("clipboard reflection remaps originals and preserves complete NBT", this::clipboard);
    }

    private static BlockState mirror(BlockState state, int axis) {
        return MinecraftBlockStateRotation.mirror(state, state,
                DiscreteRotation.IDENTITY.thenWorldMirror(axis), axis);
    }

    private void stairs() {
        for (BlockState source : Blocks.OAK_STAIRS.getStateDefinition().getPossibleStates()) {
            for (int axis = 0; axis < 3; axis++) {
                BlockState actual = mirror(source, axis);
                Direction facing = source.getValue(StairBlock.FACING);
                Direction expectedFacing = facing.getAxis().ordinal() == axis ? facing.getOpposite() : facing;
                Half half = source.getValue(StairBlock.HALF);
                Half expectedHalf = axis == 1 ? (half == Half.TOP ? Half.BOTTOM : Half.TOP) : half;
                StairsShape shape = source.getValue(StairBlock.SHAPE);
                StairsShape expectedShape = axis == 1 ? shape : switch (shape) {
                    case INNER_LEFT -> StairsShape.INNER_RIGHT;
                    case INNER_RIGHT -> StairsShape.INNER_LEFT;
                    case OUTER_LEFT -> StairsShape.OUTER_RIGHT;
                    case OUTER_RIGHT -> StairsShape.OUTER_LEFT;
                    case STRAIGHT -> shape;
                };
                GameAssertions.assertEquals(source.setValue(StairBlock.FACING, expectedFacing)
                        .setValue(StairBlock.HALF, expectedHalf).setValue(StairBlock.SHAPE, expectedShape),
                        actual, "Stair reflection must preserve geometry and waterlogging");
            }
        }
    }

    private void slabs() {
        for (BlockState source : Blocks.OAK_SLAB.getStateDefinition().getPossibleStates()) {
            SlabType type = source.getValue(SlabBlock.TYPE);
            SlabType flipped = switch (type) {
                case TOP -> SlabType.BOTTOM;
                case BOTTOM -> SlabType.TOP;
                case DOUBLE -> SlabType.DOUBLE;
            };
            GameAssertions.assertEquals(source.setValue(SlabBlock.TYPE, flipped), mirror(source, 1), "Y mirror");
            GameAssertions.assertEquals(source, mirror(source, 0), "X mirror");
            GameAssertions.assertEquals(source, mirror(source, 2), "Z mirror");
        }
    }

    private void parityAndInvolution() {
        Block[] blocks = {Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.OAK_LOG, Blocks.CHEST,
                Blocks.OAK_DOOR, Blocks.OAK_TRAPDOOR, Blocks.OAK_FENCE, Blocks.OAK_FENCE_GATE,
                Blocks.COBBLESTONE_WALL, Blocks.GLASS_PANE, Blocks.IRON_BARS, Blocks.CHAIN,
                Blocks.RAIL, Blocks.POWERED_RAIL, Blocks.OAK_SIGN, Blocks.OAK_WALL_SIGN,
                Blocks.PISTON, Blocks.HOPPER, Blocks.LEVER, Blocks.BELL, Blocks.STONE};
        for (Block block : blocks) for (BlockState source : block.getStateDefinition().getPossibleStates()) {
            for (int axis = 0; axis < 3; axis++) {
                BlockState actual = mirror(source, axis);
                GameAssertions.assertEquals(source, mirror(actual, axis), "Mirror twice: " + source + " axis " + axis);
                var weSource = ForgeAdapter.adapt(source);
                var server = ProjectionBlockStateRotation.mirror(weSource, weSource,
                        DiscreteRotation.IDENTITY.thenWorldMirror(axis), axis);
                GameAssertions.assertEquals(actual, ForgeAdapter.adapt(server), "Client/server mirror parity");
            }
        }
        BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
        GameAssertions.assertEquals(Direction.DOWN, mirror(barrel, 1).getValue(BlockStateProperties.FACING), "Six-way facing");
    }

    private void mixedTransforms() {
        for (BlockState source : Blocks.OAK_STAIRS.getStateDefinition().getPossibleStates()) {
            DiscreteRotation transform = DiscreteRotation.IDENTITY;
            BlockState current = source;
            int[] axes = {0, 2, 1, 1, 2, 0};
            boolean[] mirrors = {false, true, false, false, true, false};
            int[] turns = {1, 0, -1, 1, 0, -1};
            for (int i = 0; i < axes.length; i++) {
                int axis = axes[i];
                transform = mirrors[i] ? transform.thenWorldMirror(axis) : transform.thenWorldQuarterTurn(axis, turns[i]);
                current = mirrors[i] ? MinecraftBlockStateRotation.mirror(current, source, transform, axis)
                        : MinecraftBlockStateRotation.rotate(current, source, transform, axis, turns[i]);
                GameAssertions.assertEquals(MinecraftBlockStateRotation.rotateStair(source, transform), current,
                        "The original state and complete transform determine every step");
            }
            GameAssertions.assertEquals(DiscreteRotation.IDENTITY, transform, "Inverse mixed sequence");
            GameAssertions.assertEquals(source, current, "Mixed sequence must restore complete source state");
        }
    }

    private void clipboard() {
        BlockArrayClipboard source = new BlockArrayClipboard(new CuboidRegion(BlockVector3.ZERO, BlockVector3.at(2, 1, 3)));
        BlockState stair = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
        setBlock(source, BlockVector3.ZERO, ForgeAdapter.adapt(stair));
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:chest");
        nbt.putString("CustomName", "{\"text\":\"Mirror inventory\"}");
        CompoundTag custom = new CompoundTag();
        custom.putInt("savedValue", 73);
        nbt.put("custom", custom);
        var chest = ForgeAdapter.adapt(Blocks.CHEST.defaultBlockState()).toBaseBlock(NBTConverter.fromNative(nbt));
        setBlock(source, BlockVector3.at(2, 1, 3), chest);
        for (int axis = 0; axis < 3; axis++) {
            var transform = DiscreteRotation.IDENTITY.thenWorldMirror(axis);
            var first = ProjectionClipboardRotation.mirror(source, source, transform, axis);
            var restored = ProjectionClipboardRotation.mirror(first.current(), first.original(),
                    transform.thenWorldMirror(axis), axis);
            GameAssertions.assertEquals(source.getDimensions(), first.current().getDimensions(), "Mirror keeps dimensions");
            for (BlockVector3 pos : source.getRegion()) {
                GameAssertions.assertEquals(source.getFullBlock(pos), restored.current().getFullBlock(pos), "State/NBT round trip");
                GameAssertions.assertEquals(source.getFullBlock(pos), restored.original().getFullBlock(pos), "Original pairing round trip");
            }
            GameAssertions.assertEquals(ForgeAdapter.adapt(stair), source.getBlock(BlockVector3.ZERO), "Source clipboard immutable");
            GameAssertions.assertEquals(chest, source.getFullBlock(BlockVector3.at(2, 1, 3)), "Source NBT immutable");
        }
    }
    private static <B extends com.sk89q.worldedit.world.block.BlockStateHolder<B>> void setBlock(
            BlockArrayClipboard clipboard, BlockVector3 position, B state) {
        try { clipboard.setBlock(position, state); }
        catch (com.sk89q.worldedit.WorldEditException exception) { throw new AssertionError(exception); }
    }

}
