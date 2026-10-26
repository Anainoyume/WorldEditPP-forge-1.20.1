package com.redawn.worldeditpp.bridge.minecraft.math;

import com.redawn.worldeditpp.math.DiscreteRotation;
import com.redawn.worldeditpp.math.StairSlabRotation;

import com.redawn.worldeditpp.math.DiscreteRotation.Vector;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Applies projection rotations directly to Minecraft states without a WorldEdit registry lookup. */
public final class MinecraftBlockStateRotation {
    private static final String[] DIRECTION_PROPERTY_NAMES = {
            "down", "up", "north", "south", "west", "east"
    };

    public static BlockState rotate(
            BlockState current,
            BlockState original,
            DiscreteRotation finalRotation,
            int stepAxis,
            int stepQuarterTurn
    ) {
        if (isStairState(original)) return rotateStair(original, finalRotation);
        if (isSlabState(original)) return rotateSlab(original, finalRotation);
        return rotateIncrementally(current, stepAxis, stepQuarterTurn);
    }

    public static BlockState rotateStair(BlockState original, DiscreteRotation finalRotation) {
        return StairSlabRotation.rotateStair(original, finalRotation, PROPERTIES);
    }

    public static BlockState mirror(
            BlockState current, BlockState original, DiscreteRotation finalTransform, int axis
    ) {
        if (isStairState(original)) return rotateStair(original, finalTransform);
        if (isSlabState(original)) return rotateSlab(original, finalTransform);
        DiscreteRotation step = DiscreteRotation.IDENTITY.thenWorldMirror(axis);
        // Vanilla knows block-specific handedness (door hinges, chest halves, rail curves).
        Mirror vanillaMirror = axis == 0 ? Mirror.FRONT_BACK : axis == 2 ? Mirror.LEFT_RIGHT : Mirror.NONE;
        BlockState transformed = current.mirror(vanillaMirror);
        // Read directions from the source so vanilla's facing changes are not applied twice.
        for (Property<?> property : current.getProperties()) {
            Comparable<?> value = value(current, property);
            if (value instanceof Direction direction) {
                transformed = withValue(transformed, property, rotate(direction, step));
            } else if (value instanceof FrontAndTop orientation) {
                transformed = withValue(transformed, property, rotate(orientation, step));
            } else if (axis != 1 && "rotation".equals(property.getName()) && value instanceof Integer segment) {
                transformed = withValue(transformed, property, vanillaMirror.mirror(segment, 16));
            } else if (axis == 1) {
                String replacement = switch (property.getName()) {
                    case "half", "type" -> switch (stateName(current, property)) {
                        case "top" -> "bottom";
                        case "bottom" -> "top";
                        case "upper" -> "lower";
                        case "lower" -> "upper";
                        default -> stateName(current, property);
                    };
                    case "face", "attachment" -> switch (stateName(current, property)) {
                        case "floor" -> "ceiling";
                        case "ceiling" -> "floor";
                        default -> stateName(current, property);
                    };
                    default -> stateName(current, property);
                };
                transformed = withNamedValue(transformed, property, replacement);
            }
        }
        // Undirected axis properties do not change under an axis-aligned reflection.
        return rotateConnectionProperties(current, transformed, step);
    }

    public static BlockState rotateSlab(BlockState original, DiscreteRotation finalRotation) {
        return StairSlabRotation.rotateSlab(original, finalRotation, PROPERTIES);
    }

    /** Native Minecraft adapter for the shared stair/slab transform. */
    private static final StairSlabRotation.Properties<BlockState, Property<?>> PROPERTIES =
            new StairSlabRotation.Properties<>() {
                @Override public Property<?> property(BlockState state, String name) {
                    return MinecraftBlockStateRotation.property(state, name);
                }
                @Override public String valueName(BlockState state, Property<?> property) {
                    return stateName(state, property);
                }
                @Override public BlockState withNamedValue(BlockState state, Property<?> property, String valueName) {
                    return MinecraftBlockStateRotation.withNamedValue(state, property, valueName);
                }
            };

    private static BlockState rotateIncrementally(BlockState current, int axis, int quarterTurn) {
        DiscreteRotation step = DiscreteRotation.IDENTITY.thenWorldQuarterTurn(axis, quarterTurn);
        BlockState transformed = rotateDirectionalProperties(current, step);
        return rotateConnectionProperties(current, transformed, step);
    }

