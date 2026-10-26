package com.redawn.worldeditpp.mode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EditSubModeTest {
    @Test
    void stableIdsAndNetworkValuesRoundTrip() {
        for (EditSubMode mode : EditSubMode.values()) {
            assertEquals(mode, EditSubMode.fromId(mode.id()));
            assertEquals(mode, EditSubMode.fromNetworkValue(mode.networkValue()).orElseThrow());
        }
        assertTrue(EditSubMode.fromNetworkValue(1.5).isEmpty());
        assertTrue(EditSubMode.fromNetworkValue(99).isEmpty());
    }

    @Test
    void legacyIdsMigrateByOriginalShortcutPosition() {
        assertEquals(EditSubMode.MOVE, EditSubMode.fromId("selection"));
        assertEquals(EditSubMode.STRETCH, EditSubMode.fromId("build"));
        assertEquals(EditSubMode.MOVE, EditSubMode.fromId("unknown"));
    }

    @Test
    void eachModeOwnsOnlyItsCapability() {
        assertTrue(EditSubMode.MOVE.supports(EditSubMode.Capability.TRANSLATE));
        assertFalse(EditSubMode.MOVE.supports(EditSubMode.Capability.STRETCH));
        assertTrue(EditSubMode.STRETCH.supports(EditSubMode.Capability.STRETCH));
        assertFalse(EditSubMode.STRETCH.supports(EditSubMode.Capability.TRANSLATE));
        assertTrue(EditSubMode.ROTATE.supports(EditSubMode.Capability.ROTATE));
        assertFalse(EditSubMode.ROTATE.supports(EditSubMode.Capability.STRETCH));
    }
}
