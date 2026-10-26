package com.redawn.worldeditpp.math;

import com.redawn.worldeditpp.math.DiscreteRotation.Vector;
import java.util.Locale;

/** Exact, history-independent mapping of the directional properties of a stair. */
public final class StairStateTransform {
    private static final Vector WORLD_UP = new Vector(0, 1, 0);

    public static Result transform(State original, DiscreteRotation rotation) {
        Vector solid = rotation.apply(0, original.half() == Half.BOTTOM ? -1 : 1, 0);
        Vector facing = rotation.apply(original.facing().vector());
        return switch (original.shape()) {
            case STRAIGHT -> transformStraight(original, solid, facing);
            case INNER_LEFT, INNER_RIGHT -> transformCorner(original, solid, facing,
                    rotation.apply(sideVector(original)), true);
            case OUTER_LEFT, OUTER_RIGHT -> isVertical(solid)
                    ? canonicalCorner(original, solid, facing, rotation.apply(sideVector(original)), false)
                    : new Result(original, false);
        };
    }

    /**
     * A straight stair is the union of two perpendicular half-spaces. Rotating around X or Z can
     * exchange which half-space is the full slab and which one is the step direction.
     */
    private static Result transformStraight(State original, Vector solid, Vector facing) {
        if (isVertical(solid)) {
            return canonicalStraight(original, solid, facing);
        }
        if (isVertical(facing)) {
            return canonicalStraight(original, facing, solid);
        }
        return new Result(original, false);
    }

    /**
     * An inner stair occupies the union of three perpendicular half-spaces, so its three local
     * axes may cycle when a different one becomes vertical. This preserves the oriented corner
     * instead of incrementally mutating the last visible BlockState.
     */
    private static Result transformCorner(
            State original, Vector solid, Vector facing, Vector side, boolean inner
    ) {
        if (isVertical(solid)) {
            return canonicalCorner(original, solid, facing, side, inner);
        }
        if (isVertical(facing)) {
            return canonicalCorner(original, facing, side, solid, inner);
        }
        if (isVertical(side)) {
            return canonicalCorner(original, side, solid, facing, inner);
        }
        return new Result(original, false);
    }

    private static Result canonicalStraight(State original, Vector solid, Vector facingVector) {
        Facing facing = Facing.fromVector(facingVector);
        if (facing == null) return new Result(original, false);
        return new Result(new State(facing, halfFromSolid(solid), Shape.STRAIGHT), true);
    }

    private static Result canonicalCorner(
            State original,
            Vector solid,
            Vector facingVector,
            Vector side,
            boolean inner
    ) {
        Facing facing = Facing.fromVector(facingVector);
        if (facing == null) return new Result(original, false);
        Vector left = WORLD_UP.cross(facingVector);
        Shape shape;
        if (side.equals(left)) {
            shape = inner ? Shape.INNER_LEFT : Shape.OUTER_LEFT;
        } else if (side.equals(left.negate())) {
            shape = inner ? Shape.INNER_RIGHT : Shape.OUTER_RIGHT;
        } else {
            return new Result(original, false);
        }
        return new Result(new State(facing, halfFromSolid(solid), shape), true);
    }

    private static Vector sideVector(State state) {
        Vector left = WORLD_UP.cross(state.facing().vector());
        return state.shape().isLeft() ? left : left.negate();
    }

    private static boolean isVertical(Vector vector) {
        return vector.x() == 0 && Math.abs(vector.y()) == 1 && vector.z() == 0;
    }

    private static Half halfFromSolid(Vector solid) {
        return solid.y() < 0 ? Half.BOTTOM : Half.TOP;
    }

    public enum Facing {
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        EAST(1, 0, 0),
        WEST(-1, 0, 0);

        private final Vector vector;

        Facing(int x, int y, int z) {
            vector = new Vector(x, y, z);
        }

        public Vector vector() {
            return vector;
        }

        public static Facing fromName(String name) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        private static Facing fromVector(Vector vector) {
            for (Facing facing : values()) {
                if (facing.vector.equals(vector)) return facing;
            }
            return null;
        }
    }

    public enum Half {
        TOP,
        BOTTOM;

        public static Half fromName(String name) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }

    public enum Shape {
        STRAIGHT,
        INNER_LEFT,
        INNER_RIGHT,
        OUTER_LEFT,
        OUTER_RIGHT;

        public static Shape fromName(String name) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        boolean isLeft() {
            return this == INNER_LEFT || this == OUTER_LEFT;
        }
    }

    public record State(Facing facing, Half half, Shape shape) {
        public State {
            if (facing == null || half == null || shape == null) {
                throw new IllegalArgumentException("Stair state properties must be present");
            }
        }
    }

    public record Result(State state, boolean legal) { }

    private StairStateTransform() { }
}
