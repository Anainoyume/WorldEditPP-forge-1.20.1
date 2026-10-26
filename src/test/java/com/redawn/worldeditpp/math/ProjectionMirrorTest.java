package com.redawn.worldeditpp.math;

import com.redawn.common.selection.SelectionBox;
import com.redawn.common.selection.SelectionFace;
import com.redawn.common.selection.SelectionTransforms;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectionMirrorTest {
    @Test
    void eachFaceMovesBoundsExactlyAcrossItsOuterPlane() {
        SelectionBox box = new SelectionBox(-9, 11, -4, -7, 12, -1);
        SelectionBox[] expected = {
                new SelectionBox(-12, 11, -4, -10, 12, -1),
                new SelectionBox(-6, 11, -4, -4, 12, -1),
                new SelectionBox(-9, 9, -4, -7, 10, -1),
                new SelectionBox(-9, 13, -4, -7, 14, -1),
                new SelectionBox(-9, 11, -8, -7, 12, -5),
                new SelectionBox(-9, 11, 0, -7, 12, 3)
        };
        for (SelectionFace face : SelectionFace.values()) {
            SelectionBox mirrored = SelectionTransforms.mirror(box, face);
            assertEquals(expected[face.ordinal()], mirrored);
            SelectionFace opposite = SelectionFace.values()[face.ordinal() ^ 1];
            assertEquals(box, SelectionTransforms.mirror(mirrored, opposite));
        }
        assertEquals(new SelectionBox(1, 0, 0, 1, 0, 0),
                SelectionTransforms.mirror(new SelectionBox(0, 0, 0, 0, 0, 0), SelectionFace.EAST));
    }

    @Test
    void nonCubicGridReflectionIsABijectionAndAnInvolution() {
        for (int axis = 0; axis < 3; axis++) {
            Set<String> occupied = new HashSet<>();
            for (int x = 0; x < 3; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 4; z++) {
                int[] source = {x, y, z};
                int[] mirrored = ProjectionGridTransforms.mirrorCoordinates(source, axis, 3, 2, 4);
                assertArrayEquals(source, ProjectionGridTransforms.mirrorCoordinates(mirrored, axis, 3, 2, 4));
                assertTrue(occupied.add(Arrays.toString(mirrored)));
                assertTrue(mirrored[0] >= 0 && mirrored[0] < 3);
                assertTrue(mirrored[1] >= 0 && mirrored[1] < 2);
                assertTrue(mirrored[2] >= 0 && mirrored[2] < 4);
            }
            assertEquals(24, occupied.size());
        }
        assertThrows(IllegalArgumentException.class,
                () -> ProjectionGridTransforms.mirrorCoordinates(new int[]{0, 0, 0}, 3, 1, 1, 1));
    }

    @Test
    void slabsFlipOnlyVerticallyAndRecoverAfterMixedTransforms() {
        for (SlabStateTransform.Type type : SlabStateTransform.Type.values()) {
            for (int axis = 0; axis < 3; axis++) {
                DiscreteRotation mirror = DiscreteRotation.IDENTITY.thenWorldMirror(axis);
                SlabStateTransform.Type expected = axis != 1 ? type : switch (type) {
                    case BOTTOM -> SlabStateTransform.Type.TOP;
                    case TOP -> SlabStateTransform.Type.BOTTOM;
                    case DOUBLE -> type;
                };
                assertEquals(expected, SlabStateTransform.transform(type, mirror).type());
                assertEquals(type, SlabStateTransform.transform(type, mirror.thenWorldMirror(axis)).type());
                DiscreteRotation mixed = mirror.thenWorldQuarterTurn(0, 1).thenWorldQuarterTurn(0, -1);
                assertEquals(SlabStateTransform.transform(type, mirror), SlabStateTransform.transform(type, mixed));
            }
        }
    }
}
