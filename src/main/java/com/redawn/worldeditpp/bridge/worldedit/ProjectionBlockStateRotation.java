package com.redawn.worldeditpp.bridge.worldedit;

import com.redawn.worldeditpp.math.DiscreteRotation;
import com.redawn.worldeditpp.bridge.minecraft.math.MinecraftBlockStateRotation;
import com.redawn.worldeditpp.math.StairSlabRotation;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;

/** WorldEdit-state projection rotation used by the server clipboard. */
public final class ProjectionBlockStateRotation {
    public static BlockState mirror(
            BlockState current, BlockState original, DiscreteRotation finalTransform, int axis
    ) {
        return WorldEditPlatform.adapt(MinecraftBlockStateRotation.mirror(
                WorldEditPlatform.adapt(current), WorldEditPlatform.adapt(original), finalTransform, axis));
    }

    public static BlockState rotate(
            BlockState current,
            BlockState original,
            DiscreteRotation finalRotation,
            int stepAxis,
            int stepQuarterTurn
    ) {
        if (isStairState(original)) {
            return rotateStair(original, finalRotation);
        }
        if (isSlabState(original)) {
            return rotateSlab(original, finalRotation);
        }
        return rotateIncrementally(current, stepAxis, stepQuarterTurn);
    }

    /**
     * Prefer the serialized state contract over the concrete Minecraft class. Compatible stair
     * implementations do not always extend vanilla's {@link StairBlock}; sending those through
     * WorldEdit's incremental fallback loses the original-state/final-matrix guarantee.
     */
    private static boolean isStairState(BlockState state) {
        return (hasValues(state, "facing", "north", "south", "east", "west")
                && hasValues(state, "half", "top", "bottom")
                && hasValues(state, "shape", "straight", "inner_left", "inner_right",
                        "outer_left", "outer_right"))
                || WorldEditPlatform.adapt(state.getBlockType()) instanceof StairBlock;
    }

    private static boolean isSlabState(BlockState state) {
        return hasValues(state, "type", "bottom", "top", "double")
                || WorldEditPlatform.adapt(state.getBlockType()) instanceof SlabBlock;
    }

    private static boolean hasValues(BlockState state, String propertyName, String... expectedNames) {
        Property<?> property = WorldEditBlockStates.property(state, propertyName);
        if (property == null) return false;
        return Arrays.stream(expectedNames).allMatch(expected -> property.getValues().stream()
                .map(value -> value.toString().toLowerCase(Locale.ROOT))
                .anyMatch(expected::equals));
    }

    public static BlockState rotateStair(BlockState original, DiscreteRotation finalRotation) {
        return StairSlabRotation.rotateStair(original, finalRotation, PROPERTIES);
    }

    public static BlockState rotateSlab(BlockState original, DiscreteRotation finalRotation) {
        return StairSlabRotation.rotateSlab(original, finalRotation, PROPERTIES);
    }

    /** WorldEdit-state adapter for the shared stair/slab transform. */
    private static final StairSlabRotation.Properties<BlockState, Property<?>> PROPERTIES =
            new StairSlabRotation.Properties<>() {
                @Override public Property<?> property(BlockState state, String name) {
                    return WorldEditBlockStates.property(state, name);
                }
                @Override public String valueName(BlockState state, Property<?> property) {
                    return WorldEditBlockStates.name(state, property);
                }
                @Override public BlockState withNamedValue(BlockState state, Property<?> property, String valueName) {
                    return WorldEditBlockStates.withNamed(state, property, valueName);
                }
            };

    private static BlockState rotateIncrementally(BlockState current, int axis, int quarterTurn) {
        try {
            net.minecraft.world.level.block.state.BlockState nativeCurrent = WorldEditPlatform.adapt(current);
            net.minecraft.world.level.block.state.BlockState nativeRotated = MinecraftBlockStateRotation.rotate(
                    nativeCurrent,
                    nativeCurrent,
                    DiscreteRotation.IDENTITY.thenWorldQuarterTurn(axis, quarterTurn),
                    axis,
                    quarterTurn);
            return WorldEditPlatform.adapt(nativeRotated);
        } catch (RuntimeException ignored) {
            return current;
        }
    }

    private ProjectionBlockStateRotation() { }
}
