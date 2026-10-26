package com.redawn.common.selection;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionInteractionStateTest {
    @Test
    void resetsInteraction() {
        SelectionInteractionState state = new SelectionInteractionState();
        state.updateEmptyHand(true);
        state.updateHover(Optional.of(new SelectionFaceHit(SelectionFace.NORTH, SelectionVector.ZERO, 1.0D)));
        state.selectFace(SelectionFace.NORTH);

        state.reset();

        assertTrue(state.hoverHit().isEmpty());
        assertNull(state.selectedFace());
        assertTrue(state.isEmptyHand());
    }

    @Test
    void selectedFaceDoesNotPlayHoverAnimation() {
        SelectionInteractionState state = new SelectionInteractionState();
        state.updateHover(Optional.of(new SelectionFaceHit(SelectionFace.NORTH, SelectionVector.ZERO, 1.0D)));
        state.selectFace(SelectionFace.NORTH);

        state.tickAnimations();

        assertEquals(0.0F, state.hoverProgress(SelectionFace.NORTH));
        assertTrue(state.selectedProgress(SelectionFace.NORTH) > 0.0F);
    }
}
