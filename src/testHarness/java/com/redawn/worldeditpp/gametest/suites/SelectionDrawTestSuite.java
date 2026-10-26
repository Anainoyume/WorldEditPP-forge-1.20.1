package com.redawn.worldeditpp.gametest.suites;

import com.redawn.worldeditpp.gametest.framework.GameAssertions;
import com.redawn.worldeditpp.gametest.framework.GameSimulation;
import com.redawn.worldeditpp.gametest.framework.GameTestSuiteDescriptor;
import com.redawn.worldeditpp.gametest.framework.TestSuiteBase;
import com.redawn.worldeditpp.bridge.minecraft.item.BlockStateItemData;
import com.redawn.worldeditpp.bridge.minecraft.item.BlockItemSavedData;
import com.redawn.worldeditpp.bridge.minecraft.item.ModItems;
import com.redawn.worldeditpp.mode.DrawMode;
import com.redawn.worldeditpp.bridge.worldedit.SelectionDrawService;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditBridge;
import java.util.LinkedHashMap;
import java.util.Map;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

@GameTestSuiteDescriptor(name = "Selection draw")
public final class SelectionDrawTestSuite extends TestSuiteBase {
    @Override
    protected void runTests(GameSimulation simulation) {
        test("set stair facing follows player horizontal direction", this::testSetFacingOrientation);
        test("set default and special facing rules match vanilla", this::testSetFacingSpecialCases);
        test("set axis follows clicked selection face", this::testSetAxisOrientation);
        test("set stair half follows clicked selection face and height", this::testSetStairHalfOrientation);
        test("set slab type follows clicked selection face and height", this::testSetSlabTypeOrientation);
        test("draw materials resolve blocks, fluid buckets, and air", this::testDrawMaterialResolution);
        test("draw item state data round-trips and ignores invalid properties", this::testDrawItemStateData);
        test("saved block items are identified for the GUI marker", this::testSavedBlockItemMarker);
        test("stored item state is applied during direct placement",
                () -> testDirectStoredStatePlacement(simulation));
        test("replace matches and places exact stored states", () -> testExactStateReplacement(simulation));
        test("replace matches and places block entity data", () -> testBlockEntityReplacement(simulation));
        test("replace preserves compatible facing values", () -> testFacingPreservation(simulation));
        test("replace preserves stair state", () -> testStairStatePreservation(simulation));
        test("replace preserves slab type", () -> testSlabStatePreservation(simulation));
        test("replace recalculates compatible fence connections", () -> testFenceStatePreservation(simulation));
        test("replace preserves iron bars and pane connections", () -> testPaneStatePreservation(simulation));
        test("replace preserves colored pane connections", () -> testColoredPaneStatePreservation(simulation));
        test("replace recalculates wall connections and up", () -> testWallStatePreservation(simulation));
        test("replace recalculates fence-to-wall connections", () -> testFenceToWallStatePreservation(simulation));
        test("replace recalculates wall-to-fence connections", () -> testWallToFenceStatePreservation(simulation));
        test("replace updates wall, fence, and fence-gate neighbors", () -> testFenceWallGateConnections(simulation));
        test("replace preserves log axes", () -> testLogAxisPreservation(simulation));
        test("draw shapes restrict set and filtered replacement and retain undo", () -> testDrawShapes(simulation));
    }

    private void testDrawShapes(GameSimulation simulation) {
        World world = simulation.worldEditWorld();
        BlockPos spawn = simulation.overworld().getSharedSpawnPos();
        BlockVector3 first = BlockVector3.at(spawn.getX(), spawn.getY() + 22, spawn.getZ());
        for (int width : new int[]{1, 2, 5}) {
            CuboidRegion region = new CuboidRegion(world, first, first.add(width - 1, 2, 4));
            var positions = new java.util.ArrayList<BlockVector3>();
            region.forEach(positions::add);
            BaseBlock[] originals = positions.stream().map(world::getFullBlock).toArray(BaseBlock[]::new);
            try {
                for (DrawMode mode : DrawMode.values()) for (boolean filtered : new boolean[]{false, true}) {
                    try (EditSession setup = WorldEditBridge.newEditSession(world)) {
                        disableShapeUpdates(setup);
                        for (var pos : positions) setup.setBlock(pos,
                                (Math.floorMod(pos.getX() + pos.getZ(), 2) == 0 ? BlockTypes.STONE : BlockTypes.DIRT).getDefaultState());
                        setup.flushSession();
                    }
                    var target = SelectionDrawService.resolveDrawMaterial(new ItemStack(Items.GOLD_BLOCK)).orElseThrow();
                    var source = filtered ? SelectionDrawService.resolveDrawMaterial(new ItemStack(Items.STONE)).orElseThrow() : null;
                    try (EditSession edit = WorldEditBridge.newEditSession(world)) {
                        int affected = SelectionDrawService.applyShape(edit, region, mode, source, target);
                        edit.flushSession();
                        int expectedCount = 0;
                        for (var pos : positions) {
                            var original = Math.floorMod(pos.getX() + pos.getZ(), 2) == 0 ? BlockTypes.STONE : BlockTypes.DIRT;
                            boolean wall = pos.getX() == first.getX() || pos.getX() == first.getX() + width - 1
                                    || pos.getZ() == first.getZ() || pos.getZ() == first.getZ() + 4;
                            boolean changed = (mode == DrawMode.SET || wall) && (!filtered || original == BlockTypes.STONE);
                            if (changed) expectedCount++;
                            GameAssertions.assertEquals(changed ? BlockTypes.GOLD_BLOCK : original,
                                    world.getBlock(pos).getBlockType(), "Shape/filter touched an incorrect cell");
                        }
                        GameAssertions.assertEquals(expectedCount, affected, "Wall corners must not be applied twice");
                        try (EditSession undo = WorldEditBridge.newEditSession(world)) {
                            edit.undo(undo);
                            undo.flushSession();
                        }
                        for (var pos : positions) GameAssertions.assertEquals(
                                Math.floorMod(pos.getX() + pos.getZ(), 2) == 0 ? BlockTypes.STONE : BlockTypes.DIRT,
                                world.getBlock(pos).getBlockType(), "Undo must restore the whole operation");
                    }
                }
            } catch (WorldEditException exception) {
                throw new AssertionError(exception);
            } finally {
                restoreBlocks(world, positions.toArray(BlockVector3[]::new), originals);
            }
        }
    }

