package com.redawn.common.selection;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionFaceRaycasterTest {
    private final SelectionFaceRaycaster raycaster = new SelectionFaceRaycaster();
    private final SelectionBox box = new SelectionBox(0.0D, 0.0D, 0.0D, 2.0D, 2.0D, 2.0D);

    @Test
    void picksNearestFrontFaceByDefault() {
        Optional<SelectionFaceHit> hit = raycaster.pick(box, new SelectionVector(1.0D, 1.0D, -4.0D), new SelectionVector(0.0D, 0.0D, 1.0D), false);

        assertTrue(hit.isPresent());
        assertEquals(SelectionFace.NORTH, hit.get().face());
        assertEquals(4.0D, hit.get().distance(), 1.0E-7D);
    }

    @Test
    void altModePicksBackFaceBehindFrontFace() {
        Optional<SelectionFaceHit> hit = raycaster.pick(box, new SelectionVector(1.0D, 1.0D, -4.0D), new SelectionVector(0.0D, 0.0D, 1.0D), true);

        assertTrue(hit.isPresent());
        assertEquals(SelectionFace.SOUTH, hit.get().face());
        assertEquals(6.0D, hit.get().distance(), 1.0E-7D);
    }

    @Test
    void ignoresHitsOutsideFaceBounds() {
        Optional<SelectionFaceHit> hit = raycaster.pick(box, new SelectionVector(3.0D, 1.0D, -4.0D), new SelectionVector(0.0D, 0.0D, 1.0D), false);

        assertTrue(hit.isEmpty());
    }

    @Test
    void picksExitFaceFromInsideWithoutAlt() {
        Optional<SelectionFaceHit> hit = raycaster.pick(box, new SelectionVector(1.0D, 1.0D, 1.0D), new SelectionVector(0.0D, 0.0D, 1.0D), false);

        assertTrue(hit.isPresent());
        assertEquals(SelectionFace.SOUTH, hit.get().face());
        assertEquals(1.0D, hit.get().distance(), 1.0E-7D);
    }

    @Test
    void picksExitFaceFromInsideWithAlt() {
        Optional<SelectionFaceHit> hit = raycaster.pick(box, new SelectionVector(1.0D, 1.0D, 1.0D), new SelectionVector(1.0D, 0.0D, 0.0D), true);

        assertTrue(hit.isPresent());
        assertEquals(SelectionFace.EAST, hit.get().face());
        assertEquals(1.0D, hit.get().distance(), 1.0E-7D);
    }
}
