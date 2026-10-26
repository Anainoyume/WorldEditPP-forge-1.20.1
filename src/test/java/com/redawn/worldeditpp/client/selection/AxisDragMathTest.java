package com.redawn.common.selection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AxisDragMathTest {

    @Test
    void planeConstantAxesMapPerAxis() {
        assertEquals(1, AxisDragMath.plane1ConstantAxis(AxisDragMath.AXIS_X));
        assertEquals(2, AxisDragMath.plane2ConstantAxis(AxisDragMath.AXIS_X));
        assertEquals(2, AxisDragMath.plane1ConstantAxis(AxisDragMath.AXIS_Y));
        assertEquals(0, AxisDragMath.plane2ConstantAxis(AxisDragMath.AXIS_Y));
        assertEquals(1, AxisDragMath.plane1ConstantAxis(AxisDragMath.AXIS_Z));
        assertEquals(0, AxisDragMath.plane2ConstantAxis(AxisDragMath.AXIS_Z));
    }

    @Test
    void firstHitPicksNearestPlaneForXAxis() {
        double[] hit = AxisDragMath.firstHit(1, 0.0, 2, 0.0, 0.0, 10.0, 20.0, 0.0, -1.0, -1.0);

        assertNotNull(hit);
        assertEquals(0.0, hit[0], 1.0E-9);
        assertEquals(0.0, hit[1], 1.0E-9);
        assertEquals(10.0, hit[2], 1.0E-9);
    }

    @Test
    void firstHitPicksNearestPlaneForYAxis() {
        double[] hit = AxisDragMath.firstHit(2, 0.0, 0, 0.0, 10.0, 10.0, 20.0, -1.0, -1.0, -1.0);

        assertNotNull(hit);
        assertEquals(0.0, hit[0], 1.0E-9);
        assertEquals(0.0, hit[1], 1.0E-9);
        assertEquals(10.0, hit[2], 1.0E-9);
    }

    @Test
    void firstHitPicksNearestPlaneForZAxis() {
        double[] hit = AxisDragMath.firstHit(1, 0.0, 0, 0.0, 10.0, 20.0, 10.0, -1.0, -1.0, -1.0);

        assertNotNull(hit);
        assertEquals(0.0, hit[0], 1.0E-9);
        assertEquals(10.0, hit[1], 1.0E-9);
        assertEquals(0.0, hit[2], 1.0E-9);
    }

    @Test
    void firstHitOnArrowLineReturnsGrabPoint() {
        double[] hit = AxisDragMath.firstHit(1, 0.0, 2, 0.0, 0.0, 2.0, 2.0, 1.2, -2.0, -2.0);

        assertNotNull(hit);
        assertEquals(1.2, hit[0], 1.0E-9);
        assertEquals(0.0, hit[1], 1.0E-9);
        assertEquals(0.0, hit[2], 1.0E-9);
    }

    @Test
    void firstHitReturnsNullWhenRayParallelToBothPlanes() {
        assertNull(AxisDragMath.firstHit(1, 0.0, 2, 0.0, 0.0, 5.0, 5.0, 1.0, 0.0, 0.0));
    }

    @Test
    void firstHitReturnsNullWhenPlanesBehindRay() {
        assertNull(AxisDragMath.firstHit(1, 0.0, 2, 0.0, 0.0, 5.0, 5.0, 0.0, 1.0, 1.0));
    }

    @Test
    void singlePlaneFirstHitOnXOZFace() {
        double[] hit = AxisDragMath.firstHit(1, 0.0, 0.0, 5.0, 4.0, 1.2, -1.0, -2.0);

        assertNotNull(hit);
        assertEquals(6.0, hit[0], 1.0E-9);
        assertEquals(0.0, hit[1], 1.0E-9);
        assertEquals(-6.0, hit[2], 1.0E-9);
    }

    @Test
    void singlePlaneFirstHitReturnsNullWhenParallel() {
        assertNull(AxisDragMath.firstHit(1, 0.0, 0.0, 5.0, 4.0, 1.0, 0.0, 0.5));
    }

    @Test
    void faceMoveAxesExcludeNormalAxis() {
        assertArrayEquals(new int[]{1, 2}, AxisDragMath.faceMoveAxes(0));
        assertArrayEquals(new int[]{2, 0}, AxisDragMath.faceMoveAxes(1));
        assertArrayEquals(new int[]{0, 1}, AxisDragMath.faceMoveAxes(2));
    }

    @Test
    void projectedDeltaIsSignedAlongDraggedAxis() {
        assertEquals(5.0, AxisDragMath.projectedDelta(AxisDragMath.AXIS_X, 7.0, 2.0, 9.0, 2.0, 3.0, 5.0), 1.0E-9);
        assertEquals(-1.0, AxisDragMath.projectedDelta(AxisDragMath.AXIS_Y, 7.0, 2.0, 9.0, 2.0, 3.0, 5.0), 1.0E-9);
        assertEquals(4.0, AxisDragMath.projectedDelta(AxisDragMath.AXIS_Z, 7.0, 2.0, 9.0, 2.0, 3.0, 5.0), 1.0E-9);
    }

    @Test
    void playerDistanceLimitIsStrictlyBeyond100() {
        assertFalse(AxisDragMath.exceedsPlayerDistance(0.0, 0.0, 0.0, 100.0, 0.0, 0.0));
        assertFalse(AxisDragMath.exceedsPlayerDistance(10.0, 5.0, 3.0, 70.0, 45.0, 3.0));
        assertTrue(AxisDragMath.exceedsPlayerDistance(0.0, 0.0, 0.0, 100.1, 0.0, 0.0));
        assertTrue(AxisDragMath.exceedsPlayerDistance(0.0, 0.0, 0.0, 0.0, -100.1, 0.0));
        assertTrue(AxisDragMath.exceedsPlayerDistance(0.0, 0.0, 0.0, 0.0, 0.0, -100.1));
        assertTrue(AxisDragMath.exceedsPlayerDistance(0.0, 0.0, 0.0, 60.0, 80.0, 0.5));
    }

    @Test
    void callerSuppliedRenderDistanceCanExceedLegacyLimit() {
        assertFalse(AxisDragMath.exceedsPlayerDistance(
                0.0, 0.0, 0.0, 256.0, 0.0, 0.0, 256.0));
        assertTrue(AxisDragMath.exceedsPlayerDistance(
                0.0, 0.0, 0.0, 256.1, 0.0, 0.0, 256.0));
        assertTrue(AxisDragMath.exceedsPlayerDistance(
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
    }

    @Test
    void perUpdateLimitIsStrictlyBeyond20ForArrows() {
        assertFalse(AxisDragMath.exceedsPerUpdateDistance(20, 0, 0, 0, 0, 0));
        assertFalse(AxisDragMath.exceedsPerUpdateDistance(-20, 0, 0, 0, 0, 0));
        assertTrue(AxisDragMath.exceedsPerUpdateDistance(21, 0, 0, 0, 0, 0));
        assertTrue(AxisDragMath.exceedsPerUpdateDistance(0, -21, 0, 0, 0, 0));
        assertTrue(AxisDragMath.exceedsPerUpdateDistance(0, 0, 21, 0, 0, 0));
    }

    @Test
    void perUpdateLimitUsesEuclideanDistanceForFaces() {
        assertFalse(AxisDragMath.exceedsPerUpdateDistance(14, 0, 14, 0, 0, 0));
        assertTrue(AxisDragMath.exceedsPerUpdateDistance(15, 0, 15, 0, 0, 0));
        assertFalse(AxisDragMath.exceedsPerUpdateDistance(0, 14, 14, 0, 0, 0));
        assertTrue(AxisDragMath.exceedsPerUpdateDistance(0, 15, 15, 0, 0, 0));
    }
}
