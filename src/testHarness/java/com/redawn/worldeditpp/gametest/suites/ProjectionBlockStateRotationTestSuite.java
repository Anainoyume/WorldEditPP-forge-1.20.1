package com.redawn.worldeditpp.gametest.suites;

import com.redawn.worldeditpp.gametest.framework.GameAssertions;
import com.redawn.worldeditpp.gametest.framework.GameSimulation;
import com.redawn.worldeditpp.gametest.framework.GameTestSuiteDescriptor;
import com.redawn.worldeditpp.gametest.framework.TestSuiteBase;
import com.redawn.worldeditpp.math.DiscreteRotation;
import com.redawn.worldeditpp.bridge.minecraft.math.MinecraftBlockStateRotation;
import com.redawn.worldeditpp.math.SlabStateTransform;
import com.redawn.worldeditpp.math.SlabStateTransform.Type;
import com.redawn.worldeditpp.math.StairStateTransform;
import com.redawn.worldeditpp.math.StairStateTransform.Facing;
import com.redawn.worldeditpp.math.StairStateTransform.Half;
import com.redawn.worldeditpp.math.StairStateTransform.Shape;
import com.redawn.worldeditpp.math.StairStateTransform.State;
import com.redawn.worldeditpp.bridge.worldedit.ProjectionBlockStateRotation;
import com.redawn.worldeditpp.bridge.worldedit.ProjectionClipboardRotation;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

@GameTestSuiteDescriptor(name = "Projection block-state rotation")
public final class ProjectionBlockStateRotationTestSuite extends TestSuiteBase {
    @Override
    protected void runTests(GameSimulation simulation) {
        test("all 960 stair mappings reach BlockState", this::testAllMappings);
        test("Minecraft registry stair states use final-matrix mapping", this::testRegistryRoundTripMappings);
        test("client-native stair states use final-matrix mapping", this::testNativeStairMappings);
        test("client-native stair rotation recovers after an illegal step", this::testNativeStairRecovery);
        test("every stair transition is derived from its original state", this::testAllStairTransitions);
        test("illegal quarter turn does not hide a later legal half turn", this::testOriginalStateRecovery);
        test("clipboard pair retains original state across rotations", this::testClipboardPairRecovery);
        test("adjacent stair states remain paired with their source cells", this::testAdjacentStairPairing);
        test("shape handedness and waterlogging are preserved", this::testShapeAndWaterlogging);
        test("all 72 slab mappings reach BlockState", this::testAllSlabMappings);
        test("client-native slab states use final-matrix mapping", this::testNativeSlabMappings);
        test("slabs recover from illegal intermediate rotations", this::testSlabOriginalStateRecovery);
        test("non-stair rotation keeps WorldEdit behavior", this::testNonStairFallback);
        test("client-native fallback rotates facing and axis properties", this::testNativeNonStairFallback);
        test("client and server fallback rotations stay identical", this::testFallbackParity);
    }

    private void testAllMappings() {
        Set<DiscreteRotation> rotations = enumerateOrientations();
        GameAssertions.assertEquals(24, rotations.size(), "Expected all 24 spatial orientations");
        int mappings = 0;
        for (Facing facing : Facing.values()) {
            for (Half half : Half.values()) {
                for (Shape shape : Shape.values()) {
                    State originalMath = new State(facing, half, shape);
                    BlockState originalBlock = stairState(originalMath, false);
                    for (DiscreteRotation rotation : rotations) {
                        StairStateTransform.Result expected = StairStateTransform.transform(originalMath, rotation);
                        BlockState actual = ProjectionBlockStateRotation.rotateStair(originalBlock, rotation);
                        assertStairState(expected.state(), actual);
                        mappings++;
                    }
                }
            }
        }
        GameAssertions.assertEquals(960, mappings, "Expected all 40 stair states across 24 orientations");
    }

    private void testOriginalStateRecovery() {
        State originalMath = new State(Facing.EAST, Half.BOTTOM, Shape.STRAIGHT);
        BlockState original = stairState(originalMath, false);
        DiscreteRotation x90 = DiscreteRotation.IDENTITY.thenWorldQuarterTurn(0, 1);
        BlockState first = ProjectionBlockStateRotation.rotate(original, original, x90, 0, 1);
        GameAssertions.assertEquals(original, first, "A sideways stair must fall back to its original BlockState");

        DiscreteRotation x180 = x90.thenWorldQuarterTurn(0, 1);
        BlockState second = ProjectionBlockStateRotation.rotate(first, original, x180, 0, 1);
        assertStairState(new State(Facing.EAST, Half.TOP, Shape.STRAIGHT), second);
    }

