package com.redawn.common.selection;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SelectionTransformsTest {
    @Test
    void contractionNeverInvertsThinAxes() {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    SelectionBox box = new SelectionBox(-10, -20, -30, -10 + x, -20 + y, -30 + z);
                    SelectionBox smaller = SelectionTransforms.resize(box, -1).orElse(box);
                    assertTrue(smaller.minX() <= smaller.maxX());
                    assertTrue(smaller.minY() <= smaller.maxY());
                    assertTrue(smaller.minZ() <= smaller.maxZ());
                    assertEquals(Math.max(0, x - 2), smaller.maxX() - smaller.minX());
                    assertEquals(Math.max(0, y - 2), smaller.maxY() - smaller.minY());
                    assertEquals(Math.max(0, z - 2), smaller.maxZ() - smaller.minZ());
                }
            }
        }
    }

    @Test
    void singleBlockCannotContractAndTwoBlocksKeepLowerCorner() {
        SelectionBox point = new SelectionBox(-2, 4, 7, -2, 4, 7);
        assertTrue(SelectionTransforms.resize(point, -1).isEmpty());
        assertEquals(point, SelectionTransforms.resize(new SelectionBox(-2, 4, 7, -1, 5, 8), -1).orElseThrow());
    }

    @Test
    void expansionAndContractionRoundTripAllAxes() {
        SelectionBox box = new SelectionBox(-4, 2, -8, -4, 7, 5);
        SelectionBox expanded = SelectionTransforms.resize(box, 1).orElseThrow();
        assertEquals(new SelectionBox(-5, 1, -9, -3, 8, 6), expanded);
        assertEquals(box, SelectionTransforms.resize(expanded, -1).orElseThrow());
        assertTrue(SelectionTransforms.resize(box, 0).isEmpty());
    }

    @Test
    void moveKeepsExtentsAndUsesTheDragOrigin() {
        SelectionBox origin = new SelectionBox(-7, 2, 8, -3, 4, 12);
        assertEquals(new SelectionBox(-4, 0, 13, 0, 2, 17), SelectionTransforms.move(origin, 3, -2, 5));
        assertEquals(origin, SelectionTransforms.move(SelectionTransforms.move(origin, 3, -2, 5), -3, 2, -5));
    }

    @Test
    void positiveXClockwiseRotationUsesTheFaceNormalAndKeepsIntegerCorners() {
        SelectionBox box = new SelectionBox(-1, 0, 10, 2, 2, 14);
        SelectionTransforms.RotatedSelection rotated = SelectionTransforms.rotate(
                box, -1, 0, 10, 2, 2, 14, SelectionFace.EAST, false);

        assertEquals(new SelectionBox(-1, -1, 11, 2, 3, 13), rotated.box());
        assertEquals(-1, rotated.pos1X());
        assertEquals(-1, rotated.pos1Y());
        assertEquals(13, rotated.pos1Z());
        assertEquals(2, rotated.pos2X());
        assertEquals(3, rotated.pos2Y());
        assertEquals(11, rotated.pos2Z());
        assertEquals(-1, rotated.quarterTurn());
    }

    @Test
    void clockwiseThenCounterClockwiseRestoresEveryFace() {
        SelectionBox original = new SelectionBox(-5, -2, 7, 0, 2, 9);
        for (SelectionFace face : SelectionFace.values()) {
            SelectionTransforms.RotatedSelection clockwise = SelectionTransforms.rotate(
                    original, -5, -2, 7, 0, 2, 9, face, false);
            SelectionTransforms.RotatedSelection restored = SelectionTransforms.rotate(
                    clockwise.box(),
                    clockwise.pos1X(), clockwise.pos1Y(), clockwise.pos1Z(),
                    clockwise.pos2X(), clockwise.pos2Y(), clockwise.pos2Z(),
                    face, true);
            assertEquals(original, restored.box(), face.name());
            assertEquals(-5, restored.pos1X(), face.name());
            assertEquals(-2, restored.pos1Y(), face.name());
            assertEquals(7, restored.pos1Z(), face.name());
            assertEquals(0, restored.pos2X(), face.name());
            assertEquals(2, restored.pos2Y(), face.name());
            assertEquals(9, restored.pos2Z(), face.name());
        }
    }

    @Test
    void fourClockwiseQuarterTurnsReturnExactlyToTheStart() {
        SelectionBox original = new SelectionBox(-4, 1, -8, 2, 3, -4);
        for (SelectionFace face : SelectionFace.values()) {
            SelectionBox box = original;
            int p1x = -4, p1y = 1, p1z = -8;
            int p2x = 2, p2y = 3, p2z = -4;
            for (int turn = 0; turn < 4; turn++) {
                SelectionTransforms.RotatedSelection rotated = SelectionTransforms.rotate(
                        box, p1x, p1y, p1z, p2x, p2y, p2z, face, false);
                box = rotated.box();
                p1x = rotated.pos1X();
                p1y = rotated.pos1Y();
                p1z = rotated.pos1Z();
                p2x = rotated.pos2X();
                p2y = rotated.pos2Y();
                p2z = rotated.pos2Z();
            }
            assertEquals(original, box, face.name());
            assertArrayEquals(new int[]{-4, 1, -8, 2, 3, -4},
                    new int[]{p1x, p1y, p1z, p2x, p2y, p2z}, face.name());
        }
    }

    @Test
    void everyFaceReversesDirectionWhenViewedFromBehind() {
        SelectionBox box = new SelectionBox(-2, -2, -2, 2, 2, 2);
        for (SelectionFace face : SelectionFace.values()) {
            SelectionVector eye = new SelectionVector(face.normalX() * 10, face.normalY() * 10, face.normalZ() * 10);
            SelectionVector front = new SelectionVector(-face.normalX(), -face.normalY(), -face.normalZ());
            SelectionVector back = new SelectionVector(face.normalX(), face.normalY(), face.normalZ());
            int sign = (int) (face.normalX() + face.normalY() + face.normalZ());
            assertEquals(box.withFace(face, face.plane(box) - sign),
                    SelectionTransforms.nudge(box, face, 1, eye, front).orElseThrow());
            assertEquals(box.withFace(face, face.plane(box) + sign),
                    SelectionTransforms.nudge(box, face, 1, eye, back).orElseThrow());
            assertEquals(SelectionTransforms.nudge(box, face, 1, eye, back),
                    SelectionTransforms.nudge(box, face, -1, eye, front));
        }
    }

    @Test
    void nudgingPastOppositeFaceIsRejected() {
        SelectionBox point = new SelectionBox(0, 0, 0, 0, 0, 0);
        for (SelectionFace face : SelectionFace.values()) {
            SelectionVector eye = new SelectionVector(face.normalX() * 10, face.normalY() * 10, face.normalZ() * 10);
            SelectionVector look = new SelectionVector(-face.normalX(), -face.normalY(), -face.normalZ());
            assertTrue(SelectionTransforms.nudge(point, face, 1, eye, look).isEmpty());
            assertTrue(SelectionTransforms.nudge(point, face, 0, eye, look).isEmpty());
        }
    }
}
