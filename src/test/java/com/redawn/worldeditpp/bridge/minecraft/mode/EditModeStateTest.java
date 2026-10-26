package com.redawn.worldeditpp.bridge.minecraft.mode;



import com.redawn.worldeditpp.mode.EditModeState;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditModeStateTest {
    @AfterEach
    void tearDown() {
        EditModeState.clearAll();
    }

    @Test
    void tracksGlobalEditStateAcrossPlayers() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        EditModeState.setEditing(first, true);
        assertTrue(EditModeState.isInEditMode);
        assertTrue(EditModeState.isEditing(first));
        assertFalse(EditModeState.isEditing(second));

        EditModeState.setEditing(second, true);
        assertTrue(EditModeState.isInEditMode);
        assertTrue(EditModeState.isEditing(first));
        assertTrue(EditModeState.isEditing(second));

        EditModeState.setEditing(first, false);
        assertTrue(EditModeState.isInEditMode);
        assertFalse(EditModeState.isEditing(first));
        assertTrue(EditModeState.isEditing(second));

        EditModeState.setEditing(second, false);
        assertFalse(EditModeState.isInEditMode);
        assertFalse(EditModeState.isEditing(first));
        assertFalse(EditModeState.isEditing(second));
    }

    @Test
    void keepsIntegratedClientAndServerStateIndependent() {
        UUID player = UUID.randomUUID();

        EditModeState.setServerEditing(player, true);
        assertTrue(EditModeState.isServerEditing(player));
        assertFalse(EditModeState.isClientEditing(player));

        EditModeState.setClientEditing(player, true);
        EditModeState.setServerEditing(player, false);
        assertTrue(EditModeState.isClientEditing(player));
        assertFalse(EditModeState.isServerEditing(player));
        assertTrue(EditModeState.isInEditMode);

        EditModeState.setClientEditing(player, false);
        assertFalse(EditModeState.isInEditMode);
    }
}
