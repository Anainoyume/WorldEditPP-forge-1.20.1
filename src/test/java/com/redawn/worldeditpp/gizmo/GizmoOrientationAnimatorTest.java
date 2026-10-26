package com.redawn.common.gizmo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GizmoOrientationAnimatorTest {
    @Test
    void firstOctantIsAppliedWithoutAnEntranceAnimation() {
        GizmoOrientationAnimator animator = new GizmoOrientationAnimator();
        animator.update(-2.0, 3.0, -4.0, 0L);

        assertEquals(-1.0F, animator.x());
        assertEquals(1.0F, animator.y());
        assertEquals(-1.0F, animator.z());
        assertFalse(animator.isTransitioning());
    }

    @Test
    void directionChangeShrinksThroughZeroAndFinishesInTwoTenths() {
        GizmoOrientationAnimator animator = new GizmoOrientationAnimator();
        animator.update(1.0, 1.0, 1.0, 0L);
        animator.update(-1.0, 1.0, 1.0, 0L);
        assertTrue(animator.isTransitioning());

        animator.update(-1.0, 1.0, 1.0, 100_000_000L);
        assertTrue(animator.x() < 0.0F);
        assertTrue(Math.abs(animator.x()) < 1.0F);

        animator.update(-1.0, 1.0, 1.0, GizmoOrientationAnimator.DURATION_NANOS);
        assertEquals(-1.0F, animator.x());
        assertFalse(animator.isTransitioning());
    }

    @Test
    void rapidReversalContinuesFromTheCurrentValue() {
        GizmoOrientationAnimator animator = new GizmoOrientationAnimator();
        animator.update(1.0, 1.0, 1.0, 0L);
        animator.update(-1.0, 1.0, 1.0, 0L);
        animator.update(-1.0, 1.0, 1.0, 80_000_000L);
        float beforeReversal = animator.x();

        animator.update(1.0, 1.0, 1.0, 80_000_000L);
        assertEquals(beforeReversal, animator.x());
        assertTrue(animator.isTransitioning());

        animator.update(1.0, 1.0, 1.0, 280_000_000L);
        assertEquals(1.0F, animator.x());
        assertFalse(animator.isTransitioning());
    }

    @Test
    void exactCenterPlaneKeepsTheExistingTarget() {
        GizmoOrientationAnimator animator = new GizmoOrientationAnimator();
        animator.update(-1.0, -1.0, -1.0, 0L);
        animator.update(0.0, 0.0, 0.0, 500_000_000L);

        assertEquals(-1.0F, animator.x());
        assertEquals(-1.0F, animator.y());
        assertEquals(-1.0F, animator.z());
        assertFalse(animator.isTransitioning());
    }
}