    private void testRegistryRoundTripMappings() {
        int mappings = 0;
        for (Facing facing : Facing.values()) {
            for (Half half : Half.values()) {
                for (Shape shape : Shape.values()) {
                    State originalMath = new State(facing, half, shape);
                    net.minecraft.world.level.block.state.BlockState minecraftOriginal = Blocks.OAK_STAIRS
                            .defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.valueOf(facing.name()))
                            .setValue(StairBlock.HALF, half == Half.BOTTOM
                                    ? net.minecraft.world.level.block.state.properties.Half.BOTTOM
                                    : net.minecraft.world.level.block.state.properties.Half.TOP)
                            .setValue(StairBlock.SHAPE, StairsShape.valueOf(shape.name()));
                    int originalId = Block.BLOCK_STATE_REGISTRY.getId(minecraftOriginal);
                    BlockState worldEditOriginal = ForgeAdapter.adapt(
                            Block.BLOCK_STATE_REGISTRY.byId(originalId));
                    for (DiscreteRotation rotation : enumerateOrientations()) {
                        StairStateTransform.Result expected = StairStateTransform.transform(originalMath, rotation);
                        BlockState transformed = ProjectionBlockStateRotation.rotate(
                                worldEditOriginal, worldEditOriginal, rotation, 0, 1);
                        net.minecraft.world.level.block.state.BlockState minecraftResult =
                                ForgeAdapter.adapt(transformed);
                        GameAssertions.assertEquals(expected.state().facing().name(),
                                minecraftResult.getValue(StairBlock.FACING).name().toUpperCase(Locale.ROOT),
                                "Registry round-trip stair facing mismatch");
                        GameAssertions.assertEquals(expected.state().half() == Half.BOTTOM
                                        ? net.minecraft.world.level.block.state.properties.Half.BOTTOM
                                        : net.minecraft.world.level.block.state.properties.Half.TOP,
                                minecraftResult.getValue(StairBlock.HALF),
                                "Registry round-trip stair half mismatch");
                        GameAssertions.assertEquals(StairsShape.valueOf(expected.state().shape().name()),
                                minecraftResult.getValue(StairBlock.SHAPE),
                                "Registry round-trip stair shape mismatch");
                        mappings++;
                    }
                }
            }
        }
        GameAssertions.assertEquals(960, mappings,
                "Expected all stair states to survive the client registry bridge");
    }

    private void testNativeStairMappings() {
        int mappings = 0;
        for (Facing facing : Facing.values()) {
            for (Half half : Half.values()) {
                for (Shape shape : Shape.values()) {
                    State originalMath = new State(facing, half, shape);
                    net.minecraft.world.level.block.state.BlockState original = Blocks.OAK_STAIRS
                            .defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.valueOf(facing.name()))
                            .setValue(StairBlock.HALF, half == Half.BOTTOM
                                    ? net.minecraft.world.level.block.state.properties.Half.BOTTOM
                                    : net.minecraft.world.level.block.state.properties.Half.TOP)
                            .setValue(StairBlock.SHAPE, StairsShape.valueOf(shape.name()));
                    for (DiscreteRotation rotation : enumerateOrientations()) {
                        StairStateTransform.Result expected = StairStateTransform.transform(originalMath, rotation);
                        net.minecraft.world.level.block.state.BlockState actual =
                                MinecraftBlockStateRotation.rotateStair(original, rotation);
                        GameAssertions.assertEquals(
                                expected.state().facing().name(),
                                actual.getValue(StairBlock.FACING).name(),
                                "Native stair facing mismatch");
                        GameAssertions.assertEquals(
                                expected.state().half() == Half.BOTTOM
                                        ? net.minecraft.world.level.block.state.properties.Half.BOTTOM
                                        : net.minecraft.world.level.block.state.properties.Half.TOP,
                                actual.getValue(StairBlock.HALF),
                                "Native stair half mismatch");
                        GameAssertions.assertEquals(
                                StairsShape.valueOf(expected.state().shape().name()),
                                actual.getValue(StairBlock.SHAPE),
                                "Native stair shape mismatch");
                        mappings++;
                    }
                }
            }
        }
        GameAssertions.assertEquals(960, mappings,
                "Expected all stair states to rotate without the WorldEdit client registry");
    }

    private void testNativeStairRecovery() {
        net.minecraft.world.level.block.state.BlockState original = Blocks.OAK_STAIRS
                .defaultBlockState()
                .setValue(StairBlock.FACING, Direction.EAST)
                .setValue(StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.BOTTOM)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
        DiscreteRotation x90 = DiscreteRotation.IDENTITY.thenWorldQuarterTurn(0, 1);
        net.minecraft.world.level.block.state.BlockState first = MinecraftBlockStateRotation.rotate(
                original, original, x90, 0, 1);
        GameAssertions.assertEquals(original, first,
                "An illegal native stair turn must return its original state");

        DiscreteRotation x180 = x90.thenWorldQuarterTurn(0, 1);
        net.minecraft.world.level.block.state.BlockState second = MinecraftBlockStateRotation.rotate(
                first, original, x180, 0, 1);
        GameAssertions.assertEquals(Direction.EAST, second.getValue(StairBlock.FACING),
                "A later legal native stair turn must retain its mapped facing");
        GameAssertions.assertEquals(
                net.minecraft.world.level.block.state.properties.Half.TOP,
                second.getValue(StairBlock.HALF),
                "A later legal native stair turn must derive half from the original state");
    }

    private void testAllStairTransitions() {
        Set<DiscreteRotation> rotations = enumerateOrientations();
        int transitions = 0;
        for (Facing facing : Facing.values()) {
            for (Half half : Half.values()) {
                for (Shape shape : Shape.values()) {
                    State originalMath = new State(facing, half, shape);
                    BlockState originalBlock = stairState(originalMath, false);
                    for (DiscreteRotation rotation : rotations) {
                        BlockState currentBlock = ProjectionBlockStateRotation.rotateStair(originalBlock, rotation);
                        for (int axis = 0; axis < 3; axis++) {
                            for (int quarterTurn : new int[]{-1, 1}) {
                                DiscreteRotation finalRotation = rotation.thenWorldQuarterTurn(axis, quarterTurn);
                                BlockState expected = ProjectionBlockStateRotation.rotateStair(
                                        originalBlock, finalRotation);
                                BlockState actual = ProjectionBlockStateRotation.rotate(
                                        currentBlock, originalBlock, finalRotation, axis, quarterTurn);
                                GameAssertions.assertEquals(expected, actual,
                                        "Stair transition must ignore its intermediate BlockState");
                                transitions++;
                            }
                        }
                    }
                }
            }
        }
        GameAssertions.assertEquals(5_760, transitions,
                "Expected every stair state, orientation, axis, and direction");
    }

    private void testShapeAndWaterlogging() {
        State originalMath = new State(Facing.NORTH, Half.TOP, Shape.OUTER_RIGHT);
        BlockState original = stairState(originalMath, true);
        DiscreteRotation x180 = DiscreteRotation.IDENTITY.thenWorldQuarterTurn(0, 2);
        BlockState result = ProjectionBlockStateRotation.rotateStair(original, x180);
        assertStairState(new State(Facing.SOUTH, Half.BOTTOM, Shape.OUTER_LEFT), result);
        GameAssertions.assertEquals("true", stateName(result, "waterlogged"),
                "Rotation must retain unrelated stair properties");
    }

    private void testClipboardPairRecovery() {
        State originalMath = new State(Facing.EAST, Half.BOTTOM, Shape.STRAIGHT);
        BlockState originalState = stairState(originalMath, false);
        BlockArrayClipboard current = oneBlockClipboard(originalState);
        BlockArrayClipboard original = oneBlockClipboard(originalState);

        DiscreteRotation x90 = DiscreteRotation.IDENTITY.thenWorldQuarterTurn(0, 1);
        ProjectionClipboardRotation.Result first = ProjectionClipboardRotation.rotate(
                current, original, x90, 0, 1);
        GameAssertions.assertEquals(originalState, first.current().getBlock(BlockVector3.ZERO),
                "The first illegal rotation must render the original stair");
        GameAssertions.assertEquals(originalState, first.original().getBlock(BlockVector3.ZERO),
                "The parallel clipboard must retain the original stair");

        DiscreteRotation x180 = x90.thenWorldQuarterTurn(0, 1);
        ProjectionClipboardRotation.Result second = ProjectionClipboardRotation.rotate(
                first.current(), first.original(), x180, 0, 1);
        assertStairState(new State(Facing.EAST, Half.TOP, Shape.STRAIGHT),
                second.current().getBlock(BlockVector3.ZERO));
        GameAssertions.assertEquals(originalState, second.original().getBlock(BlockVector3.ZERO),
                "The original-state clipboard must survive multiple rotations unchanged");
    }

    private void testAdjacentStairPairing() {
        State[] row = {
                new State(Facing.EAST, Half.BOTTOM, Shape.STRAIGHT),
                new State(Facing.WEST, Half.TOP, Shape.STRAIGHT),
                new State(Facing.EAST, Half.TOP, Shape.STRAIGHT),
                new State(Facing.WEST, Half.BOTTOM, Shape.STRAIGHT)
        };
        State[] expectedRow = {
                new State(Facing.WEST, Half.BOTTOM, Shape.STRAIGHT),
                new State(Facing.EAST, Half.TOP, Shape.STRAIGHT),
                new State(Facing.EAST, Half.BOTTOM, Shape.STRAIGHT),
                new State(Facing.WEST, Half.TOP, Shape.STRAIGHT)
        };
        BlockArrayClipboard current = new BlockArrayClipboard(
                new CuboidRegion(BlockVector3.ZERO, BlockVector3.at(row.length - 1, 0, 0)));
        BlockArrayClipboard original = new BlockArrayClipboard(
                new CuboidRegion(BlockVector3.ZERO, BlockVector3.at(row.length - 1, 0, 0)));
        current.setOrigin(BlockVector3.ZERO);
        original.setOrigin(BlockVector3.ZERO);
        for (int x = 0; x < row.length; x++) {
            BlockState state = stairState(row[x], false);
            setBlock(current, BlockVector3.at(x, 0, 0), state);
            setBlock(original, BlockVector3.at(x, 0, 0), state);
        }

        DiscreteRotation clockwisePositiveZ = DiscreteRotation.IDENTITY.thenWorldQuarterTurn(2, -1);
        ProjectionClipboardRotation.Result result = ProjectionClipboardRotation.rotate(
                current, original, clockwisePositiveZ, 2, -1);
        for (int sourceX = 0; sourceX < row.length; sourceX++) {
            BlockVector3 target = BlockVector3.at(0, row.length - 1 - sourceX, 0);
            BlockState expected = stairState(expectedRow[sourceX], false);
            GameAssertions.assertEquals(expected, result.current().getBlock(target),
                    "A clockwise +Z turn must apply the observed straight-stair state cycle");
            GameAssertions.assertEquals(stairState(row[sourceX], false), result.original().getBlock(target),
                    "The original-state clipboard must follow the same target cell");
        }
    }

    private void testAllSlabMappings() {
        Set<DiscreteRotation> rotations = enumerateOrientations();
        int mappings = 0;
        for (Type type : Type.values()) {
            BlockState original = slabState(type, false);
            for (DiscreteRotation rotation : rotations) {
                SlabStateTransform.Result expected = SlabStateTransform.transform(type, rotation);
                BlockState actual = ProjectionBlockStateRotation.rotateSlab(original, rotation);
                GameAssertions.assertEquals(expected.type().name().toLowerCase(Locale.ROOT),
                        stateName(actual, "type"), "Slab type mismatch");
                mappings++;
            }
        }
        GameAssertions.assertEquals(72, mappings, "Expected three slab types across 24 orientations");

        BlockState waterlogged = slabState(Type.BOTTOM, true);
        BlockState y90 = ProjectionBlockStateRotation.rotateSlab(
                waterlogged, DiscreteRotation.IDENTITY.thenWorldQuarterTurn(1, 1));
        GameAssertions.assertEquals("true", stateName(y90, "waterlogged"),
                "Slab rotation must retain unrelated properties");
    }

    private void testSlabOriginalStateRecovery() {
        BlockState originalState = slabState(Type.BOTTOM, false);
        BlockArrayClipboard current = oneBlockClipboard(originalState);
        BlockArrayClipboard original = oneBlockClipboard(originalState);

        DiscreteRotation x90 = DiscreteRotation.IDENTITY.thenWorldQuarterTurn(0, 1);
        ProjectionClipboardRotation.Result first = ProjectionClipboardRotation.rotate(
                current, original, x90, 0, 1);
        GameAssertions.assertEquals(originalState, first.current().getBlock(BlockVector3.ZERO),
                "A sideways slab must fall back to its original BlockState");

        DiscreteRotation x180 = x90.thenWorldQuarterTurn(0, 1);
        ProjectionClipboardRotation.Result second = ProjectionClipboardRotation.rotate(
                first.current(), first.original(), x180, 0, 1);
        GameAssertions.assertEquals("top", stateName(second.current().getBlock(BlockVector3.ZERO), "type"),
                "A later legal half turn must be computed from the original slab");
        GameAssertions.assertEquals(originalState, second.original().getBlock(BlockVector3.ZERO),
                "The original-state clipboard must retain the original slab");
    }

    private void testNativeSlabMappings() {
        int mappings = 0;
        for (Type type : Type.values()) {
            net.minecraft.world.level.block.state.BlockState original = Blocks.OAK_SLAB
                    .defaultBlockState()
                    .setValue(SlabBlock.TYPE,
                            net.minecraft.world.level.block.state.properties.SlabType.valueOf(type.name()));
            for (DiscreteRotation rotation : enumerateOrientations()) {
                SlabStateTransform.Result expected = SlabStateTransform.transform(type, rotation);
                net.minecraft.world.level.block.state.BlockState actual =
                        MinecraftBlockStateRotation.rotateSlab(original, rotation);
                GameAssertions.assertEquals(
                        net.minecraft.world.level.block.state.properties.SlabType.valueOf(
                                expected.type().name()),
                        actual.getValue(SlabBlock.TYPE),
                        "Native slab type mismatch");
                mappings++;
            }
        }
        GameAssertions.assertEquals(72, mappings,
                "Expected all slab states to rotate without the WorldEdit client registry");
    }

    private void testNativeNonStairFallback() {
        net.minecraft.world.level.block.state.BlockState furnace = Blocks.FURNACE
                .defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.EAST);
        DiscreteRotation y90 = DiscreteRotation.IDENTITY.thenWorldQuarterTurn(1, 1);
        net.minecraft.world.level.block.state.BlockState rotatedFurnace = MinecraftBlockStateRotation.rotate(
                furnace, furnace, y90, 1, 1);
        GameAssertions.assertEquals(
                Direction.NORTH,
                rotatedFurnace.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING),
                "Native fallback must rotate horizontal facing without WorldEdit");

        net.minecraft.world.level.block.state.BlockState log = Blocks.OAK_LOG
                .defaultBlockState()
                .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Y);
        DiscreteRotation x90 = DiscreteRotation.IDENTITY.thenWorldQuarterTurn(0, 1);
        net.minecraft.world.level.block.state.BlockState rotatedLog = MinecraftBlockStateRotation.rotate(
                log, log, x90, 0, 1);
        GameAssertions.assertEquals(
                Direction.Axis.Z,
                rotatedLog.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS),
                "Native fallback must rotate pillar axes without WorldEdit");

        net.minecraft.world.level.block.state.BlockState sign = Blocks.OAK_SIGN
                .defaultBlockState()
                .setValue(BlockStateProperties.ROTATION_16, 0);
        net.minecraft.world.level.block.state.BlockState rotatedSign = MinecraftBlockStateRotation.rotate(
                sign, sign, y90, 1, 1);
        GameAssertions.assertEquals(12, rotatedSign.getValue(BlockStateProperties.ROTATION_16),
                "Native fallback must rotate all 16 standing-sign segments");

        net.minecraft.world.level.block.state.BlockState jigsaw = Blocks.JIGSAW
                .defaultBlockState()
                .setValue(BlockStateProperties.ORIENTATION, FrontAndTop.NORTH_UP);
        net.minecraft.world.level.block.state.BlockState rotatedJigsaw = MinecraftBlockStateRotation.rotate(
                jigsaw, jigsaw, y90, 1, 1);
        GameAssertions.assertEquals(FrontAndTop.WEST_UP,
                rotatedJigsaw.getValue(BlockStateProperties.ORIENTATION),
                "Native fallback must rotate compound front-and-top orientations");
    }

    private void testFallbackParity() {
        assertNativeAndServerFallbackAgree(Blocks.FURNACE.defaultBlockState(), 1, 1);
        assertNativeAndServerFallbackAgree(Blocks.OAK_LOG.defaultBlockState(), 0, 1);
        assertNativeAndServerFallbackAgree(Blocks.OAK_SIGN.defaultBlockState(), 1, 1);
        assertNativeAndServerFallbackAgree(Blocks.JIGSAW.defaultBlockState(), 2, -1);
    }

    private static void assertNativeAndServerFallbackAgree(
            net.minecraft.world.level.block.state.BlockState source, int axis, int quarterTurn) {
        DiscreteRotation step = DiscreteRotation.IDENTITY.thenWorldQuarterTurn(axis, quarterTurn);
        net.minecraft.world.level.block.state.BlockState nativeResult = MinecraftBlockStateRotation.rotate(
                source, source, step, axis, quarterTurn);
        BlockState serverResult = ProjectionBlockStateRotation.rotate(
                ForgeAdapter.adapt(source), ForgeAdapter.adapt(source), step, axis, quarterTurn);
        GameAssertions.assertEquals(nativeResult, ForgeAdapter.adapt(serverResult),
                "Client preview and server clipboard state must use identical fallback rotation");
    }

    private void testNonStairFallback() {
        BlockState furnace = withProperty(BlockTypes.FURNACE.getDefaultState(), "facing", "east");
        DiscreteRotation y90 = DiscreteRotation.IDENTITY.thenWorldQuarterTurn(1, 1);
        BlockState rotatedFurnace = ProjectionBlockStateRotation.rotate(furnace, furnace, y90, 1, 1);
        GameAssertions.assertEquals("north", stateName(rotatedFurnace, "facing"),
                "Non-stair directional blocks must retain WorldEdit rotation semantics");

        BlockState stone = BlockTypes.STONE.getDefaultState();
        GameAssertions.assertEquals(stone,
                ProjectionBlockStateRotation.rotate(stone, stone, y90, 1, 1),
                "Non-directional blocks must remain unchanged");
    }

    private static Set<DiscreteRotation> enumerateOrientations() {
        Set<DiscreteRotation> found = new LinkedHashSet<>();
        ArrayDeque<DiscreteRotation> queue = new ArrayDeque<>();
        found.add(DiscreteRotation.IDENTITY);
        queue.add(DiscreteRotation.IDENTITY);
        while (!queue.isEmpty()) {
            DiscreteRotation current = queue.removeFirst();
            for (int axis = 0; axis < 3; axis++) {
                DiscreteRotation next = current.thenWorldQuarterTurn(axis, 1);
                if (found.add(next)) queue.addLast(next);
            }
        }
        return found;
    }

    private static BlockState stairState(State state, boolean waterlogged) {
        BlockState block = BlockTypes.OAK_STAIRS.getDefaultState();
        block = withProperty(block, "facing", state.facing().name().toLowerCase(Locale.ROOT));
        block = withProperty(block, "half", state.half().name().toLowerCase(Locale.ROOT));
        block = withProperty(block, "shape", state.shape().name().toLowerCase(Locale.ROOT));
        return withProperty(block, "waterlogged", Boolean.toString(waterlogged));
    }

    private static BlockState slabState(Type type, boolean waterlogged) {
        BlockState block = BlockTypes.OAK_SLAB.getDefaultState();
        block = withProperty(block, "type", type.name().toLowerCase(Locale.ROOT));
        return withProperty(block, "waterlogged", Boolean.toString(waterlogged));
    }

    private static BlockArrayClipboard oneBlockClipboard(BlockState state) {
        BlockArrayClipboard clipboard = new BlockArrayClipboard(
                new CuboidRegion(BlockVector3.ZERO, BlockVector3.ZERO));
        clipboard.setOrigin(BlockVector3.ZERO);
        setBlock(clipboard, BlockVector3.ZERO, state);
        return clipboard;
    }

    private static void assertStairState(State expected, BlockState actual) {
        GameAssertions.assertEquals(expected.facing().name().toLowerCase(Locale.ROOT),
                stateName(actual, "facing"), "Stair facing mismatch");
        GameAssertions.assertEquals(expected.half().name().toLowerCase(Locale.ROOT),
                stateName(actual, "half"), "Stair half mismatch");
        GameAssertions.assertEquals(expected.shape().name().toLowerCase(Locale.ROOT),
                stateName(actual, "shape"), "Stair shape mismatch");
    }

    private static BlockState withProperty(BlockState state, String name, String valueName) {
        Property<?> property = state.getBlockType().getProperty(name);
        if (property == null) throw new AssertionError("Missing property: " + name);
        Object value = property.getValueFor(valueName);
        return withState(state, property, value);
    }

    private static String stateName(BlockState state, String propertyName) {
        return state.getStates().entrySet().stream()
                .filter(entry -> entry.getKey().getName().equals(propertyName))
                .map(entry -> entry.getValue().toString().toLowerCase(Locale.ROOT))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing property: " + propertyName));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState withState(BlockState state, Property property, Object value) {
        return state.with(property, value);
    }
    private static void setBlock(BlockArrayClipboard clipboard, BlockVector3 position, BlockState state) {
        try { clipboard.setBlock(position, state); }
        catch (com.sk89q.worldedit.WorldEditException exception) { throw new AssertionError(exception); }
    }

}
