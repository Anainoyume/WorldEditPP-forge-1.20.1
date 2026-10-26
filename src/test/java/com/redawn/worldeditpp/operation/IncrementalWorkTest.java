package com.redawn.worldeditpp.operation;

import com.redawn.worldeditpp.operation.task.*;
import com.redawn.worldeditpp.client.selection.projection.*;
import com.redawn.worldeditpp.math.ProjectionGridTransforms;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IncrementalWorkTest {
    @Test void budgetHonoursTimeAndWork() {
        AtomicLong clock = new AtomicLong(10);
        TickBudget budget = new TickBudget(3, 10, clock::get);
        assertTrue(budget.take()); clock.set(20); assertFalse(budget.take());
        budget = new TickBudget(2, 100, clock::get);
        assertTrue(budget.take()); assertTrue(budget.take()); assertFalse(budget.take());
    }

    @Test void repeatedLayoutMatchesExpandedCoordinatesUnderEveryTurnAndMirror() {
        for (int axis = 0; axis < 3; axis++) for (int turn : new int[]{-1, 1}) for (boolean mirror : new boolean[]{false, true}) {
            ProjectionLayout layout = new ProjectionLayout(2, 3, 4);
            layout.stretch(0, true); layout.stretch(2, true);
            int sx = layout.size(0), sy = layout.size(1), sz = layout.size(2);
            layout.transform(axis, turn, mirror);
            for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) {
                int[] target = mirror ? ProjectionGridTransforms.mirrorCoordinates(new int[]{x,y,z},axis,sx,sy,sz)
                        : ProjectionGridTransforms.rotateCoordinates(new int[]{x,y,z},axis,turn,sx,sy,sz);
                assertEquals((x % 2) + 2 * ((y % 3) + 3 * (z % 4)), layout.sourceCell(target[0],target[1],target[2]));
            }
            assertEquals(4, layout.copies());
        }
    }

    @Test void layoutNeverShrinksBelowOneTileAndRejectsOverflow() {
        var layout = new ProjectionLayout(256,256,256);
        assertEquals(-1, layout.copiesAfterStretch(0, true));
        assertEquals(-1, layout.copiesAfterStretch(1, false));
        assertEquals(-1, layout.sourceCell(-1,0,0));
    }

    @Test void resizeInvalidatesOnlyChangedPagesAndTheirNeighbourSeams() {
        ProjectionPage interior = ProjectionPage.at(0,0,0,32,20,20);
        ProjectionPage edge = ProjectionPage.at(16,0,0,32,20,20);
        assertTrue(interior.matches(48,20,20));
        assertFalse(edge.matches(48,20,20));
        assertTrue(ProjectionPage.at(16,0,0,48,20,20).matches(64,20,20));
        assertFalse(ProjectionPage.at(32,0,0,48,20,20).matches(32,20,20));
        assertFalse(interior.matches(15,20,20));
    }
}

