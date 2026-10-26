package com.redawn.worldeditpp.math;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProjectionGridTransformsTest {
    @Test
    void positiveExpansionAppendsOneWholeTile() {
        int[] source = new int[18];
        for (int target = 0; target < source.length; target++) {
            source[target] = ProjectionGridTransforms.stretchSourceCoordinate(
                    target, 9, 9, true, true);
        }
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 0, 1, 2, 3, 4, 5, 6, 7, 8}, source);
    }

    @Test
    void negativeExpansionPrependsAndShiftsTheExistingTiles() {
        assertEquals(0, ProjectionGridTransforms.stretchSourceCoordinate(0, 6, 3, false, true));
        assertEquals(2, ProjectionGridTransforms.stretchSourceCoordinate(2, 6, 3, false, true));
        assertEquals(0, ProjectionGridTransforms.stretchSourceCoordinate(3, 6, 3, false, true));
        assertEquals(5, ProjectionGridTransforms.stretchSourceCoordinate(8, 6, 3, false, true));
    }

    @Test
    void negativeFaceContractionRemovesExactlyOneTile() {
        assertEquals(3, ProjectionGridTransforms.stretchSourceCoordinate(0, 9, 3, false, false));
        assertEquals(8, ProjectionGridTransforms.stretchSourceCoordinate(5, 9, 3, false, false));
    }

    @Test
    void quarterTurnUsesTheSelectionRotationGridConvention() {
        assertArrayEquals(new int[]{2, 0, 0},
                ProjectionGridTransforms.rotateCoordinates(new int[]{0, 0, 0}, 1, -1, 2, 1, 3));
        assertArrayEquals(new int[]{0, 0, 1},
                ProjectionGridTransforms.rotateCoordinates(new int[]{1, 0, 2}, 1, -1, 2, 1, 3));
    }

    @Test
    void discreteRotationUsesTheSameWorldAxisSignsAsGridCoordinates() {
        int[] origin = {1, 1, 1};
        int[][] basis = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        for (int axis = 0; axis < 3; axis++) {
            for (int quarterTurn : new int[]{-1, 1}) {
                int[] targetOrigin = ProjectionGridTransforms.rotateCoordinates(
                        origin, axis, quarterTurn, 4, 5, 6);
                DiscreteRotation rotation = DiscreteRotation.IDENTITY
                        .thenWorldQuarterTurn(axis, quarterTurn);
                for (int[] vector : basis) {
                    int[] endpoint = {
                            origin[0] + vector[0],
                            origin[1] + vector[1],
                            origin[2] + vector[2]
                    };
                    int[] targetEndpoint = ProjectionGridTransforms.rotateCoordinates(
                            endpoint, axis, quarterTurn, 4, 5, 6);
                    DiscreteRotation.Vector expected = rotation.apply(vector[0], vector[1], vector[2]);
                    assertArrayEquals(new int[]{expected.x(), expected.y(), expected.z()}, new int[]{
                            targetEndpoint[0] - targetOrigin[0],
                            targetEndpoint[1] - targetOrigin[1],
                            targetEndpoint[2] - targetOrigin[2]
                    });
                }
            }
        }
    }

    @Test
    void accumulatedMatrixMatchesSequentialWorldAxisGridRotations() {
        int[] first = {0, 0, 0};
        int[] second = {1, 2, 3};
        int[] firstAfterX = ProjectionGridTransforms.rotateCoordinates(first, 0, 1, 2, 3, 4);
        int[] secondAfterX = ProjectionGridTransforms.rotateCoordinates(second, 0, 1, 2, 3, 4);
        int[] firstAfterZ = ProjectionGridTransforms.rotateCoordinates(firstAfterX, 2, -1, 2, 4, 3);
        int[] secondAfterZ = ProjectionGridTransforms.rotateCoordinates(secondAfterX, 2, -1, 2, 4, 3);

        DiscreteRotation accumulated = DiscreteRotation.IDENTITY
                .thenWorldQuarterTurn(0, 1)
                .thenWorldQuarterTurn(2, -1);
        DiscreteRotation.Vector expected = accumulated.apply(1, 2, 3);
        assertArrayEquals(new int[]{expected.x(), expected.y(), expected.z()}, new int[]{
                secondAfterZ[0] - firstAfterZ[0],
                secondAfterZ[1] - firstAfterZ[1],
                secondAfterZ[2] - firstAfterZ[2]
        });
    }
}
