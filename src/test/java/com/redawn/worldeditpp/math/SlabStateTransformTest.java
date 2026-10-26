package com.redawn.worldeditpp.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redawn.worldeditpp.math.SlabStateTransform.Result;
import com.redawn.worldeditpp.math.SlabStateTransform.Type;
import com.redawn.worldeditpp.math.StairStateTransform.Facing;
import com.redawn.worldeditpp.math.StairStateTransform.Half;
import com.redawn.worldeditpp.math.StairStateTransform.Shape;
import com.redawn.worldeditpp.math.StairStateTransform.State;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SlabStateTransformTest {
    private static final List<DiscreteRotation> ORIENTATIONS = enumerateOrientations();

    @Test
    void topAndBottomEachHaveEightLegalAndSixteenIllegalOrientations() {
        assertEquals(24, ORIENTATIONS.size());
        for (Type type : List.of(Type.BOTTOM, Type.TOP)) {
            int legal = 0;
            int illegal = 0;
            for (DiscreteRotation rotation : ORIENTATIONS) {
                Result result = SlabStateTransform.transform(type, rotation);
                if (result.legal()) {
                    legal++;
                } else {
                    illegal++;
                    assertEquals(type, result.type(), "A sideways slab must return its original type");
                }
            }
            assertEquals(8, legal);
            assertEquals(16, illegal);
        }
    }

    @Test
    void xAndZHalfTurnsSwapTypesWhileQuarterTurnsFallBack() {
        for (int axis : List.of(0, 2)) {
            DiscreteRotation quarterTurn = DiscreteRotation.IDENTITY.thenWorldQuarterTurn(axis, 1);
            DiscreteRotation halfTurn = quarterTurn.thenWorldQuarterTurn(axis, 1);
            for (Type type : List.of(Type.BOTTOM, Type.TOP)) {
                Result quarterResult = SlabStateTransform.transform(type, quarterTurn);
                assertFalse(quarterResult.legal());
                assertEquals(type, quarterResult.type());

                Result halfResult = SlabStateTransform.transform(type, halfTurn);
                assertTrue(halfResult.legal());
                assertEquals(type == Type.BOTTOM ? Type.TOP : Type.BOTTOM, halfResult.type());
            }
        }
    }

    @Test
    void doubleSlabsRemainDoubleInEveryOrientation() {
        for (DiscreteRotation rotation : ORIENTATIONS) {
            Result result = SlabStateTransform.transform(Type.DOUBLE, rotation);
            assertTrue(result.legal());
            assertEquals(Type.DOUBLE, result.type());
        }
    }

    @Test
    void equivalentMultiAxisSequencesProduceTheSameResult() {
        DiscreteRotation twoXTurns = DiscreteRotation.IDENTITY
                .thenWorldQuarterTurn(0, 1)
                .thenWorldQuarterTurn(0, 1);
        DiscreteRotation yThenZHalfTurns = DiscreteRotation.IDENTITY
                .thenWorldQuarterTurn(1, 2)
                .thenWorldQuarterTurn(2, 2);
        assertEquals(twoXTurns, yThenZHalfTurns);
        for (Type type : Type.values()) {
            assertEquals(
                    SlabStateTransform.transform(type, twoXTurns),
                    SlabStateTransform.transform(type, yThenZHalfTurns));
        }
    }

    @Test
    void stairAndSlabVerticalHalvesAgreeWheneverTheSlabIsRepresentable() {
        for (Half half : Half.values()) {
            Type slabType = half == Half.BOTTOM ? Type.BOTTOM : Type.TOP;
            State stair = new State(Facing.NORTH, half, Shape.STRAIGHT);
            for (DiscreteRotation rotation : ORIENTATIONS) {
                StairStateTransform.Result stairResult = StairStateTransform.transform(stair, rotation);
                Result slabResult = SlabStateTransform.transform(slabType, rotation);
                if (slabResult.legal()) {
                    assertTrue(stairResult.legal());
                    Type expected = stairResult.state().half() == Half.BOTTOM ? Type.BOTTOM : Type.TOP;
                    assertEquals(expected, slabResult.type());
                }
            }
        }
    }

    private static List<DiscreteRotation> enumerateOrientations() {
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
        return new ArrayList<>(found);
    }
}
