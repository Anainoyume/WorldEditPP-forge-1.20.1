package com.redawn.worldeditpp.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redawn.worldeditpp.math.DiscreteRotation.Vector;
import com.redawn.worldeditpp.math.StairStateTransform.Facing;
import com.redawn.worldeditpp.math.StairStateTransform.Half;
import com.redawn.worldeditpp.math.StairStateTransform.Result;
import com.redawn.worldeditpp.math.StairStateTransform.Shape;
import com.redawn.worldeditpp.math.StairStateTransform.State;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StairStateTransformTest {
    private static final List<DiscreteRotation> ORIENTATIONS = enumerateOrientations();

    @Test
    void clockwisePositiveZMatchesObservedStraightStairCycle() {
        DiscreteRotation clockwisePositiveZ = DiscreteRotation.IDENTITY
                .thenWorldQuarterTurn(2, -1);

        assertEquals(new State(Facing.WEST, Half.BOTTOM, Shape.STRAIGHT),
                StairStateTransform.transform(
                        new State(Facing.EAST, Half.BOTTOM, Shape.STRAIGHT),
                        clockwisePositiveZ).state());
        assertEquals(new State(Facing.EAST, Half.TOP, Shape.STRAIGHT),
                StairStateTransform.transform(
                        new State(Facing.WEST, Half.TOP, Shape.STRAIGHT),
                        clockwisePositiveZ).state());
        assertEquals(new State(Facing.WEST, Half.TOP, Shape.STRAIGHT),
                StairStateTransform.transform(
                        new State(Facing.WEST, Half.BOTTOM, Shape.STRAIGHT),
                        clockwisePositiveZ).state());
        assertEquals(new State(Facing.EAST, Half.BOTTOM, Shape.STRAIGHT),
                StairStateTransform.transform(
                        new State(Facing.EAST, Half.TOP, Shape.STRAIGHT),
                        clockwisePositiveZ).state());
    }

    @Test
    void quarterTurnsGenerateExactlyTwentyFourProperAxisAlignedRotations() {
        assertEquals(24, ORIENTATIONS.size());
        Set<Vector> axes = Set.of(
                new Vector(1, 0, 0), new Vector(-1, 0, 0),
                new Vector(0, 1, 0), new Vector(0, -1, 0),
                new Vector(0, 0, 1), new Vector(0, 0, -1));
        for (DiscreteRotation rotation : ORIENTATIONS) {
            Vector x = rotation.apply(1, 0, 0);
            Vector y = rotation.apply(0, 1, 0);
            Vector z = rotation.apply(0, 0, 1);
            assertTrue(axes.contains(x));
            assertTrue(axes.contains(y));
            assertTrue(axes.contains(z));
            assertEquals(3, Set.of(x, y, z).size());
            assertEquals(1, rotation.determinant());
        }
    }

    @Test
    void everyAxisTurnTransitionsBetweenTheTwentyFourAreaStates() {
        Set<DiscreteRotation> states = new HashSet<>(ORIENTATIONS);
        for (DiscreteRotation state : ORIENTATIONS) {
            for (int axis = 0; axis < 3; axis++) {
                for (int quarterTurn : new int[]{-1, 1}) {
                    assertTrue(states.contains(state.thenWorldQuarterTurn(axis, quarterTurn)),
                            "Every clockwise or counter-clockwise axis turn must remain in the 24-state group");
                }
            }
        }
    }

    @Test
    void everyStraightStairHasSixteenLegalOrientationsAndEightFallbacks() {
        for (Facing facing : Facing.values()) {
            for (Half half : Half.values()) {
                State original = new State(facing, half, Shape.STRAIGHT);
                int legal = 0;
                Set<State> legalStates = new HashSet<>();
                for (DiscreteRotation rotation : ORIENTATIONS) {
                    Result result = StairStateTransform.transform(original, rotation);
                    if (result.legal()) {
                        legal++;
                        legalStates.add(result.state());
                    } else {
                        assertEquals(original, result.state(),
                                "An illegal orientation must return the original state");
                    }
                }
                assertEquals(16, legal,
                        "A straight stair remains representable when its slab and step axes exchange roles");
                assertEquals(8, legalStates.size(), "Legal orientations must cover all eight basic stair states");
                assertEquals(8, ORIENTATIONS.size() - legal);
            }
        }
    }

    @Test
    void twoXQuarterTurnsUseTheOriginalStateAndProduceTheHalfTurnResult() {
        State original = new State(Facing.EAST, Half.BOTTOM, Shape.STRAIGHT);
        DiscreteRotation x90 = DiscreteRotation.IDENTITY.thenWorldQuarterTurn(0, 1);
        DiscreteRotation x180 = x90.thenWorldQuarterTurn(0, 1);

        Result illegalQuarterTurn = StairStateTransform.transform(original, x90);
        assertFalse(illegalQuarterTurn.legal());
        assertEquals(original, illegalQuarterTurn.state());

        Result halfTurn = StairStateTransform.transform(original, x180);
        assertTrue(halfTurn.legal());
        assertEquals(new State(Facing.EAST, Half.TOP, Shape.STRAIGHT), halfTurn.state());
    }

    @Test
    void differentSequencesWithTheSameFinalMatrixProduceIdenticalStates() {
        DiscreteRotation twoXTurns = DiscreteRotation.IDENTITY
                .thenWorldQuarterTurn(0, 1)
                .thenWorldQuarterTurn(0, 1);
        DiscreteRotation yThenZHalfTurns = DiscreteRotation.IDENTITY
                .thenWorldQuarterTurn(1, 2)
                .thenWorldQuarterTurn(2, 2);
        assertEquals(twoXTurns, yThenZHalfTurns);

        for (Facing facing : Facing.values()) {
            for (Half half : Half.values()) {
                for (Shape shape : Shape.values()) {
                    State original = new State(facing, half, shape);
                    assertEquals(
                            StairStateTransform.transform(original, twoXTurns),
                            StairStateTransform.transform(original, yThenZHalfTurns));
                }
            }
        }
    }

    @Test
    void rotationOrderIsPreservedAndFourTurnsRestoreIdentity() {
        DiscreteRotation xThenY = DiscreteRotation.IDENTITY
                .thenWorldQuarterTurn(0, 1)
                .thenWorldQuarterTurn(1, 1);
        DiscreteRotation yThenX = DiscreteRotation.IDENTITY
                .thenWorldQuarterTurn(1, 1)
                .thenWorldQuarterTurn(0, 1);
        assertNotEquals(xThenY, yThenX, "World-axis rotations must remain non-commutative");

        for (int axis = 0; axis < 3; axis++) {
            DiscreteRotation fullTurn = DiscreteRotation.IDENTITY;
            for (int index = 0; index < 4; index++) {
                fullTurn = fullTurn.thenWorldQuarterTurn(axis, 1);
            }
            assertEquals(DiscreteRotation.IDENTITY, fullTurn);
            for (Facing facing : Facing.values()) {
                for (Half half : Half.values()) {
                    State original = new State(facing, half, Shape.OUTER_LEFT);
                    assertEquals(original, StairStateTransform.transform(original, fullTurn).state());
                }
            }
        }
    }

    @Test
    void allFortyStatesUseTheExpectedShapeFamilyOrientationCounts() {
        for (Facing facing : Facing.values()) {
            for (Half half : Half.values()) {
                for (Shape shape : Shape.values()) {
                    State original = new State(facing, half, shape);
                    int legal = 0;
                    for (DiscreteRotation rotation : ORIENTATIONS) {
                        Result result = StairStateTransform.transform(original, rotation);
                        if (!result.legal()) {
                            assertEquals(original, result.state());
                            continue;
                        }
                        legal++;
                        assertEquals(shapeFamily(shape), shapeFamily(result.state().shape()),
                                "Rotation must not change straight, inner, or outer geometry");
                    }
                    int expected = switch (shape) {
                        case STRAIGHT -> 16;
                        case INNER_LEFT, INNER_RIGHT -> 24;
                        case OUTER_LEFT, OUTER_RIGHT -> 8;
                    };
                    assertEquals(expected, legal);
                }
            }
        }
    }

    @Test
    void everyLegalMappingMatchesTheRotatedVanillaOctantShape() {
        for (Facing facing : Facing.values()) {
            for (Half half : Half.values()) {
                for (Shape shape : Shape.values()) {
                    State original = new State(facing, half, shape);
                    int originalMask = vanillaOctantMask(original);
                    for (DiscreteRotation rotation : ORIENTATIONS) {
                        int rotatedMask = rotateOctantMask(originalMask, rotation);
                        Result result = StairStateTransform.transform(original, rotation);
                        if (result.legal()) {
                            assertEquals(rotatedMask, vanillaOctantMask(result.state()),
                                    "Mapped BlockState must occupy the rotated vanilla stair octants");
                        } else {
                            assertFalse(isRepresentableVanillaMask(rotatedMask),
                                    "Fallback is only valid when no stair BlockState has this geometry");
                        }
                    }
                }
            }
        }
    }

    private static List<DiscreteRotation> enumerateOrientations() {
        return enumerateOrientations(false);
    }

    @Test
    void rotationsAndMirrorsGenerateExactlyFortyEightOrientations() {
        List<DiscreteRotation> transforms = enumerateOrientations(true);
        assertEquals(48, transforms.size());
        assertEquals(24, transforms.stream().filter(t -> t.determinant() == -1).count());
        for (DiscreteRotation transform : transforms) {
            for (int axis = 0; axis < 3; axis++) {
                assertEquals(transform, transform.thenWorldMirror(axis).thenWorldMirror(axis));
            }
        }
        assertEquals(DiscreteRotation.IDENTITY.thenWorldQuarterTurn(1, 2),
                DiscreteRotation.IDENTITY.thenWorldMirror(0).thenWorldMirror(2));
        assertNotEquals(DiscreteRotation.IDENTITY.thenWorldMirror(0).thenWorldQuarterTurn(1, 1),
                DiscreteRotation.IDENTITY.thenWorldQuarterTurn(1, 1).thenWorldMirror(0));
    }

    @Test
    void allFortyStairsMatchGeometryAcrossFortyEightMirroredAndRotatedOrientations() {
        for (Facing facing : Facing.values()) {
            for (Half half : Half.values()) {
                for (Shape shape : Shape.values()) {
                    State original = new State(facing, half, shape);
                    for (DiscreteRotation transform : enumerateOrientations(true)) {
                        int expected = rotateOctantMask(vanillaOctantMask(original), transform);
                        Result result = StairStateTransform.transform(original, transform);
                        if (result.legal()) {
                            assertEquals(expected, vanillaOctantMask(result.state()),
                                    () -> original + " under " + transform);
                        } else {
                            assertEquals(original, result.state());
                            assertFalse(isRepresentableVanillaMask(expected));
                        }
                    }
                }
            }
        }
    }

    private static List<DiscreteRotation> enumerateOrientations(boolean mirrors) {
        Set<DiscreteRotation> found = new LinkedHashSet<>();
        ArrayDeque<DiscreteRotation> queue = new ArrayDeque<>();
        found.add(DiscreteRotation.IDENTITY);
        queue.add(DiscreteRotation.IDENTITY);
        while (!queue.isEmpty()) {
            DiscreteRotation current = queue.removeFirst();
            for (int axis = 0; axis < 3; axis++) {
                DiscreteRotation next = current.thenWorldQuarterTurn(axis, 1);
                if (found.add(next)) queue.addLast(next);
                if (mirrors) {
                    DiscreteRotation reflected = current.thenWorldMirror(axis);
                    if (found.add(reflected)) queue.addLast(reflected);
                }
            }
        }
        return new ArrayList<>(found);
    }

    private static String shapeFamily(Shape shape) {
        return switch (shape) {
            case STRAIGHT -> "straight";
            case INNER_LEFT, INNER_RIGHT -> "inner";
            case OUTER_LEFT, OUTER_RIGHT -> "outer";
        };
    }

    private static int vanillaOctantMask(State state) {
        int solidY = state.half() == Half.BOTTOM ? -1 : 1;
        int mask = 0;
        for (int x : new int[]{-1, 1}) {
            for (int z : new int[]{-1, 1}) mask |= octantBit(x, solidY, z);
        }
        int quarters = quarterMask(state.facing(), state.shape());
        int stepY = -solidY;
        if ((quarters & 1) != 0) mask |= octantBit(-1, stepY, -1);
        if ((quarters & 2) != 0) mask |= octantBit(1, stepY, -1);
        if ((quarters & 4) != 0) mask |= octantBit(-1, stepY, 1);
        if ((quarters & 8) != 0) mask |= octantBit(1, stepY, 1);
        return mask;
    }

    /** Mirrors StairBlock.SHAPE_BY_STATE for NW, NE, SW, and SE step quarters. */
    private static int quarterMask(Facing facing, Shape shape) {
        return switch (shape) {
            case STRAIGHT -> switch (facing) {
                case SOUTH -> 12; case WEST -> 5; case NORTH -> 3; case EAST -> 10;
            };
            case INNER_LEFT -> switch (facing) {
                case SOUTH -> 14; case WEST -> 13; case NORTH -> 7; case EAST -> 11;
            };
            case INNER_RIGHT -> switch (facing) {
                case SOUTH -> 13; case WEST -> 7; case NORTH -> 11; case EAST -> 14;
            };
            case OUTER_LEFT -> switch (facing) {
                case SOUTH -> 8; case WEST -> 4; case NORTH -> 1; case EAST -> 2;
            };
            case OUTER_RIGHT -> switch (facing) {
                case SOUTH -> 4; case WEST -> 1; case NORTH -> 2; case EAST -> 8;
            };
        };
    }

    private static int rotateOctantMask(int mask, DiscreteRotation rotation) {
        int rotated = 0;
        for (int x : new int[]{-1, 1}) {
            for (int y : new int[]{-1, 1}) {
                for (int z : new int[]{-1, 1}) {
                    if ((mask & octantBit(x, y, z)) == 0) continue;
                    Vector target = rotation.apply(x, y, z);
                    rotated |= octantBit(target.x(), target.y(), target.z());
                }
            }
        }
        return rotated;
    }

    private static boolean isRepresentableVanillaMask(int mask) {
        for (Facing facing : Facing.values()) {
            for (Half half : Half.values()) {
                for (Shape shape : Shape.values()) {
                    if (vanillaOctantMask(new State(facing, half, shape)) == mask) return true;
                }
            }
        }
        return false;
    }

    private static int octantBit(int x, int y, int z) {
        int index = (x > 0 ? 1 : 0) | (y > 0 ? 2 : 0) | (z > 0 ? 4 : 0);
        return 1 << index;
    }
}
