package com.redawn.common.ui.radial;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RadialMenuLayoutTest {
    @Test void anyEntryCountDividesTheRingEvenly() {
        for (int count : new int[]{1, 2, 3, 4, 7, 12, 32}) {
            var layout = new RadialMenuLayout(count, 32, 88, 9);
            for (int index = 0; index < count; index++) {
                for (double offset : new double[]{-0.49, 0, 0.49}) {
                    double angle = layout.centerAngle(index) + offset * layout.step();
                    assertEquals(index, layout.hit(Math.cos(angle) * 60, Math.sin(angle) * 60));
                }
            }
            assertEquals(Math.PI * 2, count * layout.step(), 1e-12);
        }
    }

    @Test void centerAndOutsideAreNeutralAndPushedEdgeRemainsSelectable() {
        var layout = new RadialMenuLayout(2, 32, 88, 9);
        assertEquals(-1, layout.hit(0, 0));
        assertEquals(-1, layout.hit(0, -32));
        assertEquals(0, layout.hit(0, -96));
        assertEquals(1, layout.hit(0, 60));
        assertEquals(-1, layout.hit(0, -98));
    }

    @Test void hoverReversalIsContinuousBoundedAndFrameRateIndependent() {
        double halfway = RadialMenuLayout.animate(0, true, 0.05);
        assertTrue(halfway > 0 && halfway < 1);
        assertEquals(halfway, RadialMenuLayout.animate(halfway, false, 0));
        double returning = RadialMenuLayout.animate(halfway, false, 0.03);
        assertTrue(returning > 0 && returning < halfway);
        assertTrue(RadialMenuLayout.animate(0, true, 0.2) > 0.97);
        double manyFrames = 0;
        for (int i = 0; i < 20; i++) manyFrames = RadialMenuLayout.animate(manyFrames, true, 0.01);
        assertEquals(RadialMenuLayout.animate(0, true, 0.2), manyFrames, 1e-12);
    }

    @Test void invalidDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RadialMenuLayout(0, 32, 88, 9));
        assertThrows(IllegalArgumentException.class, () -> new RadialMenuLayout(2, 88, 32, 9));
        assertThrows(IllegalArgumentException.class, () -> new RadialMenuLayout(2, 32, Double.NaN, 9));
    }
}
