package com.redawn.common.selection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionOrientationTest {
    private final SelectionBox box = new SelectionBox(0.0D, 0.0D, 0.0D, 10.0D, 10.0D, 10.0D);

    @Test
    void lookingEastAtWestFace_isFront() {
        SelectionVector playerEye = new SelectionVector(-5.0D, 5.0D, 5.0D);
        SelectionVector lookDir = new SelectionVector(1.0D, 0.0D, 0.0D);
        assertTrue(SelectionOrientation.isFrontFace(SelectionFace.WEST, box, playerEye, lookDir));
    }

    @Test
    void lookingWestAtWestFace_isBack() {
        SelectionVector playerEye = new SelectionVector(-5.0D, 5.0D, 5.0D);
        SelectionVector lookDir = new SelectionVector(-1.0D, 0.0D, 0.0D);
        assertFalse(SelectionOrientation.isFrontFace(SelectionFace.WEST, box, playerEye, lookDir));
        assertTrue(SelectionOrientation.isFaceBack(SelectionFace.WEST, box, playerEye, lookDir));
    }

    @Test
    void lookingWestAtEastFace_isFront() {
        SelectionVector playerEye = new SelectionVector(15.0D, 5.0D, 5.0D);
        SelectionVector lookDir = new SelectionVector(-1.0D, 0.0D, 0.0D);
        assertTrue(SelectionOrientation.isFrontFace(SelectionFace.EAST, box, playerEye, lookDir));
    }

    @Test
    void lookingEastAtEastFace_isBack() {
        SelectionVector playerEye = new SelectionVector(15.0D, 5.0D, 5.0D);
        SelectionVector lookDir = new SelectionVector(1.0D, 0.0D, 0.0D);
        assertTrue(SelectionOrientation.isFaceBack(SelectionFace.EAST, box, playerEye, lookDir));
    }

    @Test
    void lookingUpAtDownFace_isFront() {
        SelectionVector playerEye = new SelectionVector(5.0D, -5.0D, 5.0D);
        SelectionVector lookDir = new SelectionVector(0.0D, 1.0D, 0.0D);
        assertTrue(SelectionOrientation.isFrontFace(SelectionFace.DOWN, box, playerEye, lookDir));
    }

    @Test
    void lookingDownAtUpFace_isFront() {
        SelectionVector playerEye = new SelectionVector(5.0D, 15.0D, 5.0D);
        SelectionVector lookDir = new SelectionVector(0.0D, -1.0D, 0.0D);
        assertTrue(SelectionOrientation.isFrontFace(SelectionFace.UP, box, playerEye, lookDir));
    }

    @Test
    void lookingSouthAtNorthFace_isFront() {
        SelectionVector playerEye = new SelectionVector(5.0D, 5.0D, -5.0D);
        SelectionVector lookDir = new SelectionVector(0.0D, 0.0D, 1.0D);
        assertTrue(SelectionOrientation.isFrontFace(SelectionFace.NORTH, box, playerEye, lookDir));
    }

    @Test
    void lookingNorthAtSouthFace_isFront() {
        SelectionVector playerEye = new SelectionVector(5.0D, 5.0D, 15.0D);
        SelectionVector lookDir = new SelectionVector(0.0D, 0.0D, -1.0D);
        assertTrue(SelectionOrientation.isFrontFace(SelectionFace.SOUTH, box, playerEye, lookDir));
    }

    @Test
    void perpendicularView_isFront() {
        SelectionVector playerEye = new SelectionVector(-5.0D, 5.0D, 5.0D);
        SelectionVector lookDir = new SelectionVector(0.0D, 0.0D, 1.0D);
        assertTrue(SelectionOrientation.isFrontFace(SelectionFace.WEST, box, playerEye, lookDir));
    }

    @Test
    void insideBox_allFacesAreBack() {
        SelectionVector playerEye = new SelectionVector(5.0D, 5.0D, 5.0D);
        SelectionVector lookDir = new SelectionVector(1.0D, 0.0D, 0.0D);
        assertTrue(SelectionOrientation.isInside(box, playerEye));
        for (SelectionFace face : SelectionFace.values()) {
            assertTrue(SelectionOrientation.isFaceBack(face, box, playerEye, lookDir),
                    face + " should be back when inside");
        }
    }

    @Test
    void playerPositionIrrelevant_onlyLookDirectionMatters() {
        SelectionVector playerEye = new SelectionVector(15.0D, 5.0D, 5.0D);
        SelectionVector lookDir = new SelectionVector(1.0D, 0.0D, 0.0D);
        assertFalse(SelectionOrientation.isFrontFace(SelectionFace.EAST, box, playerEye, lookDir),
                "even standing east of EAST face, looking east = back");
    }
}
