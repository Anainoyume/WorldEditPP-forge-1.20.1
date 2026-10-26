package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectionCoordinateSpaceRenderTest {
    @Test
    void cameraTranslationKeepsPrecisionNearWorldBorder() {
        assertEquals(3.25F, SelectionBlockProjectionLayer.cameraRelativeOrigin(
                29_999_990.0D, 29_999_986.75D));
        assertEquals(-2.5F, SelectionBlockProjectionLayer.cameraRelativeOrigin(
                -29_999_990.0D, -29_999_987.5D));
    }

    @Test
    void modelFluidAndBlockEntityOriginsUseTheSameSnapshotLocalSpace() {
        assertEquals(5, SelectionProjectionMesh.localCoordinate(29_999_995, 29_999_990));
        assertEquals(-6, SelectionProjectionMesh.localSectionOrigin(29_999_995, 29_999_990));
        assertEquals(5, SelectionProjectionMesh.localSectionOrigin(29_999_995, 29_999_990)
                + (29_999_995 & 15));

        assertEquals(7, SelectionProjectionMesh.localCoordinate(-29_999_983, -29_999_990));
        assertEquals(6, SelectionProjectionMesh.localSectionOrigin(-29_999_983, -29_999_990));
        assertEquals(7, SelectionProjectionMesh.localSectionOrigin(-29_999_983, -29_999_990)
                + (-29_999_983 & 15));
    }
}
