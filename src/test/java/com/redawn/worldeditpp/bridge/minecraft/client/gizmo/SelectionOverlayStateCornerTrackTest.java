package com.redawn.worldeditpp.bridge.minecraft.client.gizmo;





import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectionOverlayStateCornerTrackTest {

    @Test
    void pos1AtMinXMinYMaxZ_preservedAfterExpansion() {
        int newP1X = SelectionOverlayState.trackCorner(26, 26, 27, 25, 28);
        int newP1Y = SelectionOverlayState.trackCorner(-52, -52, -51, -53, -50);
        int newP1Z = SelectionOverlayState.trackCorner(37, 34, 37, 33, 38);

        assertEquals(25, newP1X, "pos1X=26 在原 minX(26)，扩张后应跟随 minX→25");
        assertEquals(-53, newP1Y, "pos1Y=-52 在原 minY(-52)，扩张后应跟随 minY→-53");
        assertEquals(38, newP1Z, "pos1Z=37 在原 maxZ(37)，扩张后应跟随 maxZ→38");
    }

    @Test
    void pos2AtMaxXMaxYMinZ_preservedAfterExpansion() {
        int newP2X = SelectionOverlayState.trackCorner(27, 26, 27, 25, 28);
        int newP2Y = SelectionOverlayState.trackCorner(-51, -52, -51, -53, -50);
        int newP2Z = SelectionOverlayState.trackCorner(34, 34, 37, 33, 38);

        assertEquals(28, newP2X, "pos2X=27 在原 maxX(27)，扩张后应跟随 maxX→28");
        assertEquals(-50, newP2Y, "pos2Y=-51 在原 maxY(-51)，扩张后应跟随 maxY→-50");
        assertEquals(33, newP2Z, "pos2Z=34 在原 minZ(34)，扩张后应跟随 minZ→33");
    }

    @Test
    void relativeOrderPreserved_alwaysPos2X_gt_pos1X() {
        int p1x = SelectionOverlayState.trackCorner(26, 26, 27, 25, 28);
        int p2x = SelectionOverlayState.trackCorner(27, 26, 27, 25, 28);
        assertEquals(1, Integer.compare(p2x, p1x));
    }

    @Test
    void relativeOrderPreserved_alwaysPos1Z_gt_pos2Z() {
        int p1z = SelectionOverlayState.trackCorner(37, 34, 37, 33, 38);
        int p2z = SelectionOverlayState.trackCorner(34, 34, 37, 33, 38);
        assertEquals(1, Integer.compare(p1z, p2z));
    }

    @Test
    void sameBlockSelection_rawTrackCornerFollowsMin() {
        int p1 = SelectionOverlayState.trackCorner(5, 5, 5, 4, 6);
        int p2 = SelectionOverlayState.trackCorner(5, 5, 5, 4, 6);
        assertEquals(4, p1, "trackCorner原始行为：等价oldMin时返回newMin");
        assertEquals(4, p2, "trackCorner不区分pos1/pos2，两者都返回newMin");
    }

    @Test
    void oneBlockTallY_pos1FollowsMin_pos2FollowsMax() {
        int p1y = SelectionOverlayState.trackCorner(-51, -51, -51, -52, -50);
        int p2y = SelectionOverlayState.trackCorner(-51, -51, -51, -52, -50);
        assertEquals(-52, p1y);
        assertEquals(-52, p2y);
    }

    @Test
    void contractionOn2BlockWide_onlyMaxMoves_minStays() {
        int p1x = SelectionOverlayState.trackCorner(0, 0, 1, 0, 0);
        int p2x = SelectionOverlayState.trackCorner(1, 0, 1, 0, 0);
        assertEquals(0, p1x, "pos1 在 min(0) 上，min 不变 → pos1 不变");
        assertEquals(0, p2x, "pos2 在 max(1) 上，max→0 → pos2→0");
    }

    @Test
    void contractionOn3BlockWide_bothSidesMove() {
        int p1x = SelectionOverlayState.trackCorner(0, 0, 2, 1, 1);
        int p2x = SelectionOverlayState.trackCorner(2, 0, 2, 1, 1);
        assertEquals(1, p1x, "pos1 在 min(0)，min→1 → pos1→1");
        assertEquals(1, p2x, "pos2 在 max(2)，max→1 → pos2→1");
    }

    @Test
    void cornerNotOnEitherBoundary_unchanged() {
        int c = SelectionOverlayState.trackCorner(99, 0, 10, -1, 11);
        assertEquals(99, c, "不在任何边界上的坐标应保持不变");
    }

    @Test
    void doubleExpansion_chainCorrect() {
        int oldMinX = 26, oldMaxX = 27;
        int newMin1 = 25, newMax1 = 28;
        int newMin2 = 24, newMax2 = 29;

        int p1x_1 = SelectionOverlayState.trackCorner(26, oldMinX, oldMaxX, newMin1, newMax1);
        assertEquals(25, p1x_1);

        int p1x_2 = SelectionOverlayState.trackCorner(p1x_1, newMin1, newMax1, newMin2, newMax2);
        assertEquals(24, p1x_2);

        int p2x_1 = SelectionOverlayState.trackCorner(27, oldMinX, oldMaxX, newMin1, newMax1);
        assertEquals(28, p2x_1);

        int p2x_2 = SelectionOverlayState.trackCorner(p2x_1, newMin1, newMax1, newMin2, newMax2);
        assertEquals(29, p2x_2);
    }
}