    private static BlockState rotateDirectionalProperties(BlockState source, DiscreteRotation step) {
        BlockState transformed = source;
        for (Property<?> property : source.getProperties()) {
            Comparable<?> value = value(source, property);
            if (value instanceof Direction direction) {
                transformed = withValue(transformed, property, rotate(direction, step));
            } else if (value instanceof Direction.Axis axis) {
                transformed = withValue(transformed, property, rotate(axis, step));
            } else if (value instanceof FrontAndTop orientation) {
                transformed = withValue(transformed, property, rotate(orientation, step));
            } else if ("rotation".equals(property.getName()) && value instanceof Integer rotation) {
                transformed = rotateHorizontalSegment(transformed, property, rotation, step);
            }
        }
        return transformed;
    }

    private static BlockState rotateHorizontalSegment(
            BlockState state, Property<?> property, int rotation, DiscreteRotation step) {
        Vector south = step.apply(0, 0, 1);
        if (south.y() != 0) return state;
        int quarterTurns = switch (south.x() + "," + south.z()) {
            case "0,1" -> 0;
            case "-1,0" -> 1;
            case "0,-1" -> 2;
            case "1,0" -> 3;
            default -> 0;
        };
        return withValue(state, property, Math.floorMod(rotation + quarterTurns * 4, 16));
    }

    private static BlockState rotateConnectionProperties(
            BlockState source, BlockState transformed, DiscreteRotation step) {
        for (String sourceName : DIRECTION_PROPERTY_NAMES) {
            Property<?> sourceProperty = property(source, sourceName);
            if (sourceProperty == null) continue;
            Direction targetDirection = rotate(Direction.byName(sourceName), step);
            if (targetDirection == null || property(source, targetDirection.getName()) == null) {
                return transformed;
            }
        }

        BlockState result = transformed;
        for (String sourceName : DIRECTION_PROPERTY_NAMES) {
            Property<?> sourceProperty = property(source, sourceName);
            if (sourceProperty == null) continue;
            Direction targetDirection = rotate(Direction.byName(sourceName), step);
            Property<?> targetProperty = property(source, targetDirection.getName());
            result = withNamedValue(result, targetProperty, stateName(source, sourceProperty));
        }
        return result;
    }

    private static Direction rotate(Direction direction, DiscreteRotation rotation) {
        if (direction == null) return null;
        Vector result = rotation.apply(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        for (Direction candidate : Direction.values()) {
            if (candidate.getStepX() == result.x()
                    && candidate.getStepY() == result.y()
                    && candidate.getStepZ() == result.z()) {
                return candidate;
            }
        }
        return direction;
    }

    private static Direction.Axis rotate(Direction.Axis axis, DiscreteRotation rotation) {
        Vector result = switch (axis) {
            case X -> rotation.apply(1, 0, 0);
            case Y -> rotation.apply(0, 1, 0);
            case Z -> rotation.apply(0, 0, 1);
        };
        if (result.x() != 0) return Direction.Axis.X;
        if (result.y() != 0) return Direction.Axis.Y;
        return Direction.Axis.Z;
    }

    private static FrontAndTop rotate(FrontAndTop orientation, DiscreteRotation rotation) {
        return FrontAndTop.fromFrontAndTop(
                rotate(orientation.front(), rotation),
                rotate(orientation.top(), rotation));
    }

    private static boolean isStairState(BlockState state) {
        return hasValues(state, "facing", "north", "south", "east", "west")
                && hasValues(state, "half", "top", "bottom")
                && hasValues(state, "shape", "straight", "inner_left", "inner_right",
                        "outer_left", "outer_right");
    }

    private static boolean isSlabState(BlockState state) {
        return hasValues(state, "type", "bottom", "top", "double");
    }

    private static boolean hasValues(BlockState state, String propertyName, String... expectedNames) {
        Property<?> property = property(state, propertyName);
        if (property == null) return false;
        return Arrays.stream(expectedNames).allMatch(expected -> property.getPossibleValues().stream()
                .map(value -> value.toString().toLowerCase(Locale.ROOT))
                .anyMatch(expected::equals));
    }

    private static Property<?> property(BlockState state, String name) {
        return state.getBlock().getStateDefinition().getProperty(name);
    }

    private static String stateName(BlockState state, Property<?> property) {
        Comparable<?> value = value(state, property);
        return value == null ? "" : value.toString().toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Comparable<?> value(BlockState state, Property property) {
        return state.getValue(property);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState withNamedValue(BlockState state, Property property, String valueName) {
        if (property == null) return state;
        return (BlockState) property.getValue(valueName)
                .map(value -> state.setValue(property, (Comparable) value))
                .orElse(state);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState withValue(BlockState state, Property property, Comparable value) {
        return property.getPossibleValues().contains(value) ? state.setValue(property, value) : state;
    }

    private MinecraftBlockStateRotation() { }
}