    private void testSetFacingOrientation() {
        BlockState original = withProperties(BlockTypes.OAK_STAIRS, new String[] {
                "facing", "north", "half", "top", "shape", "outer_left", "waterlogged", "true"
        });
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState oriented = SelectionDrawService.orientForSet(
                    original, direction, Direction.UP, Direction.UP, false);
            GameAssertions.assertEquals(direction.getSerializedName(), stateValueName(oriented, "facing"),
                    "Set facing must follow the player's horizontal direction");
            GameAssertions.assertEquals("bottom", stateValueName(oriented, "half"),
                    "A stair placed against the selection top face must use the bottom half");
            GameAssertions.assertEquals("outer_left", stateValueName(oriented, "shape"),
                    "Set orientation must retain stair shape");
            GameAssertions.assertEquals("true", stateValueName(oriented, "waterlogged"),
                    "Set orientation must retain unrelated properties");
        }
    }

    private void testSetFacingSpecialCases() {
        assertSetFacing(BlockTypes.FURNACE, Direction.NORTH, Direction.UP, Direction.UP, Direction.SOUTH);
        assertSetFacing(BlockTypes.RED_BED, Direction.NORTH, Direction.UP, Direction.UP, Direction.NORTH);
        assertSetFacing(BlockTypes.OAK_DOOR, Direction.WEST, Direction.DOWN, Direction.UP, Direction.WEST);
        assertSetFacing(BlockTypes.CAMPFIRE, Direction.EAST, Direction.DOWN, Direction.UP, Direction.EAST);
        assertSetFacing(BlockTypes.ANVIL, Direction.NORTH, Direction.UP, Direction.UP, Direction.EAST);
        assertSetFacing(BlockTypes.BARREL, Direction.NORTH, Direction.UP, Direction.UP, Direction.DOWN);
        assertSetFacing(BlockTypes.DISPENSER, Direction.NORTH, Direction.UP, Direction.SOUTH, Direction.DOWN);
        assertSetFacing(BlockTypes.OBSERVER, Direction.NORTH, Direction.UP, Direction.UP, Direction.UP);
        assertSetFacing(BlockTypes.SHULKER_BOX, Direction.NORTH, Direction.NORTH, Direction.DOWN, Direction.DOWN);
        assertSetFacing(BlockTypes.END_ROD, Direction.NORTH, Direction.NORTH, Direction.UP, Direction.UP);
        assertSetFacing(BlockTypes.HOPPER, Direction.NORTH, Direction.NORTH, Direction.EAST, Direction.WEST);
        assertSetFacing(BlockTypes.OAK_TRAPDOOR, Direction.NORTH, Direction.NORTH, Direction.WEST, Direction.WEST);
        assertSetFacing(BlockTypes.BELL, Direction.NORTH, Direction.NORTH, Direction.WEST, Direction.EAST);
        assertSetFacing(BlockTypes.LADDER, Direction.NORTH, Direction.NORTH, Direction.WEST, Direction.WEST);
    }

    private static void assertSetFacing(
            BlockType type,
            Direction horizontal,
            Direction look,
            Direction clickedFace,
            Direction expected
    ) {
        BlockState oriented = SelectionDrawService.orientForSet(
                type.getDefaultState(), horizontal, look, clickedFace, false);
        GameAssertions.assertEquals(expected.getSerializedName(), stateValueName(oriented, "facing"),
                "Set facing mismatch for " + type.getId());
    }

    private void testSetAxisOrientation() {
        for (Direction clickedFace : Direction.values()) {
            BlockState oriented = SelectionDrawService.orientForSet(
                    BlockTypes.OAK_LOG.getDefaultState(), Direction.NORTH, Direction.NORTH, clickedFace, false);
            GameAssertions.assertEquals(
                    clickedFace.getAxis().getSerializedName(),
                    stateValueName(oriented, "axis"),
                    "Set axis must follow the clicked selection face");
        }
    }

    private void testSetStairHalfOrientation() {
        for (Direction face : Direction.Plane.HORIZONTAL) {
            assertSetVerticalProperty(BlockTypes.OAK_STAIRS, face, false, "half", "bottom");
            assertSetVerticalProperty(BlockTypes.OAK_STAIRS, face, true, "half", "top");
        }
        assertSetVerticalProperty(BlockTypes.OAK_STAIRS, Direction.UP, false, "half", "bottom");
        assertSetVerticalProperty(BlockTypes.OAK_STAIRS, Direction.UP, true, "half", "bottom");
        assertSetVerticalProperty(BlockTypes.OAK_STAIRS, Direction.DOWN, false, "half", "top");
        assertSetVerticalProperty(BlockTypes.OAK_STAIRS, Direction.DOWN, true, "half", "top");
    }

    private void testSetSlabTypeOrientation() {
        for (Direction face : Direction.Plane.HORIZONTAL) {
            assertSetVerticalProperty(BlockTypes.OAK_SLAB, face, false, "type", "bottom");
            assertSetVerticalProperty(BlockTypes.OAK_SLAB, face, true, "type", "top");
        }
        assertSetVerticalProperty(BlockTypes.OAK_SLAB, Direction.UP, false, "type", "bottom");
        assertSetVerticalProperty(BlockTypes.OAK_SLAB, Direction.UP, true, "type", "bottom");
        assertSetVerticalProperty(BlockTypes.OAK_SLAB, Direction.DOWN, false, "type", "top");
        assertSetVerticalProperty(BlockTypes.OAK_SLAB, Direction.DOWN, true, "type", "top");
    }

    private static void assertSetVerticalProperty(
            BlockType type,
            Direction clickedFace,
            boolean clickedUpperHalf,
            String property,
            String expected
    ) {
        BlockState oriented = SelectionDrawService.orientForSet(
                type.getDefaultState(), Direction.WEST, Direction.UP, clickedFace, clickedUpperHalf);
        GameAssertions.assertEquals(expected, stateValueName(oriented, property),
                "Set " + property + " mismatch for " + type.getId() + " on " + clickedFace
                        + " (upper=" + clickedUpperHalf + ")");
        if (type == BlockTypes.OAK_STAIRS) {
            GameAssertions.assertEquals("west", stateValueName(oriented, "facing"),
                    "Stair facing must continue to follow the player direction");
        }
    }

    private void testDrawMaterialResolution() {
        GameAssertions.assertEquals(
                BlockTypes.WATER,
                SelectionDrawService.resolveDrawMaterial(new ItemStack(Items.WATER_BUCKET))
                        .orElseThrow()
                        .state()
                        .getBlockType(),
                "A water bucket must resolve to water in both set and replace");
        GameAssertions.assertEquals(
                BlockTypes.LAVA,
                SelectionDrawService.resolveDrawMaterial(new ItemStack(Items.LAVA_BUCKET))
                        .orElseThrow()
                        .state()
                        .getBlockType(),
                "A lava bucket must resolve to lava in both set and replace");
        GameAssertions.assertEquals(
                BlockTypes.AIR,
                SelectionDrawService.resolveDrawMaterial(new ItemStack(ModItems.AIR_BLOCK.get()))
                        .orElseThrow()
                        .state()
                        .getBlockType(),
                "The air placeholder must resolve to vanilla air in both set and replace");
        GameAssertions.assertEquals(
                BlockTypes.STONE,
                SelectionDrawService.resolveDrawMaterial(new ItemStack(Items.STONE))
                        .orElseThrow()
                        .state()
                        .getBlockType(),
                "A block item must resolve to its block in both set and replace");
        GameAssertions.check(
                SelectionDrawService.resolveDrawMaterial(new ItemStack(Items.BUCKET)).isEmpty(),
                "An empty bucket must not become a draw material");
    }

    private void testDrawItemStateData() {
        net.minecraft.world.level.block.state.BlockState expected = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.EAST)
                .setValue(net.minecraft.world.level.block.StairBlock.HALF, Half.TOP)
                .setValue(net.minecraft.world.level.block.StairBlock.SHAPE, StairsShape.INNER_LEFT)
                .setValue(net.minecraft.world.level.block.StairBlock.WATERLOGGED, true);
        ItemStack stack = new ItemStack(Items.OAK_STAIRS);
        BlockStateItemData.write(stack, expected);

        CompoundTag customData = stack.getTag();
        GameAssertions.check(customData != null, "Stored states must use the item NBT tag");
        CompoundTag root = customData.getCompound(BlockStateItemData.ROOT_KEY);
        GameAssertions.assertEquals(
                expected.getProperties().size(), root.size(),
                "Ctrl-pick metadata must contain every property of the copied state");
        stack.getOrCreateTag().getCompound(BlockStateItemData.ROOT_KEY).putString("big", "invalid");

        BlockStateItemData.StoredState decoded = BlockStateItemData.read(stack).orElseThrow();
        GameAssertions.assertEquals(expected, decoded.state(), "Valid saved properties must round-trip exactly");
        GameAssertions.check(!decoded.properties().containsKey("big"), "Unknown properties must be ignored");
        GameAssertions.check(
                SelectionDrawService.resolveDrawMaterial(stack).orElseThrow().exactState(),
                "A worldeditpp state root must select exact-state draw behavior");
    }

    private void testSavedBlockItemMarker() {
        ItemStack plainStone = new ItemStack(Items.STONE);
        ItemStack statefulStone = plainStone.copy();
        BlockStateItemData.write(statefulStone, Blocks.STONE.defaultBlockState());
        GameAssertions.check(
                !BlockItemSavedData.hasSavedData(plainStone),
                "An ordinary block item must not receive the saved-state marker");
        GameAssertions.check(
                !BlockItemSavedData.hasSavedData(statefulStone),
                "A block without properties must continue to use vanilla pick behavior");

        ItemStack statefulStair = new ItemStack(Items.OAK_STAIRS);
        BlockStateItemData.write(statefulStair, Blocks.OAK_STAIRS.defaultBlockState());
        GameAssertions.check(
                BlockItemSavedData.hasSavedData(statefulStair),
                "A worldeditpp state compound must receive the marker");

        ItemStack plainChest = new ItemStack(Items.CHEST);
        ItemStack copiedChest = plainChest.copy();
        copiedChest.setHoverName( net.minecraft.network.chat.Component.literal("Stored chest"));
        GameAssertions.check(
                !BlockItemSavedData.hasSavedData(plainChest),
                "An ordinary block-entity item must not receive the marker");
        GameAssertions.check(
                BlockItemSavedData.hasSavedData(copiedChest),
                "A block-entity item with copied components must receive the marker");
    }

    private void testDirectStoredStatePlacement(GameSimulation simulation) {
        ServerLevel level = simulation.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos position = spawn.offset(0, 16, 0);
        net.minecraft.world.level.block.state.BlockState original = level.getBlockState(position);
        net.minecraft.world.level.block.state.BlockState saved = Blocks.OAK_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, SlabType.TOP);
        ItemStack stack = new ItemStack(Items.OAK_SLAB);
        BlockStateItemData.write(stack, saved);

        try {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            BlockHitResult hit = new BlockHitResult(
                    new Vec3(position.getX() + 0.5D, position.getY() + 0.25D, position.getZ() + 0.5D),
                    Direction.UP,
                    position,
                    false);
            InteractionResult result = ((BlockItem) stack.getItem()).place(
                    new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, stack, hit));

            GameAssertions.check(result.consumesAction(), "Stored-state slab placement must succeed");
            GameAssertions.assertEquals(saved, level.getBlockState(position),
                    "Direct placement must apply the state stored in worldeditpp custom data");
        } finally {
            level.setBlock(position, original, Block.UPDATE_ALL);
        }
    }

    private void testExactStateReplacement(GameSimulation simulation) {
        for (DrawMode mode : DrawMode.values()) testExactStateReplacement(simulation, mode);
    }

    private void testExactStateReplacement(GameSimulation simulation, DrawMode mode) {
        World world = simulation.worldEditWorld();
        BlockPos spawn = simulation.overworld().getSharedSpawnPos();
        BlockVector3 first = BlockVector3.at(spawn.getX(), spawn.getY() + 14, spawn.getZ());
        BlockVector3 second = first.add(1, 0, 0);
        BaseBlock originalFirst = world.getFullBlock(first);
        BaseBlock originalSecond = world.getFullBlock(second);

        net.minecraft.world.level.block.state.BlockState sourceState = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.EAST)
                .setValue(net.minecraft.world.level.block.StairBlock.HALF, Half.BOTTOM);
        net.minecraft.world.level.block.state.BlockState otherState = sourceState
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.WEST);
        net.minecraft.world.level.block.state.BlockState targetState = Blocks.STONE_STAIRS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH)
                .setValue(net.minecraft.world.level.block.StairBlock.HALF, Half.TOP)
                .setValue(net.minecraft.world.level.block.StairBlock.SHAPE, StairsShape.OUTER_RIGHT);
        ItemStack sourceStack = new ItemStack(Items.OAK_STAIRS);
        ItemStack targetStack = new ItemStack(Items.STONE_STAIRS);
        BlockStateItemData.write(sourceStack, sourceState);
        BlockStateItemData.write(targetStack, targetState);
        var source = SelectionDrawService.resolveDrawMaterial(sourceStack).orElseThrow();
        var target = SelectionDrawService.resolveDrawMaterial(targetStack).orElseThrow();

        try {
            try (EditSession setup = WorldEditBridge.newEditSession(world)) {
                setup.setBlock(first, ForgeAdapter.adapt(sourceState));
                setup.setBlock(second, ForgeAdapter.adapt(otherState));
                setup.flushSession();
            }
            int affected;
            try (EditSession replacement = WorldEditBridge.newEditSession(world)) {
                affected = SelectionDrawService.applyShape(
                        replacement, new CuboidRegion(world, first, second), mode, source, target);
                replacement.flushSession();
            }
            GameAssertions.assertEquals(1, affected, "Exact source state must replace only the matching stair");
            GameAssertions.assertEquals(
                    ForgeAdapter.adapt(targetState), world.getBlock(first),
                    "Exact target state must not inherit properties from the source");
            GameAssertions.assertEquals(
                    ForgeAdapter.adapt(otherState), world.getBlock(second),
                    "A different source state must remain untouched");
        } catch (WorldEditException exception) {
            throw new IllegalStateException("Failed to exercise exact draw-state replacement", exception);
        } finally {
            restoreBlocks(world, new BlockVector3[] {first, second}, new BaseBlock[] {originalFirst, originalSecond});
        }
    }

    private void testBlockEntityReplacement(GameSimulation simulation) {
        for (DrawMode mode : DrawMode.values()) testBlockEntityReplacement(simulation, mode);
    }

    private void testBlockEntityReplacement(GameSimulation simulation, DrawMode mode) {
        World world = simulation.worldEditWorld();
        BlockPos spawn = simulation.overworld().getSharedSpawnPos();
        BlockVector3 matching = BlockVector3.at(spawn.getX(), spawn.getY() + 15, spawn.getZ());
        BlockVector3 different = matching.add(1, 0, 0);
        BaseBlock originalMatching = world.getFullBlock(matching);
        BaseBlock originalDifferent = world.getFullBlock(different);

        ItemStack diamondChest = containerItem(Items.CHEST, Blocks.CHEST.defaultBlockState(), Items.DIAMOND, 7,
                simulation.overworld().registryAccess());
        ItemStack dirtChest = containerItem(Items.CHEST, Blocks.CHEST.defaultBlockState(), Items.DIRT, 3,
                simulation.overworld().registryAccess());
        ItemStack goldBarrel = containerItem(Items.BARREL, Blocks.BARREL.defaultBlockState(), Items.GOLD_INGOT, 11,
                simulation.overworld().registryAccess());
        var source = SelectionDrawService.resolveDrawMaterial(
                diamondChest, simulation.overworld().registryAccess()).orElseThrow();
        var other = SelectionDrawService.resolveDrawMaterial(
                dirtChest, simulation.overworld().registryAccess()).orElseThrow();
        var target = SelectionDrawService.resolveDrawMaterial(
                goldBarrel, simulation.overworld().registryAccess()).orElseThrow();
        GameAssertions.check(source.blockEntityNbt() != null, "Picked chest contents must become draw NBT");
        GameAssertions.check(target.blockEntityNbt() != null, "Target barrel contents must become draw NBT");

        try {
            try (EditSession setup = WorldEditBridge.newEditSession(world)) {
                setup.setBlock(matching, source.toBaseBlock());
                setup.setBlock(different, other.toBaseBlock());
                setup.flushSession();
            }
            int affected;
            try (EditSession replacement = WorldEditBridge.newEditSession(world)) {
                affected = SelectionDrawService.applyShape(
                        replacement, new CuboidRegion(world, matching, different), mode, source, target);
                replacement.flushSession();
            }
            GameAssertions.assertEquals(1, affected, "Source block entity NBT must be matched exactly");
            GameAssertions.assertEquals(
                    Blocks.BARREL, simulation.overworld().getBlockState(ForgeAdapter.toBlockPos(matching)).getBlock(),
                    "Matching chest must become the target block entity");
            BlockEntity result = simulation.overworld().getBlockEntity(ForgeAdapter.toBlockPos(matching));
            GameAssertions.check(result instanceof Container, "Placed barrel must expose its inventory");
            ItemStack placedContents = ((Container) result).getItem(0);
            GameAssertions.assertEquals(
                    Items.GOLD_INGOT, placedContents.getItem(),
                    "Target block entity item type must be preserved");
            GameAssertions.assertEquals(
                    11, placedContents.getCount(), "Target block entity item count must be preserved");
            GameAssertions.assertEquals(
                    Blocks.CHEST, simulation.overworld().getBlockState(ForgeAdapter.toBlockPos(different)).getBlock(),
                    "A chest with different NBT must remain untouched");
        } catch (WorldEditException exception) {
            throw new IllegalStateException("Failed to exercise block entity draw replacement", exception);
        } finally {
            restoreBlocks(world, new BlockVector3[] {matching, different},
                    new BaseBlock[] {originalMatching, originalDifferent});
        }
    }

    private static ItemStack containerItem(
            net.minecraft.world.item.Item item,
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.item.Item contents,
            int count,
            net.minecraft.core.HolderLookup.Provider registries
    ) {
        BlockEntity blockEntity = ((EntityBlock) state.getBlock()).newBlockEntity(BlockPos.ZERO, state);
        if (!(blockEntity instanceof Container container)) {
            throw new IllegalStateException("Expected a container block entity for " + item);
        }
        container.setItem(0, new ItemStack(contents, count));
        ItemStack stack = new ItemStack(item);
        blockEntity.saveToItem(stack);
        return stack;
    }

    private void testStairStatePreservation(GameSimulation simulation) {
        testPreservedStates(simulation, 4, BlockTypes.OAK_STAIRS, BlockTypes.STONE_STAIRS, new String[][] {
                {"facing", "north", "half", "top", "shape", "inner_left"},
                {"facing", "east", "half", "bottom", "shape", "outer_right"},
                {"facing", "south", "half", "top", "shape", "straight"},
                {"facing", "west", "half", "bottom", "shape", "inner_right"}
        });
    }

    private void testSlabStatePreservation(GameSimulation simulation) {
        testPreservedStates(simulation, 5, BlockTypes.OAK_SLAB, BlockTypes.STONE_SLAB, new String[][] {
                {"type", "bottom"},
                {"type", "top"},
                {"type", "double"}
        });
    }

    private void testFenceStatePreservation(GameSimulation simulation) {
        testPreservedStates(simulation, 6, BlockTypes.OAK_FENCE, BlockTypes.NETHER_BRICK_FENCE, new String[][] {
                {"east", "true", "north", "false", "south", "false", "west", "false"},
                {"east", "false", "north", "false", "south", "false", "west", "true"}
        });
    }

    private void testWallStatePreservation(GameSimulation simulation) {
        testMappedStates(simulation, 9, BlockTypes.COBBLESTONE_WALL, BlockTypes.BRICK_WALL,
                new String[][] {
                        {"east", "low", "north", "tall", "south", "none", "west", "low", "up", "false"},
                        {"east", "tall", "north", "none", "south", "low", "west", "tall", "up", "true"}
                },
                new String[][] {
                        {"east", "low", "north", "none", "south", "none", "west", "none", "up", "true"},
                        {"east", "none", "north", "none", "south", "none", "west", "low", "up", "true"}
                });
    }

    private void testFenceToWallStatePreservation(GameSimulation simulation) {
        testMappedStates(simulation, 10, BlockTypes.OAK_FENCE, BlockTypes.COBBLESTONE_WALL,
                new String[][] {
                        {"east", "true", "north", "false", "south", "true", "west", "false"},
                        {"east", "false", "north", "true", "south", "false", "west", "true"}
                },
                new String[][] {
                        {"east", "low", "north", "none", "south", "none", "west", "none", "up", "true"},
                        {"east", "none", "north", "none", "south", "none", "west", "low", "up", "true"}
                });
    }

    private void testWallToFenceStatePreservation(GameSimulation simulation) {
        testMappedStates(simulation, 11, BlockTypes.COBBLESTONE_WALL, BlockTypes.OAK_FENCE,
                new String[][] {
                        {"east", "low", "north", "tall", "south", "none", "west", "low", "up", "false"},
                        {"east", "none", "north", "low", "south", "tall", "west", "none", "up", "true"}
                },
                new String[][] {
                        {"east", "true", "north", "false", "south", "false", "west", "false"},
                        {"east", "false", "north", "false", "south", "false", "west", "true"}
                });
    }

    private void testFenceWallGateConnections(GameSimulation simulation) {
        World world = simulation.worldEditWorld();
        BlockPos spawn = simulation.overworld().getSharedSpawnPos();
        BlockVector3 wallPosition = BlockVector3.at(spawn.getX(), spawn.getY() + 13, spawn.getZ());
        BlockVector3 gatePosition = wallPosition.add(1, 0, 0);
        BlockVector3 fencePosition = wallPosition.add(0, 0, -1);
        Map<BlockVector3, BaseBlock> originals = new LinkedHashMap<>();

        try {
            try (EditSession setup = WorldEditBridge.newEditSession(world)) {
                disableShapeUpdates(setup);
                for (int x = wallPosition.getX() - 1; x <= gatePosition.getX() + 1; x++) {
                    for (int z = wallPosition.getZ() - 1; z <= wallPosition.getZ() + 1; z++) {
                        for (int y = wallPosition.getY(); y <= wallPosition.getY() + 1; y++) {
                            BlockVector3 position = BlockVector3.at(x, y, z);
                            originals.put(position, world.getFullBlock(position));
                            setup.setBlock(position, BlockTypes.AIR.getDefaultState());
                        }
                    }
                }
                setup.setBlock(wallPosition, BlockTypes.OAK_FENCE.getDefaultState());
                setup.setBlock(gatePosition, withProperties(BlockTypes.OAK_FENCE_GATE, new String[] {
                        "facing", "north", "in_wall", "false"
                }));
                setup.setBlock(fencePosition, withProperties(BlockTypes.NETHER_BRICK_FENCE, new String[] {
                        "south", "true"
                }));
                setup.flushSession();
            }

            try (EditSession replacement = WorldEditBridge.newEditSession(world)) {
                SelectionDrawService.replaceBlocks(
                        replacement,
                        new CuboidRegion(world, wallPosition, wallPosition),
                        BlockTypes.OAK_FENCE,
                        BlockTypes.COBBLESTONE_WALL.getDefaultState());
                replacement.flushSession();
            }

            assertBlockProperties(world, wallPosition, BlockTypes.COBBLESTONE_WALL, new String[] {
                    "north", "none", "up", "true"
            });
            assertWallSide(world, wallPosition, "east", "low", "tall");
            assertBlockProperties(world, gatePosition, BlockTypes.OAK_FENCE_GATE, new String[] {
                    "facing", "north", "in_wall", "true"
            });
            assertBlockProperties(world, fencePosition, BlockTypes.NETHER_BRICK_FENCE, new String[] {
                    "south", "false"
            });
        } catch (WorldEditException exception) {
            throw new IllegalStateException("Failed to exercise vanilla connection recalculation", exception);
        } finally {
            try (EditSession restore = WorldEditBridge.newEditSession(world)) {
                for (Map.Entry<BlockVector3, BaseBlock> entry : originals.entrySet()) {
                    restore.setBlock(entry.getKey(), entry.getValue());
                }
                restore.flushSession();
            } catch (WorldEditException exception) {
                throw new IllegalStateException("Failed to restore connection recalculation test blocks", exception);
            }
        }
    }

    private void testLogAxisPreservation(GameSimulation simulation) {
        testPreservedStates(simulation, 12, BlockTypes.OAK_LOG, BlockTypes.STRIPPED_OAK_LOG, new String[][] {
                {"axis", "x"},
                {"axis", "y"},
                {"axis", "z"}
        });
    }

    private void testPaneStatePreservation(GameSimulation simulation) {
        testPreservedStates(simulation, 7, BlockTypes.IRON_BARS, BlockTypes.GLASS_PANE, new String[][] {
                {"east", "true", "north", "false", "south", "true", "west", "false"},
                {"east", "false", "north", "true", "south", "false", "west", "true"}
        });
    }

    private void testColoredPaneStatePreservation(GameSimulation simulation) {
        testPreservedStates(simulation, 8,
                BlockTypes.WHITE_STAINED_GLASS_PANE,
                BlockTypes.RED_STAINED_GLASS_PANE,
                new String[][] {
                        {"east", "true", "north", "true", "south", "false", "west", "false"},
                        {"east", "false", "north", "false", "south", "true", "west", "true"}
                });
    }

    private void testPreservedStates(
            GameSimulation simulation,
            int yOffset,
            BlockType sourceType,
            BlockType targetType,
            String[][] states) {
        testMappedStates(simulation, yOffset, sourceType, targetType, states, states);
    }

    private void testMappedStates(
            GameSimulation simulation,
            int yOffset,
            BlockType sourceType,
            BlockType targetType,
            String[][] sourceStates,
            String[][] expectedStates) {
        World world = simulation.worldEditWorld();
        BlockPos spawn = simulation.overworld().getSharedSpawnPos();
        BlockVector3 first = BlockVector3.at(spawn.getX(), spawn.getY() + yOffset, spawn.getZ());
        BlockVector3 last = first.add(sourceStates.length - 1, 0, 0);
        Map<BlockVector3, BaseBlock> originals = new LinkedHashMap<>();
        CuboidRegion region = new CuboidRegion(world, first, last);

        try {
            try (EditSession setup = WorldEditBridge.newEditSession(world)) {
                disableShapeUpdates(setup);
                for (int x = first.getX() - 1; x <= last.getX() + 1; x++) {
                    for (int z = first.getZ() - 1; z <= first.getZ() + 1; z++) {
                        for (int y = first.getY(); y <= first.getY() + 1; y++) {
                            BlockVector3 position = BlockVector3.at(x, y, z);
                            originals.put(position, world.getFullBlock(position));
                            setup.setBlock(position, BlockTypes.AIR.getDefaultState());
                        }
                    }
                }
                for (int index = 0; index < sourceStates.length; index++) {
                    setup.setBlock(first.add(index, 0, 0), withProperties(sourceType, sourceStates[index]));
                }
                setup.flushSession();
            }

            int affected;
            try (EditSession replacement = WorldEditBridge.newEditSession(world)) {
                affected = SelectionDrawService.replaceBlocks(
                        replacement, region, sourceType, targetType.getDefaultState());
                replacement.flushSession();
            }

            GameAssertions.assertEquals(sourceStates.length, affected,
                    "Every matching source block should be replaced exactly once");
            for (int index = 0; index < expectedStates.length; index++) {
                assertBlockProperties(world, first.add(index, 0, 0), targetType, expectedStates[index]);
            }
        } catch (WorldEditException exception) {
            throw new IllegalStateException("Failed to exercise state-preserving replacement", exception);
        } finally {
            try (EditSession restore = WorldEditBridge.newEditSession(world)) {
                for (Map.Entry<BlockVector3, BaseBlock> entry : originals.entrySet()) {
                    restore.setBlock(entry.getKey(), entry.getValue());
                }
                restore.flushSession();
            } catch (WorldEditException exception) {
                throw new IllegalStateException("Failed to restore state replacement test blocks", exception);
            }
        }
    }

    private void testFacingPreservation(GameSimulation simulation) {
        World world = simulation.worldEditWorld();
        BlockPos spawn = simulation.overworld().getSharedSpawnPos();
        BlockVector3 first = BlockVector3.at(spawn.getX(), spawn.getY() + 3, spawn.getZ());
        BlockVector3 second = first.add(1, 0, 0);
        BlockVector3 untouched = first.add(2, 0, 0);
        CuboidRegion region = new CuboidRegion(world, first, untouched);
        BaseBlock originalFirst = world.getFullBlock(first);
        BaseBlock originalSecond = world.getFullBlock(second);
        BaseBlock originalUntouched = world.getFullBlock(untouched);

        try {
            try (EditSession setup = WorldEditBridge.newEditSession(world)) {
                setup.setBlock(first, withProperty(BlockTypes.FURNACE, "facing", "east"));
                setup.setBlock(second, withProperty(BlockTypes.FURNACE, "facing", "west"));
                setup.setBlock(untouched, BlockTypes.STONE.getDefaultState());
                setup.flushSession();
            }

            int affected;
            try (EditSession replacement = WorldEditBridge.newEditSession(world)) {
                affected = SelectionDrawService.replaceBlocks(
                        replacement, region, BlockTypes.FURNACE, BlockTypes.BLAST_FURNACE.getDefaultState());
                replacement.flushSession();
            }

            GameAssertions.assertEquals(2, affected, "Only matching source block types should be replaced");
            assertBlock(world, first, BlockTypes.BLAST_FURNACE, "east");
            assertBlock(world, second, BlockTypes.BLAST_FURNACE, "west");
            GameAssertions.assertEquals(BlockTypes.STONE, world.getBlock(untouched).getBlockType(),
                    "Non-matching block types must remain unchanged");
        } catch (WorldEditException exception) {
            throw new IllegalStateException("Failed to exercise selection draw replacement", exception);
        } finally {
            try (EditSession restore = WorldEditBridge.newEditSession(world)) {
                restore.setBlock(first, originalFirst);
                restore.setBlock(second, originalSecond);
                restore.setBlock(untouched, originalUntouched);
                restore.flushSession();
            } catch (WorldEditException exception) {
                throw new IllegalStateException("Failed to restore selection draw test blocks", exception);
            }
        }
    }

    private static void assertBlock(World world, BlockVector3 position, BlockType type, String facing) {
        BlockState state = world.getBlock(position);
        GameAssertions.assertEquals(type, state.getBlockType(), "Replacement block type mismatch");
        GameAssertions.assertEquals(facing, propertyValue(state, "facing").toString().toLowerCase(java.util.Locale.ROOT),
                "Replacement must preserve the source facing value");
    }

    private static BlockState withProperty(BlockType type, String propertyName, String value) {
        Property<?> property = type.getProperties().stream()
                .filter(candidate -> candidate.getName().equals(propertyName))
                .findFirst()
                .orElseThrow();
        Object propertyValue = property.getValueFor(value);
        if (propertyValue == null) throw new IllegalArgumentException("Unsupported property value: " + value);
        return withState(type.getDefaultState(), property, propertyValue);
    }

    private static BlockState withProperties(BlockType type, String[] properties) {
        BlockState state = type.getDefaultState();
        for (int index = 0; index < properties.length; index += 2) {
            String propertyName = properties[index];
            Property<?> property = type.getProperties().stream()
                    .filter(candidate -> candidate.getName().equals(propertyName))
                    .findFirst()
                    .orElseThrow();
            Object value = property.getValueFor(properties[index + 1]);
            if (value == null) {
                throw new IllegalArgumentException("Unsupported property value: " + properties[index + 1]);
            }
            state = withState(state, property, value);
        }
        return state;
    }

    private static void assertBlockProperties(
            World world, BlockVector3 position, BlockType type, String[] properties) {
        BlockState state = world.getBlock(position);
        GameAssertions.assertEquals(type, state.getBlockType(), "Replacement block type mismatch");
        for (int index = 0; index < properties.length; index += 2) {
            GameAssertions.assertEquals(
                    properties[index + 1],
                    propertyValue(state, properties[index]).toString().toLowerCase(java.util.Locale.ROOT),
                    "Replacement property mismatch: " + properties[index]);
        }
    }

    private static void assertWallSide(
            World world, BlockVector3 position, String property, String... allowed) {
        BlockState state = world.getBlock(position);
        String actual = propertyValue(state, property).toString().toLowerCase(java.util.Locale.ROOT);
        GameAssertions.check(java.util.Arrays.asList(allowed).contains(actual),
                "Wall side must connect after vanilla recalculation: " + property + "=" + actual);
    }

    private static void disableShapeUpdates(EditSession editSession) {
        editSession.setSideEffectApplier(editSession.getSideEffectApplier()
                .with(SideEffect.NEIGHBORS, SideEffect.State.OFF)
                .with(SideEffect.UPDATE, SideEffect.State.OFF)
                .with(SideEffect.VALIDATION, SideEffect.State.OFF));
    }

    private static void restoreBlocks(World world, BlockVector3[] positions, BaseBlock[] blocks) {
        try (EditSession restore = WorldEditBridge.newEditSession(world)) {
            for (int index = 0; index < positions.length; index++) {
                restore.setBlock(positions[index], blocks[index]);
            }
            restore.flushSession();
        } catch (WorldEditException exception) {
            throw new IllegalStateException("Failed to restore draw test blocks", exception);
        }
    }

    private static Object propertyValue(BlockState state, String propertyName) {
        return state.getStates().entrySet().stream()
                .filter(entry -> entry.getKey().getName().equals(propertyName))
                .map(java.util.Map.Entry::getValue)
                .findFirst()
                .orElseThrow();
    }

    private static String stateValueName(BlockState state, String propertyName) {
        return propertyValue(state, propertyName).toString().toLowerCase(java.util.Locale.ROOT);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState withState(BlockState state, Property property, Object value) {
        return state.with(property, value);
    }
}
