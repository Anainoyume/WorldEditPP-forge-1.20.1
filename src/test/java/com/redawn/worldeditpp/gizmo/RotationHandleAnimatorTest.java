package com.redawn.common.gizmo;

import com.redawn.common.selection.SelectionFace;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RotationHandleAnimatorTest {
    @Test
    void faceChangesCrossFadeInTwoTenthsOfASecond() {
        RotationHandleAnimator animator = new RotationHandleAnimator();
        long start = 1L;

        animator.update(SelectionFace.EAST, start);
        animator.update(SelectionFace.EAST, start + 100_000_000L);
        assertEquals(0.5F, animator.progress(SelectionFace.EAST), 0.0001F);

        animator.update(SelectionFace.UP, start + 100_000_000L);
        animator.update(SelectionFace.UP, start + 200_000_000L);
        assertEquals(0.0F, animator.progress(SelectionFace.EAST), 0.0001F);
        assertEquals(0.5F, animator.progress(SelectionFace.UP), 0.0001F);

        animator.update(SelectionFace.UP, start + 300_000_000L);
        assertEquals(1.0F, animator.progress(SelectionFace.UP), 0.0001F);
    }
}
